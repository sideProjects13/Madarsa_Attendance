package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Date

class StudentPaymentHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val organizationId: String? = FirebaseAuthManager.getOrganizationId(application)

    private val _studentDetails = MutableLiveData<StudentDetailsItem?>()
    val studentDetails: LiveData<StudentDetailsItem?> = _studentDetails

    // LiveData to signal the result of an operation (add, update, delete)
    private val _operationStatus = MutableLiveData<Event<Pair<Boolean, String>>>()
    val operationStatus: LiveData<Event<Pair<Boolean, String>>> = _operationStatus

    fun fetchStudentDetails(organizationId: String, studentId: String) {
        viewModelScope.launch {
            db.collection("organizations").document(organizationId)
                .collection("students").document(studentId)
                .get()
                .addOnSuccessListener { document ->
                    val student = document.toObject(StudentDetailsItem::class.java)
                    _studentDetails.postValue(student)
                }
                .addOnFailureListener {
                    _studentDetails.postValue(null)
                }
        }
    }

    fun addPayment(paymentData: HashMap<String, Any>) {
        if (organizationId == null) {
            _operationStatus.value = Event(Pair(false, "Error: Organization ID missing."))
            return
        }
        db.collection("organizations").document(organizationId)
            .collection("feePayments").add(paymentData)
            .addOnSuccessListener {
                _operationStatus.value = Event(Pair(true, "Payment Recorded!"))
            }
            .addOnFailureListener { e ->
                Log.e("PaymentVM", "Error adding payment", e)
                _operationStatus.value = Event(Pair(false, "Failed to Record Payment"))
            }
    }

    fun updatePayment(paymentId: String, updatedData: HashMap<String, Any>) {
        if (organizationId == null) {
            _operationStatus.value = Event(Pair(false, "Error: Organization ID missing."))
            return
        }
        db.collection("organizations").document(organizationId)
            .collection("feePayments").document(paymentId)
            .update(updatedData)
            .addOnSuccessListener {
                _operationStatus.value = Event(Pair(true, "Payment Updated!"))
            }
            .addOnFailureListener { e ->
                Log.e("PaymentVM", "Error updating payment", e)
                _operationStatus.value = Event(Pair(false, "Failed to Update Payment"))
            }
    }

    fun deletePayment(paymentId: String) {
        if (organizationId == null) {
            _operationStatus.value = Event(Pair(false, "Error: Organization ID missing."))
            return
        }
        db.collection("organizations").document(organizationId)
            .collection("feePayments").document(paymentId)
            .delete()
            .addOnSuccessListener {
                _operationStatus.value = Event(Pair(true, "Payment Deleted!"))
            }
            .addOnFailureListener { e ->
                Log.e("PaymentVM", "Error deleting payment", e)
                _operationStatus.value = Event(Pair(false, "Failed to Delete Payment"))
            }
    }
}