package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.madarsa_attendance.models.Organization // Import the Organization data class
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

    // --- 1. MODIFIED: The hardcoded threshold is replaced by a default value ---
    // This is used only if the value is not found in Firestore.
    private val DEFAULT_ABSENCE_THRESHOLD = 3
    // --- END OF MODIFICATION ---

    // LiveData declarations...
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _isStudentListLoading = MutableLiveData<Boolean>()
    val isStudentListLoading: LiveData<Boolean> = _isStudentListLoading
    private val _totalStudents = MutableLiveData<Long>()
    val totalStudents: LiveData<Long> = _totalStudents
    private val _totalTeachers = MutableLiveData<Long>()
    val totalTeachers: LiveData<Long> = _totalTeachers

    /*
    private val _feesThisMonth = MutableLiveData<Double>()
    val feesThisMonth: LiveData<Double> = _feesThisMonth
    private val _feesThisYear = MutableLiveData<Double>()
    val feesThisYear: LiveData<Double> = _feesThisYear
    */

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
                // --- 2. NEW: Concurrently fetch the main organization document ---
                // This is needed to get the custom threshold value set by the admin.
                val orgDocDeferred = async {
                    db.collection("organizations").document(organizationId).get().await()
                }
                // --- END OF NEW FETCH ---

                // Other concurrent fetches remain the same
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

                // Await all results
                val orgDocument = orgDocDeferred.await()
                val allTeachers = allTeachersDeferred.await()
                val allActiveStudents = allActiveStudentsDeferred.await()
                val allInactiveStudents = allInactiveStudentsDeferred.await()

                // --- 3. NEW: Extract the threshold from the fetched organization data ---
                val orgData = orgDocument.toObject(Organization::class.java)
                val highAbsenceThreshold = orgData?.highAbsenceThreshold ?: DEFAULT_ABSENCE_THRESHOLD
                // --- END OF EXTRACTION ---

                // Post basic counts
                _totalStudents.postValue(allActiveStudents.size.toLong())
                _totalTeachers.postValue(allTeachers.size.toLong())
                _totalInactiveStudents.postValue(allInactiveStudents.size())

                // Defer other complex calculations
                val classDistDeferred = async { fetchClassDistribution(allActiveStudents) }
                val attendanceDeferred = async { fetchTodaysAttendanceStats(organizationId, allActiveStudents.size, allTeachers) }

                // --- 4. MODIFIED: Pass the fetched threshold to the calculation function ---
                val highAbsenceDeferred = async { calculateHighAbsenceStats(organizationId, allActiveStudents, highAbsenceThreshold) }
                // --- END OF MODIFICATION ---

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
        if (organizationId == null) return
        // Only fetch if a refresh is forced or if the list is currently empty.
        if (!forceRefresh && !_allStudentsList.value.isNullOrEmpty()) {
            return
        }
        if (isStudentListFetchInProgress) return

        isStudentListFetchInProgress = true
        _isStudentListLoading.postValue(true)
        db.collection("organizations").document(organizationId)
            .collection("students").whereEqualTo("isActive", true)
            .orderBy("studentName") // Good practice to order
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

    // --- 5. MODIFIED: The function signature now accepts the threshold as a parameter ---
    private suspend fun calculateHighAbsenceStats(
        orgId: String,
        activeStudents: List<StudentDetailsItem>,
        threshold: Int // <-- It receives the value fetched in refreshData()
    ): List<DashboardStudentItem> {
        try {
            // Get the date range for the current month
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1

            val firstDayOfMonth = String.format(Locale.getDefault(), "%d-%02d-01", year, month)
            val lastDayOfMonth = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))

            // Fetch all attendance records for the current month
            val monthlyAttendanceRecords = db.collection("organizations").document(orgId)
                .collection("attendanceRecords")
                .whereGreaterThanOrEqualTo("date", firstDayOfMonth)
                .whereLessThanOrEqualTo("date", lastDayOfMonth)
                .get().await()

            // Count absences for each student
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

            // --- 6. MODIFIED: Use the dynamic `threshold` parameter in the filter ---
            val highAbsenceStudentIds = absentCounts.filterValues { it >= threshold }.keys
            // --- END OF MODIFICATION ---

            if (highAbsenceStudentIds.isEmpty()) return emptyList()

            // For efficient lookup, create a map of active students by their ID
            val activeStudentsMap = activeStudents.associateBy { it.id }

            // Create the final list of DashboardStudentItem objects
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
            return emptyList() // Return an empty list on error
        }
    }
}