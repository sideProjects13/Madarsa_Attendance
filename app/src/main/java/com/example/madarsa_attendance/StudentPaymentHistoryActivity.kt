package com.example.madarsa_attendance

import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// FeePaymentItem is assumed to be correctly defined in DataModels.kt.
// I'll keep the placeholder here for the example, but it should be moved.
data class FeePaymentItem(
    val id: String = "",
    val studentId: String = "",
    val teacherId: String = "",
    val studentName: String? = null,
    val teacherName: String? = null,
    val paymentAmount: Double = 0.0,
    val paymentDate: String = "",
    val paymentMonth: String = "",
    val paymentYear: Int = 0,
    val paymentMode: String? = null,
    val notes: String? = null,
    val recordedAt: Timestamp? = null
)

class StudentPaymentHistoryActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "StudentPaymentHistory"
        private const val MIN_FEE_AMOUNT = 100.0
    }


    private lateinit var tvStudentNameHeader: TextView
    private lateinit var btnRecordNewPayment: Button
    private lateinit var recyclerViewPaymentHistory: RecyclerView
    private lateinit var paymentHistoryAdapter: PaymentHistoryAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoHistory: TextView

    private lateinit var db: FirebaseFirestore
    private var currentStudentId: String? = null
    private var currentStudentName: String? = null
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentOrganizationId: String? = null // NEW: Organization ID

    private var studentParentMobile: String? = null
    private var studentMonthlyFee: Double? = null

    private val paymentHistoryList = mutableListOf<FeePaymentItem>()
    private var selectedPaymentDateCalendar: Calendar = Calendar.getInstance()
    private val dialogDateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

    private var paymentRecordedInThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_payment_history)

        db = FirebaseFirestore.getInstance()
        currentStudentId = intent.getStringExtra("STUDENT_ID")
        currentStudentName = intent.getStringExtra("STUDENT_NAME")
        currentTeacherId = intent.getStringExtra("TEACHER_ID")
        currentTeacherName = intent.getStringExtra("TEACHER_NAME")
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this) // NEW: Get organization ID
        tvStudentNameHeader = findViewById(R.id.tvStudentNameForPayments)
        btnRecordNewPayment = findViewById(R.id.btnRecordNewPayment)
        recyclerViewPaymentHistory = findViewById(R.id.recyclerViewPaymentHistory)
        progressBar = findViewById(R.id.progressBarPaymentHistory)
        tvNoHistory = findViewById(R.id.tvNoPaymentHistory)

        tvStudentNameHeader.text = "Student: ${currentStudentName ?: "N/A"}"

        if (currentStudentId == null || currentTeacherId == null) {
            Toast.makeText(this, "Student or Class information missing.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        if (currentOrganizationId == null) { // NEW: Check organization ID
            Toast.makeText(this, "Organization information missing. Please log in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }


        fetchStudentDetailsAndFee()
        setupRecyclerView()
        loadPaymentHistory()

        btnRecordNewPayment.setOnClickListener {
            showRecordPaymentDialog()
        }
    }

    private fun fetchStudentDetailsAndFee() {
        if (currentStudentId == null || currentOrganizationId == null) return // NEW: Check organization ID
        // NEW: Scope query to the organization
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").document(currentStudentId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    studentParentMobile = document.getString("parentMobileNumber")
                    studentMonthlyFee = document.getDouble("monthlyFee")
                    Log.d(TAG, "Fetched student monthly fee: $studentMonthlyFee, parent mobile: $studentParentMobile for Org ID: $currentOrganizationId")
                } else {
                    Log.w(TAG, "Student document not found for ID: $currentStudentId in Org ID: $currentOrganizationId")
                    studentParentMobile = null
                    studentMonthlyFee = null
                    Toast.makeText(this, "Student data not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching student details for Org ID: $currentOrganizationId: ${e.message}", e)
                studentParentMobile = null
                studentMonthlyFee = null
                Toast.makeText(this, "Failed to load student data.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRecyclerView() {
        paymentHistoryAdapter = PaymentHistoryAdapter(paymentHistoryList)
        recyclerViewPaymentHistory.layoutManager = LinearLayoutManager(this)
        recyclerViewPaymentHistory.adapter = paymentHistoryAdapter
    }

    private fun loadPaymentHistory() {
        if (currentStudentId == null || currentOrganizationId == null) return // NEW: Check organization ID
        Log.d(TAG, "Loading payment history for student ID: $currentStudentId in Org ID: $currentOrganizationId")
        progressBar.visibility = View.VISIBLE
        tvNoHistory.visibility = View.GONE
        recyclerViewPaymentHistory.visibility = View.GONE

        // NEW: Scope query to the organization
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("feePayments")
            .whereEqualTo("studentId", currentStudentId)
            .orderBy("paymentDate", Query.Direction.DESCENDING)
            .orderBy("recordedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                progressBar.visibility = View.GONE
                paymentHistoryList.clear()
                if (!querySnapshot.isEmpty) {
                    for (doc in querySnapshot.documents) {
                        paymentHistoryList.add(
                            FeePaymentItem(
                                id = doc.id,
                                studentId = doc.getString("studentId") ?: "",
                                teacherId = doc.getString("teacherId") ?: "",
                                studentName = doc.getString("studentName"),
                                teacherName = doc.getString("teacherName"),
                                paymentAmount = doc.getDouble("paymentAmount") ?: 0.0,
                                paymentDate = doc.getString("paymentDate") ?: "N/A",
                                paymentMonth = doc.getString("paymentMonth") ?: "",
                                paymentYear = (doc.getLong("paymentYear") ?: 0).toInt(),
                                paymentMode = doc.getString("paymentMode"),
                                notes = doc.getString("notes"),
                                recordedAt = doc.getTimestamp("recordedAt")
                            )
                        )
                    }
                    paymentHistoryAdapter.updateData(paymentHistoryList)
                    recyclerViewPaymentHistory.visibility = View.VISIBLE
                } else {
                    tvNoHistory.visibility = View.VISIBLE
                    recyclerViewPaymentHistory.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                tvNoHistory.text = "Error loading history."
                tvNoHistory.visibility = View.VISIBLE
                Log.e(TAG, "Error fetching payment history for Org ID: $currentOrganizationId: ", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRecordPaymentDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_record_payment, null)
        val tilAmount = dialogView.findViewById<TextInputLayout>(R.id.tilPaymentAmount)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etPaymentAmountDialog)
        val btnSelectDate = dialogView.findViewById<Button>(R.id.btnSelectPaymentDateDialog)
        val etMode = dialogView.findViewById<TextInputEditText>(R.id.etPaymentModeDialog)
        val etNotes = dialogView.findViewById<TextInputEditText>(R.id.etPaymentNotesDialog)

        selectedPaymentDateCalendar = Calendar.getInstance()
        btnSelectDate.text = "Date: ${dialogDateFormat.format(selectedPaymentDateCalendar.time)}"

        studentMonthlyFee?.let { fee ->
            val formattedFee = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                minimumFractionDigits = 0
                maximumFractionDigits = 2
            }.format(fee)
            etAmount.setText(formattedFee)
            etAmount.isEnabled = false
            tilAmount.isEnabled = false
        } ?: run {
            etAmount.hint = "Amount (No default fee set)"
            etAmount.isEnabled = true
            tilAmount.isEnabled = true
            Toast.makeText(this, "Monthly fee not set for this student. Please enter manually.", Toast.LENGTH_LONG).show()
        }

        btnSelectDate.setOnClickListener {
            val cal = selectedPaymentDateCalendar
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedPaymentDateCalendar.set(year, month, dayOfMonth)
                    btnSelectDate.text = "Date: ${dialogDateFormat.format(selectedPaymentDateCalendar.time)}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Record Payment for ${currentStudentName ?: "Student"}")
            .setView(dialogView)
            .setPositiveButton("Save Payment", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val amountStr = etAmount.text.toString().trim()
                val mode = etMode.text.toString().trim()
                val notes = etNotes.text.toString().trim()

                val amount: Double? = if (studentMonthlyFee != null) {
                    studentMonthlyFee
                } else {
                    if (amountStr.isEmpty()) { etAmount.error = "Amount is required"; return@setOnClickListener }
                    val parsedAmount = amountStr.toDoubleOrNull()
                    if (parsedAmount == null || parsedAmount < MIN_FEE_AMOUNT) {
                        etAmount.error = "Minimum amount is ₹${String.format("%.0f", MIN_FEE_AMOUNT)}"; return@setOnClickListener
                    }
                    parsedAmount
                }

                if (amount == null) {
                    Toast.makeText(this, "Invalid amount.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val paymentDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedPaymentDateCalendar.time)
                val paymentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(selectedPaymentDateCalendar.time)
                val paymentYearInt = selectedPaymentDateCalendar.get(Calendar.YEAR)

                recordPaymentInFirestore(amount, paymentDateStr, paymentMonthStr, paymentYearInt, mode, notes)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun recordPaymentInFirestore(
        amount: Double, paymentDate: String, paymentMonth: String, paymentYear: Int,
        mode: String?, notes: String?
    ) {
        if (currentStudentId == null || currentTeacherId == null || currentOrganizationId == null) { // NEW: Check organization ID
            Toast.makeText(this, "Cannot record payment: Missing IDs.", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Recording payment: Amount: $amount, Date: $paymentDate for studentId: $currentStudentId in Org ID: $currentOrganizationId")
        progressBar.visibility = View.VISIBLE

        val paymentData = hashMapOf(
            "studentId" to currentStudentId!!,
            "studentName" to (currentStudentName ?: "N/A"),
            "teacherId" to currentTeacherId!!,
            "teacherName" to (currentTeacherName ?: "N/A"),
            "paymentAmount" to amount,
            "paymentDate" to paymentDate,
            "paymentMonth" to paymentMonth,
            "paymentYear" to paymentYear,
            "paymentMode" to (mode ?: ""),
            "notes" to (notes ?: ""),
            "recordedAt" to FieldValue.serverTimestamp()
        )

        // NEW: Scope addition to the organization
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("feePayments").add(paymentData)
            .addOnSuccessListener {
                Log.d(TAG, "Payment recorded successfully with ID: ${it.id} in Org ID: $currentOrganizationId")
                Toast.makeText(this, "Payment of ₹${String.format("%.0f", amount)} recorded!", Toast.LENGTH_SHORT).show()

                paymentRecordedInThisSession = true
                setResult(Activity.RESULT_OK)

                loadPaymentHistory()
                sendWhatsAppMessageToParent(amount, paymentDate)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error recording payment in Org ID: $currentOrganizationId: ", e)
                Toast.makeText(this, "Failed to record payment: ${e.message}", Toast.LENGTH_LONG).show()
            }
            .addOnCompleteListener { progressBar.visibility = View.GONE }
    }

    private fun sendWhatsAppMessageToParent(amountPaid: Double, paymentDateString: String) {
        if (studentParentMobile.isNullOrEmpty()) {
            Log.w(TAG, "Parent mobile not available for student $currentStudentName. Cannot open WhatsApp.")
            return
        }
        var cleanMobileNumber = studentParentMobile!!.replace(Regex("[^0-9+]"), "")
        val whatsAppNumberForUrl: String

        if (cleanMobileNumber.startsWith("+")) {
            if (cleanMobileNumber.startsWith("+91") && cleanMobileNumber.length == 13) {
                whatsAppNumberForUrl = cleanMobileNumber.substring(1)
            } else {
                whatsAppNumberForUrl = cleanMobileNumber.substring(1)
            }
        } else if (cleanMobileNumber.length == 10) {
            whatsAppNumberForUrl = "91$cleanMobileNumber"
        } else if (cleanMobileNumber.length == 12 && cleanMobileNumber.startsWith("91")) {
            whatsAppNumberForUrl = cleanMobileNumber
        }
        else {
            Log.e(TAG, "Invalid parent mobile number format: '$cleanMobileNumber'. Cannot reliably format for WhatsApp.")
            Toast.makeText(this, "Parent mobile number format seems incorrect for WhatsApp.", Toast.LENGTH_LONG).show()
            return
        }

        val studentFirstName = currentStudentName?.split(" ")?.firstOrNull() ?: "your child"
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        val formattedPaymentDateForMessage = try {
            val date = inputFormat.parse(paymentDateString)
            if (date != null) outputFormat.format(date) else paymentDateString
        } catch (e: Exception) {
            paymentDateString
        }

        val formattedAmount = if (amountPaid % 1 == 0.0) String.format("%.0f", amountPaid) else String.format("%.2f", amountPaid)
        val message = "Dear Parent, a fee payment of Rs. $formattedAmount for $studentFirstName on $formattedPaymentDateForMessage has been received by the Madarsa. Thank you."
        try {
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val uri = Uri.parse("https://wa.me/$whatsAppNumberForUrl?text=$encodedMessage")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            Log.d(TAG, "Attempting to launch WhatsApp with URI: $uri")
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "WhatsApp not installed or no app can handle this action.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "No activity found to handle WhatsApp intent for $whatsAppNumberForUrl", e)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error launching WhatsApp intent: ", e)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (paymentRecordedInThisSession) {
            setResult(Activity.RESULT_OK)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        super.onBackPressed()
    }
}