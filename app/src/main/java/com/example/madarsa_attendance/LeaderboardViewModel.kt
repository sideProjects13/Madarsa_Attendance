package com.example.madarsa_attendance

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class LeaderboardViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // LiveData to hold the state for the UI
    private val _leaderboardData = MutableLiveData<List<LeaderboardItem>>()
    val leaderboardData: LiveData<List<LeaderboardItem>> = _leaderboardData

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadLeaderboardForYear(year: Int) {
        Log.d("LeaderboardViewModel", "Loading leaderboard for year: $year")
        _isLoading.value = true
        _errorMessage.value = null // Clear previous errors

        val firstDayOfYear = "$year-01-01"
        val lastDayOfYear = "$year-12-31"

        // Chain of Firestore calls starts here
        fetchTeachersAndStudents(firstDayOfYear, lastDayOfYear)
    }

    private fun fetchTeachersAndStudents(firstDay: String, lastDay: String) {
        val teachersMap = mutableMapOf<String, String>()
        db.collection("teachers").get().addOnSuccessListener { teachersSnapshot ->
            teachersSnapshot.forEach { doc ->
                teachersMap[doc.id] = doc.getString("teacherName") ?: "Unknown Teacher"
            }

            val studentsMap = mutableMapOf<String, Pair<String, String>>() // StudentID -> Pair(StudentName, TeacherID)
            db.collection("students").get().addOnSuccessListener { studentsSnapshot ->
                if (studentsSnapshot.isEmpty) {
                    _errorMessage.value = "No students found in the tuition."
                    _isLoading.value = false
                    _leaderboardData.value = emptyList()
                    return@addOnSuccessListener
                }
                studentsSnapshot.forEach { doc ->
                    val studentId = doc.id
                    val studentName = doc.getString("studentName") ?: "N/A"
                    val teacherId = doc.getString("teacherId") ?: ""
                    studentsMap[studentId] = Pair(studentName, teacherId)
                }

                fetchAttendanceAndProcess(firstDay, lastDay, studentsMap, teachersMap)

            }.addOnFailureListener { e ->
                Log.e("LeaderboardViewModel", "Error fetching students", e)
                _errorMessage.value = "Error loading students."
                _isLoading.value = false
            }
        }.addOnFailureListener { e ->
            Log.e("LeaderboardViewModel", "Error fetching teachers", e)
            _errorMessage.value = "Error loading teachers."
            _isLoading.value = false
        }
    }

    private fun fetchAttendanceAndProcess(
        firstDay: String, lastDay: String,
        studentsMap: Map<String, Pair<String, String>>,
        teachersMap: Map<String, String>
    ) {
        val studentPresentDays = mutableMapOf<String, Int>()
        val studentAbsentDays = mutableMapOf<String, Int>()

        db.collection("attendanceRecords")
            .whereGreaterThanOrEqualTo("date", firstDay)
            .whereLessThanOrEqualTo("date", lastDay).get()
            .addOnSuccessListener { attendanceSnapshot ->
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
            }.addOnFailureListener { e ->
                Log.e("LeaderboardViewModel", "Error fetching attendance records", e)
                _errorMessage.value = "Error loading attendance data."
                _isLoading.value = false
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
        _leaderboardData.value = newLeaderboardList
    }
}