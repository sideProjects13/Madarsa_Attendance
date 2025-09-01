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
import com.opencsv.CSVReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.util.Locale

class BulkAddOrgActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "BulkAddOrgActivity"
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var db: FirebaseFirestore
    private var currentOrganizationId: String? = null
    private var csvFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bulk_add_org)

        db = FirebaseFirestore.getInstance()
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)
        csvFileUri = intent.data

        progressBar = findViewById(R.id.progressBarBulkAddOrg)
        tvStatus = findViewById(R.id.tvBulkAddOrgStatus)

        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.bulk_add_org_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        if (currentOrganizationId == null || csvFileUri == null) {
            Toast.makeText(this, "Missing data for bulk import.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        startBulkImport()
    }

    private fun startBulkImport() {
        progressBar.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Starting organization-wide import..."

        lifecycleScope.launch {
            var isSuccess = false
            var finalMessage: String
            var successCount = 0
            var combinedFailureDetails = listOf<String>()

            try {
                tvStatus.text = "Fetching existing teacher and student data..."
                val teachersMapDeferred = async(Dispatchers.IO) { fetchTeachersMap(currentOrganizationId!!) }
                val existingRegNosDeferred = async(Dispatchers.IO) { fetchExistingRegNos(currentOrganizationId!!) }
                val teachersMap = teachersMapDeferred.await()
                val existingRegNos = existingRegNosDeferred.await()

                if (teachersMap.isEmpty()) {
                    throw IllegalStateException("No teachers found in the database. Cannot assign students.")
                }

                tvStatus.text = "Parsing CSV file..."
                val (parsedStudents, parseFailures) = withContext(Dispatchers.IO) {
                    parseCsvFile(csvFileUri!!, existingRegNos, teachersMap)
                }

                if (parsedStudents.isEmpty()) {
                    isSuccess = false
                    finalMessage = "Import Failed. No valid new students found to import."
                    combinedFailureDetails = parseFailures
                } else {
                    tvStatus.text = "Found ${parsedStudents.size} valid students. Saving to database..."
                    val (firestoreSuccessCount, firestoreFailures) = saveStudentsToFirestore(parsedStudents)

                    successCount = firestoreSuccessCount
                    combinedFailureDetails = parseFailures + firestoreFailures

                    isSuccess = successCount > 0
                    finalMessage = "Import Complete!\nSuccess: $successCount, Failed: ${combinedFailureDetails.size}"
                }
            } catch (e: Exception) {
                isSuccess = false
                finalMessage = "A critical error occurred: ${e.message}"
                Log.e(TAG, "Critical error during bulk import", e)
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE

                if (combinedFailureDetails.isNotEmpty()) {
                    val errorDetails = combinedFailureDetails.joinToString("\n")
                    finalMessage += "\n\nError Details:\n$errorDetails"
                    Log.e(TAG, "--- IMPORT FAILURES ---\n$errorDetails")
                }

                tvStatus.text = "Import Finished. See dialog for results."
                setResult(Activity.RESULT_OK, Intent().putExtra("SUCCESS_COUNT", successCount))

                StatusDialogFragment.newInstance(
                    isSuccess = isSuccess,
                    message = finalMessage,
                    finishActivityOnDismiss = true
                ).show(supportFragmentManager, "statusDialog")
            }
        }
    }

    private suspend fun fetchTeachersMap(orgId: String): Map<String, Pair<String, String>> {
        return try {
            val snapshot = db.collection("organizations").document(orgId).collection("teachers").get().await()
            snapshot.documents.associate { doc ->
                val teacherName = (doc.getString("teacherName") ?: "").trim().lowercase(Locale.ROOT)
                val teacherId = doc.id
                // Store both the ID and the original cased name for later use
                teacherName to Pair(teacherId, doc.getString("teacherName") ?: "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch teachers map", e)
            emptyMap()
        }
    }

    private suspend fun fetchExistingRegNos(orgId: String): Set<String> {
        return try {
            val snapshot = db.collection("organizations").document(orgId).collection("students").get().await()
            snapshot.documents.mapNotNull { it.getString("regNo")?.trim() }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch registration numbers", e)
            emptySet()
        }
    }

    private fun parseCsvFile(
        uri: Uri,
        existingRegNos: Set<String>,
        teachersMap: Map<String, Pair<String, String>>
    ): Pair<List<StudentDetailsItem>, List<String>> {
        val students = mutableListOf<StudentDetailsItem>()
        val failures = mutableListOf<String>()
        val seenRegNosInThisBatch = mutableSetOf<String>()
        var lineNumber = 1 // Header row

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val csvReader = CSVReader(InputStreamReader(inputStream))
                csvReader.skip(1) // Skip header
                var line: Array<String>?
                while (csvReader.readNext().also { line = it } != null) {
                    lineNumber++
                    val columns = line!!

                    // --- START: DETAILED VALIDATION ---
                    val studentName = columns.getOrNull(0)?.trim()
                    val regNo = columns.getOrNull(3)?.trim()
                    val teacherNameInCsv = columns.getOrNull(11)?.trim()

                    val validationErrors = mutableListOf<String>()

                    if (studentName.isNullOrBlank()) validationErrors.add("Student Name is missing")
                    if (columns.getOrNull(1).isNullOrBlank()) validationErrors.add("Parent Name is missing")
                    if (columns.getOrNull(2).isNullOrBlank()) validationErrors.add("Parent Mobile is missing")

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

                    val teacherData = teacherNameInCsv?.lowercase(Locale.ROOT)?.let { teachersMap[it] }
                    if (teacherNameInCsv.isNullOrBlank()) {
                        validationErrors.add("Teacher Name is missing from CSV")
                    } else if (teacherData == null) {
                        validationErrors.add("Teacher '$teacherNameInCsv' not found in database")
                    }

                    if (validationErrors.isNotEmpty()) {
                        val errorPrefix = "Line $lineNumber (Name: '${studentName ?: "N/A"}', Reg: '${regNo ?: "N/A"}')"
                        failures.add("$errorPrefix: ${validationErrors.joinToString(", ")}.")
                        continue
                    }
                    // --- END: DETAILED VALIDATION ---

                    seenRegNosInThisBatch.add(regNo!!)

                    students.add(
                        StudentDetailsItem(
                            studentName = studentName!!,
                            parentName = columns[1].trim(),
                            parentMobileNumber = columns[2].trim(),
                            regNo = regNo,
                            gender = columns.getOrNull(4)?.trim() ?: "",
                            birthDate = columns.getOrNull(5)?.trim() ?: "",
                            admissionDate = columns.getOrNull(6)?.trim() ?: "",
                            monthlyFee = columns.getOrNull(7)?.trim()?.toDoubleOrNull() ?: 0.0,
                            alternateMobileNumber = columns.getOrNull(8)?.trim()?.ifEmpty { null },
                            address = columns.getOrNull(9)?.trim()?.ifEmpty { null },
                            profileImageUrl = columns.getOrNull(10)?.trim()?.ifEmpty { null },
                            isActive = true,
                            teacherId = teacherData!!.first,  // ID from the map
                            teacherName = teacherData.second // Original cased name from the map
                        )
                    )
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
        val failures = mutableListOf<String>()
        val studentsCollection = db.collection("organizations").document(currentOrganizationId!!).collection("students")

        students.chunked(490).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { student ->
                val docRef = studentsCollection.document()
                val studentDataMap = mapOf(
                    "studentName" to student.studentName,
                    "teacherId" to student.teacherId,
                    "teacherName" to student.teacherName,
                    "parentName" to student.parentName,
                    "parentMobileNumber" to student.parentMobileNumber,
                    "profileImageUrl" to (student.profileImageUrl ?: ""),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "regNo" to student.regNo,
                    "gender" to student.gender,
                    "admissionDate" to student.admissionDate,
                    "birthDate" to student.birthDate,
                    "isActive" to student.isActive,
                    "monthlyFee" to student.monthlyFee,
                    "alternateMobileNumber" to student.alternateMobileNumber,
                    "address" to student.address
                ).filterValues { it != null }
                batch.set(docRef, studentDataMap)
            }

            try {
                batch.commit().await()
                successCount += chunk.size
            } catch (e: Exception) {
                val failedStudentInfo = chunk.joinToString(", ") { "'${it.studentName}' ('${it.regNo}')" }
                failures.add("Database Error: A batch of ${chunk.size} students failed to save. Students: $failedStudentInfo. Reason: ${e.message}")
                Log.e(TAG, "Firestore batch commit failed", e)
            }
        }
        return Pair(successCount, failures)
    }
}