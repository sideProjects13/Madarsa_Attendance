package com.example.madarsa_attendance

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BulkMoveStudentsActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "BulkMoveActivity"
    }

    // Views
    private lateinit var tvSourceClass: TextView
    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BulkMoveAdapter
    private lateinit var tvNoStudents: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerDestination: Spinner
    private lateinit var btnMove: MaterialButton
    private lateinit var tvDestinationLabel: TextView

    // Data
    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var sourceTeacherId: String? = null
    private var sourceTeacherName: String? = null
    private var destinationTeacher: TeacherSpinnerItem? = null

    // The sharedViewModel is no longer needed here
    // private val sharedViewModel: TeacherDataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_bulk_move_students)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)
        sourceTeacherId = intent.getStringExtra("SOURCE_TEACHER_ID")
        sourceTeacherName = intent.getStringExtra("SOURCE_TEACHER_NAME")

        if (organizationId == null || sourceTeacherId == null) {
            Toast.makeText(this, "Required information is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupSearchView()

        loadSourceStudents()
        loadDestinationTeachers()

        btnMove.setOnClickListener { performBulkMove() }
    }

    private fun initializeViews() {
        tvSourceClass = findViewById(R.id.tv_source_class_info)
        searchView = findViewById(R.id.search_view_students)
        recyclerView = findViewById(R.id.recycler_view_students)
        tvNoStudents = findViewById(R.id.tv_no_students)
        progressBar = findViewById(R.id.progress_bar)
        spinnerDestination = findViewById(R.id.spinner_destination_class)
        btnMove = findViewById(R.id.btn_move_students)
        tvDestinationLabel = findViewById(R.id.tv_destination_label)
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val appBarLayout: AppBarLayout = findViewById(R.id.app_bar_layout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = BulkMoveAdapter(emptyList()) { selectionCount ->
            updateMoveButton(selectionCount)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun updateMoveButton(count: Int) {
        if (count > 0) {
            btnMove.isEnabled = true
            tvDestinationLabel.text = "Move ($count) Selected Students To:"
        } else {
            btnMove.isEnabled = false
            tvDestinationLabel.text = "Move (0) Selected Students To:"
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText)
                return true
            }
        })
    }

    private fun loadSourceStudents() {
        progressBar.visibility = View.VISIBLE
        tvSourceClass.text = "Moving students from: ${sourceTeacherName ?: "..."}"

        db.collection("organizations").document(organizationId!!)
            .collection("students")
            .whereEqualTo("teacherId", sourceTeacherId)
            .whereEqualTo("isActive", true)
            .orderBy("studentName")
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                val students = documents.toObjects<StudentDetailsItem>()
                if (students.isEmpty()) {
                    tvNoStudents.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvNoStudents.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
                adapter.updateData(students)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadDestinationTeachers() {
        db.collection("organizations").document(organizationId!!)
            .collection("teachers")
            .orderBy("teacherName")
            .get()
            .addOnSuccessListener { documents ->
                val teachers = documents.toObjects<Teacher>().map {
                    TeacherSpinnerItem(it.teacherId, it.teacherName, it.profileImageUrl)
                }.filter { it.id != sourceTeacherId }

                if (teachers.isEmpty()) {
                    spinnerDestination.visibility = View.GONE
                    btnMove.isEnabled = false
                    tvDestinationLabel.text = "No other classes available to move to."
                    return@addOnSuccessListener
                }

                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, teachers.map { it.name })
                spinnerDestination.adapter = adapter

                spinnerDestination.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        destinationTeacher = teachers[position]
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        destinationTeacher = null
                    }
                }
            }
    }

    private fun performBulkMove() {
        val selectedStudents = adapter.getSelectedStudents()
        if (selectedStudents.isEmpty()) {
            Toast.makeText(this, "Please select at least one student to move.", Toast.LENGTH_SHORT).show()
            return
        }
        if (destinationTeacher == null) {
            Toast.makeText(this, "Please select a destination class.", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Moving ${selectedStudents.size} students...").apply {
            isCancelable = false
        }
        loadingDialog.show(supportFragmentManager, "bulkMoveDialog")

        lifecycleScope.launch {
            try {
                val batch = db.batch()

                for (student in selectedStudents) {
                    val studentRef = db.collection("organizations").document(organizationId!!)
                        .collection("students").document(student.id)
                    batch.update(studentRef, mapOf("teacherId" to destinationTeacher!!.id, "teacherName" to destinationTeacher!!.name))

                    val feesSnapshot = db.collection("organizations").document(organizationId!!)
                        .collection("feePayments").whereEqualTo("studentId", student.id).get().await()
                    feesSnapshot.documents.forEach { doc ->
                        batch.update(doc.reference, mapOf("teacherId" to destinationTeacher!!.id, "teacherName" to destinationTeacher!!.name))
                    }

                    val examsSnapshot = db.collection("organizations").document(organizationId!!)
                        .collection("examResults").whereEqualTo("studentId", student.id).get().await()
                    examsSnapshot.documents.forEach { doc ->
                        batch.update(doc.reference, mapOf("teacherId" to destinationTeacher!!.id, "teacherName" to destinationTeacher!!.name))
                    }

                    val attendanceSnapshot = db.collection("organizations").document(organizationId!!)
                        .collection("attendanceRecords").whereEqualTo("teacherId", student.teacherId).get().await()
                    for (doc in attendanceSnapshot.documents) {
                        val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>
                        if (studentAttendances?.any { it["studentId"] == student.id } == true) {
                            batch.update(doc.reference, mapOf("teacherId" to destinationTeacher!!.id, "teacherName" to destinationTeacher!!.name))
                        }
                    }
                }

                batch.commit().await()

                loadingDialog.dismiss()
                StatusDialogFragment.newInstance(true, "${selectedStudents.size} students moved successfully!").show(supportFragmentManager, "successDialog")
                setResult(Activity.RESULT_OK)

                loadSourceStudents()
                updateMoveButton(0)

            } catch (e: Exception) {
                loadingDialog.dismiss()
                StatusDialogFragment.newInstance(false, "Error moving students: ${e.message}").show(supportFragmentManager, "errorDialog")
                Log.e(TAG, "Error in bulk move", e)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}