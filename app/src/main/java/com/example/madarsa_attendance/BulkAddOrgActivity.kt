package com.example.madarsa_attendance

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
            var failureCount: Int

            try {
                tvStatus.text = "Fetching teacher and student data..."
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
                    failureCount = parseFailures.size
                    finalMessage = if (failureCount > 0) "Import Failed. All records had parsing errors." else "No valid new students found in the file."
                } else {
                    tvStatus.text = "Found ${parsedStudents.size} valid students. Saving to database..."
                    val (firestoreSuccessCount, firestoreFailures) = saveStudentsToFirestore(parsedStudents)
                    val combinedFailureDetails = parseFailures + firestoreFailures

                    successCount = firestoreSuccessCount
                    failureCount = combinedFailureDetails.size

                    isSuccess = successCount > 0
                    finalMessage = "Import Complete!\nSuccess: $successCount, Failed: $failureCount"

                    if (combinedFailureDetails.isNotEmpty()) {
                        Log.e(TAG, "--- IMPORT FAILURES ---")
                        combinedFailureDetails.forEach { Log.e(TAG, " - $it") }
                    }
                }
            } catch (e: Exception) {
                isSuccess = false
                finalMessage = "A critical error occurred: ${e.message}"
                Log.e(TAG, "Critical error during bulk import", e)
                failureCount = -1
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                tvStatus.text = finalMessage
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
            snapshot.documents.associate {
                val teacherName = (it.getString("teacherName") ?: "").trim().lowercase(Locale.ROOT)
                val teacherId = it.id
                teacherName to Pair(teacherId, it.getString("teacherName") ?: "")
            }
        } catch (e: Exception) { emptyMap() }
    }

    private suspend fun fetchExistingRegNos(orgId: String): Set<String> {
        return try {
            val snapshot = db.collection("organizations").document(orgId).collection("students").get().await()
            snapshot.documents.mapNotNull { it.getString("regNo")?.trim() }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    private fun parseCsvFile(uri: Uri, existingRegNos: Set<String>, teachersMap: Map<String, Pair<String, String>>): Pair<List<StudentDetailsItem>, List<String>> {
        val students = mutableListOf<StudentDetailsItem>()
        val failures = mutableListOf<String>()
        val seenRegNosInThisBatch = mutableSetOf<String>()
        var lineNumber = 1

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val csvReader = CSVReader(InputStreamReader(inputStream))
                csvReader.skip(1)
                var line: Array<String>?
                while (csvReader.readNext().also { line = it } != null) {
                    lineNumber++
                    val columns = line!!
                    if (columns.size < 12) {
                        failures.add("Line $lineNumber: Not enough columns.")
                        continue
                    }

                    val regNo = columns[3].trim()
                    val teacherNameInCsv = columns[11].trim()
                    val teacherData = teachersMap[teacherNameInCsv.lowercase(Locale.ROOT)]

                    if (regNo.isBlank() || existingRegNos.contains(regNo) || seenRegNosInThisBatch.contains(regNo)) {
                        failures.add("Line $lineNumber: Invalid or duplicate Reg No '$regNo'.")
                        continue
                    }
                    if (teacherData == null) {
                        failures.add("Line $lineNumber: Teacher '$teacherNameInCsv' not found.")
                        continue
                    }
                    seenRegNosInThisBatch.add(regNo)

                    students.add(
                        StudentDetailsItem(
                            studentName = columns[0].trim(),
                            parentName = columns[1].trim(),
                            parentMobileNumber = columns[2].trim(),
                            regNo = regNo,
                            gender = columns[4].trim(),
                            birthDate = columns[5].trim(),
                            admissionDate = columns[6].trim(),
                            monthlyFee = columns[7].trim().toDoubleOrNull() ?: 0.0,
                            alternateMobileNumber = columns.getOrNull(8)?.trim()?.ifEmpty { null },
                            address = columns.getOrNull(9)?.trim()?.ifEmpty { null },
                            profileImageUrl = columns.getOrNull(10)?.trim()?.ifEmpty { null },
                            isActive = true,
                            teacherId = teacherData.first,
                            teacherName = teacherData.second
                        )
                    )
                }
            }
        } catch (e: Exception) {
            failures.add("File Read Error: ${e.message}")
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
                failures.add("A batch of ${chunk.size} students failed to save: ${e.message}")
            }
        }
        return Pair(successCount, failures)
    }
}