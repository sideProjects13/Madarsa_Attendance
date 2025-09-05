package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.AggregateSource // <-- ADD THIS IMPORT
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SuperAdminViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "SuperAdminVM"

    // LiveData for UI state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData for dashboard stats
    private val _totalOrgs = MutableLiveData<Int>()
    val totalOrgs: LiveData<Int> = _totalOrgs

    private val _totalStudents = MutableLiveData<Int>()
    val totalStudents: LiveData<Int> = _totalStudents

    private val _totalTeachers = MutableLiveData<Int>()
    val totalTeachers: LiveData<Int> = _totalTeachers

    // LiveData for the list of organizations
    private val _orgStatsList = MutableLiveData<List<OrganizationStat>>()
    val orgStatsList: LiveData<List<OrganizationStat>> = _orgStatsList

    // LiveData for operation status (e.g., sending an announcement)
    private val _operationStatus = MutableLiveData<Event<Pair<Boolean, String>>>()
    val operationStatus: LiveData<Event<Pair<Boolean, String>>> = _operationStatus

    init {
        fetchAllStats()
    }

    fun fetchAllStats() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val orgsSnapshot = db.collection("organizations").get().await()
                val organizations = orgsSnapshot.documents

                _totalOrgs.postValue(organizations.size)

                val statsDeferred = organizations.map { orgDoc ->
                    async {
                        val orgName = orgDoc.getString("organizationName") ?: "Unnamed Org"

                        // --- THIS IS THE FIX ---
                        // You must provide AggregateSource.SERVER to the .get() method for a .count() query.
                        val studentCountQuery = orgDoc.reference.collection("students").count().get(AggregateSource.SERVER).await()
                        val teacherCountQuery = orgDoc.reference.collection("teachers").count().get(AggregateSource.SERVER).await()
                        // --- END OF FIX ---

                        OrganizationStat(
                            orgName = orgName,
                            studentCount = studentCountQuery.count.toInt(),
                            teacherCount = teacherCountQuery.count.toInt()
                        )
                    }
                }

                val orgStats = statsDeferred.awaitAll()

                _totalStudents.postValue(orgStats.sumOf { it.studentCount })
                _totalTeachers.postValue(orgStats.sumOf { it.teacherCount })
                _orgStatsList.postValue(orgStats.sortedBy { it.orgName })

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching all stats", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendAnnouncement(message: String) {
        if (message.isBlank()) {
            _operationStatus.value = Event(Pair(false, "Message cannot be empty."))
            return
        }

        val announcement = hashMapOf(
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("announcements").add(announcement)
            .addOnSuccessListener {
                _operationStatus.value = Event(Pair(true, "Announcement sent successfully!"))
            }
            .addOnFailureListener { e ->
                _operationStatus.value = Event(Pair(false, "Failed to send: ${e.message}"))
            }
    }
}