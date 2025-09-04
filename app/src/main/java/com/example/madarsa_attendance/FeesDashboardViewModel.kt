package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FeesDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val organizationId = FirebaseAuthManager.getOrganizationId(application)
    private val TAG = "FeesDashboardVM"

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _monthlyTotal = MutableLiveData<Double>()
    val monthlyTotal: LiveData<Double> = _monthlyTotal

    private val _yearlyTotal = MutableLiveData<Double>()
    val yearlyTotal: LiveData<Double> = _yearlyTotal

    fun loadFeeDataForPeriod(year: Int, month: Int) { // month is 0-11
        if (organizationId == null) {
            Log.e(TAG, "Organization ID is null. Cannot fetch fees.")
            _monthlyTotal.value = 0.0
            _yearlyTotal.value = 0.0
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Calculate monthly total
                val calendar = Calendar.getInstance().apply { set(year, month, 1) }
                val monthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)

                val monthQuery = db.collection("organizations").document(organizationId)
                    .collection("feePayments")
                    .whereEqualTo("paymentMonth", monthYearStr)
                    .get().await()

                _monthlyTotal.postValue(monthQuery.sumOf { it.getDouble("paymentAmount") ?: 0.0 })

                // Calculate yearly total
                val yearQuery = db.collection("organizations").document(organizationId)
                    .collection("feePayments")
                    .whereEqualTo("paymentYear", year)
                    .get().await()

                _yearlyTotal.postValue(yearQuery.sumOf { it.getDouble("paymentAmount") ?: 0.0 })

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching fee stats", e)
                _monthlyTotal.postValue(0.0)
                _yearlyTotal.postValue(0.0)
            } finally {
                _isLoading.value = false
            }
        }
    }
}