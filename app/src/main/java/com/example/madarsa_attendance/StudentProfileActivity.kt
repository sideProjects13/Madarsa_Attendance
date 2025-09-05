package com.example.madarsa_attendance

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

// --- IMPLEMENT THE NEW ADAPTER INTERFACE ---
class StudentProfileActivity : AppCompatActivity(), ExamHistoryAdapter.OnExamHistoryInteractionListener {

    companion object {
        const val EXTRA_STUDENT_ID = "extra_student_id"
    }

    private lateinit var viewModel: StudentProfileViewModel
    private var studentId: String? = null

    // --- Existing Views (Unchanged) ---
    private lateinit var ivProfile: ImageView
    private lateinit var tvStudentName: TextView
    private lateinit var tvTeacherName: TextView
    private lateinit var tvRegNo: TextView
    private lateinit var tvParentInfo: TextView
    private lateinit var tvContactInfo: TextView
    private lateinit var tvPersonalInfo: TextView
    private lateinit var tvAcademicInfo: TextView
    private lateinit var tvAttendancePresent: TextView
    private lateinit var tvAttendanceAbsent: TextView
    private lateinit var tvAttendancePercentage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var contentLayout: MaterialCardView
    private lateinit var ivCopyName: ImageView
    private lateinit var ivCopyRegNo: ImageView
    private lateinit var ivCopyParentInfo: ImageView
    private lateinit var ivCopyContactInfo: ImageView

    // --- NEW VIEWS AND ADAPTERS FOR HISTORY ---
    private lateinit var rvClassHistory: RecyclerView
    private lateinit var tvNoClassHistory: TextView
    private lateinit var classHistoryAdapter: ClassHistoryAdapter

    private lateinit var rvExamHistory: RecyclerView
    private lateinit var tvNoExamHistory: TextView
    private lateinit var examHistoryAdapter: ExamHistoryAdapter
    // --- END OF NEW VIEWS ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        studentId = intent.getStringExtra(EXTRA_STUDENT_ID)
        if (studentId == null) {
            Toast.makeText(this, "Student ID is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[StudentProfileViewModel::class.java]

        initializeViews()
        setupRecyclerViews() // New setup function
        setupObservers()

        viewModel.loadStudentData(studentId!!)
    }

    private fun initializeViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_student_profile)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Initialization of all existing views is unchanged
        ivProfile = findViewById(R.id.iv_student_profile_pic)
        tvStudentName = findViewById(R.id.tv_student_profile_name)
        tvTeacherName = findViewById(R.id.tv_student_profile_teacher)
        tvRegNo = findViewById(R.id.tv_student_profile_regno)
        tvParentInfo = findViewById(R.id.tv_parent_info)
        tvContactInfo = findViewById(R.id.tv_contact_info)
        tvPersonalInfo = findViewById(R.id.tv_personal_info)
        tvAcademicInfo = findViewById(R.id.tv_academic_info)
        tvAttendancePresent = findViewById(R.id.tv_attendance_present)
        tvAttendanceAbsent = findViewById(R.id.tv_attendance_absent)
        tvAttendancePercentage = findViewById(R.id.tv_attendance_percentage)
        progressBar = findViewById(R.id.progressBar_student_profile)
        contentLayout = findViewById(R.id.card_student_profile_content)
        ivCopyName = findViewById(R.id.iv_copy_name)
        ivCopyRegNo = findViewById(R.id.iv_copy_reg_no)
        ivCopyParentInfo = findViewById(R.id.iv_copy_parent_info)
        ivCopyContactInfo = findViewById(R.id.iv_copy_contact_info)

