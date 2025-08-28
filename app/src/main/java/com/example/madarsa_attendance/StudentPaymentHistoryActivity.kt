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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
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

    private val viewModel: StudentPaymentHistoryViewModel by viewModels()
    private lateinit var tvStudentNameHeader: TextView
    private lateinit var btnRecordNewPayment: Button
    private lateinit var recyclerViewPaymentHistory: RecyclerView
    private lateinit var paymentHistoryAdapter: PaymentHistoryAdapter
    private lateinit var progressBarHistory: ProgressBar
    private lateinit var tvNoHistory: TextView
    private lateinit var db: FirebaseFirestore
    private var currentStudentId: String? = null
    private var currentStudentName: String? = null
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentOrganizationId: String? = null
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
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)

        if (currentStudentId == null || currentTeacherId == null || currentOrganizationId == null) {
            Toast.makeText(this, "Essential information is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvStudentNameHeader.text = "Student: ${currentStudentName ?: "N/A"}"
        setupRecyclerView()
        setupObservers()
        viewModel.fetchStudentDetails(currentOrganizationId!!, currentStudentId!!)
        loadPaymentHistory()
        btnRecordNewPayment.setOnClickListener { showRecordPaymentDialog(null) }
    }

    private fun initViews() {
        tvStudentNameHeader = findViewById(R.id.tvStudentNameForPayments)
        btnRecordNewPayment = findViewById(R.id.btnRecordNewPayment)
        recyclerViewPaymentHistory = findViewById(R.id.recyclerViewPaymentHistory)
        progressBarHistory = findViewById(R.id.progressBarPaymentHistory)
        tvNoHistory = findViewById(R.id.tvNoPaymentHistory)
    }

    private fun setupObservers() {
        viewModel.studentDetails.observe(this) { student ->
            fullStudentDetails = student
        }

        viewModel.operationStatus.observe(this) { event ->
            event.getContentIfNotHandled()?.let { (isSuccess, message) ->
                progressBarHistory.visibility = View.GONE
                // --- THIS IS THE FIX ---
                StatusDialogFragment.newInstance(isSuccess, message).show(supportFragmentManager, "statusDialog")
                if (isSuccess) {
                    paymentModifiedInThisSession = true
                    setResult(Activity.RESULT_OK)
                    loadPaymentHistory() // Refresh the list on any success
                }
            }
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
                progressBarHistory.visibility = View.VISIBLE
                viewModel.deletePayment(payment.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadPaymentHistory() {
        progressBarHistory.visibility = View.VISIBLE
        tvNoHistory.visibility = View.GONE
        recyclerViewPaymentHistory.visibility = View.GONE

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("feePayments")
            .whereEqualTo("studentId", currentStudentId)
            .orderBy("paymentDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                progressBarHistory.visibility = View.GONE
                if (querySnapshot.isEmpty) {
                    tvNoHistory.visibility = View.VISIBLE
                    recyclerViewPaymentHistory.visibility = View.GONE
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
        val btnSelectDate = dialogView.findViewById<Button>(R.id.btnSelectPaymentDateDialog)
        val dialogDateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        val selectedDate = Calendar.getInstance()

        if (isEditing) {
            etAmount.setText(String.format(Locale.US, "%.0f", paymentToEdit!!.paymentAmount))
            paymentToEdit.paymentDate?.let { selectedDate.time = it }
        } else {
            fullStudentDetails?.monthlyFee?.let { etAmount.setText(String.format(Locale.US, "%.0f", it)) }
        }
        btnSelectDate.text = "Date: ${dialogDateFormat.format(selectedDate.time)}"

        btnSelectDate.setOnClickListener {
            DatePickerDialog(
                this, R.style.DatePickerDialog_App_Monochrome,
                { _, year, month, dayOfMonth ->
                    selectedDate.set(year, month, dayOfMonth)
                    btnSelectDate.text = "Date: ${dialogDateFormat.format(selectedDate.time)}"
                }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
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
                        "paymentDate" to selectedDate.time,
                        "paymentMonth" to SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(selectedDate.time),
                        "paymentYear" to selectedDate.get(Calendar.YEAR)
                    )
                    viewModel.updatePayment(paymentToEdit!!.id, updatedData)
                } else {
                    val paymentData = hashMapOf<String, Any>(
                        "studentId" to currentStudentId!!,
                        "studentName" to (currentStudentName ?: "N/A"),
                        "teacherId" to currentTeacherId!!,
                        "teacherName" to (currentTeacherName ?: "N/A"),
                        "paymentAmount" to amount,
                        "paymentDate" to selectedDate.time,
                        "paymentMonth" to SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(selectedDate.time),
                        "paymentYear" to selectedDate.get(Calendar.YEAR),
                        "recordedAt" to FieldValue.serverTimestamp()
                    )
                    viewModel.addPayment(paymentData)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
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
        val progressBarReceipt = findViewById<ProgressBar>(R.id.progressBarReceipt)
        progressBarReceipt.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val receiptUri = ReceiptImageGenerator.createFeeReceiptImage(
                context = applicationContext,
                studentName = fullStudentDetails!!.studentName,
                teacherName = fullStudentDetails!!.teacherName ?: "N/A",
                registrationId = fullStudentDetails!!.regNo ?: "N/A",
                feeMonth = SimpleDateFormat("MMMM, yyyy", Locale.getDefault()).format(payment.paymentDate!!),
                totalAmount = fullStudentDetails!!.monthlyFee ?: payment.paymentAmount,
                depositAmount = payment.paymentAmount,
                remainingAmount = (fullStudentDetails!!.monthlyFee ?: payment.paymentAmount) - payment.paymentAmount
            )
            withContext(Dispatchers.Main) {
                progressBarReceipt.visibility = View.GONE
                if (receiptUri != null) {
                    shareReceiptViaWhatsApp(receiptUri, fullStudentDetails!!)
                } else {
                    Toast.makeText(applicationContext, "Failed to create receipt image.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- THIS IS THE FINAL, CORRECTED SHARING FUNCTION ---
    private fun shareReceiptViaWhatsApp(receiptUri: Uri, student: StudentDetailsItem) {
        val parentMobile = student.parentMobileNumber
        if (parentMobile.isNullOrEmpty()) {
            Toast.makeText(this, "Parent mobile number not available.", Toast.LENGTH_LONG).show()
            return
        }

        // 1. Robustly clean the phone number: remove anything that isn't a digit.
        val digitsOnlyNumber = parentMobile.filter { it.isDigit() }

        // 2. Format for WhatsApp JID: Ensure it has the country code.
        val whatsappNumber = when {
            digitsOnlyNumber.length > 10 && digitsOnlyNumber.startsWith("91") -> digitsOnlyNumber
            digitsOnlyNumber.length == 10 -> "91$digitsOnlyNumber"
            else -> digitsOnlyNumber // Fallback for other formats
        }

        if (whatsappNumber.isEmpty()) {
            Toast.makeText(this, "Invalid parent mobile number format.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            // 3. Create the ACTION_SEND Intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, receiptUri)
                setPackage("com.whatsapp")
                // 4. Use the "jid" extra to target the unsaved number directly
                putExtra("jid", "$whatsappNumber@s.whatsapp.net")
            }
            // 5. Add this flag to grant WhatsApp permission to read the image file
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(shareIntent)

        } catch (e: ActivityNotFoundException) {
            // This fallback will be used if WhatsApp is not installed.
            Toast.makeText(this, "WhatsApp is not installed. Opening general share dialog.", Toast.LENGTH_LONG).show()
            try {
                val genericShareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, receiptUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(genericShareIntent, "Share receipt via"))
            } catch (ex: Exception) {
                Toast.makeText(this, "Could not open any sharing application.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not share receipt directly.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error sharing receipt directly", e)
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