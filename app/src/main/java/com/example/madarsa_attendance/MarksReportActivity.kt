package com.example.madarsa_attendance

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch

class MarksReportActivity : AppCompatActivity() {

    private lateinit var chipGroupScope: ChipGroup
    private lateinit var spinnerSelectClass: Spinner
    private lateinit var spinnerSelectExam: Spinner
    private lateinit var btnGenerateReport: MaterialButton

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var teachersList = listOf<Teacher>()
    private var examsList = listOf<Exam>()
    private var selectedTeacher: Teacher? = null
    private var selectedExam: Exam? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_marks_report)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupListeners()
        fetchTeachersAndExams()
    }

    private fun initializeViews() {
        chipGroupScope = findViewById(R.id.chip_group_scope)
        spinnerSelectClass = findViewById(R.id.spinner_select_class)
        spinnerSelectExam = findViewById(R.id.spinner_select_exam)
        btnGenerateReport = findViewById(R.id.btn_generate_report)
    }

    private fun setupToolbar() {
        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar_marks_report)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupListeners() {
        chipGroupScope.setOnCheckedChangeListener { _, checkedId ->
            spinnerSelectClass.visibility = if (checkedId == R.id.chip_scope_class) View.VISIBLE else View.GONE
        }
        btnGenerateReport.setOnClickListener { generateReport() }
    }

    private fun fetchTeachersAndExams() {
        // Fetch Teachers
        db.collection("organizations").document(organizationId!!).collection("teachers")
            .orderBy("teacherName").get()
            .addOnSuccessListener { snapshot ->
                teachersList = snapshot.toObjects(Teacher::class.java)
                val teacherNames = teachersList.map { it.teacherName }
                spinnerSelectClass.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, teacherNames)
            }

        // Fetch Exams
        db.collection("organizations").document(organizationId!!).collection("exams")
            .orderBy("name").get()
            .addOnSuccessListener { snapshot ->
                examsList = snapshot.toObjects(Exam::class.java)
                val examNames = examsList.map { it.name }
                spinnerSelectExam.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, examNames)
            }
    }

    private fun generateReport() {
        selectedTeacher = if (chipGroupScope.checkedChipId == R.id.chip_scope_class) {
            if (teachersList.isNotEmpty()) teachersList[spinnerSelectClass.selectedItemPosition] else null
        } else {
            null
        }

        selectedExam = if (examsList.isNotEmpty()) examsList[spinnerSelectExam.selectedItemPosition] else null

        if (chipGroupScope.checkedChipId == R.id.chip_scope_class && selectedTeacher == null) {
            Toast.makeText(this, "Please select a class.", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedExam == null) {
            Toast.makeText(this, "Please select an exam.", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Generating Report...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "loading")

        lifecycleScope.launch {
            val pdfUri = MarksReportGenerator.generateReport(this@MarksReportActivity, db, organizationId!!, selectedTeacher, selectedExam!!)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                loadingDialog.dismiss()
                if (pdfUri != null) {
                    StatusDialogFragment.newInstance(true, "Report Generated!").show(supportFragmentManager, "successDialog")
                    openPdf(pdfUri)
                } else {
                    StatusDialogFragment.newInstance(false, "No data found for selected criteria.").show(supportFragmentManager, "failureDialog")
                }
            }
        }
    }

    private fun openPdf(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No application found to open PDF files.", Toast.LENGTH_SHORT).show()
        }
    }
}