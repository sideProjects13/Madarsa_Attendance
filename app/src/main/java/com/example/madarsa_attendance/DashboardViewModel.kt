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

    // LiveData declarations...
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
                // Fetch all necessary data concurrently
                val orgDocDeferred = async {
                    db.collection("organizations").document(organizationId).get().await()
                }
                val allTeachersDeferred = async {
                    db.collection("organizations").document(organizationId)
                        .collection("teachers").get().await().toObjects<Teacher>()
                }
                // --- THIS IS THE OPTIMIZED APPROACH ---
                // Fetch ALL students once, then filter in code.
                val allStudentsDeferred = async {
                    db.collection("organizations").document(organizationId)
                        .collection("students").get().await().toObjects<StudentDetailsItem>()
                }

                // Await all results
                val orgDocument = orgDocDeferred.await()
                val allTeachers = allTeachersDeferred.await()
                val allStudents = allStudentsDeferred.await()

                // Filter students in-memory for consistency across all calculations
                val allActiveStudents = allStudents.filter { it.isActive }
                val allInactiveStudents = allStudents.filter { !it.isActive }

                // Extract organization settings
                val orgData = orgDocument.toObject(Organization::class.java)
                val highAbsenceThreshold = orgData?.highAbsenceThreshold ?: DEFAULT_ABSENCE_THRESHOLD

                // Post simple counts to the UI
                _totalStudents.postValue(allActiveStudents.size.toLong())
                _totalTeachers.postValue(allTeachers.size.toLong())
                _totalInactiveStudents.postValue(allInactiveStudents.size)

                // Defer more complex calculations, passing the consistent, filtered lists
                val classDistDeferred = async { fetchClassDistribution(allActiveStudents) }
                // --- THIS IS THE FIX ---
                // Pass the reliable list of active students to the attendance function.
                val attendanceDeferred = async { fetchTodaysAttendanceStats(organizationId, allActiveStudents, allTeachers) }
                val highAbsenceDeferred = async { calculateHighAbsenceStats(organizationId, allActiveStudents, highAbsenceThreshold) }

                // Await and post the final results
                classDistDeferred.await()
                attendanceDeferred.await()
                _highAbsenceStudents.postValue(highAbsenceDeferred.await())

                isDashboardDataLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing dashboard data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchStudentListForSearch(forceRefresh: Boolean) {
        if (organizationId == null || isStudentListFetchInProgress || (!forceRefresh && !_allStudentsList.value.isNullOrEmpty())) {
            return
        }
        isStudentListFetchInProgress = true
        _isStudentListLoading.postValue(true)
        db.collection("organizations").document(organizationId)
            .collection("students").whereEqualTo("isActive", true)
            .orderBy("studentName")
            .get()
            .addOnSuccessListener { documents -> _allStudentsList.postValue(documents.toObjects()) }
            .addOnFailureListener { _allStudentsList.postValue(emptyList()) }
            .addOnCompleteListener { isStudentListFetchInProgress = false; _isStudentListLoading.postValue(false) }
    }

    // --- THIS FUNCTION IS NOW FULLY CORRECTED ---
    private suspend fun fetchTodaysAttendanceStats(orgId: String, allActiveStudents: List<StudentDetailsItem>, allTeachers: List<Teacher>) {
        try {
            // Create a Set of active student IDs for very fast lookups.
            val activeStudentIds = allActiveStudents.map { it.id }.toSet()
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

                    // --- THIS IS THE CORE FIX ---
                    // Only count the attendance if the student is in our active list.
                    if (studentId != null && activeStudentIds.contains(studentId)) {
                        when (studentMap["status"] as? String) {
                            "Present" -> present++
                            "Absent" -> {
                                absent++
                                // Also ensure we only add valid absentees to the list
                                absentStudentItems.add(
                                    DashboardStudentItem(
                                        id = studentId,
                                        name = studentMap["studentName"] as? String ?: "Unknown",
                                        imageUrl = null, // You might fetch this from the student list if needed
                                        subtitle = doc.getString("teacherName")
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // The calculation is now correct and will never be negative.
            _presentCount.postValue(present)
            _absentCount.postValue(absent)
            _notMarkedCount.postValue(totalActiveStudentsCount - (present + absent))
            _absentStudents.postValue(absentStudentItems)

            // Unmarked teachers logic remains the same
            val unmarked = allTeachers.filterNot { markedTeacherIds.contains(it.teacherId) }
            _unmarkedTeachers.postValue(unmarked)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching attendance stats", e)
            _presentCount.postValue(0)
            _absentCount.postValue(0)
            _notMarkedCount.postValue(allActiveStudents.size) // Fallback to total active students on error
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