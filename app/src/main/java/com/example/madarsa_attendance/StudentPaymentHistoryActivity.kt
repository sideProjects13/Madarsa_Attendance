package com.example.madarsa_attendance

import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StudentPaymentHistoryActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "StudentPaymentHistory"
    }

    private lateinit var tvStudentNameHeader: TextView
    private lateinit var btnRecordNewPayment: Button
    private lateinit var recyclerViewPaymentHistory: RecyclerView
    private lateinit var paymentHistoryAdapter: PaymentHistoryAdapter
    private lateinit var progressBarHistory: ProgressBar
    private lateinit var progressBarReceipt: ProgressBar
    private lateinit var tvNoHistory: TextView
    private lateinit var db: FirebaseFirestore
    private var currentStudentId: String? = null
    private var currentStudentName: String? = null
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var organizationId: String? = null // Correctly defined as a class property
    private var fullStudentDetails: StudentDetailsItem? = null
    private var paymentModifiedInThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_payment_history)
        initViews()
        db = FirebaseFirestore.getInstance()
        currentStudentId = intent.getStringExtra("STUDENT_ID")
        currentStudentName = intent.getStringExtra("STUDENT_NAME")
        currentTeacherId = intent.getStringExtra("TEACHER_ID")
        currentTeacherName = intent.getStringExtra("TEACHER_NAME")
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (currentStudentId == null || currentTeacherId == null || organizationId == null) {
            Toast.makeText(this, "Essential information is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvStudentNameHeader.text = "Student: ${currentStudentName ?: "N/A"}"
        setupRecyclerView()
        fetchFullStudentDetails()
        loadPaymentHistory()
        btnRecordNewPayment.setOnClickListener { showRecordPaymentDialog(null) }
    }

    private fun initViews() {
        tvStudentNameHeader = findViewById(R.id.tvStudentNameForPayments)
        btnRecordNewPayment = findViewById(R.id.btnRecordNewPayment)
        recyclerViewPaymentHistory = findViewById(R.id.recyclerViewPaymentHistory)
        progressBarHistory = findViewById(R.id.progressBarPaymentHistory)
        progressBarReceipt = findViewById(R.id.progressBarReceipt)
        tvNoHistory = findViewById(R.id.tvNoPaymentHistory)
    }

    private fun fetchFullStudentDetails() {
        db.collection("organizations").document(organizationId!!)
            .collection("students").document(currentStudentId!!)
            .get()
            .addOnSuccessListener { document ->
                fullStudentDetails = document.toObject(StudentDetailsItem::class.java)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not load full student details.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRecyclerView() {
        paymentHistoryAdapter = PaymentHistoryAdapter(emptyList()) { paymentItem ->
            showPaymentOptionsDialog(paymentItem)
        }
        recyclerViewPaymentHistory.layoutManager = LinearLayoutManager(this)
        recyclerViewPaymentHistory.adapter = paymentHistoryAdapter
    }

    private fun showPaymentOptionsDialog(payment: FeePaymentItem) {
        val options = arrayOf("Share Receipt", "Edit Payment", "Delete Payment")
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Payment Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> handleShareReceiptClick(payment)
                    1 -> showRecordPaymentDialog(payment)
                    2 -> confirmDeletePayment(payment)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeletePayment(payment: FeePaymentItem) {
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Payment")
            .setMessage("Are you sure you want to delete this payment of ₹${payment.paymentAmount}?")
            .setPositiveButton("Delete") { _, _ ->
                deletePayment(payment.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePayment(paymentId: String) {
        progressBarHistory.visibility = View.VISIBLE
        db.collection("organizations").document(organizationId!!)
            .collection("feePayments").document(paymentId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Payment deleted.", Toast.LENGTH_SHORT).show()
                paymentModifiedInThisSession = true
                setResult(Activity.RESULT_OK)
                loadPaymentHistory()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                progressBarHistory.visibility = View.GONE
            }
    }

    private fun loadPaymentHistory() {
        progressBarHistory.visibility = View.VISIBLE
        tvNoHistory.visibility = View.GONE
        recyclerViewPaymentHistory.visibility = View.GONE

        db.collection("organizations").document(organizationId!!)
            .collection("feePayments")
            .whereEqualTo("studentId", currentStudentId)
            .orderBy("paymentDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                progressBarHistory.visibility = View.GONE
                if (querySnapshot.isEmpty) {
                    tvNoHistory.visibility = View.VISIBLE
                    paymentHistoryAdapter.updateData(emptyList())
                } else {
                    val payments = querySnapshot.toObjects(FeePaymentItem::class.java)
                    paymentHistoryAdapter.updateData(payments)
                    tvNoHistory.visibility = View.GONE
                    recyclerViewPaymentHistory.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                progressBarHistory.visibility = View.GONE
                tvNoHistory.text = "Error loading history."
                tvNoHistory.visibility = View.VISIBLE
                Log.e(TAG, "Error fetching payment history: ", e)
            }
    }

    private fun showRecordPaymentDialog(paymentToEdit: FeePaymentItem?) {
        val isEditing = paymentToEdit != null
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_record_payment, null)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etPaymentAmountDialog)
        val btnSelectFeeMonth = dialogView.findViewById<Button>(R.id.btnSelectFeeMonthDialog)

        val feeMonthCalendar = Calendar.getInstance()

        if (isEditing) {
            etAmount.setText(String.format(Locale.US, "%.0f", paymentToEdit!!.paymentAmount))
            val monthYearParts = paymentToEdit.paymentMonth.split("-")
            if (monthYearParts.size == 2) {
                try {
                    feeMonthCalendar.set(Calendar.YEAR, monthYearParts[0].toInt())
                    feeMonthCalendar.set(Calendar.MONTH, monthYearParts[1].toInt() - 1)
                } catch (e: NumberFormatException) { /* Keep current date on error */ }
            }
        } else {
            fullStudentDetails?.monthlyFee?.let { etAmount.setText(String.format(Locale.US, "%.0f", it)) }
        }
        btnSelectFeeMonth.text = "Fee for: ${SimpleDateFormat("MMMM, yyyy", Locale.getDefault()).format(feeMonthCalendar.time)}"

        btnSelectFeeMonth.setOnClickListener {
            showMonthYearPickerDialog(feeMonthCalendar) { updatedCalendar ->
                feeMonthCalendar.time = updatedCalendar.time
                btnSelectFeeMonth.text = "Fee for: ${SimpleDateFormat("MMMM, yyyy", Locale.getDefault()).format(feeMonthCalendar.time)}"
            }
        }

        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle(if (isEditing) "Edit Payment" else "Record Payment")
            .setView(dialogView)
            .setPositiveButton(if (isEditing) "Update" else "Save") { _, _ ->
                val amountStr = etAmount.text.toString().trim()
                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                progressBarHistory.visibility = View.VISIBLE
                if (isEditing) {
                    val updatedData = hashMapOf<String, Any>(
                        "paymentAmount" to amount,
                        "paymentMonth" to SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(feeMonthCalendar.time),
                        "paymentYear" to feeMonthCalendar.get(Calendar.YEAR)
                    )
                    updatePayment(paymentToEdit!!.id, updatedData)
                } else {
                    val paymentData = hashMapOf(
                        "studentId" to currentStudentId!!,
                        "studentName" to (currentStudentName ?: "N/A"),
                        "teacherId" to currentTeacherId!!,
                        "teacherName" to (currentTeacherName ?: "N/A"),
                        "paymentAmount" to amount,
                        "paymentDate" to Date(),
                        "paymentMonth" to SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(feeMonthCalendar.time),
                        "paymentYear" to feeMonthCalendar.get(Calendar.YEAR),
                        "recordedAt" to FieldValue.serverTimestamp()
                    )
                    addPayment(paymentData)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun addPayment(paymentData: HashMap<String, Any>) {
        db.collection("organizations").document(organizationId!!)
            .collection("feePayments").add(paymentData)
            .addOnSuccessListener {
                Toast.makeText(this, "Payment Saved.", Toast.LENGTH_SHORT).show()
                paymentModifiedInThisSession = true
                setResult(Activity.RESULT_OK)
                loadPaymentHistory()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener { progressBarHistory.visibility = View.GONE }
    }

    private fun updatePayment(paymentId: String, updatedData: HashMap<String, Any>) {
        db.collection("organizations").document(organizationId!!)
            .collection("feePayments").document(paymentId)
            .update(updatedData)
            .addOnSuccessListener {
                Toast.makeText(this, "Payment Updated.", Toast.LENGTH_SHORT).show()
                paymentModifiedInThisSession = true
                setResult(Activity.RESULT_OK)
                loadPaymentHistory()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener { progressBarHistory.visibility = View.GONE }
    }

    private fun showMonthYearPickerDialog(initialCalendar: Calendar, onDateSet: (Calendar) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_month_year_picker, null)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.picker_month)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.picker_year)

        monthPicker.minValue = 0
        monthPicker.maxValue = 11
        monthPicker.displayedValues = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        monthPicker.value = initialCalendar.get(Calendar.MONTH)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        yearPicker.minValue = currentYear - 10
        yearPicker.maxValue = currentYear + 10
        yearPicker.value = initialCalendar.get(Calendar.YEAR)

        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(Calendar.YEAR, yearPicker.value)
                selectedCalendar.set(Calendar.MONTH, monthPicker.value)
                onDateSet(selectedCalendar)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleShareReceiptClick(payment: FeePaymentItem) {
        if (payment.paymentDate == null) {
            Toast.makeText(this, "Cannot generate receipt, payment date is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        if (fullStudentDetails == null) {
            Toast.makeText(this, "Student details not loaded yet, please wait.", Toast.LENGTH_SHORT).show()
            return
        }
        generateAndShareReceiptImage(payment)
    }

    private fun generateAndShareReceiptImage(payment: FeePaymentItem) {
        val student = fullStudentDetails!!
        progressBarReceipt.visibility = View.VISIBLE

        lifecycleScope.launch {
            val logoBitmap = LogoProvider.getActiveLogo(applicationContext)

            val receiptUri = withContext(Dispatchers.IO) {
                ReceiptImageGenerator.createFeeReceiptImage(
                    context = applicationContext,
                    studentName = student.studentName,
                    teacherName = student.teacherName ?: "N/A",
                    registrationId = student.regNo ?: "N/A",
                    feeMonth = SimpleDateFormat("MMMM, yyyy", Locale.getDefault()).format(payment.paymentDate!!),
                    paymentDate = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(payment.paymentDate!!),
                    totalAmount = student.monthlyFee ?: payment.paymentAmount,
                    depositAmount = payment.paymentAmount,
                    remainingAmount = (student.monthlyFee ?: payment.paymentAmount) - payment.paymentAmount,
                    logoBitmap = logoBitmap
                )
            }

            progressBarReceipt.visibility = View.GONE
            if (receiptUri != null) {
                shareReceiptViaWhatsApp(receiptUri, student)
            } else {
                Toast.makeText(applicationContext, "Failed to create receipt image.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareReceiptViaWhatsApp(receiptUri: Uri, student: StudentDetailsItem) {
        val parentMobile = student.parentMobileNumber
        if (parentMobile.isNullOrEmpty()) {
            Toast.makeText(this, "Parent mobile number not available.", Toast.LENGTH_LONG).show()
            return
        }
        val cleanMobileNumber = parentMobile.replace(Regex("[^0-9]"), "")
        val whatsappNumber = if (cleanMobileNumber.length > 10) cleanMobileNumber else "91$cleanMobileNumber"

        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, receiptUri)
                putExtra("jid", "$whatsappNumber@s.whatsapp.net")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(shareIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (paymentModifiedInThisSession) {
            setResult(Activity.RESULT_OK)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        super.onBackPressed()
    }
}