package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ResultGeneratorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val context = application.applicationContext
    private val organizationId: String? = FirebaseAuthManager.getOrganizationId(context)
    private val reportCardGenerator = ReportCardGenerator(context)
    private val TAG = "ResultGeneratorVM"

    private companion object {
        private const val ORG_ADDRESS_FULL = "BIBI AAISHA MASJID SARNI SOCIETY AHMEDABAD"
    }

    // LiveData to communicate status back to the UI (MainActivity)
    private val _generationStatus = MutableLiveData<Event<Pair<Boolean, String>>>()
    val generationStatus: LiveData<Event<Pair<Boolean, String>>> = _generationStatus

    fun generateSingleStudentReport(student: StudentDetailsItem, exam: Exam) {
        if (organizationId == null) {
            _generationStatus.value = Event(Pair(false, "Organization ID missing."))
            return
        }
        _generationStatus.value = Event(Pair(true, "Generating Report..."))

        viewModelScope.launch {
            try {
                val subjects = db.collection("organizations").document(organizationId)
                    .collection("subjects").whereEqualTo("teacherId", student.teacherId)
                    .get().await().toObjects<SubjectItem>()

                val docId = "${exam.id}_${student.id}"
                val marksDoc = db.collection("organizations").document(organizationId)
                    .collection("examResults").document(docId).get().await()
                val marks = marksDoc.get("marks") as? Map<String, String> ?: emptyMap()

                val reportData = ReportCardGenerator.ReportData(student, exam.name, marks, subjects)
                val organizationName = FirebaseAuthManager.getOrganizationName(context) ?: "Madarsa"
                val organizationAddress = ORG_ADDRESS_FULL

                withContext(Dispatchers.IO) {
                    reportCardGenerator.generateSingleReport(reportData, organizationName, organizationAddress)
                }
                withContext(Dispatchers.Main) {
                    _generationStatus.value = Event(Pair(true, "Report Generated Successfully!"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error generating single student report", e)
                withContext(Dispatchers.Main) {
                    _generationStatus.value = Event(Pair(false, "Report Generation Failed"))
                }
            }
        }
    }

    fun generateClassReport(teacher: Teacher, exam: Exam) {
        if (organizationId == null) {
            _generationStatus.value = Event(Pair(false, "Organization ID missing."))
            return
        }
        _generationStatus.value = Event(Pair(true, "Generating Bulk Report..."))

        viewModelScope.launch {
            try {
                val students = db.collection("organizations").document(organizationId)
                    .collection("students").whereEqualTo("teacherId", teacher.teacherId)
                    .get().await().toObjects<StudentDetailsItem>()

                if (students.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _generationStatus.value = Event(Pair(false, "No students found in this class."))
                    }
                    return@launch
                }

                val subjects = db.collection("organizations").document(organizationId)
                    .collection("subjects").whereEqualTo("teacherId", teacher.teacherId)
                    .get().await().toObjects<SubjectItem>()

                val marksQuery = db.collection("organizations").document(organizationId)
                    .collection("examResults").whereEqualTo("examId", exam.id)
                    .whereEqualTo("teacherId", teacher.teacherId).get().await()
                val marksMap = marksQuery.documents.associate { it.getString("studentId") to it.get("marks") as? Map<String, String> }

                val reportDataList = students.map { student ->
                    ReportCardGenerator.ReportData(student, exam.name, marksMap[student.id] ?: emptyMap(), subjects)
                }
                val organizationName = FirebaseAuthManager.getOrganizationName(context) ?: "Madarsa"
                val organizationAddress = ORG_ADDRESS_FULL

                withContext(Dispatchers.IO) {
                    reportCardGenerator.generateBulkReport(reportDataList, organizationName, organizationAddress)
                }
                withContext(Dispatchers.Main) {
                    _generationStatus.value = Event(Pair(true, "Bulk Report Generated!"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error generating class report", e)
                withContext(Dispatchers.Main) {
                    _generationStatus.value = Event(Pair(false, "Bulk Report Failed"))
                }
            }
        }
    }
}