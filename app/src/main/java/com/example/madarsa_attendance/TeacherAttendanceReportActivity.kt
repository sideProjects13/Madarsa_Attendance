package com.example.madarsa_attendance

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TeacherAttendanceReportActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "TeacherAttendanceReport"
    }

    private lateinit var btnSelectDate: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TeacherAttendanceReportAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoData: TextView

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_teacher_attendance_report)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization information missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupRecyclerView()

        updateDateButtonText()
        btnSelectDate.setOnClickListener { showDatePicker() }

        loadReportForSelectedDate()
    }

    private fun initializeViews() {
        btnSelectDate = findViewById(R.id.btn_select_date)
        recyclerView = findViewById(R.id.rv_teacher_attendance)
        progressBar = findViewById(R.id.progress_bar)
        tvNoData = findViewById(R.id.tv_no_data)
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
        adapter = TeacherAttendanceReportAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun updateDateButtonText() {
        val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        btnSelectDate.text = "Date: ${dateFormat.format(selectedDate.time)}"
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                updateDateButtonText()
                loadReportForSelectedDate()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadReportForSelectedDate() {
        progressBar.visibility = View.VISIBLE
        tvNoData.visibility = View.GONE
        recyclerView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // 1. Fetch all teachers in the organization
                val teachersSnapshot = db.collection("organizations").document(organizationId!!)
                    .collection("teachers").get().await()
                val allTeachers = teachersSnapshot.toObjects<Teacher>()

                if (allTeachers.isEmpty()) {
                    tvNoData.text = "No teachers found in this organization."
                    tvNoData.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    adapter.updateData(emptyList())
                    return@launch
                }

                // 2. Fetch all attendance records for the selected date
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)
                val attendanceSnapshot = db.collection("organizations").document(organizationId!!)
                    .collection("teacherAttendance")
                    .whereEqualTo("date", dateStr)
                    .get().await()
                val attendanceRecords = attendanceSnapshot.toObjects<TeacherAttendanceRecord>()
                val attendanceMap = attendanceRecords.associateBy { it.teacherId }

                // 3. Combine the lists to create the final report
                val reportList = allTeachers.map { teacher ->
                    val status = attendanceMap[teacher.teacherId]?.status ?: "Not Marked"
                    TeacherWithAttendanceStatus(teacher, status)
                }.sortedBy { it.teacher.teacherName } // Sort alphabetically

                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.updateData(reportList)

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                tvNoData.text = "Error loading report."
                tvNoData.visibility = View.VISIBLE
                Log.e(TAG, "Error loading teacher attendance report", e)
                Toast.makeText(this@TeacherAttendanceReportActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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