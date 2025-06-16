package com.example.madarsa_attendance

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.madarsa_attendance.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DashboardFragment : Fragment() {

    private companion object {
        private const val TAG = "DashboardFragment"
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalTeachers: TextView
    private lateinit var tvFeesCollectedMonth: TextView

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        tvTotalStudents = view.findViewById(R.id.tvTotalStudentsCount)
        tvTotalTeachers = view.findViewById(R.id.tvTotalTeachersCount)
        tvFeesCollectedMonth = view.findViewById(R.id.tvFeesCollectedMonth)

        // Initial placeholder text
        tvTotalStudents.text = "..."
        tvTotalTeachers.text = "..."
        tvFeesCollectedMonth.text = "..."
    }

    override fun onResume() {
        super.onResume()
        // Fetch data every time the fragment is shown
        Log.d(TAG, "onResume: Fetching dashboard data.")
        fetchDashboardData()
    }

    private fun fetchDashboardData() {
        if (!isAdded) return // Make sure fragment is still attached

        // Fetch Total Students
        db.collection("students")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                tvTotalStudents.text = snapshot.size().toString()
                Log.d(TAG, "Total students: ${snapshot.size()}")
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Error fetching student count", e)
                tvTotalStudents.text = "N/A"
            }

        // Fetch Total Teachers
        db.collection("teachers")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                tvTotalTeachers.text = snapshot.size().toString()
                Log.d(TAG, "Total teachers: ${snapshot.size()}")
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Error fetching teacher count", e)
                tvTotalTeachers.text = "N/A"
            }

        // Fetch Fees Collected This Month
        val calendar = Calendar.getInstance()
        val currentMonthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        Log.d(TAG, "Fetching fees for month: $currentMonthYearStr")

        db.collection("feePayments")
            .whereEqualTo("paymentMonth", currentMonthYearStr)
            .get()
            .addOnSuccessListener { snapshot: QuerySnapshot? ->
                if (!isAdded) return@addOnSuccessListener
                var totalFeesThisMonth = 0.0
                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        totalFeesThisMonth += doc.getDouble("paymentAmount") ?: 0.0
                    }
                    Log.d(TAG, "Total fees for $currentMonthYearStr: $totalFeesThisMonth")
                } else {
                    Log.d(TAG, "No fee payments found for $currentMonthYearStr")
                }
                tvFeesCollectedMonth.text = currencyFormatter.format(totalFeesThisMonth)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Error fetching fees for month $currentMonthYearStr", e)
                tvFeesCollectedMonth.text = "N/A"
                Toast.makeText(requireContext(), "Error fetching fees: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}