package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.madarsa_attendance.models.Organization
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TeacherDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "TeacherDashboardVM"
    private val DEFAULT_ABSENCE_THRESHOLD = 3

    // LiveData for UI
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData for the 4 main cards
    private val _totalStudents = MutableLiveData<Long>()
    val totalStudents: LiveData<Long> = _totalStudents
    private val _totalClasses = MutableLiveData<Long>()
    val totalClasses: LiveData<Long> = _totalClasses
    private val _totalInactiveStudents = MutableLiveData<Int>()
    val totalInactiveStudents: LiveData<Int> = _totalInactiveStudents
    private val _highAbsenceStudents = MutableLiveData<List<DashboardStudentItem>>()
    val highAbsenceStudents: LiveData<List<DashboardStudentItem>> = _highAbsenceStudents

    // LiveData for the 3 summary items
    private val _presentCount = MutableLiveData<Int>()
    val presentCount: LiveData<Int> = _presentCount
    private val _absentCount = MutableLiveData<Int>()
    val absentCount: LiveData<Int> = _absentCount
    private val _notMarkedCount = MutableLiveData<Int>()
    val notMarkedCount: LiveData<Int> = _notMarkedCount

    // LiveData for the Bar Chart
    private val _classDistribution = MutableLiveData<Map<String, Int>>()
    val classDistribution: LiveData<Map<String, Int>> = _classDistribution

    // Helper LiveData
    private val _unmarkedClasses = MutableLiveData<List<Teacher>>()
    val unmarkedClasses: LiveData<List<Teacher>> = _unmarkedClasses


    private val organizationId: String? = FirebaseAuthManager.getOrganizationId(application.applicationContext)
    private val currentUserUid: String? = auth.currentUser?.uid

    init {
        refreshData()
    }

    fun refreshData() {
        if (organizationId == null || currentUserUid == null) {
            Log.e(TAG, "Cannot refresh data: Organization ID or User UID is null.")
            _isLoading.postValue(false)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Fetch all classes assigned to this teacher
                val teacherClassesSnapshot = db.collection("organizations").document(organizationId)
                    .collection("teachers")
                    .whereEqualTo("uid", currentUserUid)
                    .get().await()
                val teacherClasses = teacherClassesSnapshot.toObjects<Teacher>()
                val teacherIds = teacherClasses.map { it.teacherId }
                _totalClasses.postValue(teacherClasses.size.toLong())

                if (teacherIds.isEmpty()) {
                    // If teacher has no classes, set all stats to zero and finish
                    _totalStudents.postValue(0)
                    _totalInactiveStudents.postValue(0)
                    _highAbsenceStudents.postValue(emptyList())
                    _presentCount.postValue(0)
                    _absentCount.postValue(0)
                    _notMarkedCount.postValue(0)
                    _classDistribution.postValue(emptyMap())
                    _isLoading.postValue(false)
                    return@launch
                }

                // 2. Fetch all students belonging to this teacher's classes
                val allStudentsSnapshot = db.collection("organizations").document(organizationId)
                    .collection("students")
                    .whereIn("teacherId", teacherIds)
                    .get().await()
                val allStudents = allStudentsSnapshot.toObjects<StudentDetailsItem>()

                val activeStudents = allStudents.filter { it.isActive }
                val inactiveStudents = allStudents.filter { !it.isActive }

                _totalStudents.postValue(activeStudents.size.toLong())
                _totalInactiveStudents.postValue(inactiveStudents.size)

                // 3. Defer other calculations
                val orgDocDeferred = async { db.collection("organizations").document(organizationId).get().await() }
                val attendanceDeferred = async { fetchTodaysAttendanceStats(organizationId, activeStudents, teacherClasses) }
                val highAbsenceDeferred = async { calculateHighAbsenceStats(organizationId, activeStudents, orgDocDeferred.await()) }

                // Set class distribution for the bar chart
                _classDistribution.postValue(activeStudents.groupBy { it.teacherName ?: "Unassigned" }.mapValues { it.value.size })

                // Await and post results
                attendanceDeferred.await()
                _highAbsenceStudents.postValue(highAbsenceDeferred.await())

            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing teacher dashboard data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchTodaysAttendanceStats(orgId: String, activeStudents: List<StudentDetailsItem>, teacherClasses: List<Teacher>) {
        val activeStudentIds = activeStudents.map { it.id }.toSet()
        val totalStudentsInClasses = activeStudentIds.size
        val teacherIds = teacherClasses.map { it.teacherId }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val attendanceQuery = db.collection("organizations").document(orgId)
            .collection("attendanceRecords")
            .whereIn("teacherId", teacherIds)
            .whereEqualTo("date", todayStr)
            .get().await()

        var present = 0
        var absent = 0
        val markedTeacherIds = mutableSetOf<String>()

        for (doc in attendanceQuery.documents) {
            markedTeacherIds.add(doc.getString("teacherId") ?: "")
            val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>
            studentAttendances?.forEach { studentMap ->
                if (activeStudentIds.contains(studentMap["studentId"] as? String)) {
                    when (studentMap["status"] as? String) {
                        "Present" -> present++
                        "Absent" -> absent++
                    }
                }
            }
        }

        _presentCount.postValue(present)
        _absentCount.postValue(absent)
        _notMarkedCount.postValue(totalStudentsInClasses - (present + absent))

        // Find which of the teacher's own classes are not marked
        _unmarkedClasses.postValue(teacherClasses.filterNot { markedTeacherIds.contains(it.teacherId) })
    }

    private suspend fun calculateHighAbsenceStats(orgId: String, activeStudents: List<StudentDetailsItem>, orgDoc: com.google.firebase.firestore.DocumentSnapshot): List<DashboardStudentItem> {
        val orgData = orgDoc.toObject(Organization::class.java)
        val threshold = orgData?.highAbsenceThreshold ?: DEFAULT_ABSENCE_THRESHOLD
        val activeStudentIds = activeStudents.map { it.id }

        if (activeStudentIds.isEmpty()) return emptyList()

        // This logic remains the same, but it will now only check for students in this teacher's classes
        val calendar = Calendar.getInstance()
        val firstDayOfMonth = String.format(Locale.getDefault(), "%d-%02d-01", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
        val lastDayOfMonth = String.format(Locale.getDefault(), "%d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))

        val monthlyAttendanceRecords = db.collection("organizations").document(orgId)
            .collection("attendanceRecords")
            .whereGreaterThanOrEqualTo("date", firstDayOfMonth)
            .whereLessThanOrEqualTo("date", lastDayOfMonth)
            .get().await()

        val absentCounts = mutableMapOf<String, Int>()
        for (doc in monthlyAttendanceRecords.documents) {
            val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>
            studentAttendances?.forEach { studentMap ->
                val studentId = studentMap["studentId"] as? String
                // Only count if the student is one of this teacher's students
                if (studentId != null && activeStudentIds.contains(studentId) && studentMap["status"] as? String == "Absent") {
                    absentCounts[studentId] = (absentCounts[studentId] ?: 0) + 1
                }
            }
        }

        val highAbsenceStudentIds = absentCounts.filterValues { it >= threshold }.keys
        if (highAbsenceStudentIds.isEmpty()) return emptyList()

        val activeStudentsMap = activeStudents.associateBy { it.id }
        return highAbsenceStudentIds.mapNotNull { studentId ->
            activeStudentsMap[studentId]?.let { studentDetails ->
                DashboardStudentItem(
                    id = studentId,
                    name = studentDetails.studentName ?: "N/A",
                    imageUrl = studentDetails.profileImageUrl,
                    subtitle = "${absentCounts[studentId]} Absents this month"
                )
            }
        }
    }
}