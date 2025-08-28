package com.example.madarsa_attendance

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText // NEW IMPORT
import com.google.android.material.textfield.TextInputLayout // NEW IMPORT
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch

class ReportGeneratorActivity : AppCompatActivity() {

    private lateinit var spinnerTeacherFilter: AutoCompleteTextView
    private lateinit var fieldsContainer: GridLayout
    private lateinit var btnGeneratePdf: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var previewHeaderContainer: LinearLayout
    private lateinit var rvReportPreview: RecyclerView
    private lateinit var tvNoPreviewData: TextView
    private lateinit var toggleOrientation: MaterialButtonToggleGroup

    // NEW: Report Name fields
    private lateinit var tilReportName: TextInputLayout
    private lateinit var etReportName: TextInputEditText

    private lateinit var reportAdapter: DynamicReportAdapter
    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var teacherList = mutableListOf<Teacher>()
    private var studentPreviewList: List<StudentDetailsItem> = listOf()
    private var selectedTeacherId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_report_generator)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization data missing. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupCheckboxListeners()
        loadTeachersIntoFilter()

        btnGeneratePdf.setOnClickListener {
            generateReport()
        }
    }

    private fun initializeViews() {
        spinnerTeacherFilter = findViewById(R.id.spinner_teacher_filter)
        fieldsContainer = findViewById(R.id.fields_checkbox_container)
        btnGeneratePdf = findViewById(R.id.btn_generate_pdf)
        progressBar = findViewById(R.id.progress_bar_generate)
        previewHeaderContainer = findViewById(R.id.preview_header_container)
        rvReportPreview = findViewById(R.id.rv_report_preview)
        tvNoPreviewData = findViewById(R.id.tv_no_preview_data)
        toggleOrientation = findViewById(R.id.toggle_orientation)
        // NEW: Initialize Report Name fields
        tilReportName = findViewById(R.id.til_report_name)
        etReportName = findViewById(R.id.et_report_name)
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
        reportAdapter = DynamicReportAdapter(emptyList(), getSelectedColumns())
        rvReportPreview.layoutManager = LinearLayoutManager(this)
        rvReportPreview.adapter = reportAdapter
    }

    private fun setupCheckboxListeners() {
        fieldsContainer.children.forEach { view ->
            if (view is CheckBox) {
                view.setOnCheckedChangeListener { _, _ ->
                    updatePreview()
                }
            }
        }
    }

    private fun loadTeachersIntoFilter() {
        if (organizationId == null) return

        db.collection("organizations").document(organizationId!!)
            .collection("teachers")
            .orderBy("teacherName")
            .get()
            .addOnSuccessListener { documents ->
                teacherList.clear()
                teacherList.add(0, Teacher(teacherId = "ALL", teacherName = "All Students"))
                teacherList.addAll(documents.toObjects())

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teacherList.map { it.teacherName })
                spinnerTeacherFilter.setAdapter(adapter)

                spinnerTeacherFilter.setText(teacherList[0].teacherName, false)
                selectedTeacherId = null
                loadStudentPreviewData()

                spinnerTeacherFilter.setOnItemClickListener { _, _, position, _ ->
                    val selectedTeacher = teacherList[position]
                    selectedTeacherId = if (selectedTeacher.teacherId == "ALL") null else selectedTeacher.teacherId
                    loadStudentPreviewData()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading teachers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadStudentPreviewData() {
        if (organizationId == null) return
        progressBar.visibility = View.VISIBLE
        tvNoPreviewData.visibility = View.GONE
        rvReportPreview.visibility = View.GONE

        var query: Query = db.collection("organizations").document(organizationId!!)
            .collection("students")

        if (selectedTeacherId != null) {
            query = query.whereEqualTo("teacherId", selectedTeacherId)
        }

        query.orderBy("studentName").limit(5).get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                studentPreviewList = documents.toObjects()
                if (studentPreviewList.isEmpty()) {
                    tvNoPreviewData.visibility = View.VISIBLE
                    rvReportPreview.visibility = View.GONE
                } else {
                    tvNoPreviewData.visibility = View.GONE
                    rvReportPreview.visibility = View.VISIBLE
                }
                updatePreview()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                tvNoPreviewData.visibility = View.VISIBLE
                Toast.makeText(this, "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getSelectedColumns(): List<ReportColumn> {
        val selectedColumns = mutableListOf<ReportColumn>()
        fieldsContainer.children.forEach { view ->
            if (view is CheckBox && view.isChecked) {
                ReportColumn.fromId(view.id)?.let {
                    selectedColumns.add(it)
                }
            }
        }
        return selectedColumns
    }

    private fun updatePreview() {
        val selectedColumns = getSelectedColumns()

        previewHeaderContainer.removeAllViews()
        for (column in selectedColumns) {
            val headerView = LayoutInflater.from(this)
                .inflate(R.layout.item_report_header, previewHeaderContainer, false) as TextView
            headerView.text = column.title
            previewHeaderContainer.addView(headerView)
        }

        reportAdapter.updateColumns(selectedColumns)
        reportAdapter.updateData(studentPreviewList)
    }

    private fun generateReport() {
        if (organizationId == null) return
        setInputsEnabled(false)

        val selectedColumns = getSelectedColumns()
        if (selectedColumns.isEmpty()) {
            // --- CHANGE ---
            StatusDialogFragment.newInstance(false, "Please select at least one column.").show(supportFragmentManager, "failureDialog")
            setInputsEnabled(true)
            return
        }

        val selectedOrientation = if (toggleOrientation.checkedButtonId == R.id.btn_landscape) {
            PageOrientation.LANDSCAPE
        } else {
            PageOrientation.PORTRAIT
        }

        var query: Query = db.collection("organizations").document(organizationId!!)
            .collection("students")

        if (selectedTeacherId != null) {
            query = query.whereEqualTo("teacherId", selectedTeacherId)
        }

        query.orderBy("studentName").get()
            .addOnSuccessListener { documents ->
                val fullStudentList = documents.toObjects<StudentDetailsItem>()
                if (fullStudentList.isEmpty()) {
                    StatusDialogFragment.newInstance(false, "No students found for this filter.").show(supportFragmentManager, "failureDialog")
                    setInputsEnabled(true)
                    return@addOnSuccessListener
                }

                lifecycleScope.launch {
                    val pdfGenerator = StudentPdfGenerator(this@ReportGeneratorActivity)
                    val reportName = etReportName.text.toString().trim()

                    val pdfUri = pdfGenerator.generatePdf(
                        fullStudentList,
                        selectedColumns,
                        selectedOrientation,
                        reportName
                    )

                    runOnUiThread {
                        setInputsEnabled(true)
                        if (pdfUri != null) {
                            // --- CHANGE ---
                            StatusDialogFragment.newInstance(true, "Report Generated!").show(supportFragmentManager, "successDialog")
                            openPdfFile(pdfUri)
                        } else {
                            // --- CHANGE ---
                            StatusDialogFragment.newInstance(false, "Failed to Create PDF").show(supportFragmentManager, "failureDialog")
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                setInputsEnabled(true)
                // --- CHANGE ---
                StatusDialogFragment.newInstance(false, "Error Fetching Data").show(supportFragmentManager, "failureDialog")
                Log.e("ReportGenerator", "Error fetching student data for report", e)
            }
    }

    private fun openPdfFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No application found to open PDF files.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
        btnGeneratePdf.isEnabled = enabled
        spinnerTeacherFilter.isEnabled = enabled
        toggleOrientation.isEnabled = enabled
        fieldsContainer.children.forEach { if (it is CheckBox) it.isEnabled = enabled }
        // NEW: Enable/disable report name field
        etReportName.isEnabled = enabled
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}