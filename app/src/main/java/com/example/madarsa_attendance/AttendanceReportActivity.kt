package com.example.madarsa_attendance

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AttendanceReportActivity : AppCompatActivity() {

    private lateinit var chipGroupScope: ChipGroup
    private lateinit var spinnerSelectClass: Spinner
    private lateinit var chipGroupDateType: ChipGroup
    private lateinit var layoutMonthSelection: LinearLayout
    private lateinit var spinnerSelectMonth: Spinner
    private lateinit var spinnerSelectYear: Spinner
    private lateinit var layoutRangeSelection: LinearLayout
    private lateinit var btnSelectStartDate: Button
    private lateinit var btnSelectEndDate: Button
    private lateinit var btnGenerateReport: Button

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var teachersList = listOf<Teacher>()
    private var selectedTeacher: Teacher? = null
    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_report)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupSpinners()
        setupListeners()
        fetchTeachers()
    }

    private fun initializeViews() {
        chipGroupScope = findViewById(R.id.chip_group_scope)
        spinnerSelectClass = findViewById(R.id.spinner_select_class)
        chipGroupDateType = findViewById(R.id.chip_group_date_type)
        layoutMonthSelection = findViewById(R.id.layout_month_selection)
        spinnerSelectMonth = findViewById(R.id.spinner_select_month)
        spinnerSelectYear = findViewById(R.id.spinner_select_year)
        layoutRangeSelection = findViewById(R.id.layout_range_selection)
        btnSelectStartDate = findViewById(R.id.btn_select_start_date)
        btnSelectEndDate = findViewById(R.id.btn_select_end_date)
        btnGenerateReport = findViewById(R.id.btn_generate_report)
    }

    private fun setupToolbar() {
        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar_attendance_report)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupSpinners() {
        val months = (0..11).map {
            val cal = Calendar.getInstance()
            cal.set(Calendar.MONTH, it)
            SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
        }
        spinnerSelectMonth.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)
        spinnerSelectMonth.setSelection(Calendar.getInstance().get(Calendar.MONTH))

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear - 5..currentYear).map { it.toString() }.reversed()
        spinnerSelectYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
    }

    private fun setupListeners() {
        chipGroupScope.setOnCheckedChangeListener { _, checkedId ->
            spinnerSelectClass.visibility = if (checkedId == R.id.chip_scope_class) View.VISIBLE else View.GONE
        }
        chipGroupDateType.setOnCheckedChangeListener { _, checkedId ->
            layoutMonthSelection.visibility = if (checkedId == R.id.chip_date_month) View.VISIBLE else View.GONE
            layoutRangeSelection.visibility = if (checkedId == R.id.chip_date_range) View.VISIBLE else View.GONE
        }
        btnSelectStartDate.setOnClickListener { showDatePicker(isStartDate = true) }
        btnSelectEndDate.setOnClickListener { showDatePicker(isStartDate = false) }
        btnGenerateReport.setOnClickListener { generateReport() }

        btnSelectStartDate.text = "Start Date: ${dateFormat.format(startDate.time)}"
        btnSelectEndDate.text = "End Date: ${dateFormat.format(endDate.time)}"
    }

    private fun fetchTeachers() {
        db.collection("organizations").document(organizationId!!).collection("teachers")
            .orderBy("teacherName").get()
            .addOnSuccessListener { snapshot ->
                teachersList = snapshot.toObjects(Teacher::class.java)
                val teacherNames = teachersList.map { it.teacherName }
                spinnerSelectClass.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, teacherNames)
            }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = if (isStartDate) startDate else endDate
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            if (isStartDate) {
                btnSelectStartDate.text = "Start Date: ${dateFormat.format(calendar.time)}"
            } else {
                btnSelectEndDate.text = "End Date: ${dateFormat.format(calendar.time)}"
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun generateReport() {
        selectedTeacher = if (chipGroupScope.checkedChipId == R.id.chip_scope_class) {
            if (teachersList.isNotEmpty()) teachersList[spinnerSelectClass.selectedItemPosition] else null
        } else {
            null
        }

        if (chipGroupScope.checkedChipId == R.id.chip_scope_class && selectedTeacher == null) {
            Toast.makeText(this, "Please select a class.", Toast.LENGTH_SHORT).show()
            return
        }

        val reportStartDate: Date
        val reportEndDate: Date

        if (chipGroupDateType.checkedChipId == R.id.chip_date_month) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, spinnerSelectYear.selectedItem.toString().toInt())
            cal.set(Calendar.MONTH, spinnerSelectMonth.selectedItemPosition)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            reportStartDate = cal.time
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            reportEndDate = cal.time
        } else {
            reportStartDate = startDate.time
            reportEndDate = endDate.time
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Generating Report...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "loading")

        lifecycleScope.launch {
            val pdfUri = AttendanceReportGenerator.generateReport(this@AttendanceReportActivity, db, organizationId!!, selectedTeacher, reportStartDate, reportEndDate)
            loadingDialog.dismiss()
            if (pdfUri != null) {
                StatusDialogFragment.newInstance(true, "Report Generated!").show(supportFragmentManager, "successDialog")
                openPdf(pdfUri)
            } else {
                StatusDialogFragment.newInstance(false, "No data found for selected criteria.").show(supportFragmentManager, "failureDialog")
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