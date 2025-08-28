package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects

// Event class to handle one-time events
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }
    fun peekContent(): T = content
}

class TeacherDataViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "TeacherDataViewModel"
    private val organizationId: String? = FirebaseAuthManager.getOrganizationId(application)

    private val _teachersList = MutableLiveData<List<Teacher>>()
    val teachersList: LiveData<List<Teacher>> = _teachersList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _studentsDataMightHaveChanged = MutableLiveData<Event<Unit>>()
    val studentsDataMightHaveChanged: LiveData<Event<Unit>> get() = _studentsDataMightHaveChanged

    fun notifyStudentDataChanged() {
        _studentsDataMightHaveChanged.value = Event(Unit)
    }

    // --- THIS IS THE MISSING FUNCTION ---
    fun fetchTeachers() {
        if (organizationId == null) {
            Log.e(TAG, "Organization ID is null. Cannot fetch teachers.")
            _teachersList.value = emptyList()
            return
        }

        _isLoading.value = true
        db.collection("organizations").document(organizationId)
            .collection("teachers")
            .orderBy("teacherName")
            .get()
            .addOnSuccessListener { documents ->
                _teachersList.value = documents.toObjects<Teacher>()
                _isLoading.value = false
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error getting teachers: ", exception)
                _teachersList.value = emptyList()
                _isLoading.value = false
            }
    }
}