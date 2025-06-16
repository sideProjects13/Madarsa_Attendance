package com.example.madarsa_attendance

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ExamViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val examsCollection = db.collection("exams")

    private val _exams = MutableLiveData<List<Exam>>()
    val exams: LiveData<List<Exam>> = _exams

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    init {
        loadExams()
    }

    private fun loadExams() {
        _isLoading.value = true
        // Using a snapshot listener to get real-time updates
        examsCollection.orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e("ExamViewModel", "Error loading exams", error)
                    _toastMessage.value = "Error loading exams."
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // Convert the snapshot into a list of Exam objects
                    _exams.value = snapshot.toObjects(Exam::class.java)
                }
            }
    }

    fun addExam(examName: String) {
        if (examName.isBlank()) {
            _toastMessage.value = "Exam name cannot be empty."
            return
        }
        val exam = hashMapOf("name" to examName)
        examsCollection.add(exam)
            .addOnSuccessListener {
                _toastMessage.value = "Exam added successfully."
            }
            .addOnFailureListener { e ->
                Log.e("ExamViewModel", "Error adding exam", e)
                _toastMessage.value = "Failed to add exam."
            }
    }

    fun deleteExam(examId: String) {
        examsCollection.document(examId).delete()
            .addOnSuccessListener {
                _toastMessage.value = "Exam deleted."
            }
            .addOnFailureListener { e ->
                Log.e("ExamViewModel", "Error deleting exam", e)
                _toastMessage.value = "Failed to delete exam."
            }
    }

    // Call this to clear the toast message after it's shown
    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}