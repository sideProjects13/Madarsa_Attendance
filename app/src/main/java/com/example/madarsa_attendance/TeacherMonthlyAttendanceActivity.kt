package com.example.madarsa_attendance

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TeacherMonthlyAttendanceActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "TeacherMonthlyAtt"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvTeacherNameHeader: TextView
    private lateinit var tvMonthYearHeader: TextView
    private lateinit var btnChangeMonthYear: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DailyTeacherAttendanceAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoData: TextView

    private lateinit var db: FirebaseFirestore
    private var teacherId: String? = null
    private var teacherName: String? = null
    private var targetYear: Int = 0
    private var targetMonth: Int = 0
    private var currentOrganizationId: String? = null

    private val dailyAttendanceList = mutableListOf<DailyAttendanceStatus>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_monthly_attendance)

        db = FirebaseFirestore.getInstance()
        teacherId = intent.getStringExtra("TEACHER_ID")
        teacherName = intent.getStringExtra("TEACHER_NAME")
        targetYear = intent.getIntExtra("TARGET_YEAR", Calendar.getInstance().get(Calendar.YEAR))
        targetMonth = intent.getIntExtra("TARGET_MONTH", Calendar.getInstance().get(Calendar.MONTH))
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)

        initializeViews()
        setupToolbar()
        setupRecyclerView()

        if (teacherId == null || currentOrganizationId == null) {
            Toast.makeText(this, "Required information missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvTeacherNameHeader.text = "Teacher: ${teacherName ?: "N/A"}"
        updateMonthYearHeader()
        loadMonthlyAttendance()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.teacher_monthly_attendance_toolbar)
        tvTeacherNameHeader = findViewById(R.id.tvTeacherNameMonthlyHeader)
        tvMonthYearHeader = findViewById(R.id.tvMonthYearHeader)
        btnChangeMonthYear = findViewById(R.id.btnChangeMonthYear)
        recyclerView = findViewById(R.id.recyclerViewDailyTeacherAttendance)
        progressBar = findViewById(R.id.progressBarMonthlyAttendance)
        tvNoData = findViewById(R.id.tvNoMonthlyAttendanceData)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        btnChangeMonthYear.setOnClickListener { showMonthYearPickerDialog() }
    }

    private fun setupRecyclerView() {
        adapter = DailyTeacherAttendanceAdapter(dailyAttendanceList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun updateMonthYearHeader() {
        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(
            Calendar.getInstance().apply { set(targetYear, targetMonth, 1) }.time
        )
        tvMonthYearHeader.text = "Record for: $monthName $targetYear"
    }

    private fun showMonthYearPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_month_year_picker, null)
        val monthSpinner = dialogView.findViewById<Spinner>(R.id.picker_month)
        val yearSpinner = dialogView.findViewById<Spinner>(R.id.picker_year)

        // Month Spinner Setup
        val months = SimpleDateFormat("MMMM", Locale.getDefault()).let { sdf ->
            (0..11).map {
                val cal = Calendar.getInstance().apply { set(Calendar.MONTH, it) }
                sdf.format(cal.time)
            }
        }
        monthSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        monthSpinner.setSelection(targetMonth)

        // Year Spinner Setup
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear - 5..currentYear).map { it.toString() }.reversed()
        yearSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        yearSpinner.setSelection(years.indexOf(targetYear.toString()))

        AlertDialog.Builder(this)
            .setTitle("Select Month and Year")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                targetMonth = monthSpinner.selectedItemPosition
                targetYear = yearSpinner.selectedItem.toString().toInt()
                updateMonthYearHeader()
                loadMonthlyAttendance()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadMonthlyAttendance() {
        if (teacherId == null || currentOrganizationId == null) return
        Log.d(TAG, "Loading attendance for Teacher: $teacherId, Year: $targetYear, Month: ${targetMonth + 1}")

        progressBar.visibility = View.VISIBLE
        tvNoData.visibility = View.GONE
        recyclerView.visibility = View.GONE

        val calendar = Calendar.getInstance().apply { set(targetYear, targetMonth, 1) }
        val monthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        val firstDayOfMonth = "$monthYearStr-01"
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lastDayOfMonth = "$monthYearStr-${String.format("%02d", lastDay)}"

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teacherAttendance")
            .whereEqualTo("teacherId", teacherId)
            .whereGreaterThanOrEqualTo("date", firstDayOfMonth)
            .whereLessThanOrEqualTo("date", lastDayOfMonth)
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { recordsSnapshot ->
                progressBar.visibility = View.GONE
                dailyAttendanceList.clear()

                val attendanceMap = recordsSnapshot.documents.associate { doc ->
                    (doc.getString("date") ?: "") to (doc.getString("status") ?: "Not Marked")
                }

                for (day in 1..lastDay) {
                    val dateStr = "$monthYearStr-${String.format("%02d", day)}"
                    val status = attendanceMap[dateStr] ?: "Not Marked"
                    dailyAttendanceList.add(DailyAttendanceStatus(dateStr, status))
                }

                if (dailyAttendanceList.isEmpty()) {
                    tvNoData.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvNoData.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
                adapter.updateData(dailyAttendanceList)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                tvNoData.text = "Error loading attendance."
                tvNoData.visibility = View.VISIBLE
                Log.e(TAG, "Error fetching teacher monthly attendance", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}