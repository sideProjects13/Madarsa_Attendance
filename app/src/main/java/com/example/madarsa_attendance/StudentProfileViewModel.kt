package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

// Data class for the summary, can stay in this file
data class AttendanceSummary(val presentDays: Int, val absentDays: Int, val percentage: Double)

class StudentProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val organizationId = FirebaseAuthManager.getOrganizationId(application)
    private val TAG = "StudentProfileVM" // Added TAG for logging
    private val reportCardGenerator = ReportCardGenerator(application) // Added for generating reports

    // --- EXISTING LiveData (Unchanged) ---
    private val _student = MutableLiveData<StudentDetailsItem?>()
    val student: LiveData<StudentDetailsItem?> = _student

    private val _attendanceSummary = MutableLiveData<AttendanceSummary?>()
    val attendanceSummary: LiveData<AttendanceSummary?> = _attendanceSummary

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    // --- END OF EXISTING LiveData ---

    // --- NEW: LiveData for historical data ---
    private val _classHistory = MutableLiveData<List<ClassHistoryItem>>()
    val classHistory: LiveData<List<ClassHistoryItem>> = _classHistory

    private val _examHistory = MutableLiveData<List<ExamHistoryItem>>()
    val examHistory: LiveData<List<ExamHistoryItem>> = _examHistory
    // --- END OF NEW LiveData ---


    // --- EXISTING loadStudentData function (Modified to fetch new data) ---
    fun loadStudentData(studentId: String) {
        if (organizationId == null) {
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // This maintains your original logic of fetching student details first
                val studentDoc = db.collection("organizations").document(organizationId)
                    .collection("students").document(studentId).get().await()

                val studentDetails = studentDoc.toObject<StudentDetailsItem>()
                _student.postValue(studentDetails) // Use postValue for thread safety

                if (studentDetails != null) {
                    // Then fetch the other data based on the student details
                    val summary = calculateAttendanceSummary(studentDetails.id, studentDetails.teacherId)
                    _attendanceSummary.postValue(summary)

                    // --- NEW: Fetch historical data ---
                    val classHistoryList = fetchClassHistory(studentDetails.id)
                    _classHistory.postValue(classHistoryList)

                    val examHistoryList = fetchExamHistory(studentDetails.id)
                    _examHistory.postValue(examHistoryList)
                    // --- END OF NEW LOGIC ---
                }
            } catch (e: Exception) {
                _student.postValue(null)
                _attendanceSummary.postValue(null)
                // --- NEW: Post empty lists on error ---
                _classHistory.postValue(emptyList())
                _examHistory.postValue(emptyList())
                Log.e(TAG, "Error loading student data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- EXISTING calculateAttendanceSummary function (Unchanged) ---
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
            Log.e(TAG, "Error calculating attendance summary", e)
        }

        val totalDays = presentDays + absentDays
        val percentage = if (totalDays > 0) {
            (presentDays.toDouble() / totalDays.toDouble()) * 100
        } else {
            0.0
        }

        return AttendanceSummary(presentDays, absentDays, percentage)
    }

    // --- NEW: Function to fetch and format class history ---
    private suspend fun fetchClassHistory(studentId: String): List<ClassHistoryItem> {
        if (organizationId == null) return emptyList()
        return try {
            val historySnapshot = db.collection("organizations").document(organizationId)
                .collection("students").document(studentId)
                .collection("studentClassHistory")
                .orderBy("startDate", Query.Direction.DESCENDING)
                .get().await()

            val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            historySnapshot.toObjects<StudentClassHistory>().map { historyDoc ->
                val startDate = historyDoc.startDate?.let { dateFormat.format(it) } ?: "N/A"
                val endDate = historyDoc.endDate?.let { dateFormat.format(it) } ?: "Present"
                ClassHistoryItem(
                    teacherName = historyDoc.teacherName,
                    academicYear = historyDoc.academicYear,
                    duration = "$startDate - $endDate"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching class history", e)
            emptyList()
        }
    }

    // --- NEW: Function to fetch and format exam history ---
    private suspend fun fetchExamHistory(studentId: String): List<ExamHistoryItem> {
        if (organizationId == null) return emptyList()
        return try {
            val resultsSnapshot = db.collection("organizations").document(organizationId)
                .collection("examResults")
                .whereEqualTo("studentId", studentId)
                .orderBy("resultDate", Query.Direction.DESCENDING)
                .get().await()

            resultsSnapshot.toObjects<ExamResult>().map { resultDoc ->
                ExamHistoryItem(
                    examName = resultDoc.examName,
                    academicYear = resultDoc.academicYear,
                    teacherName = resultDoc.teacherName,
                    fullResult = resultDoc
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching exam history", e)
            emptyList()
        }
    }

    // --- NEW: Function to generate a historical report card ---
    fun generateHistoricalReport(examHistoryItem: ExamHistoryItem) {
        viewModelScope.launch {
            val studentDetails = _student.value
            if (studentDetails == null) {
                Log.e(TAG, "Cannot generate report, student details are null.")
                return@launch
            }

            val subjectsForReport = examHistoryItem.fullResult.subjects.map {
                SubjectItem(id = it.subjectId, subjectName = it.subjectName)
            }

            val reportData = ReportCardGenerator.ReportData(
                student = studentDetails,
                examName = examHistoryItem.examName,
                marks = examHistoryItem.fullResult.marks,
                subjects = subjectsForReport
            )
            reportCardGenerator.generateSingleReport(reportData)
        }
    }
}