package com.example.madarsa_attendance // <<< YOUR ACTUAL PACKAGE NAME

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
// Assumes StudentPaymentSummaryItem is defined in DataModels.kt (or imported)

class ClassPaymentSummaryActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "ClassPaymentSummary"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var spinnerMonth: Spinner
    private lateinit var spinnerYear: Spinner
    private lateinit var recyclerViewPayments: RecyclerView
    private lateinit var paymentSummaryAdapter: PaymentSummaryAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoData: TextView

    private lateinit var db: FirebaseFirestore
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null

    private val paymentSummaryDisplayList = mutableListOf<StudentPaymentSummaryItem>()

    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) // 0-indexed (January is 0)
    private var initialSpinnerSetupDone = false // Flag to prevent auto-load on initial spinner set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_payment_summary)

        Log.d(TAG, "onCreate: Activity starting.")
        db = FirebaseFirestore.getInstance()
        currentTeacherId = intent.getStringExtra("TEACHER_ID")
        currentTeacherName = intent.getStringExtra("TEACHER_NAME")

        toolbar = findViewById(R.id.class_payment_summary_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.title = "Payments: ${currentTeacherName ?: "Class"}"
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        spinnerMonth = findViewById(R.id.spinnerMonthPaymentSummary)
        spinnerYear = findViewById(R.id.spinnerYearPaymentSummary)
        recyclerViewPayments = findViewById(R.id.recyclerViewPaymentSummary)
        progressBar = findViewById(R.id.progressBarPaymentSummary)
        tvNoData = findViewById(R.id.tvNoPaymentData)

        if (currentTeacherId == null) {
            Log.e(TAG, "Teacher ID is null. Finishing activity.")
            Toast.makeText(this, "Teacher information missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupRecyclerView()
        setupSpinners() // Setup spinners before initial load
        // loadPaymentSummaryData() will be called by spinner's onItemSelected initially if needed,
        // or we can call it explicitly after setup if setSelection doesn't trigger it.
        // For safety, let's call it once after spinners are set.
        // The initialSpinnerSetupDone flag will prevent multiple loads.
    }

    private fun setupSpinners() {
        Log.d(TAG, "setupSpinners called.")
        // Month Spinner
        val months = SimpleDateFormat("MMMM", Locale.getDefault()).let { sdf ->
            (0..11).map {
                val cal = Calendar.getInstance(); cal.set(Calendar.MONTH, it); sdf.format(cal.time)
            }
        }
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonth.adapter = monthAdapter
        spinnerMonth.setSelection(selectedMonth, false) // Set initial without triggering listener

        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!initialSpinnerSetupDone && position == selectedMonth) return // Skip initial auto-trigger if same
                if (selectedMonth != position) {
                    selectedMonth = position
                    Log.d(TAG, "Month selected by user: ${selectedMonth + 1}")
                    loadPaymentSummaryData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Year Spinner
        val currentYearValue = Calendar.getInstance().get(Calendar.YEAR)
        // Show last 5 years, current year, and next year for flexibility
        val years = (currentYearValue - 5..currentYearValue + 1).map { it.toString() }.reversed().toList()
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerYear.adapter = yearAdapter
        spinnerYear.setSelection(years.indexOf(selectedYear.toString()), false) // Set initial

        spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val year = years[position].toInt()
                if (!initialSpinnerSetupDone && year == selectedYear) return // Skip initial auto-trigger if same
                if (selectedYear != year) {
                    selectedYear = year
                    Log.d(TAG, "Year selected by user: $selectedYear")
                    loadPaymentSummaryData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        initialSpinnerSetupDone = true // Mark spinners as set up
        loadPaymentSummaryData() // Perform initial data load now that spinners are ready
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView called.")
        paymentSummaryAdapter = PaymentSummaryAdapter(paymentSummaryDisplayList) { studentSummaryItem ->
            Log.d(TAG, "Student item clicked: ${studentSummaryItem.studentName}, ID: ${studentSummaryItem.studentId}")
            val intent = Intent(this, StudentPaymentHistoryActivity::class.java).apply {
                putExtra("STUDENT_ID", studentSummaryItem.studentId)
                putExtra("STUDENT_NAME", studentSummaryItem.studentName)
                putExtra("TEACHER_ID", currentTeacherId) // Pass teacherId for context
            }
            // Consider using ActivityResultLauncher if you need to refresh this screen after StudentPaymentHistoryActivity
            startActivity(intent)
        }
        recyclerViewPayments.layoutManager = LinearLayoutManager(this)
        recyclerViewPayments.adapter = paymentSummaryAdapter
    }

    private fun loadPaymentSummaryData() {
        if (currentTeacherId == null) {
            Log.e(TAG, "loadPaymentSummaryData: Aborting, currentTeacherId is null.")
            return
        }
        Log.d(TAG, "Loading payment summary for Teacher ID: $currentTeacherId, Year: $selectedYear, Month: ${selectedMonth + 1}")

        progressBar.visibility = View.VISIBLE
        tvNoData.visibility = View.GONE
        recyclerViewPayments.visibility = View.GONE // Hide while loading

        val calendar = Calendar.getInstance()
        calendar.set(selectedYear, selectedMonth, 1)
        val targetMonthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        Log.d(TAG, "Querying for paymentMonth: $targetMonthYearStr")

        val studentsMap = mutableMapOf<String, String>() // studentId to studentName
        val studentMonthlyPaymentDetails = mutableMapOf<String, Pair<Double, Int>>() // studentId to Pair<TotalAmount, PaymentCount>

        // 1. Fetch all students for this teacher
        db.collection("students")
            .whereEqualTo("teacherId", currentTeacherId)
            .orderBy("studentName") // Order for consistent display
            .get()
            .addOnSuccessListener { studentsSnapshot ->
                if (studentsSnapshot.isEmpty) {
                    Log.d(TAG, "No students found for teacher $currentTeacherId")
                    progressBar.visibility = View.GONE
                    tvNoData.text = "No students in this class."
                    tvNoData.visibility = View.VISIBLE
                    recyclerViewPayments.visibility = View.GONE
                    paymentSummaryAdapter.updateData(emptyList()) // Clear adapter
                    return@addOnSuccessListener
                }
                Log.d(TAG, "Fetched ${studentsSnapshot.size()} students for class.")
                studentsSnapshot.forEach { doc ->
                    val studentId = doc.id
                    studentsMap[studentId] = doc.getString("studentName") ?: "N/A"
                    studentMonthlyPaymentDetails[studentId] = Pair(0.0, 0) // Initialize
                }

                // 2. Fetch feePayments for the selected month and for THIS teacher
                db.collection("feePayments")
                    .whereEqualTo("teacherId", currentTeacherId) // Filter by teacherId
                    .whereEqualTo("paymentMonth", targetMonthYearStr) // Filter by "yyyy-MM"
                    .get()
                    .addOnSuccessListener { paymentsSnapshot ->
                        progressBar.visibility = View.GONE // Hide progress bar after all queries attempt
                        if (!paymentsSnapshot.isEmpty) {
                            Log.d(TAG, "Found ${paymentsSnapshot.size()} payment records for $targetMonthYearStr for this teacher.")
                            paymentsSnapshot.forEach { paymentDoc ->
                                val studentId = paymentDoc.getString("studentId")
                                val amount = paymentDoc.getDouble("paymentAmount") ?: 0.0
                                if (studentId != null && studentsMap.containsKey(studentId)) { // Ensure payment is for a student in this class
                                    val currentData = studentMonthlyPaymentDetails[studentId] ?: Pair(0.0, 0)
                                    studentMonthlyPaymentDetails[studentId] = Pair(currentData.first + amount, currentData.second + 1)
                                }
                            }
                        } else {
                            Log.d(TAG, "No payment records found for $targetMonthYearStr for this teacher.")
                        }
                        processAndDisplaySummary(studentsMap, studentMonthlyPaymentDetails)
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        Log.e(TAG, "Error fetching payment records for $targetMonthYearStr", e)
                        Toast.makeText(this, "Error loading payment records: ${e.message}", Toast.LENGTH_LONG).show()
                        tvNoData.text = "Error loading payment records."
                        tvNoData.visibility = View.VISIBLE
                        paymentSummaryAdapter.updateData(emptyList())
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error fetching students for teacher $currentTeacherId", e)
                Toast.makeText(this, "Error loading students: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoData.text = "Error loading students."
                tvNoData.visibility = View.VISIBLE
            }
    }

    private fun processAndDisplaySummary(
        studentsMap: Map<String, String>,
        studentMonthlyPaymentDetails: Map<String, Pair<Double, Int>>
    ) {
        Log.d(TAG, "processAndDisplaySummary: Processing ${studentsMap.size} students.")
        paymentSummaryDisplayList.clear()
        // Iterate through the students of the class (from studentsMap) to ensure all are listed
        studentsMap.forEach { (studentId, studentName) ->
            val paymentInfo = studentMonthlyPaymentDetails[studentId] ?: Pair(0.0, 0)
            paymentSummaryDisplayList.add(
                StudentPaymentSummaryItem(
                    studentId = studentId,
                    studentName = studentName,
                    totalPaidThisMonth = paymentInfo.first,
                    paymentCountThisMonth = paymentInfo.second
                )
            )
        }

        // Sort by student name for consistent order
        // paymentSummaryDisplayList.sortBy { it.studentName } // Already fetched ordered by studentName

        if (paymentSummaryDisplayList.isEmpty() && studentsMap.isNotEmpty()){
            tvNoData.text = "No payment data for students in this period."
            tvNoData.visibility = View.VISIBLE
            recyclerViewPayments.visibility = View.GONE
        } else if (studentsMap.isEmpty()){ // This case should be handled by the student fetch logic
            // tvNoData.text = "No students in this class."
            // tvNoData.visibility = View.VISIBLE
            // recyclerViewPayments.visibility = View.GONE
        } else if (paymentSummaryDisplayList.isNotEmpty()) {
            tvNoData.visibility = View.GONE
            recyclerViewPayments.visibility = View.VISIBLE
        } else { // No students and no payments (should be caught by studentsMap.isEmpty)
            tvNoData.text = "No students or payment data available."
            tvNoData.visibility = View.VISIBLE
            recyclerViewPayments.visibility = View.GONE
        }
        paymentSummaryAdapter.updateData(paymentSummaryDisplayList)
        Log.d(TAG, "Payment summary UI updated with ${paymentSummaryDisplayList.size} student items.")
    }
}