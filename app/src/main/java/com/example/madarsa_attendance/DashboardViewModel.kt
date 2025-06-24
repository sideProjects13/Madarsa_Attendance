package com.example.madarsa_attendance

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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

class DashboardViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

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

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch all data in parallel
                val countsDeferred = async { fetchCounts() }
                val feesDeferred = async { fetchFeeStats() }
                val recentStudentsDeferred = async { fetchRecentlyJoined() }
                val absentStudentsDeferred = async { fetchAbsentStudents() }
                val classDistDeferred = async { fetchClassDistribution() }

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

    private suspend fun fetchCounts() {
        try {
            val studentsCount = db.collection("students").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
            val teachersCount = db.collection("teachers").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
            _totalStudents.postValue(studentsCount)
            _totalTeachers.postValue(teachersCount)
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Error fetching counts", e)
            _totalStudents.postValue(0)
            _totalTeachers.postValue(0)
        }
    }

    private suspend fun fetchFeeStats() {
        try {
            val calendar = Calendar.getInstance()
            val currentMonthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
            val currentYear = calendar.get(Calendar.YEAR)

            // Fees this month
            val monthQuery = db.collection("feePayments")
                .whereEqualTo("paymentMonth", currentMonthYearStr)
                .get().await()
            val monthlyTotal = monthQuery.sumOf { it.getDouble("paymentAmount") ?: 0.0 }
            _feesThisMonth.postValue(monthlyTotal)

            // Fees this year
            val yearQuery = db.collection("feePayments")
                .whereEqualTo("paymentYear", currentYear)
                .get().await()
            val yearlyTotal = yearQuery.sumOf { it.getDouble("paymentAmount") ?: 0.0 }
            _feesThisYear.postValue(yearlyTotal)

        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Error fetching fees", e)
            _feesThisMonth.postValue(0.0)
            _feesThisYear.postValue(0.0)
        }
    }

    private suspend fun fetchRecentlyJoined() {
        try {
            val query = db.collection("students")
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
            Log.e("DashboardViewModel", "Error fetching recent students", e)
            _recentlyJoinedStudents.postValue(emptyList())
        }
    }

    /**
     * IMPORTANT: This assumes you have an 'attendance' collection.
     * Document structure should be like:
     * Collection: attendance
     * Document ID: 2025-06-17 (yyyy-MM-dd)
     * Fields:
     *  - absent_students: ["studentId1", "studentId2", ...] (Array of student document IDs)
     */
    private suspend fun fetchAbsentStudents() {
        try {
            // 1. Get today's date in the "yyyy-MM-dd" format.
            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
            Log.d("DashboardViewModel", "Fetching absentees from 'attendanceRecords' for date: $todayDateStr")

            // 2. Query the 'attendanceRecords' collection for a document with today's date.
            //    Since there might be multiple records if multiple teachers take attendance,
            //    we will fetch all of them and combine the results.
            val attendanceQuery = db.collection("attendanceRecords")
                .whereEqualTo("date", todayDateStr)
                .get()
                .await()

            if (attendanceQuery.isEmpty) {
                // 3. No attendance was marked for today in any class.
                Log.d("DashboardViewModel", "No 'attendanceRecords' document found for $todayDateStr.")
                _absentStudents.postValue(emptyList())
                return
            }

            // 4. We found one or more attendance records for today. Let's process them.
            val absentStudentList = mutableListOf<DashboardStudentItem>()
            val processedStudentIds = mutableSetOf<String>() // To prevent duplicates if a student is in multiple records

            for (doc in attendanceQuery.documents) {
                // 5. Get the array of maps. Safely cast it.
                val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>

                studentAttendances?.forEach { studentMap ->
                    val status = studentMap["status"] as? String
                    val studentId = studentMap["studentId"] as? String

                    // 6. Check if the student is absent AND we haven't already added them.
                    if (status == "Absent" && studentId != null && !processedStudentIds.contains(studentId)) {
                        val studentName = studentMap["studentName"] as? String ?: "Unknown Student"

                        // Add this student to our list for the dashboard.
                        absentStudentList.add(
                            DashboardStudentItem(
                                id = studentId,
                                name = studentName,
                                // We need the profile image URL. It's not in your attendance record, so we'll fetch it.
                                // This part is less efficient but necessary with the current structure.
                                imageUrl = null, // Will be fetched in the next step
                                subtitle = doc.getString("teacherName") // Show the teacher's name
                            )
                        )
                        // Mark this student ID as processed.
                        processedStudentIds.add(studentId)
                    }
                }
            }

            // 7. OPTIONAL BUT RECOMMENDED: Fetch profile images for the absent students.
            //    Your 'attendanceRecords' do not store the image URL, so we need to get it from the 'students' collection.
            if (absentStudentList.isNotEmpty()) {
                val studentIdsToFetch = absentStudentList.map { it.id }
                val studentsSnapshot = db.collection("students").whereIn(com.google.firebase.firestore.FieldPath.documentId(), studentIdsToFetch).get().await()
                val imageUrlMap = studentsSnapshot.documents.associate { it.id to it.getString("profileImageUrl") }

                val finalAbsentList = absentStudentList.map { studentItem ->
                    studentItem.copy(imageUrl = imageUrlMap[studentItem.id])
                }
                Log.d("DashboardViewModel", "Successfully processed details for ${finalAbsentList.size} absent students.")
                _absentStudents.postValue(finalAbsentList)
            } else {
                // No students were marked as "Absent" in today's records.
                Log.d("DashboardViewModel", "Attendance was marked, but no students were absent.")
                _absentStudents.postValue(emptyList())
            }

        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Error fetching absent students from 'attendanceRecords'", e)
            _absentStudents.postValue(emptyList()) // Post an empty list on error
        }
    }


    /**
     * WARNING: This can be inefficient on large datasets as it reads ALL students.
     * For production apps with thousands of students, use a Cloud Function to aggregate this data.
     */
    private suspend fun fetchClassDistribution() {
        try {
            val allStudents = db.collection("students").get().await()
            val distribution = allStudents.documents
                .mapNotNull { it.getString("teacherName") }
                .groupBy { it }
                .mapValues { it.value.size }
            _classDistribution.postValue(distribution)
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Error fetching class distribution", e)
            _classDistribution.postValue(emptyMap())
        }
    }
}