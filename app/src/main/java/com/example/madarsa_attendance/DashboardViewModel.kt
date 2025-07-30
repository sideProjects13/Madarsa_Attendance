package com.example.madarsa_attendance

import android.app.Application // Import Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel // Change to AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Changed from ViewModel() to AndroidViewModel(application)
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val context = application.applicationContext // Get context from Application

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _totalStudents = MutableLiveData<Long>()
    val totalStudents: LiveData<Long> = _totalStudents

    private val _totalTeachers = MutableLiveData<Long>()
    val totalTeachers: LiveData<Long> = _totalTeachers

    private val _feesThisMonth = MutableLiveData<Double>()
    val feesThisMonth: LiveData<Double> = _feesThisMonth

    private val _feesThisYear = MutableLiveData<Double>()
    val feesThisYear: LiveData<Double> = _feesThisYear

    private val _recentlyJoinedStudents = MutableLiveData<List<DashboardStudentItem>>()
    val recentlyJoinedStudents: LiveData<List<DashboardStudentItem>> = _recentlyJoinedStudents

    private val _absentStudents = MutableLiveData<List<DashboardStudentItem>>()
    val absentStudents: LiveData<List<DashboardStudentItem>> = _absentStudents

    private val _classDistribution = MutableLiveData<Map<String, Int>>()
    val classDistribution: LiveData<Map<String, Int>> = _classDistribution

    // Get organization ID once, when ViewModel is created
    private val organizationId: String? = FirebaseAuthManager.getOrganizationId(context)
    init {
        // If organizationId is null, log an error or handle it. Dashboard shouldn't load.
        if (organizationId == null) {
            Log.e("DashboardViewModel", "Organization ID is NULL. Dashboard data cannot be loaded.")
            // Consider posting error states or a message to UI
        }
        // Load data initially when ViewModel is created
        loadDashboardData()
    }


    fun loadDashboardData() {
        if (organizationId == null) {
            Log.e("DashboardViewModel", "Aborting loadDashboardData: Organization ID is null.")
            _isLoading.postValue(false)
            // Optionally clear data or post error to relevant LiveData
            _totalStudents.postValue(0)
            _totalTeachers.postValue(0)
            _feesThisMonth.postValue(0.0)
            _feesThisYear.postValue(0.0)
            _recentlyJoinedStudents.postValue(emptyList())
            _absentStudents.postValue(emptyList())
            _classDistribution.postValue(emptyMap())
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch all data in parallel
                val countsDeferred = async { fetchCounts(organizationId) } // Pass organizationId
                val feesDeferred = async { fetchFeeStats(organizationId) } // Pass organizationId
                val recentStudentsDeferred = async { fetchRecentlyJoined(organizationId) } // Pass organizationId
                val absentStudentsDeferred = async { fetchAbsentStudents(organizationId) } // Pass organizationId
                val classDistDeferred = async { fetchClassDistribution(organizationId) } // Pass organizationId

                // Wait for all to complete
                awaitAll(countsDeferred, feesDeferred, recentStudentsDeferred, absentStudentsDeferred, classDistDeferred)

            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error loading dashboard data", e)
                // Optionally post error states to LiveData
            } finally {
                _isLoading.value = false
            }
        }
    }

    // NEW: Pass organizationId to all data fetching functions
    private suspend fun fetchCounts(orgId: String) {
        try {
            // NEW: Scope queries to the organization
            val studentsCount = db.collection("organizations").document(orgId)
                .collection("students").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
            val teachersCount = db.collection("organizations").document(orgId)
                .collection("teachers").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
            _totalStudents.postValue(studentsCount)
            _totalTeachers.postValue(teachersCount)
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Error fetching counts for org $orgId", e)
            _totalStudents.postValue(0)
            _totalTeachers.postValue(0)
        }
    }

    // NEW: Pass organizationId
    private suspend fun fetchFeeStats(orgId: String) {
        try {
            val calendar = Calendar.getInstance()
            val currentMonthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
            val currentYear = calendar.get(Calendar.YEAR)

            // Fees this month (scoped to organization)
            val monthQuery = db.collection("organizations").document(orgId) // NEW
                .collection("feePayments") // NEW
                .whereEqualTo("paymentMonth", currentMonthYearStr)
                .get().await()
            val monthlyTotal = monthQuery.sumOf { it.getDouble("paymentAmount") ?: 0.0 }
            _feesThisMonth.postValue(monthlyTotal)

            // Fees this year (scoped to organization)
            val yearQuery = db.collection("organizations").document(orgId) // NEW
                .collection("feePayments") // NEW
                .whereEqualTo("paymentYear", currentYear)
                .get().await()
            val yearlyTotal = yearQuery.sumOf { it.getDouble("paymentAmount") ?: 0.0 }
            _feesThisYear.postValue(yearlyTotal)

        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Error fetching fees for org $orgId", e)
            _feesThisMonth.postValue(0.0)
            _feesThisYear.postValue(0.0)
        }
    }

    // NEW: Pass organizationId
    private suspend fun fetchRecentlyJoined(orgId: String) {
        try {
            // NEW: Scope queries to the organization
            val query = db.collection("organizations").document(orgId) // NEW
                .collection("students") // NEW
                .orderBy("admissionDate", Query.Direction.DESCENDING)
                .limit(10)
                .get().await()

            val recentList = query.documents.mapNotNull { doc ->
                val admissionDate = doc.getString("admissionDate")
                val formattedDate = try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(admissionDate ?: "")
                    if (date != null) SimpleDateFormat("MMM dd", Locale.getDefault()).format(date) else ""
                } catch (e: Exception) { "" }

                DashboardStudentItem(
                    id = doc.id,
                    name = doc.getString("studentName") ?: "N/A",
                    imageUrl = doc.getString("profileImageUrl"),
                    subtitle = "Joined $formattedDate"
                )
            }
            _recentlyJoinedStudents.postValue(recentList)
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Error fetching recent students for org $orgId", e)
            _recentlyJoinedStudents.postValue(emptyList())
        }
    }

    // NEW: Pass organizationId
    private suspend fun fetchAbsentStudents(orgId: String) {
        try {
            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
            Log.d("DashboardViewModel", "Fetching absentees from 'attendanceRecords' for date: $todayDateStr for org $orgId")

            // NEW: Scope queries to the organization
            val attendanceQuery = db.collection("organizations").document(orgId) // NEW
                .collection("attendanceRecords") // NEW
                .whereEqualTo("date", todayDateStr)
                .get()
                .await()

            if (attendanceQuery.isEmpty) {
                Log.d("DashboardViewModel", "No 'attendanceRecords' document found for $todayDateStr for org $orgId.")
                _absentStudents.postValue(emptyList())
                return
            }

            val absentStudentList = mutableListOf<DashboardStudentItem>()
            val processedStudentIds = mutableSetOf<String>()

            for (doc in attendanceQuery.documents) {
                val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>

                studentAttendances?.forEach { studentMap ->
                    val status = studentMap["status"] as? String
                    val studentId = studentMap["studentId"] as? String

                    if (status == "Absent" && studentId != null && !processedStudentIds.contains(studentId)) {
                        val studentName = studentMap["studentName"] as? String ?: "Unknown Student"

                        absentStudentList.add(
                            DashboardStudentItem(
                                id = studentId,
                                name = studentName,
                                imageUrl = null,
                                subtitle = doc.getString("teacherName") // Show the teacher's name
                            )
                        )
                        processedStudentIds.add(studentId)
                    }
                }
            }

            if (absentStudentList.isNotEmpty()) {
                val studentIdsToFetch = absentStudentList.map { it.id }
                // NEW: Fetch student details (including image URL) scoped to the organization
                val studentsSnapshot = db.collection("organizations").document(orgId) // NEW
                    .collection("students").whereIn(com.google.firebase.firestore.FieldPath.documentId(), studentIdsToFetch).get().await()
                val imageUrlMap = studentsSnapshot.documents.associate { it.id to it.getString("profileImageUrl") }

                val finalAbsentList = absentStudentList.map { studentItem ->
                    studentItem.copy(imageUrl = imageUrlMap[studentItem.id])
                }
                Log.d("DashboardViewModel", "Successfully processed details for ${finalAbsentList.size} absent students for org $orgId.")
                _absentStudents.postValue(finalAbsentList)
            } else {
                Log.d("DashboardViewModel", "Attendance was marked, but no students were absent for org $orgId.")
                _absentStudents.postValue(emptyList())
            }

        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Error fetching absent students from 'attendanceRecords' for org $orgId", e)
            _absentStudents.postValue(emptyList())
        }
    }


    // NEW: Pass organizationId
    private suspend fun fetchClassDistribution(orgId: String) {
        try {
            // NEW: Scope queries to the organization
            val allStudents = db.collection("organizations").document(orgId) // NEW
                .collection("students").get().await()
            val distribution = allStudents.documents
                .mapNotNull { it.getString("teacherName") }
                .groupBy { it }
                .mapValues { it.value.size }
            _classDistribution.postValue(distribution)
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Error fetching class distribution for org $orgId", e)
            _classDistribution.postValue(emptyMap())
        }
    }
}