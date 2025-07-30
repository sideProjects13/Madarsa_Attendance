package com.example.madarsa_attendance

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.DocumentSnapshot // Keep this import for explicit typing
// import com.google.firebase.firestore.FieldPath // REMOVED: No longer needed with this fallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BulkAddStudentsActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "BulkAddStudentsActivity"
        private const val CSV_HEADERS = "Student Name,Parent Name,Parent Mobile Number,Registration Number,Gender,Birth Date (YYYY-MM-DD),Admission Date (YYYY-MM-DD),Monthly Fee (e.g., 500.00),Profile Image URL (Optional)"
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var db: FirebaseFirestore

    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentOrganizationId: String? = null
    private var csvFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bulk_add_students)

        db = FirebaseFirestore.getInstance()

        currentTeacherId = intent.getStringExtra("TEACHER_ID")
        currentTeacherName = intent.getStringExtra("TEACHER_NAME")
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)
        csvFileUri = intent.getStringExtra("CSV_FILE_URI")?.let { Uri.parse(it) }

        progressBar = findViewById(R.id.progressBarBulkAdd)
        tvStatus = findViewById(R.id.tvBulkAddStatus)

        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.bulk_add_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        toolbar.title = "Bulk Add Students"


        if (currentTeacherId == null || currentOrganizationId == null || csvFileUri == null) {
            Toast.makeText(this, "Missing data for bulk import.", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        startBulkImport()
    }

    private fun startBulkImport() {
        progressBar.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Starting import..."

        lifecycleScope.launch {
            try {
                // Fetch existing registration numbers first
                val existingRegNos = withContext(Dispatchers.IO) {
                    currentOrganizationId?.let { fetchExistingRegistrationNumbers(it) } ?: emptySet()
                }
                Log.d(TAG, "Fetched ${existingRegNos.size} existing registration numbers.")

                val (parsedStudents, parseFailures) = withContext(Dispatchers.IO) {
                    parseCsvFile(csvFileUri!!, existingRegNos)
                }

                if (parseFailures.isNotEmpty()) {
                    Log.w(TAG, "CSV parsing warnings/failures:")
                    parseFailures.forEach { Log.w(TAG, "  - ${it.first}: ${it.second}") }
                    tvStatus.text = "Parsed with ${parseFailures.size} warnings/failures. Saving valid entries..."
                }

                if (parsedStudents.isEmpty()) {
                    val finalMessage = if (parseFailures.isNotEmpty()) "All students failed parsing or were duplicates. Check logs." else "No valid student data found in the CSV file."
                    tvStatus.text = finalMessage
                    Toast.makeText(this@BulkAddStudentsActivity, finalMessage, Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_CANCELED)
                    progressBar.visibility = View.GONE
                    return@launch
                }

                tvStatus.text = "Found ${parsedStudents.size} unique students. Saving to Firestore..."
                Log.d(TAG, "Parsed ${parsedStudents.size} unique students. Starting Firestore batch write.")

                val (successCount, firestoreFailureDetails) = saveStudentsToFirestore(parsedStudents)

                val combinedFailureDetails = parseFailures + firestoreFailureDetails

                val message = "Import complete! Successfully added $successCount students." +
                        if (combinedFailureDetails.isNotEmpty()) "\nFailed: ${combinedFailureDetails.size}. See logs for details." else ""
                tvStatus.text = message
                Toast.makeText(this@BulkAddStudentsActivity, message, Toast.LENGTH_LONG).show()

                if (combinedFailureDetails.isNotEmpty()) {
                    Log.e(TAG, "Bulk import combined failures:")
                    combinedFailureDetails.forEach { Log.e(TAG, "  - ${it.first}: ${it.second}") }
                }

                val resultIntent = intent.apply {
                    putExtra("SUCCESS_COUNT", successCount)
                    putExtra("FAILURE_COUNT", combinedFailureDetails.size)
                }
                setResult(Activity.RESULT_OK, resultIntent)

            } catch (e: Exception) {
                Log.e(TAG, "Bulk import failed: ${e.message}", e)
                tvStatus.text = "Import failed: ${e.message}"
                Toast.makeText(this@BulkAddStudentsActivity, "Bulk import failed: ${e.message}", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_CANCELED)
            } finally {
                progressBar.visibility = View.GONE
                withContext(Dispatchers.Main) {
                    tvStatus.postDelayed({ finish() }, 2000)
                }
            }
        }
    }

    // NEW: Fetch existing registration numbers - **USING FALLBACK WITHOUT .select()**
    private suspend fun fetchExistingRegistrationNumbers(orgId: String): Set<String> {
        return try {
            val snapshot = db.collection("organizations").document(orgId)
                .collection("students")
                .get().await() // This will now fetch full documents

            // Explicitly type the lambda parameter to DocumentSnapshot for clarity and safety
            snapshot.documents.mapNotNull { documentSnapshot: DocumentSnapshot ->
                documentSnapshot.getString("regNo")
            }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching existing registration numbers (fallback method): ${e.message}", e)
            emptySet()
        }
    }


    private fun parseCsvFile(uri: Uri, existingRegNos: Set<String>): Pair<List<StudentDetailsItem>, List<Pair<String, String>>> {
        val students = mutableListOf<StudentDetailsItem>()
        val failureDetails = mutableListOf<Pair<String, String>>()
        val seenRegNosInThisBatch = mutableSetOf<String>()

        var lineNumber = 0
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var headerLine = reader.readLine()
                    lineNumber++

                    val expectedHeaderColumns = CSV_HEADERS.split(",").map { it.trim().lowercase(Locale.getDefault()) }.toSet()
                    val actualHeaderColumns = headerLine?.split(",")?.map { it.trim().lowercase(Locale.getDefault()) }?.toSet() ?: emptySet()

                    if (headerLine == null || !actualHeaderColumns.containsAll(expectedHeaderColumns.filter { !it.contains("optional") })) {
                        failureDetails.add(Pair("Headers", "CSV header might be missing or incorrect. Expected: $CSV_HEADERS"))
                        Log.w(TAG, "CSV header might be missing or incorrect. Expected: $CSV_HEADERS. Found: $headerLine")
                    }


                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        lineNumber++
                        if (line.isNullOrBlank()) continue

                        val columns = line!!.split(",").map { it.trim() }
                        if (columns.size < 8) {
                            val msg = "Too few columns (${columns.size} vs 8 minimum)."
                            failureDetails.add(Pair("Line $lineNumber", msg))
                            Log.w(TAG, "Skipping line $lineNumber: $msg Line: $line")
                            continue
                        }

                        try {
                            val studentName = columns.getOrNull(0)?.ifEmpty { null }
                            val parentName = columns.getOrNull(1)?.ifEmpty { null }
                            val parentMobileNumber = columns.getOrNull(2)?.ifEmpty { null }
                            val regNo = columns.getOrNull(3)?.ifEmpty { null }
                            val gender = columns.getOrNull(4)?.ifEmpty { null }
                            val birthDate = columns.getOrNull(5)?.ifEmpty { null }
                            val admissionDate = columns.getOrNull(6)?.ifEmpty { null }
                            val monthlyFee = columns.getOrNull(7)?.toDoubleOrNull()
                            val profileImageUrl = columns.getOrNull(8)?.ifEmpty { null }

                            if (studentName == null || parentName == null || parentMobileNumber == null || regNo == null || gender == null) {
                                val msg = "Required fields (Student Name, Parent Name, Parent Mobile, Reg No, Gender) are missing."
                                failureDetails.add(Pair("Line $lineNumber", msg))
                                Log.w(TAG, "Skipping line $lineNumber: $msg Line: $line")
                                continue
                            }
                            if (!isValidIndianMobileNumber(parentMobileNumber)) {
                                val msg = "Invalid mobile number format '$parentMobileNumber'."
                                failureDetails.add(Pair("Line $lineNumber", msg))
                                Log.w(TAG, "Skipping line $lineNumber: $msg Line: $line")
                                continue
                            }
                            if (monthlyFee == null || monthlyFee <= 0) {
                                val msg = "Invalid or missing monthly fee '$monthlyFee'."
                                failureDetails.add(Pair("Line $lineNumber", msg))
                                Log.w(TAG, "Skipping line $lineNumber: $msg Line: $line")
                                continue
                            }
                            if (birthDate != null && !isValidDate(birthDate)) {
                                val msg = "Invalid Birth Date format '$birthDate'. Expected YYYY-MM-DD."
                                failureDetails.add(Pair("Line $lineNumber", msg))
                                Log.w(TAG, "Skipping line $lineNumber: $msg Line: $line")
                                continue
                            }
                            if (admissionDate != null && !isValidDate(admissionDate)) {
                                val msg = "Invalid Admission Date format '$admissionDate'. Expected YYYY-MM-DD."
                                failureDetails.add(Pair("Line $lineNumber", msg))
                                Log.w(TAG, "Skipping line $lineNumber: $msg Line: $line")
                                continue
                            }

                            if (existingRegNos.contains(regNo) || seenRegNosInThisBatch.contains(regNo)) {
                                val msg = "Duplicate Registration Number '$regNo'."
                                failureDetails.add(Pair("Line $lineNumber", msg))
                                Log.w(TAG, "Skipping line $lineNumber: $msg Line: $line")
                                continue
                            }
                            seenRegNosInThisBatch.add(regNo)

                            students.add(
                                StudentDetailsItem(
                                    studentName = studentName,
                                    parentName = parentName,
                                    parentMobileNumber = parentMobileNumber,
                                    regNo = regNo,
                                    gender = gender,
                                    birthDate = birthDate,
                                    admissionDate = admissionDate,
                                    monthlyFee = monthlyFee,
                                    profileImageUrl = profileImageUrl,
                                    teacherId = currentTeacherId!!,
                                    teacherName = currentTeacherName!!,
                                    isActive = true
                                )
                            )
                        } catch (e: Exception) {
                            val msg = "Parsing error: ${e.message}"
                            failureDetails.add(Pair("Line $lineNumber", msg))
                            Log.e(TAG, "Error parsing line $lineNumber: $line - $msg", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Error reading CSV file: ${e.message}", e)
        }
        return Pair(students, failureDetails)
    }

    private fun isValidDate(dateString: String): Boolean {
        return try {
            DATE_FORMATTER.parse(dateString)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidIndianMobileNumber(mobile: String) = mobile.length == 10 && mobile.all { it.isDigit() }


    private suspend fun saveStudentsToFirestore(students: List<StudentDetailsItem>): Pair<Int, List<Pair<String, String>>> {
        var successCount = 0
        val failureDetails = mutableListOf<Pair<String, String>>()

        if (currentOrganizationId == null) {
            return Pair(0, listOf(Pair("All Students", "Organization ID missing.")))
        }

        val studentsCollection = db.collection("organizations").document(currentOrganizationId!!)
            .collection("students")

        val chunks = students.chunked(490)

        for ((chunkIndex, chunk) in chunks.withIndex()) {
            val batch: WriteBatch = db.batch()
            Log.d(TAG, "Processing batch ${chunkIndex + 1}/${chunks.size} with ${chunk.size} students.")

            for (student in chunk) {
                try {
                    val newDocRef = studentsCollection.document()
                    val studentData = hashMapOf(
                        "studentName" to student.studentName,
                        "parentName" to student.parentName,
                        "parentMobileNumber" to student.parentMobileNumber,
                        "regNo" to student.regNo,
                        "gender" to student.gender,
                        "birthDate" to student.birthDate,
                        "admissionDate" to student.admissionDate,
                        "monthlyFee" to student.monthlyFee,
                        "profileImageUrl" to (student.profileImageUrl ?: ""),
                        "teacherId" to student.teacherId,
                        "teacherName" to student.teacherName,
                        "isActive" to student.isActive,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(newDocRef, studentData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error preparing student ${student.studentName} for batch: ${e.message}", e)
                    failureDetails.add(Pair(student.studentName, "Batch preparation failed: ${e.message}"))
                }
            }

            try {
                withContext(Dispatchers.IO) {
                    batch.commit().await()
                }
                successCount += chunk.size
                Log.d(TAG, "Batch ${chunkIndex + 1} committed successfully. ${chunk.size} students added.")
            } catch (e: Exception) {
                Log.e(TAG, "Firestore batch ${chunkIndex + 1} commit failed: ${e.message}", e)
                val failedStudentNames = chunk.map { it.studentName }
                val batchErrorMsg = "Batch commit error: ${e.message}"
                for (name in failedStudentNames) {
                    failureDetails.add(Pair(name, batchErrorMsg))
                }
            }
        }
        return Pair(successCount, failureDetails)
    }
}