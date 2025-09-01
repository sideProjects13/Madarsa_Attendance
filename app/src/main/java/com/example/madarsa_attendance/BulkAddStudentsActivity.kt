package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class BulkAddStudentsActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "BulkAddStudentsActivity"
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
        csvFileUri = intent.data

        progressBar = findViewById(R.id.progressBarBulkAdd)
        tvStatus = findViewById(R.id.tvBulkAddStatus)

        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.bulk_add_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

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
        tvStatus.text = "Starting import for class: $currentTeacherName"

        lifecycleScope.launch {
            var isSuccess = false
            var finalMessage: String
            var successCount = 0
            var failureCount: Int
            var combinedFailureDetails = listOf<String>()

            try {
                val existingRegNos = withContext(Dispatchers.IO) {
                    currentOrganizationId?.let { fetchExistingRegistrationNumbers(it) } ?: emptySet()
                }
                val (parsedStudents, parseFailures) = withContext(Dispatchers.IO) {
                    parseCsvFile(csvFileUri!!, existingRegNos)
                }

                combinedFailureDetails = parseFailures
                failureCount = combinedFailureDetails.size

                if (parsedStudents.isEmpty()) {
                    isSuccess = false
                    finalMessage = "Import Failed. No valid new students found to import."
                } else {
                    tvStatus.text = "Found ${parsedStudents.size} valid students. Saving to database..."
                    val (firestoreSuccessCount, firestoreFailures) = saveStudentsToFirestore(parsedStudents)

                    successCount = firestoreSuccessCount
                    combinedFailureDetails = parseFailures + firestoreFailures
                    failureCount = combinedFailureDetails.size

                    isSuccess = successCount > 0
                    finalMessage = "Import Complete!\nSuccess: $successCount, Failed: $failureCount"
                }

            } catch (e: Exception) {
                isSuccess = false
                finalMessage = "A critical error occurred: ${e.message}"
                Log.e(TAG, "Critical error during bulk import", e)
                failureCount = -1
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE

                // Build the detailed error message for the dialog
                if (combinedFailureDetails.isNotEmpty()) {
                    val errorDetails = combinedFailureDetails.joinToString("\n")
                    finalMessage += "\n\nError Details:\n$errorDetails"
                    Log.e(TAG, "--- IMPORT FAILURES ---\n$errorDetails")
                }

                tvStatus.text = "Import Finished. See dialog for results."
                val resultIntent = Intent().apply {
                    putExtra("SUCCESS_COUNT", successCount)
                }
                setResult(Activity.RESULT_OK, resultIntent)

                StatusDialogFragment.newInstance(
                    isSuccess = isSuccess,
                    message = finalMessage,
                    finishActivityOnDismiss = true
                ).show(supportFragmentManager, "statusDialog")
            }
        }
    }

    private suspend fun fetchExistingRegistrationNumbers(orgId: String): Set<String> {
        return try {
            val snapshot = db.collection("organizations").document(orgId).collection("students").get().await()
            snapshot.documents.mapNotNull { it.getString("regNo")?.trim() }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch registration numbers", e)
            emptySet()
        }
    }

    // Helper to handle inconsistent CSV parsing for lines with commas inside quotes
    private fun parseCsvLine(line: String): List<String> {
        // This is a simple parser. For very complex CSVs, a more robust library might be needed.
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            .map { it.trim().removeSurrounding("\"") }
    }

    private fun parseCsvFile(uri: Uri, existingRegNos: Set<String>): Pair<List<StudentDetailsItem>, List<String>> {
        val students = mutableListOf<StudentDetailsItem>()
        val failures = mutableListOf<String>()
        val seenRegNosInThisBatch = mutableSetOf<String>()
        var lineNumber = 1 // Start at 1 for the header line

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Skip header line
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        lineNumber++
                        if (line.isNullOrBlank()) continue

                        val columns = parseCsvLine(line!!)

                        // --- START: DETAILED VALIDATION ---
                        val studentName = columns.getOrNull(0)?.trim()
                        val parentName = columns.getOrNull(1)?.trim()
                        val parentMobile = columns.getOrNull(2)?.trim()
                        val regNo = columns.getOrNull(3)?.trim()

                        val validationErrors = mutableListOf<String>()

                        if (studentName.isNullOrBlank()) validationErrors.add("Student Name is missing")
                        if (parentName.isNullOrBlank()) validationErrors.add("Parent Name is missing")
                        if (parentMobile.isNullOrBlank()) validationErrors.add("Parent Mobile Number is missing")

                        if (regNo.isNullOrBlank()) {
                            validationErrors.add("Registration Number is missing")
                        } else {
                            if (existingRegNos.contains(regNo)) {
                                validationErrors.add("Reg No '$regNo' already exists in database")
                            }
                            if (seenRegNosInThisBatch.contains(regNo)) {
                                validationErrors.add("Duplicate Reg No '$regNo' in this file")
                            }
                        }

                        if (validationErrors.isNotEmpty()) {
                            val errorPrefix = "Line $lineNumber (Name: '${studentName ?: "N/A"}', Reg: '${regNo ?: "N/A"}')"
                            failures.add("$errorPrefix: ${validationErrors.joinToString(", ")}.")
                            continue // Skip this record
                        }
                        // --- END: DETAILED VALIDATION ---

                        seenRegNosInThisBatch.add(regNo!!) // Add valid Reg No to check for duplicates within the file

                        students.add(
                            StudentDetailsItem(
                                studentName = studentName!!,
                                parentName = parentName!!,
                                parentMobileNumber = parentMobile!!,
                                regNo = regNo,
                                gender = columns.getOrNull(4)?.trim() ?: "",
                                birthDate = columns.getOrNull(5)?.trim() ?: "",
                                admissionDate = columns.getOrNull(6)?.trim() ?: "",
                                monthlyFee = columns.getOrNull(7)?.trim()?.toDoubleOrNull() ?: 0.0,
                                alternateMobileNumber = columns.getOrNull(8)?.trim()?.ifEmpty { null },
                                address = columns.getOrNull(9)?.trim()?.ifEmpty { null },
                                profileImageUrl = columns.getOrNull(10)?.trim()?.ifEmpty { null },
                                teacherId = currentTeacherId!!,
                                teacherName = currentTeacherName!!,
                                isActive = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            failures.add("Critical File Read Error: ${e.message}")
            Log.e(TAG, "Error reading or parsing CSV file", e)
        }
        return Pair(students, failures)
    }

    private suspend fun saveStudentsToFirestore(students: List<StudentDetailsItem>): Pair<Int, List<String>> {
        var successCount = 0
        val failureDetails = mutableListOf<String>()
        if (currentOrganizationId == null) return Pair(0, listOf("Critical Error: Organization ID missing."))

        val studentsCollection = db.collection("organizations").document(currentOrganizationId!!)
            .collection("students")

        students.chunked(490).forEach { chunk ->
            val batch = db.batch()
            for (student in chunk) {
                val newDocRef = studentsCollection.document()
                val studentDataMap = mapOf(
                    "studentName" to student.studentName,
                    "parentName" to student.parentName,
                    "parentMobileNumber" to student.parentMobileNumber,
                    "regNo" to student.regNo,
                    "gender" to student.gender,
                    "birthDate" to student.birthDate,
                    "admissionDate" to student.admissionDate,
                    "monthlyFee" to student.monthlyFee,
                    "alternateMobileNumber" to student.alternateMobileNumber,
                    "address" to student.address,
                    "profileImageUrl" to (student.profileImageUrl ?: ""),
                    "teacherId" to student.teacherId,
                    "teacherName" to student.teacherName,
                    "isActive" to student.isActive,
                    "createdAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                batch.set(newDocRef, studentDataMap)
            }

            try {
                batch.commit().await()
                successCount += chunk.size
            } catch (e: Exception) {
                // If a whole batch fails, create a detailed error for it
                val failedStudentInfo = chunk.joinToString(", ") { "'${it.studentName}' ('${it.regNo}')" }
                failureDetails.add("Database Error: A batch of ${chunk.size} students failed to save. Students: $failedStudentInfo. Reason: ${e.message}")
                Log.e(TAG, "Firestore batch commit failed", e)
            }
        }
        return Pair(successCount, failureDetails)
    }
}