package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ManageClassViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "ManageClassVM"
    private val organizationId: String? = FirebaseAuthManager.getOrganizationId(application)

    private val _students = MutableLiveData<List<StudentDetailsItem>>()
    val students: LiveData<List<StudentDetailsItem>> = _students

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var currentTeacherId: String? = null
    private var isDataLoaded = false

    fun loadStudentsIfNeeded(teacherId: String) {
        // Only load if the teacherId has changed or if data has never been loaded
        if (teacherId != currentTeacherId || !isDataLoaded) {
            currentTeacherId = teacherId
            refreshStudents()
        }
    }

    fun refreshStudents() {
        if (organizationId == null || currentTeacherId == null) {
            _errorMessage.value = "Error: Organization or Teacher ID is missing."
            return
        }
        val teacherId = currentTeacherId!!

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val querySnapshot = db.collection("organizations").document(organizationId)
                    .collection("students")
                    .whereEqualTo("teacherId", teacherId)
                    .whereEqualTo("isActive", true)
                    .orderBy("studentName", Query.Direction.ASCENDING)
                    .get().await()

                val studentList = querySnapshot.toObjects<StudentDetailsItem>()
                _students.postValue(studentList)
                isDataLoaded = true
                Log.d(TAG, "Successfully loaded ${studentList.size} students for teacher $teacherId.")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading students for teacher $teacherId", e)
                _errorMessage.postValue("Error loading students. Please check connection and Firestore indexes.")
            } finally {
                _isLoading.value = false
            }
        }
    }
}