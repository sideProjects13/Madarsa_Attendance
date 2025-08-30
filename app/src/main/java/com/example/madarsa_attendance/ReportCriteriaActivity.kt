package com.example.madarsa_attendance

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch
import java.util.*

class ReportCriteriaActivity : AppCompatActivity() {

    private lateinit var spinnerTeacher: Spinner
    private lateinit var spinnerExam: Spinner
    private lateinit var btnGenerate: Button
    private lateinit var dateSection: LinearLayout // The date pickers

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var teacherList = mutableListOf<Teacher>()
    private var examList = mutableListOf<Exam>()
    private var selectedTeacher: Teacher? = null
    private var selectedExam: Exam? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_criteria)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Error: Organization ID not found.", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.title = "Generate Marks Report"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        spinnerTeacher = findViewById(R.id.spinner_teacher_filter)
        spinnerExam = findViewById(R.id.spinner_exam_filter) // You need to add this to your XML
        btnGenerate = findViewById(R.id.btn_generate_report)
        dateSection = findViewById(R.id.date_section) // You need to add this ID to your XML

        // Hide the date pickers as they are not needed for this report
        dateSection.visibility = View.GONE
        btnGenerate.text = "Generate Marks Report"

        loadTeachers()
        loadExams()

        btnGenerate.setOnClickListener { generateReport() }
    }

    private fun loadTeachers() {
        db.collection("organizations").document(organizationId!!)
            .collection("teachers").orderBy("teacherName").get()
            .addOnSuccessListener { documents ->
                teacherList.clear()
                teacherList.add(Teacher(teacherId = "ALL", teacherName = "All Classes / Organization"))
                teacherList.addAll(documents.toObjects())
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, teacherList.map { it.teacherName })
                spinnerTeacher.adapter = adapter
            }
        spinnerTeacher.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedTeacher = if (teacherList[pos].teacherId == "ALL") null else teacherList[pos]
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun loadExams() {
        db.collection("organizations").document(organizationId!!)
            .collection("exams").orderBy("name").get()
            .addOnSuccessListener { documents ->
                examList.clear()
                examList.addAll(documents.toObjects())
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, examList.map { it.name })
                spinnerExam.adapter = adapter
            }
        spinnerExam.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedExam = if (examList.isNotEmpty()) examList[pos] else null
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun generateReport() {
        if (selectedExam == null) {
            Toast.makeText(this, "Please select an exam.", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Generating Report...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "loading")

        lifecycleScope.launch {
            try {
                val pdfUri = MarksReportGenerator.generateReport(
                    this@ReportCriteriaActivity, db, organizationId!!, selectedTeacher, selectedExam!!
                )
                loadingDialog.dismiss()
                if (pdfUri != null) {
                    StatusDialogFragment.newInstance(true, "Report Generated!").show(supportFragmentManager, "successDialog")
                    openPdfFile(pdfUri)
                } else {
                    StatusDialogFragment.newInstance(false, "No data found for this exam/class.").show(supportFragmentManager, "failureDialog")
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                StatusDialogFragment.newInstance(false, "Report generation failed.").show(supportFragmentManager, "failureDialog")
            }
        }
    }

    private fun openPdfFile(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No PDF viewer app found.", Toast.LENGTH_SHORT).show()
        }
    }
}