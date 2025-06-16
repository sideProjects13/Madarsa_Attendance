package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ManageSubjectsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SubjectAdapter // Adapter instance
    private lateinit var fabAddSubject: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoSubjects: TextView

    private lateinit var db: FirebaseFirestore
    // This list is primarily for the adapter's initial construction.
    // The adapter will maintain its own internal list copy after updateData is called.
    private val initialSubjectListForAdapter = mutableListOf<SubjectItem>()
    private var currentTeacherIdForContext: String? = null
    private var currentTeacherNameForContext: String? = null


    companion object {
        private const val TAG = "ManageSubjectsActivity"
        const val EXTRA_TEACHER_ID_CONTEXT = "teacher_id_context"
        const val EXTRA_TEACHER_NAME_CONTEXT = "teacher_name_context"
    }

    private val addEditSubjectLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Returned from AddEditSubjectActivity with RESULT_OK. List will refresh onResume.")
        } else {
            Log.d(TAG, "Returned from AddEditSubjectActivity with result: ${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_subjects)

        db = FirebaseFirestore.getInstance()
        currentTeacherIdForContext = intent.getStringExtra(EXTRA_TEACHER_ID_CONTEXT)
        currentTeacherNameForContext = intent.getStringExtra(EXTRA_TEACHER_NAME_CONTEXT)

        Log.d(TAG, "onCreate: currentTeacherIdForContext = $currentTeacherIdForContext, currentTeacherNameForContext = $currentTeacherNameForContext")

        if (currentTeacherIdForContext == null) {
            Log.e(TAG, "CRITICAL ERROR: ManageSubjectsActivity launched without EXTRA_TEACHER_ID_CONTEXT. This screen requires a teacher context.")
            Toast.makeText(this, "Error: Teacher context missing for managing subjects.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (currentTeacherNameForContext == null) {
            Log.w(TAG, "Warning: Teacher name context is missing, toolbar title might be generic.")
            // Potentially finish() if name is also critical for user understanding
        }

        toolbar = findViewById(R.id.manage_subjects_toolbar)
        recyclerView = findViewById(R.id.recyclerViewManageSubjects)
        fabAddSubject = findViewById(R.id.fabAddSubject)
        progressBar = findViewById(R.id.progressBarManageSubjects)
        tvNoSubjects = findViewById(R.id.tvNoSubjects)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        toolbar.title = "Subjects for ${currentTeacherNameForContext ?: "Class"}"

        setupRecyclerView() // Initialize adapter here

        fabAddSubject.setOnClickListener {
            // currentTeacherIdForContext is guaranteed to be non-null if we reached here
            val intent = Intent(this, AddEditSubjectActivity::class.java)
            intent.putExtra(AddEditSubjectActivity.EXTRA_TEACHER_ID_FOR_SUBJECT, currentTeacherIdForContext)
            Log.d(TAG, "Launching AddEditSubjectActivity to ADD for teacherId: $currentTeacherIdForContext")
            addEditSubjectLauncher.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Loading/refreshing subjects.")
        if (currentTeacherIdForContext != null) {
            loadSubjects()
        } else {
            Log.e(TAG, "onResume: currentTeacherIdForContext is null. Cannot load subjects. This should have been caught in onCreate.")
            tvNoSubjects.text = "Error: Teacher context lost."
            tvNoSubjects.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            progressBar.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        // Initialize the adapter with an empty list or the initial placeholder list
        adapter = SubjectAdapter(
            initialSubjectListForAdapter, // Pass a mutable list
            onEditClick = { subject ->
                val intent = Intent(this, AddEditSubjectActivity::class.java).apply {
                    putExtra(AddEditSubjectActivity.EXTRA_SUBJECT_ID, subject.id)
                    putExtra(AddEditSubjectActivity.EXTRA_TEACHER_ID_FOR_SUBJECT, subject.teacherId)
                    Log.d(TAG, "Launching AddEditSubjectActivity to EDIT subjectId: ${subject.id} with its original teacherId: ${subject.teacherId}")
                }
                addEditSubjectLauncher.launch(intent)
            },
            onDeleteClick = { subject ->
                confirmDeleteSubject(subject)
            },
            onItemClick = { subject ->
                Toast.makeText(this, "Clicked on ${subject.subjectName}", Toast.LENGTH_SHORT).show()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter // Set the adapter to RecyclerView
        Log.d(TAG, "setupRecyclerView: Adapter initialized and set.")
    }

    private fun loadSubjects() {
        Log.d(TAG, "loadSubjects: Called for teacherId: $currentTeacherIdForContext")
        progressBar.visibility = View.VISIBLE
        tvNoSubjects.visibility = View.GONE
        recyclerView.visibility = View.GONE

        // currentTeacherIdForContext should be non-null here due to checks in onResume/onCreate
        db.collection("subjects")
            .whereEqualTo("teacherId", currentTeacherIdForContext)
            .orderBy("subjectName", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                val newFetchedSubjects = mutableListOf<SubjectItem>() // Temporary list for fetched data

                if (documents.isEmpty) {
                    Log.d(TAG, "loadSubjects: No subjects found for teacherId: $currentTeacherIdForContext")
                    tvNoSubjects.text = "No subjects added for this class yet."
                    tvNoSubjects.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    Log.d(TAG, "loadSubjects: Found ${documents.size()} subjects for teacherId: $currentTeacherIdForContext")
                    recyclerView.visibility = View.VISIBLE
                    tvNoSubjects.visibility = View.GONE
                    for (document in documents) {
                        val subject = document.toObject(SubjectItem::class.java).copy(id = document.id)
                        newFetchedSubjects.add(subject)
                        Log.d(TAG, "loadSubjects: Fetched - Name: ${subject.subjectName}, DocTeacherId: ${subject.teacherId}, DocId: ${subject.id}")
                    }
                }
                // Update the adapter with the new list of fetched subjects
                adapter.updateData(newFetchedSubjects)
                Log.d(TAG, "loadSubjects: Adapter updated with ${newFetchedSubjects.size} items. Adapter's current itemCount: ${adapter.itemCount}")

            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                tvNoSubjects.text = "Error loading subjects."
                tvNoSubjects.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                Log.e(TAG, "Error loading subjects for teacherId: $currentTeacherIdForContext", e)
                Toast.makeText(this, "Error loading subjects: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteSubject(subject: SubjectItem) {
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Subject")
            .setMessage("Are you sure you want to delete '${subject.subjectName}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSubjectFromFirestore(subject)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSubjectFromFirestore(subject: SubjectItem) {
        Log.d(TAG, "deleteSubjectFromFirestore: Attempting to delete subjectId: ${subject.id} with name: ${subject.subjectName}")
        progressBar.visibility = View.VISIBLE
        db.collection("subjects").document(subject.id)
            .delete()
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "'${subject.subjectName}' deleted successfully", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "deleteSubjectFromFirestore: Deletion successful. Reloading list.")
                loadSubjects()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error deleting subject: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error deleting subject ${subject.id}", e)
            }
    }
}