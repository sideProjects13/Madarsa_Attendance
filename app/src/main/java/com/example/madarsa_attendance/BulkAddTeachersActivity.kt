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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class BulkAddTeachersActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "BulkAddTeachersActivity"
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var db: FirebaseFirestore
    private lateinit var mainAuth: FirebaseAuth
    private lateinit var secondaryAuth: FirebaseAuth
    private var currentOrganizationId: String? = null
    private var csvFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bulk_add_teachers)

        db = FirebaseFirestore.getInstance()
        mainAuth = Firebase.auth
        secondaryAuth = FirebaseAuth.getInstance(Firebase.app("secondary"))
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)
        csvFileUri = intent.data

        progressBar = findViewById(R.id.progressBarBulkAdd)
        tvStatus = findViewById(R.id.tvBulkAddStatus)

        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        if (currentOrganizationId == null || csvFileUri == null) {
            Toast.makeText(this, "Missing required data for bulk import.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        startBulkImport()
    }

    private fun startBulkImport() {
        lifecycleScope.launch {
            var successCount = 0
            var failureCount = 0
            var finalMessage: String

            try {
                val (teachersToCreate, parseFailures) = withContext(Dispatchers.IO) {
                    parseCsvFile(csvFileUri!!)
                }
                failureCount += parseFailures.size

                if (teachersToCreate.isEmpty()) {
                    throw Exception("No valid teacher records found in the CSV file.")
                }

                tvStatus.text = "Creating ${teachersToCreate.size} teacher accounts..."
                val batch = db.batch()
                val teachersCollection = db.collection("organizations").document(currentOrganizationId!!).collection("teachers")

                for (teacherInfo in teachersToCreate) {
                    try {
                        // Create Auth User
                        val authResult = secondaryAuth.createUserWithEmailAndPassword(teacherInfo.email, teacherInfo.password).await()
                        val teacherUid = authResult.user?.uid ?: throw Exception("UID was null")

                        // Prepare Firestore document
                        val newTeacherRef = teachersCollection.document()
                        val teacherData = hashMapOf(
                            "teacherName" to teacherInfo.name,
                            "mobileNumber" to teacherInfo.mobile,
                            "email" to teacherInfo.email,
                            "uid" to teacherUid
                        )
                        batch.set(newTeacherRef, teacherData)
                        successCount++
                    } catch (e: Exception) {
                        failureCount++
                        Log.e(TAG, "Failed to create teacher ${teacherInfo.name}: ${e.message}")
                    }
                }

                // Commit the batch
                if (successCount > 0) {
                    batch.commit().await()
                }

                finalMessage = "Import Complete!\nSuccess: $successCount, Failed: $failureCount"

            } catch (e: Exception) {
                finalMessage = "Import failed: ${e.message}"
            } finally {
                // Ensure auth state is correct before finishing
                secondaryAuth.signOut()
                mainAuth.currentUser?.reload()
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                tvStatus.text = finalMessage
                setResult(Activity.RESULT_OK)
                StatusDialogFragment.newInstance(successCount > 0, finalMessage, true)
                    .show(supportFragmentManager, "statusDialog")
            }
        }
    }

    private fun parseCsvFile(uri: Uri): Pair<List<TeacherCsvInfo>, List<String>> {
        val teachers = mutableListOf<TeacherCsvInfo>()
        val failures = mutableListOf<String>()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readLine() // Skip header
                var line: String?
                var lineNumber = 1
                while (reader.readLine().also { line = it } != null) {
                    lineNumber++
                    val columns = line?.split(",")?.map { it.trim() }
                    if (columns != null && columns.size >= 4) {
                        teachers.add(TeacherCsvInfo(columns[0], columns[1], columns[2], columns[3]))
                    } else {
                        failures.add("Line $lineNumber: Invalid format")
                    }
                }
            }
        }
        return Pair(teachers, failures)
    }

    data class TeacherCsvInfo(val name: String, val mobile: String, val email: String, val password: String)
}