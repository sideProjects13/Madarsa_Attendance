package com.example.madarsa_attendance

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StudentMonthlyAttendanceActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "StudentMonthlyAtt"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvStudentNameHeader: TextView
    private lateinit var tvMonthYearHeader: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DailyAttendanceAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoData: TextView

    private lateinit var db: FirebaseFirestore
    private var studentId: String? = null
    private var studentName: String? = null
    private var teacherId: String? = null
    private var targetYear: Int = 0
    private var targetMonth: Int = 0
    private var currentOrganizationId: String? = null // NEW: Organization ID

    private val dailyAttendanceList = mutableListOf<DailyAttendanceStatus>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_monthly_attendance)

        db = FirebaseFirestore.getInstance()
        studentId = intent.getStringExtra("STUDENT_ID")
        studentName = intent.getStringExtra("STUDENT_NAME")
        teacherId = intent.getStringExtra("TEACHER_ID")
        targetYear = intent.getIntExtra("TARGET_YEAR", Calendar.getInstance().get(Calendar.YEAR))
        targetMonth = intent.getIntExtra("TARGET_MONTH", Calendar.getInstance().get(Calendar.MONTH))
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this) // NEW: Get organization ID


        toolbar = findViewById(R.id.student_monthly_attendance_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.title = "Monthly Record"
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tvStudentNameHeader = findViewById(R.id.tvStudentNameMonthlyHeader)
        tvMonthYearHeader = findViewById(R.id.tvMonthYearHeader)
        recyclerView = findViewById(R.id.recyclerViewDailyAttendance)
        progressBar = findViewById(R.id.progressBarMonthlyAttendance)
        tvNoData = findViewById(R.id.tvNoMonthlyAttendanceData)

        if (studentId == null || teacherId == null) {
            Toast.makeText(this, "Required information missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (currentOrganizationId == null) { // NEW: Check organization ID
            Toast.makeText(this, "Organization information missing. Please log in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }


        tvStudentNameHeader.text = "Student: ${studentName ?: "N/A"}"
        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(
            Calendar.getInstance().apply { set(targetYear, targetMonth, 1) }.time
        )
        tvMonthYearHeader.text = "Record for: $monthName $targetYear"

        setupRecyclerView()
        loadMonthlyAttendance()
    }

    private fun setupRecyclerView() {
        adapter = DailyAttendanceAdapter(dailyAttendanceList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadMonthlyAttendance() {
        if (studentId == null || teacherId == null || currentOrganizationId == null) return // NEW: Check organization ID
        Log.d(TAG, "Loading monthly attendance for Student: $studentId, Teacher: $teacherId, Year: $targetYear, Month: ${targetMonth + 1}, Org ID: $currentOrganizationId")

        progressBar.visibility = View.VISIBLE
        tvNoData.visibility = View.GONE
        recyclerView.visibility = View.GONE

        val calendar = Calendar.getInstance()
        calendar.set(targetYear, targetMonth, 1)
        val monthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        val firstDayOfMonth = "$monthYearStr-01"
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lastDayOfMonth = "$monthYearStr-${String.format(Locale.getDefault(), "%02d", lastDay)}"

        // NEW: Scope query to the organization
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("attendanceRecords")
            .whereEqualTo("teacherId", teacherId)
            .whereGreaterThanOrEqualTo("date", firstDayOfMonth)
            .whereLessThanOrEqualTo("date", lastDayOfMonth)
            .orderBy("date", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { recordsSnapshot ->
                progressBar.visibility = View.GONE
                dailyAttendanceList.clear()

                if (recordsSnapshot.isEmpty) {
                    Log.d(TAG, "No attendance records found for this teacher in $monthYearStr for Org ID: $currentOrganizationId")
                }

                val attendanceMap = mutableMapOf<String, String>()
                recordsSnapshot.forEach { doc ->
                    val date = doc.getString("date")
                    val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>
                    studentAttendances?.find { it["studentId"] == studentId }?.let { studentEntry ->
                        val status = studentEntry["status"] as? String
                        if (date != null && status != null) {
                            attendanceMap[date] = status
                        }
                    }
                }

                val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (day in 1..daysInMonth) {
                    val dateStr = "$monthYearStr-${String.format(Locale.getDefault(), "%02d", day)}"
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
                Log.e(TAG, "Error fetching monthly attendance for Org ID: $currentOrganizationId", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}