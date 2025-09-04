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

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _isStudentListLoading = MutableLiveData<Boolean>()
    val isStudentListLoading: LiveData<Boolean> = _isStudentListLoading
    private val _totalStudents = MutableLiveData<Long>()
    val totalStudents: LiveData<Long> = _totalStudents
    private val _totalTeachers = MutableLiveData<Long>()
    val totalTeachers: LiveData<Long> = _totalTeachers
    private val _totalInactiveStudents = MutableLiveData<Int>()
    val totalInactiveStudents: LiveData<Int> = _totalInactiveStudents
    private val _highAbsenceStudents = MutableLiveData<List<DashboardStudentItem>>()
    val highAbsenceStudents: LiveData<List<DashboardStudentItem>> = _highAbsenceStudents
    private val _classDistribution = MutableLiveData<Map<String, Int>>()
    val classDistribution: LiveData<Map<String, Int>> = _classDistribution
    private val _presentCount = MutableLiveData<Int>()
    val presentCount: LiveData<Int> = _presentCount
    private val _absentCount = MutableLiveData<Int>()
    val absentCount: LiveData<Int> = _absentCount
    private val _notMarkedCount = MutableLiveData<Int>()
    val notMarkedCount: LiveData<Int> = _notMarkedCount
    private val _absentStudents = MutableLiveData<List<DashboardStudentItem>>()
    val absentStudents: LiveData<List<DashboardStudentItem>> = _absentStudents
    private val _allStudentsList = MutableLiveData<List<StudentDetailsItem>>()
    val allStudentsList: LiveData<List<StudentDetailsItem>> get() = _allStudentsList
    private val _unmarkedTeachers = MutableLiveData<List<Teacher>>()
    val unmarkedTeachers: LiveData<List<Teacher>> = _unmarkedTeachers

    private val organizationId: String? = FirebaseAuthManager.getOrganizationId(application.applicationContext)
    private var isDashboardDataLoaded = false
    private var isStudentListFetchInProgress = false

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        if (organizationId != null) {
            if (!isDashboardDataLoaded) {
                refreshData()
            }
            if (_allStudentsList.value.isNullOrEmpty()) {
                fetchStudentListForSearch(forceRefresh = false)
            }
        }
    }

    fun refreshData() {
        if (organizationId == null) {
            Log.e(TAG, "Cannot refresh data: Organization ID is null.")
            _isLoading.postValue(false)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val orgDocDeferred = async {
                    db.collection("organizations").document(organizationId).get().await()
                }
                val allTeachersDeferred = async {
                    db.collection("organizations").document(organizationId)
                        .collection("teachers").get().await().toObjects<Teacher>()
                }
                val allActiveStudentsDeferred = async {
                    db.collection("organizations").document(organizationId)
                        .collection("students").whereEqualTo("isActive", true).get().await().toObjects<StudentDetailsItem>()
                }
                val allInactiveStudentsDeferred = async {
                    db.collection("organizations").document(organizationId)
                        .collection("students").whereEqualTo("isActive", false).get().await()
                }

                val orgDocument = orgDocDeferred.await()
                val allTeachers = allTeachersDeferred.await()
                val allActiveStudents = allActiveStudentsDeferred.await()
                val allInactiveStudents = allInactiveStudentsDeferred.await()

                val orgData = orgDocument.toObject(Organization::class.java)
                val highAbsenceThreshold = orgData?.highAbsenceThreshold ?: DEFAULT_ABSENCE_THRESHOLD

                _totalStudents.postValue(allActiveStudents.size.toLong())
                _totalTeachers.postValue(allTeachers.size.toLong())
                _totalInactiveStudents.postValue(allInactiveStudents.size())

                val classDistDeferred = async { fetchClassDistribution(allActiveStudents) }
                val attendanceDeferred = async { fetchTodaysAttendanceStats(organizationId, allActiveStudents.size, allTeachers) }
                val highAbsenceDeferred = async { calculateHighAbsenceStats(organizationId, allActiveStudents, highAbsenceThreshold) }

                classDistDeferred.await()
                attendanceDeferred.await()

                // --- THIS IS THE FIX ---
                // Use the Elvis operator (?:) to provide an empty list if the result is null.
                _highAbsenceStudents.postValue(highAbsenceDeferred.await() ?: emptyList())
                // --- END OF FIX ---

                isDashboardDataLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing dashboard data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchStudentListForSearch(forceRefresh: Boolean) {
        if (organizationId == null) return
        if (!forceRefresh && !_allStudentsList.value.isNullOrEmpty()) {
            return
        }
        if (isStudentListFetchInProgress) return

        isStudentListFetchInProgress = true
        _isStudentListLoading.postValue(true)
        db.collection("organizations").document(organizationId)
            .collection("students").whereEqualTo("isActive", true)
            .orderBy("studentName")
            .get()
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

    private suspend fun fetchTodaysAttendanceStats(orgId: String, totalActiveStudents: Int, allTeachers: List<Teacher>) {
        try {
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
                    when (studentMap["status"] as? String) {
                        "Present" -> present++
                        "Absent" -> {
                            absent++
                            absentStudentItems.add(
                                DashboardStudentItem(
                                    id = studentMap["studentId"] as? String ?: "",
                                    name = studentMap["studentName"] as? String ?: "Unknown",
                                    imageUrl = null,
                                    subtitle = doc.getString("teacherName")
                                )
                            )
                        }
                    }
                }
            }

            _presentCount.postValue(present)
            _absentCount.postValue(absent)
            _notMarkedCount.postValue(totalActiveStudents - (present + absent))
            _absentStudents.postValue(absentStudentItems)

            val unmarked = allTeachers.filterNot { markedTeacherIds.contains(it.teacherId) }
            _unmarkedTeachers.postValue(unmarked)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching attendance stats", e)
            _presentCount.postValue(0)
            _absentCount.postValue(0)
            _notMarkedCount.postValue(totalActiveStudents)
            _absentStudents.postValue(emptyList())
            _unmarkedTeachers.postValue(allTeachers)
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

    private suspend fun calculateHighAbsenceStats(
        orgId: String,
        activeStudents: List<StudentDetailsItem>,
        threshold: Int
    ): List<DashboardStudentItem> {
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
                        if (studentId != null) {
                            absentCounts[studentId] = (absentCounts[studentId] ?: 0) + 1
                        }
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
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating high absence stats", e)
            return emptyList()
        }
    }
}