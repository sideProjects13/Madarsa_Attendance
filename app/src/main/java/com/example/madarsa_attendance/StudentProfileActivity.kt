package com.example.madarsa_attendance

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

class StudentProfileActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STUDENT_ID = "extra_student_id"
    }

    private lateinit var viewModel: StudentProfileViewModel
    private var studentId: String? = null

    // Views
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        studentId = intent.getStringExtra(EXTRA_STUDENT_ID)
        if (studentId == null) {
            Toast.makeText(this, "Student ID is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Initialize ViewModel the standard, simple way
        viewModel = ViewModelProvider(this)[StudentProfileViewModel::class.java]

        initializeViews()
        setupObservers()

        // Fetch the data
        viewModel.loadStudentData(studentId!!)
    }

    private fun initializeViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_student_profile)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

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
    }

    private fun setupObservers() {
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
            contentLayout.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun populateStudentDetails(student: StudentDetailsItem) {
        // Set title
        supportActionBar?.title = student.studentName

        // Load image
        Glide.with(this)
            .load(student.profileImageUrl)
            .circleCrop()
            .placeholder(R.drawable.student)
            .error(R.drawable.student)
            .into(ivProfile)

        // Populate text fields
        tvStudentName.text = student.studentName
        tvTeacherName.text = "Class: ${student.teacherName}"
        tvRegNo.text = "Reg No: ${student.regNo ?: "N/A"}"

        tvParentInfo.text = "Parent: ${student.parentName}"
        tvContactInfo.text = "Mobile: ${student.parentMobileNumber}\nAlternate: ${student.alternateMobileNumber ?: "N/A"}"
        tvPersonalInfo.text = "Gender: ${student.gender ?: "N/A"}\nDOB: ${student.birthDate ?: "N/A"}"
        tvAcademicInfo.text = "Admission: ${student.admissionDate ?: "N/A"}\nFee: ₹${student.monthlyFee ?: "0.0"}"
    }

    private fun populateAttendanceSummary(summary: AttendanceSummary) {
        tvAttendancePresent.text = summary.presentDays.toString()
        tvAttendanceAbsent.text = summary.absentDays.toString()
        tvAttendancePercentage.text = String.format("%.1f%%", summary.percentage)
    }
}