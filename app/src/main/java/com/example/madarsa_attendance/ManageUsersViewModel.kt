package com.example.madarsa_attendance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.madarsa_attendance.models.AppUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ManageUsersViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _users = MutableLiveData<List<AppUser>>()
    val users: LiveData<List<AppUser>> = _users

    private val _operationStatus = MutableLiveData<Event<Pair<Boolean, String>>>()
    val operationStatus: LiveData<Event<Pair<Boolean, String>>> = _operationStatus

    init {
        fetchUsers()
    }

    fun fetchUsers() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Fetch all users except for the 'superadmin' role
                val userSnapshot = db.collection("users")
                    .whereNotEqualTo("role", "superadmin")
                    .get().await()
                val userList = userSnapshot.toObjects<AppUser>()
                // Sort by status first (pending), then by name, to show pending requests at the top
                _users.postValue(userList.sortedWith(compareBy({ it.accountStatus != "pending" }, { it.name })))
            } catch (e: Exception) {
                _operationStatus.postValue(Event(Pair(false, "Failed to fetch users: ${e.message}")))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUserStatus(user: AppUser, newStatus: String) {
        viewModelScope.launch {
            try {
                db.collection("users").document(user.uid).update("accountStatus", newStatus).await()
                _operationStatus.postValue(Event(Pair(true, "User status updated to '$newStatus'")))
                fetchUsers() // Refresh the list to show the change
            } catch (e: Exception) {
                _operationStatus.postValue(Event(Pair(false, "Update failed: ${e.message}")))
            }
        }
    }
}