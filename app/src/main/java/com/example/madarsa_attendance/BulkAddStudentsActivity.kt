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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

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

            try {
                val existingRegNos = withContext(Dispatchers.IO) {
                    currentOrganizationId?.let { fetchExistingRegistrationNumbers(it) } ?: emptySet()
                }
                val (parsedStudents, parseFailures) = withContext(Dispatchers.IO) {
                    parseCsvFile(csvFileUri!!, existingRegNos)
                }

                if (parsedStudents.isEmpty()) {
                    failureCount = parseFailures.size
                    finalMessage = if (failureCount > 0) "Import Failed. All records had parsing errors." else "No valid new students found in the file."
                    isSuccess = false
                } else {
                    tvStatus.text = "Found ${parsedStudents.size} valid students. Saving to database..."
                    val (firestoreSuccessCount, firestoreFailures) = saveStudentsToFirestore(parsedStudents)
                    val combinedFailureDetails = parseFailures + firestoreFailures

                    successCount = firestoreSuccessCount
                    failureCount = combinedFailureDetails.size

                    if (successCount > 0) {
                        isSuccess = true
                        finalMessage = "Import Complete!\nSuccess: $successCount, Failed: $failureCount"
                    } else {
                        isSuccess = false
                        finalMessage = "Import Failed.\nAll ${failureCount} records had errors."
                    }

                    if (combinedFailureDetails.isNotEmpty()) {
                        Log.e(TAG, "--- IMPORT FAILURES ---")
                        combinedFailureDetails.forEach { Log.e(TAG, " - $it") }
                    }
                }
            } catch (e: Exception) {
                isSuccess = false
                finalMessage = "A critical error occurred during import."
                Log.e(TAG, "Critical error during bulk import", e)
                failureCount = -1 // Indicate a catastrophic failure
            }

            // Show the final result dialog
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                tvStatus.text = finalMessage // Update the text view with the final status

                val resultIntent = Intent().apply {
                    putExtra("SUCCESS_COUNT", successCount)
                    putExtra("FAILURE_COUNT", if (failureCount != -1) failureCount else 0)
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
            snapshot.documents.mapNotNull { it.getString("regNo") }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(char)
            }
        }
        result.add(sb.toString())
        return result
    }

    private fun parseCsvFile(uri: Uri, existingRegNos: Set<String>): Pair<List<StudentDetailsItem>, List<String>> {
        val students = mutableListOf<StudentDetailsItem>()
        val failures = mutableListOf<String>()
        val seenRegNosInThisBatch = mutableSetOf<String>()
        var lineNumber = 1

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Skip header
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        lineNumber++
                        if (line.isNullOrBlank()) continue

                        val columns = parseCsvLine(line!!).map { it.trim() }

                        if (columns.size < 8) {
                            failures.add("Line $lineNumber: Not enough columns.")
                            continue
                        }

                        val regNo = columns[3]
                        if (regNo.isBlank() || existingRegNos.contains(regNo) || seenRegNosInThisBatch.contains(regNo)) {
                            failures.add("Line $lineNumber: Invalid/duplicate Reg No '$regNo'.")
                            continue
                        }
                        seenRegNosInThisBatch.add(regNo)

                        students.add(
                            StudentDetailsItem(
                                studentName = columns[0], parentName = columns[1], parentMobileNumber = columns[2],
                                regNo = regNo, gender = columns[4], birthDate = columns[5], admissionDate = columns[6],
                                monthlyFee = columns[7].toDoubleOrNull() ?: 0.0,
                                alternateMobileNumber = columns.getOrNull(8)?.ifEmpty { null },
                                address = columns.getOrNull(9)?.ifEmpty { null },
                                profileImageUrl = columns.getOrNull(10)?.ifEmpty { null },
                                teacherId = currentTeacherId!!,
                                teacherName = currentTeacherName!!,
                                isActive = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            failures.add("File Read Error: ${e.message}")
        }
        return Pair(students, failures)
    }

    private suspend fun saveStudentsToFirestore(students: List<StudentDetailsItem>): Pair<Int, List<String>> {
        var successCount = 0
        val failureDetails = mutableListOf<String>()
        if (currentOrganizationId == null) return Pair(0, listOf("Organization ID missing."))

        val studentsCollection = db.collection("organizations").document(currentOrganizationId!!)
            .collection("students")
        val chunks = students.chunked(490)

        for (chunk in chunks) {
            val batch: WriteBatch = db.batch()
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
                )
                batch.set(newDocRef, studentDataMap)
            }
            try {
                withContext(Dispatchers.IO) { batch.commit().await() }
                successCount += chunk.size
            } catch (e: Exception) {
                val failedStudentNames = chunk.joinToString { it.studentName }
                failureDetails.add("Batch failed for students: $failedStudentNames. Error: ${e.message}")
            }
        }
        return Pair(successCount, failureDetails)
    }
}