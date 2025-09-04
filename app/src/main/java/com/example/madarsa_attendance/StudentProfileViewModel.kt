package com.example.madarsa_attendance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Data class for the summary, can stay in this file
data class AttendanceSummary(val presentDays: Int, val absentDays: Int, val percentage: Double)

class StudentProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val organizationId = FirebaseAuthManager.getOrganizationId(application)

    private val _student = MutableLiveData<StudentDetailsItem?>()
    val student: LiveData<StudentDetailsItem?> = _student

    private val _attendanceSummary = MutableLiveData<AttendanceSummary?>()
    val attendanceSummary: LiveData<AttendanceSummary?> = _attendanceSummary

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadStudentData(studentId: String) {
        if (organizationId == null) {
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val studentDoc = db.collection("organizations").document(organizationId)
                    .collection("students").document(studentId).get().await()

                val studentDetails = studentDoc.toObject<StudentDetailsItem>()
                _student.value = studentDetails

                if (studentDetails != null) {
                    val summary = calculateAttendanceSummary(studentDetails.id, studentDetails.teacherId)
                    _attendanceSummary.value = summary
                }
            } catch (e: Exception) {
                _student.value = null
                _attendanceSummary.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun calculateAttendanceSummary(studentId: String, teacherId: String): AttendanceSummary {
        if (organizationId == null) return AttendanceSummary(0, 0, 0.0)

        var presentDays = 0
        var absentDays = 0

        try {
            val snapshot = db.collection("organizations").document(organizationId)
                .collection("attendanceRecords")
                .whereEqualTo("teacherId", teacherId)
                .orderBy("date", Query.Direction.ASCENDING)
                .get().await()

            for (doc in snapshot.documents) {
                val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>
                val studentStatus = studentAttendances?.find { it["studentId"] == studentId }

                if (studentStatus != null) {
                    when (studentStatus["status"] as? String) {
                        "Present" -> presentDays++
                        "Absent" -> absentDays++
                    }
                }
            }
        } catch (e: Exception) {
            // Handle Firestore query error
        }

        val totalDays = presentDays + absentDays
        val percentage = if (totalDays > 0) {
            (presentDays.toDouble() / totalDays.toDouble()) * 100
        } else {
            0.0
        }

        return AttendanceSummary(presentDays, absentDays, percentage)
    }
}