        // --- NEW: Initialize history views ---
        rvClassHistory = findViewById(R.id.rv_class_history)
        tvNoClassHistory = findViewById(R.id.tv_no_class_history)
        rvExamHistory = findViewById(R.id.rv_exam_history)
        tvNoExamHistory = findViewById(R.id.tv_no_exam_history)
        // --- END OF NEW INITIALIZATION ---
    }

    // --- NEW: Function to setup both RecyclerViews ---
    private fun setupRecyclerViews() {
        classHistoryAdapter = ClassHistoryAdapter(emptyList())
        rvClassHistory.layoutManager = LinearLayoutManager(this)
        rvClassHistory.adapter = classHistoryAdapter

        examHistoryAdapter = ExamHistoryAdapter(emptyList(), this)
        rvExamHistory.layoutManager = LinearLayoutManager(this)
        rvExamHistory.adapter = examHistoryAdapter
    }
    // --- END OF NEW FUNCTION ---

    private fun setupObservers() {
        // Existing observers (unchanged)
        viewModel.student.observe(this) { student ->
            if (student != null) {
                populateStudentDetails(student)
            } else {
                Toast.makeText(this, "Could not find student data.", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.attendanceSummary.observe(this) { summary ->
            if (summary != null) {
                populateAttendanceSummary(summary)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // This logic is updated to hide all cards during load
            val contentVisibility = if (isLoading) View.INVISIBLE else View.VISIBLE
            findViewById<View>(R.id.card_student_profile_content).visibility = contentVisibility
            findViewById<View>(R.id.card_attendance_summary).visibility = contentVisibility
            findViewById<View>(R.id.card_class_history).visibility = contentVisibility
            findViewById<View>(R.id.card_exam_history).visibility = contentVisibility
        }

        // --- NEW: Observers for history data ---
        viewModel.classHistory.observe(this) { history ->
            if (history.isEmpty()) {
                tvNoClassHistory.visibility = View.VISIBLE
                rvClassHistory.visibility = View.GONE
            } else {
                tvNoClassHistory.visibility = View.GONE
                rvClassHistory.visibility = View.VISIBLE
                classHistoryAdapter.updateData(history)
            }
        }

        viewModel.examHistory.observe(this) { history ->
            if (history.isEmpty()) {
                tvNoExamHistory.visibility = View.VISIBLE
                rvExamHistory.visibility = View.GONE
            } else {
                tvNoExamHistory.visibility = View.GONE
                rvExamHistory.visibility = View.VISIBLE
                examHistoryAdapter.updateData(history)
            }
        }
        // --- END OF NEW OBSERVERS ---
    }

    // --- NEW: Implement the listener from ExamHistoryAdapter ---
    override fun onGenerateReportClick(item: ExamHistoryItem) {
        Toast.makeText(this, "Generating report for ${item.examName}...", Toast.LENGTH_SHORT).show()
        viewModel.generateHistoricalReport(item)
    }
    // --- END OF NEW IMPLEMENTATION ---

    // --- Existing functions (Unchanged) ---
    private fun populateStudentDetails(student: StudentDetailsItem) {
        supportActionBar?.title = student.studentName

        Glide.with(this)
            .load(student.profileImageUrl)
            .circleCrop()
            .placeholder(R.drawable.student)
            .error(R.drawable.student)
            .into(ivProfile)

        tvStudentName.text = student.studentName
        tvTeacherName.text = "Class: ${student.teacherName}"
        tvRegNo.text = "Reg No: ${student.regNo ?: "N/A"}"
        tvParentInfo.text = "Parent: ${student.parentName}"
        tvContactInfo.text = "Mobile: ${student.parentMobileNumber}\nAlternate: ${student.alternateMobileNumber ?: "N/A"}"
        tvPersonalInfo.text = "Gender: ${student.gender ?: "N/A"}\nDOB: ${student.birthDate ?: "N/A"}"
        tvAcademicInfo.text = "Admission: ${student.admissionDate ?: "N/A"}\nFee: ₹${student.monthlyFee ?: "0.0"}"

        setupCopyAction(ivCopyName, "Student Name", student.studentName)
        setupCopyAction(ivCopyRegNo, "Registration No.", student.regNo ?: "")
        setupCopyAction(ivCopyParentInfo, "Parent Name", student.parentName ?: "")
        setupCopyAction(ivCopyContactInfo, "Contact Info", "Mobile: ${student.parentMobileNumber}, Alternate: ${student.alternateMobileNumber ?: "N/A"}")
    }

    private fun populateAttendanceSummary(summary: AttendanceSummary) {
        tvAttendancePresent.text = summary.presentDays.toString()
        tvAttendanceAbsent.text = summary.absentDays.toString()
        tvAttendancePercentage.text = String.format("%.1f%%", summary.percentage)
    }

    private fun setupCopyAction(imageView: ImageView, label: String, textToCopy: String) {
        if (textToCopy.isNotBlank()) {
            imageView.visibility = View.VISIBLE
            imageView.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        } else {
            imageView.visibility = View.GONE
        }
    }
}