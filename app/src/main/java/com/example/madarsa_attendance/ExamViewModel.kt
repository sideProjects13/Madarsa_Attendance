package com.example.madarsa_attendance

import android.app.Application // Import Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel // Change to AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// Changed from ViewModel() to AndroidViewModel(application)
class ExamViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val context = application.applicationContext // Get context from Application

    private var examsCollectionRef: com.google.firebase.firestore.CollectionReference? = null // Will be initialized with orgId

    private val _exams = MutableLiveData<List<Exam>>()
    val exams: LiveData<List<Exam>> = _exams

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    init {
        val organizationId = FirebaseAuthManager.getOrganizationId(context)
        if (organizationId != null) {
            // NEW: Initialize collection reference with organization ID
            examsCollectionRef = db.collection("organizations").document(organizationId)
                .collection("exams")
            loadExams() // Load exams now that the reference is available
        } else {
            Log.e("ExamViewModel", "Organization ID is NULL. Cannot load exams.")
            _toastMessage.value = "Error: Organization not found. Please log in again."
            _isLoading.value = false
        }
    }

    private fun loadExams() {
        if (examsCollectionRef == null) {
            Log.w("ExamViewModel", "Exams collection reference is null, cannot load exams.")
            return
        }

        _isLoading.value = true
        examsCollectionRef!!.orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e("ExamViewModel", "Error loading exams", error)
                    _toastMessage.value = "Error loading exams."
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    _exams.value = snapshot.toObjects(Exam::class.java)
                }
            }
    }

    fun addExam(examName: String) {
        if (examName.isBlank()) {
            _toastMessage.value = "Exam name cannot be empty."
            return
        }
        if (examsCollectionRef == null) { // NEW: Check if ref is initialized
            _toastMessage.value = "Error: Cannot add exam, organization data missing."
            return
        }

        val exam = hashMapOf("name" to examName)
        examsCollectionRef!!.add(exam) // NEW: Use the scoped reference
            .addOnSuccessListener {
                _toastMessage.value = "Exam added successfully."
            }
            .addOnFailureListener { e ->
                Log.e("ExamViewModel", "Error adding exam", e)
                _toastMessage.value = "Failed to add exam."
            }
    }

    fun deleteExam(examId: String) {
        if (examsCollectionRef == null) { // NEW: Check if ref is initialized
            _toastMessage.value = "Error: Cannot delete exam, organization data missing."
            return
        }

        examsCollectionRef!!.document(examId).delete() // NEW: Use the scoped reference
            .addOnSuccessListener {
                _toastMessage.value = "Exam deleted."
            }
            .addOnFailureListener { e ->
                Log.e("ExamViewModel", "Error deleting exam", e)
                _toastMessage.value = "Failed to delete exam."
            }
    }

    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}