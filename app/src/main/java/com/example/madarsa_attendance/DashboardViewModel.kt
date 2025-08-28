package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
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

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _isStudentListLoading = MutableLiveData<Boolean>()
    val isStudentListLoading: LiveData<Boolean> = _isStudentListLoading
    private val _totalStudents = MutableLiveData<Long>()
    val totalStudents: LiveData<Long> = _totalStudents
    private val _totalTeachers = MutableLiveData<Long>()
    val totalTeachers: LiveData<Long> = _totalTeachers
    private val _feesThisMonth = MutableLiveData<Double>()
    val feesThisMonth: LiveData<Double> = _feesThisMonth
    private val _feesThisYear = MutableLiveData<Double>()
    val feesThisYear: LiveData<Double> = _feesThisYear
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

    // --- NEW: LiveData for the list of unmarked teachers ---
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
                val allTeachersDeferred = async {
                    db.collection("organizations").document(organizationId)
                        .collection("teachers").get().await().toObjects<Teacher>()
                }

                val allStudentsDeferred = async {
                    db.collection("organizations").document(organizationId)
                        .collection("students").whereEqualTo("isActive", true).get().await().toObjects<StudentDetailsItem>()
                }

                val allTeachers = allTeachersDeferred.await()
                val allActiveStudents = allStudentsDeferred.await()

                _totalStudents.postValue(allActiveStudents.size.toLong())
                _totalTeachers.postValue(allTeachers.size.toLong())

                val feesDeferred = async { fetchFeeStats(organizationId) }
                val classDistDeferred = async { fetchClassDistribution(allActiveStudents) }
                val attendanceDeferred = async { fetchTodaysAttendanceStats(organizationId, allActiveStudents.size, allTeachers) }

                feesDeferred.await()
                classDistDeferred.await()
                attendanceDeferred.await()

                isDashboardDataLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing dashboard data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchStudentListForSearch(forceRefresh: Boolean) {
        if (isStudentListFetchInProgress || (!forceRefresh && !_allStudentsList.value.isNullOrEmpty())) return
        if (organizationId == null) return

        isStudentListFetchInProgress = true
        _isStudentListLoading.postValue(true)
        db.collection("organizations").document(organizationId)
            .collection("students").whereEqualTo("isActive", true).get()
            .addOnSuccessListener { documents -> _allStudentsList.postValue(documents.toObjects()) }
            .addOnFailureListener { _allStudentsList.postValue(emptyList()) }
            .addOnCompleteListener { isStudentListFetchInProgress = false; _isStudentListLoading.postValue(false) }
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

            // --- NEW LOGIC: Calculate and post the list of unmarked teachers ---
            val unmarked = allTeachers.filterNot { markedTeacherIds.contains(it.teacherId) }
            _unmarkedTeachers.postValue(unmarked)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching attendance stats", e)
            _presentCount.postValue(0)
            _absentCount.postValue(0)
            _notMarkedCount.postValue(totalActiveStudents)
            _absentStudents.postValue(emptyList())
            _unmarkedTeachers.postValue(allTeachers) // Assume all are unmarked on error
        }
    }

    private suspend fun fetchFeeStats(orgId: String) {
        try {
            val calendar = Calendar.getInstance()
            val currentMonthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
            val currentYear = calendar.get(Calendar.YEAR)
            val monthQuery = db.collection("organizations").document(orgId).collection("feePayments").whereEqualTo("paymentMonth", currentMonthYearStr).get().await()
            _feesThisMonth.postValue(monthQuery.sumOf { it.getDouble("paymentAmount") ?: 0.0 })
            val yearQuery = db.collection("organizations").document(orgId).collection("feePayments").whereEqualTo("paymentYear", currentYear).get().await()
            _feesThisYear.postValue(yearQuery.sumOf { it.getDouble("paymentAmount") ?: 0.0 })
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching fee stats", e)
            _feesThisMonth.postValue(0.0)
            _feesThisYear.postValue(0.0)
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
}