package com.example.madarsa_attendance // <<< YOUR PACKAGE NAME

import android.app.Activity // Import Activity for RESULT_OK
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Assumes FeePaymentItem and PaymentHistoryAdapter are defined elsewhere (e.g., DataModels.kt, PaymentHistoryAdapter.kt)
// data class FeePaymentItem(
//    val id: String,
//    val paymentAmount: Double,
//    val paymentDate: String,
//    val paymentMode: String?,
//    val notes: String?,
//    val recordedAt: com.google.firebase.Timestamp?
// )
// class PaymentHistoryAdapter(private var items: List<FeePaymentItem>) : RecyclerView.Adapter<PaymentHistoryAdapter.ViewHolder>() { /* ... */ }


class StudentPaymentHistoryActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "StudentPaymentHistory"
        private const val MIN_FEE_AMOUNT = 100.0
    }

    private lateinit var toolbar: MaterialToolbar
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
    private var studentParentMobile: String? = null

    private val paymentHistoryList = mutableListOf<FeePaymentItem>()
    private var selectedPaymentDateCalendar: Calendar = Calendar.getInstance()
    private val dialogDateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

    private var paymentRecordedInThisSession = false // Flag to track if a payment was made

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_payment_history)

        db = FirebaseFirestore.getInstance()
        currentStudentId = intent.getStringExtra("STUDENT_ID")
        currentStudentName = intent.getStringExtra("STUDENT_NAME")
        currentTeacherId = intent.getStringExtra("TEACHER_ID")

        toolbar = findViewById(R.id.student_payment_history_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.title = "Payments: ${currentStudentName ?: "Student"}"
        toolbar.setNavigationOnClickListener {
            // onBackPressedDispatcher.onBackPressed() will call the overridden onBackPressed
            onBackPressedDispatcher.onBackPressed()
        }

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

        fetchStudentParentMobile()
        setupRecyclerView()
        loadPaymentHistory()

        btnRecordNewPayment.setOnClickListener {
            showRecordPaymentDialog()
        }
    }

    private fun fetchStudentParentMobile() {
        if (currentStudentId == null) return
        db.collection("students").document(currentStudentId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    studentParentMobile = document.getString("parentMobileNumber")
                } else { studentParentMobile = null }
            }
            .addOnFailureListener { studentParentMobile = null }
    }

    private fun setupRecyclerView() {
        // Assuming PaymentHistoryAdapter is correctly implemented
        paymentHistoryAdapter = PaymentHistoryAdapter(paymentHistoryList)
        recyclerViewPaymentHistory.layoutManager = LinearLayoutManager(this)
        recyclerViewPaymentHistory.adapter = paymentHistoryAdapter
    }

    private fun loadPaymentHistory() {
        if (currentStudentId == null) return
        Log.d(TAG, "Loading payment history for student ID: $currentStudentId")
        progressBar.visibility = View.VISIBLE
        tvNoHistory.visibility = View.GONE
        recyclerViewPaymentHistory.visibility = View.GONE

        db.collection("feePayments")
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
                            FeePaymentItem( // Assuming FeePaymentItem is your data class
                                id = doc.id,
                                paymentAmount = doc.getDouble("paymentAmount") ?: 0.0,
                                paymentDate = doc.getString("paymentDate") ?: "N/A",
                                paymentMode = doc.getString("paymentMode"),
                                notes = doc.getString("notes"),
                                recordedAt = doc.getTimestamp("recordedAt")
                            )
                        )
                    }
                    paymentHistoryAdapter.updateData(paymentHistoryList) // Make sure adapter has updateData
                    recyclerViewPaymentHistory.visibility = View.VISIBLE
                } else {
                    tvNoHistory.visibility = View.VISIBLE
                    recyclerViewPaymentHistory.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE; tvNoHistory.text = "Error loading history."
                tvNoHistory.visibility = View.VISIBLE; Log.e(TAG, "Error fetching payment history: ", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRecordPaymentDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_record_payment, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etPaymentAmountDialog)
        val btnSelectDate = dialogView.findViewById<Button>(R.id.btnSelectPaymentDateDialog)
        val etMode = dialogView.findViewById<EditText>(R.id.etPaymentModeDialog)
        val etNotes = dialogView.findViewById<EditText>(R.id.etPaymentNotesDialog)

        selectedPaymentDateCalendar = Calendar.getInstance() // Reset to current date for new dialog
        btnSelectDate.text = "Date: ${dialogDateFormat.format(selectedPaymentDateCalendar.time)}"

        btnSelectDate.setOnClickListener {
            val cal = selectedPaymentDateCalendar // Use the member variable
            DatePickerDialog(
                this,
                R.style.DatePickerDialog_App_Monochrome, // Apply monochrome theme
                { _, year, month, dayOfMonth ->
                    selectedPaymentDateCalendar.set(year, month, dayOfMonth)
                    btnSelectDate.text = "Date: ${dialogDateFormat.format(selectedPaymentDateCalendar.time)}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }

        val dialog = AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Record Payment for ${currentStudentName ?: "Student"}")
            .setView(dialogView)
            .setPositiveButton("Save Payment", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val amountStr = etAmount.text.toString()
                val mode = etMode.text.toString().trim()
                val notes = etNotes.text.toString().trim()

                if (amountStr.isEmpty()) { etAmount.error = "Amount is required"; return@setOnClickListener }
                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount < MIN_FEE_AMOUNT) { etAmount.error = "Minimum amount is ₹${String.format("%.0f",MIN_FEE_AMOUNT)}"; return@setOnClickListener }

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
        Log.d(TAG, "Recording payment: Amount: $amount, Date: $paymentDate for studentId: $currentStudentId")
        progressBar.visibility = View.VISIBLE

        val paymentData = hashMapOf(
            "studentId" to currentStudentId!!,
            "studentName" to (currentStudentName ?: "N/A"),
            "teacherId" to currentTeacherId!!,
            "teacherName" to (intent.getStringExtra("TEACHER_NAME") ?: "N/A"), // Assuming teacher name is passed or available
            "paymentAmount" to amount,
            "paymentDate" to paymentDate,
            "paymentMonth" to paymentMonth,
            "paymentYear" to paymentYear,
            "paymentMode" to (mode ?: ""),
            "notes" to (notes ?: ""),
            "recordedAt" to FieldValue.serverTimestamp()
        )

        db.collection("feePayments").add(paymentData)
            .addOnSuccessListener {
                Log.d(TAG, "Payment recorded successfully with ID: ${it.id}")
                Toast.makeText(this, "Payment of ₹${String.format("%.0f", amount)} recorded!", Toast.LENGTH_SHORT).show()

                paymentRecordedInThisSession = true // Set the flag
                setResult(Activity.RESULT_OK)     // Set the result for the calling fragment/activity

                loadPaymentHistory() // Reload history in this activity to show the new payment
                sendWhatsAppMessageToParent(amount, paymentDate)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error recording payment: ", e)
                Toast.makeText(this, "Failed to record payment: ${e.message}", Toast.LENGTH_LONG).show()
            }
            .addOnCompleteListener { progressBar.visibility = View.GONE }
    }

    private fun sendWhatsAppMessageToParent(amountPaid: Double, paymentDateString: String) {
        if (studentParentMobile.isNullOrEmpty()) {
            Log.w(TAG, "Parent mobile not available for student $currentStudentName. Cannot open WhatsApp.")
            // Toast.makeText(this, "Cannot open WhatsApp: Parent mobile not found.", Toast.LENGTH_LONG).show() // Maybe too intrusive if opening WhatsApp is optional
            return
        }
        var cleanMobileNumber = studentParentMobile!!.replace(Regex("[^0-9+]"), "") // Keep + for international numbers
        val whatsAppNumberForUrl: String

        if (cleanMobileNumber.startsWith("+")) {
            // Assume it's a full international number, remove + for wa.me link if it adds it automatically.
            // Or keep it if wa.me handles it. For '91' prefix, it's better to ensure it's there.
            // Test this part carefully with actual numbers.
            // For simplicity, if it starts with +91 and is 13 chars, it's likely okay.
            if (cleanMobileNumber.startsWith("+91") && cleanMobileNumber.length == 13) {
                whatsAppNumberForUrl = cleanMobileNumber.substring(1) // remove +
            } else {
                whatsAppNumberForUrl = cleanMobileNumber.substring(1) // remove + for other international numbers
            }
        } else if (cleanMobileNumber.length == 10) {
            whatsAppNumberForUrl = "91$cleanMobileNumber" // Prepend 91 for Indian numbers
        } else if (cleanMobileNumber.length == 12 && cleanMobileNumber.startsWith("91")) {
            whatsAppNumberForUrl = cleanMobileNumber // Already has 91
        }
        else {
            Log.e(TAG, "Invalid parent mobile number format: '$cleanMobileNumber'. Cannot reliably format for WhatsApp.")
            Toast.makeText(this, "Parent mobile number format seems incorrect for WhatsApp.", Toast.LENGTH_LONG).show()
            return
        }


        val studentFirstName = currentStudentName?.split(" ")?.firstOrNull() ?: "your child"
        // Format payment date from "yyyy-MM-dd" to "dd MMM, yyyy" for message
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        val formattedPaymentDateForMessage = try {
            val date = inputFormat.parse(paymentDateString)
            if (date != null) outputFormat.format(date) else paymentDateString
        } catch (e: Exception) {
            paymentDateString // fallback to original if parsing fails
        }

        val formattedAmount = if (amountPaid % 1 == 0.0) String.format("%.0f", amountPaid) else String.format("%.2f", amountPaid)
        val message = "Dear Parent, a fee payment of Rs. $formattedAmount for $studentFirstName on $formattedPaymentDateForMessage has been received by the Madarsa. Thank you."
        try {
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val uri = Uri.parse("https://wa.me/$whatsAppNumberForUrl?text=$encodedMessage")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            Log.d(TAG, "Attempting to launch WhatsApp with URI: $uri")
            startActivity(intent)
            // Toast.makeText(this, "Opening WhatsApp...", Toast.LENGTH_SHORT).show() // Optional: can be annoying
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
            setResult(Activity.RESULT_CANCELED) // Explicitly set canceled if no payment was made
        }
        super.onBackPressed()
    }
}