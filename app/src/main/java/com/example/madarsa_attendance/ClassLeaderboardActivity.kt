package com.example.madarsa_attendance // <<< YOUR PACKAGE NAME

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ClassLeaderboardActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "ClassLeaderboard"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var spinnerMonth: Spinner
    private lateinit var spinnerYear: Spinner
    private lateinit var recyclerViewLeaderboard: RecyclerView
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoDataLeaderboard: TextView

    private lateinit var db: FirebaseFirestore
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null

    private val leaderboardList = mutableListOf<LeaderboardItem>()

    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) // 0-indexed
    private var initialSpinnerSetupDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_leaderboard_with_filters)

        db = FirebaseFirestore.getInstance()
        currentTeacherId = intent.getStringExtra("TEACHER_ID")
        currentTeacherName = intent.getStringExtra("TEACHER_NAME")

        toolbar = findViewById(R.id.class_leaderboard_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.title = "${currentTeacherName ?: "Class"} Leaderboard"
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        spinnerMonth = findViewById(R.id.spinnerMonthLeaderboard)
        spinnerYear = findViewById(R.id.spinnerYearLeaderboard)
        recyclerViewLeaderboard = findViewById(R.id.recyclerViewLeaderboard)
        progressBar = findViewById(R.id.progressBarLeaderboard)
        tvNoDataLeaderboard = findViewById(R.id.tvNoDataLeaderboard)

        if (currentTeacherId == null) {
            Toast.makeText(this, "Teacher info missing.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        setupRecyclerView()
        setupSpinners()
    }

    private fun setupSpinners() {
        initialSpinnerSetupDone = false
        val staticSpinnerTextColor = ContextCompat.getColor(this, R.color.mono_palette_black)

        val months = SimpleDateFormat("MMMM", Locale.getDefault()).let { sdf ->
            (0..11).map {
                val cal = Calendar.getInstance(); cal.set(Calendar.MONTH, it); sdf.format(cal.time)
            }
        }
        val monthAdapter = ColorableSpinnerAdapter(this, months, staticSpinnerTextColor)
        spinnerMonth.adapter = monthAdapter
        spinnerMonth.setSelection(selectedMonth, false)

        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!initialSpinnerSetupDone) return
                if (selectedMonth != position) {
                    selectedMonth = position
                    loadLeaderboardData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val currentYearValue = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYearValue - 5..currentYearValue + 1).map { it.toString() }.reversed()
        val yearAdapter = ColorableSpinnerAdapter(this, years, staticSpinnerTextColor)
        spinnerYear.adapter = yearAdapter
        spinnerYear.setSelection(years.indexOf(selectedYear.toString()), false)

        spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!initialSpinnerSetupDone) return
                val year = years[position].toInt()
                if (selectedYear != year) {
                    selectedYear = year
                    loadLeaderboardData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerYear.post {
            initialSpinnerSetupDone = true
            loadLeaderboardData()
        }
    }

    private fun setupRecyclerView() {
        leaderboardAdapter = LeaderboardAdapter(leaderboardList)
        recyclerViewLeaderboard.layoutManager = LinearLayoutManager(this)
        recyclerViewLeaderboard.adapter = leaderboardAdapter
    }

    private fun loadLeaderboardData() {
        if (currentTeacherId == null) {
            Toast.makeText(this, "Teacher info missing.", Toast.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE
        tvNoDataLeaderboard.visibility = View.GONE
        recyclerViewLeaderboard.visibility = View.GONE

        val calendar = Calendar.getInstance(); calendar.set(selectedYear, selectedMonth, 1)
        val monthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        val firstDayOfMonth = "$monthYearStr-01"
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lastDayOfMonth = "$monthYearStr-${String.format(Locale.getDefault(), "%02d", lastDay)}"

        val studentsMap = mutableMapOf<String, String>()
        val studentPresentDays = mutableMapOf<String, Int>()
        val studentAbsentDays = mutableMapOf<String, Int>()
        val studentTotalMarkedDays = mutableMapOf<String, Int>()

        db.collection("students").whereEqualTo("teacherId", currentTeacherId).get()
            .addOnSuccessListener { studentsSnapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (studentsSnapshot.isEmpty) {
                    progressBar.visibility = View.GONE
                    tvNoDataLeaderboard.text = "No students in this class."
                    tvNoDataLeaderboard.visibility = View.VISIBLE
                    leaderboardAdapter.updateData(emptyList())
                    return@addOnSuccessListener
                }
                studentsSnapshot.forEach { doc ->
                    val studentId = doc.id
                    studentsMap[studentId] = doc.getString("studentName") ?: "N/A"
                    studentPresentDays[studentId] = 0; studentAbsentDays[studentId] = 0; studentTotalMarkedDays[studentId] = 0
                }

                db.collection("attendanceRecords")
                    .whereEqualTo("teacherId", currentTeacherId)
                    .whereGreaterThanOrEqualTo("date", firstDayOfMonth)
                    .whereLessThanOrEqualTo("date", lastDayOfMonth).get()
                    .addOnSuccessListener { attendanceRecordsSnapshot ->
                        if (isFinishing || isDestroyed) return@addOnSuccessListener
                        progressBar.visibility = View.GONE
                        val distinctDatesInRecords = mutableSetOf<String>()
                        attendanceRecordsSnapshot.forEach { recordDoc ->
                            distinctDatesInRecords.add(recordDoc.getString("date") ?: "")
                            val studentAttendances = recordDoc.get("studentAttendances") as? List<Map<String, Any>>
                            studentAttendances?.forEach { att ->
                                val studentId = att["studentId"] as? String; val status = att["status"] as? String
                                if (studentId != null && studentsMap.containsKey(studentId)) {
                                    studentTotalMarkedDays[studentId] = (studentTotalMarkedDays[studentId] ?: 0) + 1
                                    if (status == "Present") studentPresentDays[studentId] = (studentPresentDays[studentId] ?: 0) + 1
                                    else if (status == "Absent") studentAbsentDays[studentId] = (studentAbsentDays[studentId] ?: 0) + 1
                                }
                            }
                        }
                        val actualTotalSchoolDaysInMonthForClass = distinctDatesInRecords.size
                        processAndDisplayLeaderboard(studentsMap, studentPresentDays, studentAbsentDays, studentTotalMarkedDays, actualTotalSchoolDaysInMonthForClass)
                    }
                    .addOnFailureListener { e ->
                        if (isFinishing || isDestroyed) return@addOnFailureListener
                        progressBar.visibility = View.GONE
                        tvNoDataLeaderboard.text = "Error loading attendance data."
                        tvNoDataLeaderboard.visibility = View.VISIBLE
                    }
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                progressBar.visibility = View.GONE
                tvNoDataLeaderboard.text = "Error loading students."
                tvNoDataLeaderboard.visibility = View.VISIBLE
            }
    }

    private fun processAndDisplayLeaderboard(
        studentsMap: Map<String, String>, studentPresentDays: Map<String, Int>, studentAbsentDays: Map<String, Int>,
        studentTotalMarkedDays: Map<String, Int>, actualTotalSchoolDaysInMonthForClass: Int
    ) {
        if (isFinishing || isDestroyed) return
        leaderboardList.clear()
        studentsMap.forEach { (studentId, studentName) ->
            val presentDays = studentPresentDays[studentId] ?: 0
            val absentDays = studentAbsentDays[studentId] ?: 0
            val totalMarked = studentTotalMarkedDays[studentId] ?: 0
            val denominator = if (actualTotalSchoolDaysInMonthForClass > 0) actualTotalSchoolDaysInMonthForClass else 1
            val percentage = if (denominator > 0) (presentDays.toDouble() / denominator.toDouble()) * 100.0 else 0.0

            // =================================================================
            //  THE ONLY CHANGE IS ON THE NEXT LINE:
            //  We add 'currentTeacherName' as the last parameter.
            // =================================================================
            leaderboardList.add(LeaderboardItem(studentId, studentName, presentDays, absentDays, totalMarked, percentage, currentTeacherName ?: "N/A"))
        }
        leaderboardList.sortByDescending { it.attendancePercentage }

        if (leaderboardList.isEmpty() && studentsMap.isNotEmpty()){
            tvNoDataLeaderboard.text = "No attendance data for selected period."
            tvNoDataLeaderboard.visibility = View.VISIBLE
            recyclerViewLeaderboard.visibility = View.GONE
        } else if (studentsMap.isEmpty()){
            tvNoDataLeaderboard.text = "No students in this class."
            tvNoDataLeaderboard.visibility = View.VISIBLE
            recyclerViewLeaderboard.visibility = View.GONE
        } else if (leaderboardList.isNotEmpty()) {
            tvNoDataLeaderboard.visibility = View.GONE
            recyclerViewLeaderboard.visibility = View.VISIBLE
        } else {
            tvNoDataLeaderboard.text = "No data to display."
            tvNoDataLeaderboard.visibility = View.VISIBLE
            recyclerViewLeaderboard.visibility = View.GONE
        }
        leaderboardAdapter.updateData(leaderboardList)
        Log.d(TAG, "Leaderboard UI updated with ${leaderboardList.size} items.")
    }
}