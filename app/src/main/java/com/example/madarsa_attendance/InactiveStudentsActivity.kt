package com.example.madarsa_attendance

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class InactiveStudentsActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "InactiveStudents"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: InactiveStudentAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoInactive: TextView

    private lateinit var db: FirebaseFirestore
    private var teacherId: String? = null
    private lateinit var teacherDataViewModel: TeacherDataViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inactive_students)

        teacherId = intent.getStringExtra("TEACHER_ID")
        db = FirebaseFirestore.getInstance()
        // ViewModel to notify ManageClassFragment that a student might have been reactivated.
        // It's scoped to the host activity (TeacherOptionsActivity)
        teacherDataViewModel = ViewModelProvider(this)[TeacherDataViewModel::class.java]

        if (teacherId == null) {
            Toast.makeText(this, "Teacher information missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        toolbar = findViewById(R.id.inactive_students_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Inactive Students"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        recyclerView = findViewById(R.id.rv_inactive_students)
        progressBar = findViewById(R.id.progress_bar_inactive)
        tvNoInactive = findViewById(R.id.tv_no_inactive_students)

        setupRecyclerView()
        loadInactiveStudents()
    }

    private fun setupRecyclerView() {
        // The adapter will call the lambda function when the "Reactivate" button is clicked
        adapter = InactiveStudentAdapter { student ->
            confirmReactivateStudent(student)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadInactiveStudents() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvNoInactive.visibility = View.GONE

        db.collection("students")
            .whereEqualTo("teacherId", teacherId)
            .whereEqualTo("isActive", false) // <-- The key query for inactive students
            .orderBy("studentName", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                progressBar.visibility = View.GONE
                if (querySnapshot.isEmpty) {
                    tvNoInactive.visibility = View.VISIBLE
                    adapter.submitList(emptyList()) // Clear the adapter
                } else {
                    val students = querySnapshot.documents.mapNotNull { doc ->
                        doc.toObject(StudentDetailsItem::class.java)?.copy(id = doc.id)
                    }
                    adapter.submitList(students)
                    recyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                tvNoInactive.text = "Error loading students."
                tvNoInactive.visibility = View.VISIBLE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error loading inactive students. CHECK FIRESTORE INDEXES.", e)
            }
    }

    private fun confirmReactivateStudent(student: StudentDetailsItem) {
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Reactivate Student")
            .setMessage("Are you sure you want to reactivate ${student.studentName}? They will appear in the active class list again.")
            .setPositiveButton("Reactivate") { _, _ ->
                reactivateStudentInFirestore(student.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reactivateStudentInFirestore(studentId: String) {
        progressBar.visibility = View.VISIBLE
        db.collection("students").document(studentId)
            .update("isActive", true)
            .addOnSuccessListener {
                Toast.makeText(this, "Student reactivated!", Toast.LENGTH_SHORT).show()
                // Notify ManageClassFragment that data has changed, so it reloads when the user goes back.
                teacherDataViewModel.notifyStudentDataChanged()

                // <<< SIMPLIFIED AND MORE RELIABLE >>>
                // Just reload the list. The reactivated student will disappear automatically.
                loadInactiveStudents()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to reactivate: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}