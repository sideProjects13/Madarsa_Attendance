package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.madarsa_attendance.models.Organization
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "DashboardViewModel"
    private val DEFAULT_ABSENCE_THRESHOLD = 3

    // --- Loading States ---
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // [RESTORED] This was missing and caused the error in QuickFeesDialogFragment
    private val _isStudentListLoading = MutableLiveData<Boolean>()
    val isStudentListLoading: LiveData<Boolean> = _isStudentListLoading

    // --- Dashboard Counts ---
    private val _totalStudents = MutableLiveData<Long>()
    val totalStudents: LiveData<Long> = _totalStudents
    private val _totalTeachers = MutableLiveData<Long>()
    val totalTeachers: LiveData<Long> = _totalTeachers
    private val _totalInactiveStudents = MutableLiveData<Int>()
    val totalInactiveStudents: LiveData<Int> = _totalInactiveStudents

    // --- High Absence List ---
    private val _highAbsenceStudents = MutableLiveData<List<DashboardStudentItem>>()
    val highAbsenceStudents: LiveData<List<DashboardStudentItem>> = _highAbsenceStudents

    // --- Teacher Attendance Counts (New) ---
    private val _teacherPresentCount = MutableLiveData<Int>()
    val teacherPresentCount: LiveData<Int> = _teacherPresentCount
    private val _teacherAbsentCount = MutableLiveData<Int>()
    val teacherAbsentCount: LiveData<Int> = _teacherAbsentCount
    private val _teacherNotMarkedCount = MutableLiveData<Int>()
    val teacherNotMarkedCount: LiveData<Int> = _teacherNotMarkedCount

    // --- Student Attendance Counts (Existing) ---
    private val _presentCount = MutableLiveData<Int>()
    val presentCount: LiveData<Int> = _presentCount
    private val _absentCount = MutableLiveData<Int>()
    val absentCount: LiveData<Int> = _absentCount
    private val _notMarkedCount = MutableLiveData<Int>()
    val notMarkedCount: LiveData<Int> = _notMarkedCount

    private val _absentStudents = MutableLiveData<List<DashboardStudentItem>>()
    val absentStudents: LiveData<List<DashboardStudentItem>> = _absentStudents
    private val _unmarkedTeachers = MutableLiveData<List<Teacher>>()
    val unmarkedTeachers: LiveData<List<Teacher>> = _unmarkedTeachers

    // --- Chart Data ---
    private val _classDistribution = MutableLiveData<Map<String, Int>>()
    val classDistribution: LiveData<Map<String, Int>> = _classDistribution

    // --- Shared Data ---
    private val _allStudentsList = MutableLiveData<List<StudentDetailsItem>>()
    val allStudentsList: LiveData<List<StudentDetailsItem>> get() = _allStudentsList

    private val organizationId: String? = FirebaseAuthManager.getOrganizationId(application.applicationContext)
    private var isDashboardDataLoaded = false
    // [RESTORED] Flag to prevent double loading
    private var isStudentListFetchInProgress = false

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        if (organizationId != null) {
            if (!isDashboardDataLoaded) refreshData()
            if (_allStudentsList.value.isNullOrEmpty()) fetchStudentListForSearch(false)
        }
    }

    fun refreshData() {
        if (organizationId == null) {
            _isLoading.postValue(false)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val orgDocDeferred = async { db.collection("organizations").document(organizationId).get().await() }
                val allTeachersDeferred = async { db.collection("organizations").document(organizationId).collection("teachers").get().await().toObjects<Teacher>() }
                val allStudentsDeferred = async { db.collection("organizations").document(organizationId).collection("students").get().await().toObjects<StudentDetailsItem>() }

                val orgDocument = orgDocDeferred.await()
                val allTeachers = allTeachersDeferred.await()
                val allStudents = allStudentsDeferred.await()

                val allActiveStudents = allStudents.filter { it.isActive }
                val allInactiveStudents = allStudents.filter { !it.isActive }
                val orgData = orgDocument.toObject(Organization::class.java)
                val highAbsenceThreshold = orgData?.highAbsenceThreshold ?: DEFAULT_ABSENCE_THRESHOLD

                _totalStudents.postValue(allActiveStudents.size.toLong())
                _totalTeachers.postValue(allTeachers.size.toLong())
                _totalInactiveStudents.postValue(allInactiveStudents.size)

                // Fetch data in parallel
                val classDistDeferred = async { fetchClassDistribution(allActiveStudents) }
                val studentAttendanceDeferred = async { fetchTodaysStudentAttendance(organizationId, allActiveStudents, allTeachers) }
                val teacherAttendanceDeferred = async { fetchTodaysTeacherAttendance(organizationId, allTeachers) }
                val highAbsenceDeferred = async { calculateHighAbsenceStats(organizationId, allActiveStudents, highAbsenceThreshold) }

                classDistDeferred.await()
                studentAttendanceDeferred.await()
                teacherAttendanceDeferred.await()
                _highAbsenceStudents.postValue(highAbsenceDeferred.await() ?: emptyList())

                isDashboardDataLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing dashboard data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // [RESTORED] This function now correctly updates _isStudentListLoading
    fun fetchStudentListForSearch(forceRefresh: Boolean) {
        if (organizationId == null || isStudentListFetchInProgress || (!forceRefresh && !_allStudentsList.value.isNullOrEmpty())) {
            return
        }
        isStudentListFetchInProgress = true
        _isStudentListLoading.postValue(true)

        db.collection("organizations").document(organizationId)
            .collection("students").whereEqualTo("isActive", true)
            .orderBy("studentName").get()
            .addOnSuccessListener { documents ->
                _allStudentsList.postValue(documents.toObjects())
            }
            .addOnFailureListener {
                _allStudentsList.postValue(emptyList())
            }
            .addOnCompleteListener {
                isStudentListFetchInProgress = false
                _isStudentListLoading.postValue(false)
            }
    }

    private suspend fun fetchTodaysTeacherAttendance(orgId: String, allTeachers: List<Teacher>) {
        try {
            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val attendanceQuery = db.collection("organizations").document(orgId)
                .collection("teacherAttendance")
                .whereEqualTo("date", todayDateStr)
                .get().await()

            var present = 0
            var absent = 0
            val markedTeacherIds = mutableSetOf<String>()

            for (doc in attendanceQuery.documents) {
                val teacherId = doc.getString("teacherId") ?: continue
                markedTeacherIds.add(teacherId)

                val status = doc.getString("status")
                val classesTaken = doc.getLong("classesTaken")?.toInt() ?: 0
                val classesMissed = doc.getLong("classesMissed")?.toInt() ?: 0

                val derivedStatus = when {
                    status != null && status.isNotEmpty() -> status
                    classesTaken > 0 -> "Present"
                    classesMissed > 0 -> "Absent"
                    else -> "Present"
                }

                if (derivedStatus == "Present") present++ else absent++
            }

            val notMarked = allTeachers.size - markedTeacherIds.size

            _teacherPresentCount.postValue(present)
            _teacherAbsentCount.postValue(absent)
            _teacherNotMarkedCount.postValue(notMarked.coerceAtLeast(0))

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching teacher attendance stats", e)
            _teacherPresentCount.postValue(0)
            _teacherAbsentCount.postValue(0)
            _teacherNotMarkedCount.postValue(allTeachers.size)
        }
    }

    private suspend fun fetchTodaysStudentAttendance(orgId: String, allActiveStudents: List<StudentDetailsItem>, allTeachers: List<Teacher>) {
        try {
            val activeStudentIds = allActiveStudents.map { it.id }.toSet()
            val activeStudentsMap = allActiveStudents.associateBy { it.id }
            val totalActiveStudentsCount = activeStudentIds.size

            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val attendanceQuery = db.collection("organizations").document(orgId)
                .collection("attendanceRecords").whereEqualTo("date", todayDateStr).get().await()

            var present = 0
            var absent = 0
            val markedTeacherIds = mutableSetOf<String>()
            val absentStudentItems = mutableListOf<DashboardStudentItem>()

            for (doc in attendanceQuery.documents) {
                markedTeacherIds.add(doc.getString("teacherId") ?: "")
                val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>
                studentAttendances?.forEach { studentMap ->
                    val studentId = studentMap["studentId"] as? String
                    if (studentId != null && activeStudentIds.contains(studentId)) {
                        when (studentMap["status"] as? String) {
                            "Present" -> present++
                            "Absent" -> {
                                absent++
                                val freshStudentDetails = activeStudentsMap[studentId]
                                absentStudentItems.add(
                                    DashboardStudentItem(
                                        id = studentId,
                                        name = freshStudentDetails?.studentName ?: studentMap["studentName"] as? String ?: "Unknown",
                                        imageUrl = freshStudentDetails?.profileImageUrl,
                                        subtitle = doc.getString("teacherName")
                                    )
                                )
                            }
                        }
                    }
                }
            }

            _presentCount.postValue(present)
            _absentCount.postValue(absent)
            _notMarkedCount.postValue(totalActiveStudentsCount - (present + absent))
            _absentStudents.postValue(absentStudentItems)

            val unmarked = allTeachers.filterNot { markedTeacherIds.contains(it.teacherId) }
            _unmarkedTeachers.postValue(unmarked)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching student attendance stats", e)
        }
    }

    private suspend fun fetchClassDistribution(activeStudents: List<StudentDetailsItem>) {
        try {
            val distribution = activeStudents
                .groupBy { it.teacherName ?: "Unassigned" }
                .mapValues { it.value.size }
            _classDistribution.postValue(distribution)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching class distribution", e)
            _classDistribution.postValue(emptyMap())
        }
    }

    private suspend fun calculateHighAbsenceStats(orgId: String, activeStudents: List<StudentDetailsItem>, threshold: Int): List<DashboardStudentItem> {
        try {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val firstDayOfMonth = String.format(Locale.getDefault(), "%d-%02d-01", year, month)
            val lastDayOfMonth = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))

            val monthlyAttendanceRecords = db.collection("organizations").document(orgId)
                .collection("attendanceRecords")
                .whereGreaterThanOrEqualTo("date", firstDayOfMonth)
                .whereLessThanOrEqualTo("date", lastDayOfMonth)
                .get().await()

            val absentCounts = mutableMapOf<String, Int>()
            for (doc in monthlyAttendanceRecords.documents) {
                val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>
                studentAttendances?.forEach { studentMap ->
                    if (studentMap["status"] as? String == "Absent") {
                        val studentId = studentMap["studentId"] as? String
                        if (studentId != null) absentCounts[studentId] = (absentCounts[studentId] ?: 0) + 1
                    }
                }
            }
            val highAbsenceStudentIds = absentCounts.filterValues { it >= threshold }.keys
            if (highAbsenceStudentIds.isEmpty()) return emptyList()
            val activeStudentsMap = activeStudents.associateBy { it.id }
            return highAbsenceStudentIds.mapNotNull { studentId ->
                activeStudentsMap[studentId]?.let { studentDetails ->
                    DashboardStudentItem(studentId, studentDetails.studentName ?: "N/A", studentDetails.profileImageUrl, "${absentCounts[studentId]} Absents this month")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating high absence stats", e)
            return emptyList()
        }
    }
}