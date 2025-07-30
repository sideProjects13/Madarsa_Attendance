package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val context = application.applicationContext

    private val organizationId: String? = FirebaseAuthManager.getOrganizationId(context)

    private val _leaderboardData = MutableLiveData<List<LeaderboardItem>>()
    val leaderboardData: LiveData<List<LeaderboardItem>> = _leaderboardData

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadLeaderboardForYear(year: Int) {
        if (organizationId == null) {
            _errorMessage.value = "Organization information missing. Cannot load leaderboard."
            _isLoading.value = false
            _leaderboardData.value = emptyList()
            Log.e("LeaderboardViewModel", "Organization ID is NULL. Leaderboard data cannot be loaded.")
            return
        }

        Log.d("LeaderboardViewModel", "Loading leaderboard for year: $year, Org ID: $organizationId")
        _isLoading.value = true
        _errorMessage.value = null

        val firstDayOfYear = "$year-01-01"
        val lastDayOfYear = "$year-12-31"

        viewModelScope.launch {
            try {
                fetchTeachersAndStudents(firstDayOfYear, lastDayOfYear, organizationId)
            } catch (e: Exception) {
                Log.e("LeaderboardViewModel", "Error in loadLeaderboardForYear: ${e.message}", e)
                _errorMessage.postValue("An unexpected error occurred.")
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun fetchTeachersAndStudents(firstDay: String, lastDay: String, orgId: String) {
        val teachersMap = mutableMapOf<String, String>()
        val studentsMap = mutableMapOf<String, Pair<String, String>>()

        try {
            val teachersSnapshot = db.collection("organizations").document(orgId)
                .collection("teachers").get().await()
            teachersSnapshot.forEach { doc ->
                teachersMap[doc.id] = doc.getString("teacherName") ?: "Unknown Teacher"
            }

            val studentsSnapshot = db.collection("organizations").document(orgId)
                .collection("students").get().await()

            if (studentsSnapshot.isEmpty) {
                _errorMessage.postValue("No students found in the tuition.")
                _isLoading.postValue(false)
                _leaderboardData.postValue(emptyList())
                return
            }
            studentsSnapshot.forEach { doc ->
                val studentId = doc.id
                val studentName = doc.getString("studentName") ?: "N/A"
                val teacherId = doc.getString("teacherId") ?: ""
                studentsMap[studentId] = Pair(studentName, teacherId)
            }

            fetchAttendanceAndProcess(firstDay, lastDay, studentsMap, teachersMap, orgId)

        } catch (e: Exception) {
            Log.e("LeaderboardViewModel", "Error fetching teachers or students for org $orgId: ${e.message}", e)
            _errorMessage.postValue("Error loading teachers or students.")
            _isLoading.postValue(false)
        }
    }

    private suspend fun fetchAttendanceAndProcess(
        firstDay: String, lastDay: String,
        studentsMap: Map<String, Pair<String, String>>,
        teachersMap: Map<String, String>,
        orgId: String
    ) {
        val studentPresentDays = mutableMapOf<String, Int>()
        val studentAbsentDays = mutableMapOf<String, Int>()

        try {
            val attendanceSnapshot = db.collection("organizations").document(orgId)
                .collection("attendanceRecords")
                .whereGreaterThanOrEqualTo("date", firstDay)
                .whereLessThanOrEqualTo("date", lastDay).get().await()

            attendanceSnapshot.forEach { recordDoc ->
                val studentAttendances = recordDoc.get("studentAttendances") as? List<Map<String, Any>>
                studentAttendances?.forEach { att ->
                    val studentId = att["studentId"] as? String
                    val status = att["status"] as? String
                    if (studentId != null && studentsMap.containsKey(studentId)) {
                        when (status) {
                            "Present" -> studentPresentDays[studentId] = (studentPresentDays[studentId] ?: 0) + 1
                            "Absent" -> studentAbsentDays[studentId] = (studentAbsentDays[studentId] ?: 0) + 1
                        }
                    }
                }
            }
            processAndDisplayLeaderboard(studentsMap, teachersMap, studentPresentDays, studentAbsentDays)
        } catch (e: Exception) {
            Log.e("LeaderboardViewModel", "Error fetching attendance records for org $orgId: ${e.message}", e)
            _errorMessage.postValue("Error loading attendance data.")
            _isLoading.postValue(false)
        }
    }

    private fun processAndDisplayLeaderboard(
        studentsMap: Map<String, Pair<String, String>>,
        teachersMap: Map<String, String>,
        studentPresentDays: Map<String, Int>,
        studentAbsentDays: Map<String, Int>
    ) {
        val newLeaderboardList = mutableListOf<LeaderboardItem>()
        studentsMap.forEach { (studentId, studentData) ->
            val (studentName, teacherId) = studentData
            val presentDays = studentPresentDays[studentId] ?: 0
            val absentDays = studentAbsentDays[studentId] ?: 0
            val studentTotalMarked = presentDays + absentDays
            val percentage = if (studentTotalMarked > 0) (presentDays.toDouble() / studentTotalMarked.toDouble()) * 100.0 else 0.0
            val teacherName = teachersMap[teacherId] ?: "No Class"
            newLeaderboardList.add(LeaderboardItem(studentId, studentName, presentDays, absentDays, studentTotalMarked, percentage, teacherName))
        }

        newLeaderboardList.sortWith(compareByDescending<LeaderboardItem> { it.attendancePercentage }.thenByDescending { it.presentDays })

        _isLoading.value = false
        if (newLeaderboardList.isEmpty()) {
            _errorMessage.value = "No attendance data for the selected year."
        }
        _leaderboardData.postValue(newLeaderboardList)
    }
}