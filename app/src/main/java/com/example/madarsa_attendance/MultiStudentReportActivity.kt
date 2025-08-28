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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView // NEW IMPORT
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
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.Dispatchers // NEW IMPORT
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext // NEW IMPORT


class MultiStudentReportActivity : AppCompatActivity() {

    private val TAG = "MultiStudentReportActivity"

    // UI elements
    private lateinit var spinnerTeacherClassFilter: AutoCompleteTextView
    private lateinit var rvStudentSelection: RecyclerView
    private lateinit var tvNoStudents: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var searchViewStudents: SearchView // NEW
    private lateinit var fieldsContainer: GridLayout
    private lateinit var toggleOrientation: MaterialButtonToggleGroup
    private lateinit var etReportName: TextInputEditText
    private lateinit var btnGeneratePdf: Button

    // Data
    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var teachers = mutableListOf<Teacher>() // Includes "All Students" as a pseudo-teacher
    private lateinit var studentSelectionAdapter: MultiStudentSelectionAdapter

    // --- NEW: Master list of all selectable students (maintains selection state) ---
    private val _masterSelectableStudents = mutableListOf<MultiStudentSelectionAdapter.SelectableStudent>()
    private var currentTeacherFilterId: String? = null // Null means "All Students"
    private var currentSearchQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_multi_student_report)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization data missing. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupStudentSelectionRecyclerView()
        setupClassTeacherFilter()
        setupSelectionButtons()
        setupSearchView() // NEW: Setup Search View
        setupCheckboxListeners()

        btnGeneratePdf.setOnClickListener {
            generateReport()
        }

        // --- NEW: Load all students initially and then apply filters/search ---
        loadAllOrganizationStudents()
    }

    private fun initializeViews() {
        spinnerTeacherClassFilter = findViewById(R.id.spinner_teacher_class_filter)
        rvStudentSelection = findViewById(R.id.rv_student_selection)
        tvNoStudents = findViewById(R.id.tv_no_students_multi_report)
        progressBar = findViewById(R.id.progress_bar_multi_report)
        btnSelectAll = findViewById(R.id.btn_select_all_students)
        btnDeselectAll = findViewById(R.id.btn_deselect_all_students)
        searchViewStudents = findViewById(R.id.search_view_multi_report) // NEW
        fieldsContainer = findViewById(R.id.fields_checkbox_container_multi_report)
        toggleOrientation = findViewById(R.id.toggle_orientation_multi_report)
        etReportName = findViewById(R.id.et_report_name_multi_report)
        btnGeneratePdf = findViewById(R.id.btn_generate_pdf_multi_report)

        // Ensure orientation buttons have default IDs if not explicitly in XML
        findViewById<Button>(R.id.btn_portrait_multi_report).id // Ensure IDs are recognized
        findViewById<Button>(R.id.btn_landscape_multi_report).id
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar_multi_report)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Custom Student Info Report"

        val appBarLayout: AppBarLayout = findViewById(R.id.app_bar_layout_multi_report)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { _, insets ->
            appBarLayout.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
    }

    private fun setupStudentSelectionRecyclerView() {
        // --- MODIFIED: Pass callback to adapter ---
        studentSelectionAdapter = MultiStudentSelectionAdapter { student, isSelected ->
            // Find the student in the master list and update their selection status
            val index = _masterSelectableStudents.indexOfFirst { it.student.id == student.id }
            if (index != -1) {
                _masterSelectableStudents[index].isSelected = isSelected
            }
        }
        rvStudentSelection.layoutManager = LinearLayoutManager(this)
        rvStudentSelection.adapter = studentSelectionAdapter
    }

    // --- NEW: Setup Search View ---
    private fun setupSearchView() {
        searchViewStudents.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query
                applyFiltersAndSearch() // Re-apply all filters including search
                searchViewStudents.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText
                applyFiltersAndSearch() // Re-apply all filters including search dynamically
                return true
            }
        })
    }

    private fun setupClassTeacherFilter() {
        if (organizationId == null) return

        db.collection("organizations").document(organizationId!!)
            .collection("teachers")
            .orderBy("teacherName")
            .get()
            .addOnSuccessListener { documents ->
                teachers.clear()
                teachers.add(0, Teacher(teacherId = "ALL", teacherName = "All Students"))
                teachers.addAll(documents.toObjects())

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teachers.map { it.teacherName })
                spinnerTeacherClassFilter.setAdapter(adapter)

                // Initialize with "All Students" and trigger initial load via loadAllOrganizationStudents()
                spinnerTeacherClassFilter.setText(teachers[0].teacherName, false)
                currentTeacherFilterId = null // Default filter is 'All Students'

                spinnerTeacherClassFilter.setOnItemClickListener { _, _, position, _ ->
                    val selectedTeacher = teachers[position]
                    currentTeacherFilterId = if (selectedTeacher.teacherId == "ALL") null else selectedTeacher.teacherId
                    applyFiltersAndSearch() // Re-apply filters
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading classes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- NEW: Load all students ONCE and apply initial filters ---
    private fun loadAllOrganizationStudents() {
        if (organizationId == null) return
        progressBar.visibility = View.VISIBLE
        tvNoStudents.visibility = View.GONE
        rvStudentSelection.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val studentDetails = db.collection("organizations").document(organizationId!!)
                    .collection("students").whereEqualTo("isActive", true)
                    .orderBy("studentName")
                    .get().await().toObjects<StudentDetailsItem>()

                _masterSelectableStudents.clear()
                _masterSelectableStudents.addAll(studentDetails.map { MultiStudentSelectionAdapter.SelectableStudent(it, false) })

                withContext(Dispatchers.Main) {
                    applyFiltersAndSearch() // Apply initial filters (which are currently none)
                    progressBar.visibility = View.GONE
                    if (_masterSelectableStudents.isEmpty()) {
                        tvNoStudents.visibility = View.VISIBLE
                        rvStudentSelection.visibility = View.GONE
                    } else {
                        tvNoStudents.visibility = View.GONE
                        rvStudentSelection.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading all organization students", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvNoStudents.text = "Error loading students: ${e.message}"
                    tvNoStudents.visibility = View.VISIBLE
                    rvStudentSelection.visibility = View.GONE
                }
            }
        }
    }

    // --- NEW: Function to apply all active filters and update RecyclerView ---
    private fun applyFiltersAndSearch() {
        if (organizationId == null) return

        val filteredList = _masterSelectableStudents.filter { selectableStudent ->
            // Filter by teacher/class
            val matchesTeacherFilter = currentTeacherFilterId == null || selectableStudent.student.teacherId == currentTeacherFilterId

            // Filter by search query
            val matchesSearchQuery = currentSearchQuery.isNullOrBlank() ||
                    selectableStudent.student.studentName.contains(currentSearchQuery!!, ignoreCase = true) ||
                    (selectableStudent.student.regNo?.contains(currentSearchQuery!!, ignoreCase = true) == true) ||
                    (selectableStudent.student.parentName?.contains(currentSearchQuery!!, ignoreCase = true) == true)


            matchesTeacherFilter && matchesSearchQuery
        }

        studentSelectionAdapter.submitList(filteredList)

        // Update visibility of no data text based on filtered list size
        if (filteredList.isEmpty()) {
            tvNoStudents.text = if (currentSearchQuery.isNullOrBlank() && currentTeacherFilterId == null) {
                "No students found in the organization."
            } else if (!currentSearchQuery.isNullOrBlank() && currentTeacherFilterId != null) {
                "No students match '${currentSearchQuery}' in the selected class."
            } else if (!currentSearchQuery.isNullOrBlank()) {
                "No students match '${currentSearchQuery}'."
            }
            else { // only currentTeacherFilterId != null
                "No students in the selected class."
            }
            tvNoStudents.visibility = View.VISIBLE
            rvStudentSelection.visibility = View.GONE
        } else {
            tvNoStudents.visibility = View.GONE
            rvStudentSelection.visibility = View.VISIBLE
        }
    }


    private fun setupSelectionButtons() {
        btnSelectAll.setOnClickListener {
            studentSelectionAdapter.selectAllVisibleStudents() // Select only currently visible students
        }
        btnDeselectAll.setOnClickListener {
            studentSelectionAdapter.deselectAllVisibleStudents() // Deselect only currently visible students
        }
    }

    private fun setupCheckboxListeners() {
        fieldsContainer.children.forEach { view ->
            if (view is CheckBox) {
                view.setOnCheckedChangeListener { _, _ ->
                    // No direct preview needed for this report type, but useful for logic
                }
            }
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

    private fun generateReport() {
        val selectedStudentsDetails = _masterSelectableStudents.filter { it.isSelected }.map { it.student }

        if (selectedStudentsDetails.isEmpty()) {
            StatusDialogFragment.newInstance(false, "Please select at least one student.").show(supportFragmentManager, "failureDialog")
            return
        }

        val selectedColumns = getSelectedColumns()
        if (selectedColumns.isEmpty()) {
            StatusDialogFragment.newInstance(false, "Please select at least one column.").show(supportFragmentManager, "failureDialog")
            return
        }

        setInputsEnabled(false)

        val selectedOrientation = if (toggleOrientation.checkedButtonId == R.id.btn_landscape_multi_report) {
            PageOrientation.LANDSCAPE
        } else {
            PageOrientation.PORTRAIT
        }

        lifecycleScope.launch {
            val pdfGenerator = StudentPdfGenerator(this@MultiStudentReportActivity)
            val reportName = etReportName.text.toString().trim()

            val pdfUri = pdfGenerator.generatePdf(
                selectedStudentsDetails,
                selectedColumns,
                selectedOrientation,
                reportName
            )

            runOnUiThread {
                setInputsEnabled(true)
                if (pdfUri != null) {
                    StatusDialogFragment.newInstance(true, "Report Generated!").show(supportFragmentManager, "successDialog")
                    openPdfFile(pdfUri)
                } else {
                    StatusDialogFragment.newInstance(false, "Failed to Create PDF").show(supportFragmentManager, "failureDialog")
                }
            }
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
        spinnerTeacherClassFilter.isEnabled = enabled
        btnSelectAll.isEnabled = enabled
        btnDeselectAll.isEnabled = enabled
        searchViewStudents.isEnabled = enabled // NEW
        toggleOrientation.isEnabled = enabled
        etReportName.isEnabled = enabled
        fieldsContainer.children.forEach { if (it is CheckBox) it.isEnabled = enabled }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}