package com.example.madarsa_attendance

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects

class AbsenteesActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var spinnerTeacherFilter: AutoCompleteTextView
    private lateinit var rvAbsentees: RecyclerView
    private lateinit var tvNoAbsentees: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var absenteesAdapter: DashboardStudentAdapter

    private var teacherList = mutableListOf<Teacher>()
    private var allAbsentStudents = listOf<DashboardStudentItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_absentees)

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        loadTeachersIntoFilter()
    }

    private fun initializeViews() {
        spinnerTeacherFilter = findViewById(R.id.spinner_teacher_filter)
        rvAbsentees = findViewById(R.id.rv_absentees)
        tvNoAbsentees = findViewById(R.id.tv_no_absentees)
        progressBar = findViewById(R.id.progress_bar_absentees)
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val appBarLayout: AppBarLayout = findViewById(R.id.app_bar_layout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { _, insets ->
            appBarLayout.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
    }

    private fun setupRecyclerView() {
        absenteesAdapter = DashboardStudentAdapter()
        rvAbsentees.layoutManager = LinearLayoutManager(this)
        rvAbsentees.adapter = absenteesAdapter
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.absentStudents.observe(this) { absentees ->
            allAbsentStudents = absentees
            filterAbsentees(null) // Initially show all absentees
        }
    }

    private fun loadTeachersIntoFilter() {
        val organizationId = FirebaseAuthManager.getOrganizationId(this) ?: return
        FirebaseFirestore.getInstance().collection("organizations").document(organizationId)
            .collection("teachers").orderBy("teacherName").get()
            .addOnSuccessListener { documents ->
                teacherList.clear()
                teacherList.add(0, Teacher(teacherId = "ALL", teacherName = "All Classes"))
                teacherList.addAll(documents.toObjects())

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teacherList.map { it.teacherName })
                spinnerTeacherFilter.setAdapter(adapter)
                spinnerTeacherFilter.setText(teacherList[0].teacherName, false)

                spinnerTeacherFilter.setOnItemClickListener { _, _, position, _ ->
                    val selectedTeacher = teacherList[position]
                    if (selectedTeacher.teacherId == "ALL") {
                        filterAbsentees(null)
                    } else {
                        filterAbsentees(selectedTeacher.teacherName)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading classes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterAbsentees(teacherName: String?) {
        val filteredList = if (teacherName == null) {
            allAbsentStudents
        } else {
            allAbsentStudents.filter { it.subtitle == teacherName }
        }

        absenteesAdapter.submitList(filteredList)
        if (filteredList.isEmpty()) {
            tvNoAbsentees.visibility = View.VISIBLE
            rvAbsentees.visibility = View.GONE
        } else {
            tvNoAbsentees.visibility = View.GONE
            rvAbsentees.visibility = View.VISIBLE
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