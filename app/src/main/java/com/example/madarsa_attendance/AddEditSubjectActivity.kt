package com.example.madarsa_attendance

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions // For merging updates if needed

class AddEditSubjectActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etSubjectName: EditText
    private lateinit var etSubjectDescription: EditText
    private lateinit var btnSaveSubject: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var db: FirebaseFirestore
    private var currentEditingSubjectId: String? = null // ID of the subject being edited
    private var teacherIdForThisSubject: String? = null // The teacher this subject belongs to or will belong to
    private var currentOrganizationId: String? = null // NEW: Organization ID for multi-tenancy

    companion object {
        private const val TAG = "AddEditSubjectActivity"
        const val EXTRA_SUBJECT_ID = "subject_id"
        const val EXTRA_TEACHER_ID_FOR_SUBJECT = "teacher_id_for_subject"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_subject)

        db = FirebaseFirestore.getInstance()
        currentEditingSubjectId = intent.getStringExtra(EXTRA_SUBJECT_ID)
        teacherIdForThisSubject = intent.getStringExtra(EXTRA_TEACHER_ID_FOR_SUBJECT)
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this) // NEW: Get organization ID

        Log.d(TAG, "onCreate: currentEditingSubjectId = $currentEditingSubjectId, teacherIdForThisSubject (from intent) = $teacherIdForThisSubject, Org ID: $currentOrganizationId")

        // CRITICAL CHECK FOR ORGANIZATION ID
        if (currentOrganizationId == null) {
            Toast.makeText(this, "Organization data missing. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        toolbar = findViewById(R.id.add_edit_subject_toolbar)
        etSubjectName = findViewById(R.id.etSubjectName)
        etSubjectDescription = findViewById(R.id.etSubjectDescription)
        btnSaveSubject = findViewById(R.id.btnSaveSubject)
        progressBar = findViewById(R.id.progressBarAddEditSubject)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (currentEditingSubjectId != null) {
            toolbar.title = "Edit Subject"
            btnSaveSubject.text = "Update Subject"
            loadSubjectDetails()
        } else {
            toolbar.title = "Add New Subject"
            if (teacherIdForThisSubject == null) {
                Log.e(TAG, "CRITICAL: Trying to add a new subject without a teacherIdForThisSubject. This should not happen if subjects are teacher-specific.")
                Toast.makeText(this, "Error: Teacher association missing. Cannot add subject.", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }

        btnSaveSubject.setOnClickListener {
            saveSubject()
        }
    }

    private fun loadSubjectDetails() {
        if (currentEditingSubjectId == null || currentOrganizationId == null) {
            Log.e(TAG, "loadSubjectDetails: Aborting - currentEditingSubjectId or currentOrganizationId is null.")
            Toast.makeText(this, "Error: Missing required IDs.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSaveSubject.isEnabled = false // Disable button while loading

        db.collection("organizations").document(currentOrganizationId!!) // NEW
            .collection("subjects").document(currentEditingSubjectId!!) // NEW
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                btnSaveSubject.isEnabled = true
                if (document.exists()) {
                    etSubjectName.setText(document.getString("subjectName"))
                    etSubjectDescription.setText(document.getString("description"))
                    val fetchedTeacherId = document.getString("teacherId")
                    if (teacherIdForThisSubject == null && fetchedTeacherId != null) {
                        teacherIdForThisSubject = fetchedTeacherId
                        Log.d(TAG, "loadSubjectDetails: Loaded teacherId ($fetchedTeacherId) from existing subject.")
                    } else if (teacherIdForThisSubject != fetchedTeacherId && fetchedTeacherId != null) {
                        Log.w(TAG, "loadSubjectDetails: Mismatch or update in teacherId context. Intent teacherId: $teacherIdForThisSubject, Fetched teacherId: $fetchedTeacherId. Using intent's for save.")
                    }
                } else {
                    Toast.makeText(this, "Subject not found.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSaveSubject.isEnabled = true
                Toast.makeText(this, "Error loading subject: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error loading subject details for $currentEditingSubjectId", e)
            }
    }

    private fun saveSubject() {
        val subjectName = etSubjectName.text.toString().trim()
        val description = etSubjectDescription.text.toString().trim()

        if (subjectName.isEmpty()) {
            etSubjectName.error = "Subject name is required"
            etSubjectName.requestFocus()
            return
        }

        if (teacherIdForThisSubject == null) {
            Log.e(TAG, "saveSubject: teacherIdForThisSubject is null. Cannot save. This indicates a flow error.")
            Toast.makeText(this, "Error: Teacher association is missing. Please go back and try again.", Toast.LENGTH_LONG).show()
            return
        }
        if (currentOrganizationId == null) { // NEW: Check organization ID before saving
            Log.e(TAG, "saveSubject: currentOrganizationId is null. Aborting save.")
            Toast.makeText(this, "Error: Organization data missing. Cannot save subject.", Toast.LENGTH_LONG).show()
            return
        }

        Log.d(TAG, "saveSubject: Attempting to save. subjectName='$subjectName', description='$description', forTeacherId='$teacherIdForThisSubject', Org ID: $currentOrganizationId")

        progressBar.visibility = View.VISIBLE
        btnSaveSubject.isEnabled = false

        val subjectData = hashMapOf<String, Any?>(
            "subjectName" to subjectName,
            "description" to description,
            "teacherId" to teacherIdForThisSubject
        )

        val subjectRef = db.collection("organizations").document(currentOrganizationId!!) // NEW
            .collection("subjects") // NEW

        val task = if (currentEditingSubjectId != null) {
            Log.d(TAG, "saveSubject: Updating existing subject ID: $currentEditingSubjectId")
            subjectRef.document(currentEditingSubjectId!!).set(subjectData, SetOptions.merge())
        } else {
            Log.d(TAG, "saveSubject: Adding new subject for teacher ID: $teacherIdForThisSubject")
            subjectRef.add(subjectData)
        }

        task.addOnSuccessListener { documentReferenceOrVoid ->
            progressBar.visibility = View.GONE
            btnSaveSubject.isEnabled = true
            val message = if (currentEditingSubjectId != null) "Subject updated" else "Subject added"
            Toast.makeText(this, "$message successfully", Toast.LENGTH_SHORT).show()

            val newOrUpdatedId = if (currentEditingSubjectId != null) currentEditingSubjectId else (documentReferenceOrVoid as? com.google.firebase.firestore.DocumentReference)?.id
            Log.d(TAG, "saveSubject: Success. Subject ID: $newOrUpdatedId")

            setResult(Activity.RESULT_OK)
            finish()
        }.addOnFailureListener { e ->
            progressBar.visibility = View.GONE
            btnSaveSubject.isEnabled = true
            Toast.makeText(this, "Error saving subject: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error saving subject", e)
        }
    }
}