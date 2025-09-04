package com.example.madarsa_attendance

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class ManageClassFragment : Fragment() {

    companion object {
        private const val TAG = "ManageClassFragment"
        private const val ARG_TEACHER_ID_MCF = "teacher_id_mcf"
        private const val ARG_TEACHER_NAME_MCF = "teacher_name_mcf"
        private const val CSV_TEMPLATE_FILENAME = "Class_Student_Template.csv"
        private const val CSV_TEMPLATE_CONTENT =
            "\"Student Name\",\"Parent Name\",\"Parent Mobile Number\",\"Registration Number\",\"Gender\",\"Birth Date (YYYY-MM-DD)\",\"Admission Date (YYYY-MM-DD)\",\"Monthly Fee\",\"Alternate Mobile Number (Optional)\",\"Address (Optional)\",\"Profile Image URL (Optional)\"\n"

        @JvmStatic
        fun newInstance(teacherId: String, teacherName: String): ManageClassFragment {
            return ManageClassFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEACHER_ID_MCF, teacherId)
                    putString(ARG_TEACHER_NAME_MCF, teacherName)
                }
            }
        }
    }

    private lateinit var recyclerViewClassStudents: RecyclerView
    private lateinit var classStudentsAdapter: ClassStudentsAdapter
    private lateinit var fabAddStudentToClass: ExtendedFloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoStudents: TextView
    private lateinit var searchViewStudents: SearchView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var db: FirebaseFirestore
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentOrganizationId: String? = null

    val viewModel: ManageClassViewModel by viewModels()
    private lateinit var sharedViewModel: TeacherDataViewModel

    // This launcher correctly handles results for Add/Edit and refreshes the fragment's list.
    private val studentActionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.refreshStudents()
            }
            fabAddStudentToClass.shrink()
        }

    private val pickCsvFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    launchBulkAddStudentsActivity(uri)
                }
            }
            fabAddStudentToClass.shrink()
        }

    private val bulkAddStudentsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.refreshStudents()
                val successCount = result.data?.getIntExtra("SUCCESS_COUNT", 0) ?: 0
                val failureCount = result.data?.getIntExtra("FAILURE_COUNT", 0) ?: 0
                Toast.makeText(
                    context,
                    "Bulk import complete. Added: $successCount, Failed: $failureCount.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "Bulk import cancelled or failed.", Toast.LENGTH_SHORT)
                    .show()
            }
            fabAddStudentToClass.shrink()
        }

    private val requestWritePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) downloadCsvTemplate() else Toast.makeText(
                context,
                "Storage permission is required.",
                Toast.LENGTH_LONG
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentTeacherId = it.getString(ARG_TEACHER_ID_MCF)
            currentTeacherName = it.getString(ARG_TEACHER_NAME_MCF)
        }
        db = FirebaseFirestore.getInstance()
        sharedViewModel = ViewModelProvider(requireActivity()).get(TeacherDataViewModel::class.java)
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manage_class, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (currentTeacherId == null || currentOrganizationId == null) {
            Toast.makeText(context, "Teacher or Organization info missing.", Toast.LENGTH_LONG)
                .show()
            return
        }

        initializeViews(view)
        setupRecyclerView()
        setupFabInteraction()
        setupSearchView()
        setupSwipeToRefresh()
        setupObservers()

        fabAddStudentToClass.shrink()

        viewModel.loadStudentsIfNeeded(currentTeacherId!!)
    }

    private fun initializeViews(view: View) {
        recyclerViewClassStudents = view.findViewById(R.id.recyclerViewClassStudentsFrag)
        fabAddStudentToClass = view.findViewById(R.id.fabAddStudentToClassFrag)
        progressBar = view.findViewById(R.id.progressBarClassStudentsFrag)
        tvNoStudents = view.findViewById(R.id.tvNoStudentsInClassFrag)
        searchViewStudents = view.findViewById(R.id.searchViewStudents)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_students)
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshStudents()
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading && !swipeRefreshLayout.isRefreshing) {
                progressBar.visibility = View.VISIBLE
                recyclerViewClassStudents.visibility = View.GONE
                tvNoStudents.visibility = View.GONE
            } else if (!isLoading) {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }

        viewModel.students.observe(viewLifecycleOwner) { students ->
            classStudentsAdapter.updateData(students)
            if (students.isEmpty()) {
                tvNoStudents.text = "No active students in this class."
                tvNoStudents.visibility = View.VISIBLE
                recyclerViewClassStudents.visibility = View.GONE
            } else {
                tvNoStudents.visibility = View.GONE
                recyclerViewClassStudents.visibility = View.VISIBLE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                tvNoStudents.text = error
                tvNoStudents.visibility = View.VISIBLE
                recyclerViewClassStudents.visibility = View.GONE
            }
        }

        sharedViewModel.studentsDataMightHaveChanged.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                viewModel.refreshStudents()
            }
        }
    }

    private fun setupRecyclerView() {
        classStudentsAdapter = ClassStudentsAdapter(emptyList()) { selectedStudent ->
            showStudentOptionsDialog(selectedStudent)
        }
        recyclerViewClassStudents.layoutManager = LinearLayoutManager(context)
        recyclerViewClassStudents.adapter = classStudentsAdapter
    }

    private fun setupFabInteraction() {
        fabAddStudentToClass.setOnClickListener {
            showAddStudentOptionsDialog()
        }
    }

    // --- THIS FUNCTION IS NOW CORRECTED ---
    private fun showAddStudentOptionsDialog() {
        if (!isAdded) return
        val dialogView =
            LayoutInflater.from(context).inflate(R.layout.dialog_add_student_options, null)
        val btnAddSingle = dialogView.findViewById<View>(R.id.btnAddSingleStudent)
        val btnAddBulk = dialogView.findViewById<View>(R.id.btnAddBulkStudents)
        val tvDownloadTemplate = dialogView.findViewById<TextView>(R.id.tvDownloadTemplate)

        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setView(dialogView).create()

        btnAddSingle.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(activity, AddStudentActivity::class.java).apply {
                putExtra("PRESELECTED_TEACHER_ID", currentTeacherId)
                putExtra("PRESELECTED_TEACHER_NAME", currentTeacherName)
            }
            // Use the fragment's own launcher
            studentActionLauncher.launch(intent)
        }
        btnAddBulk.setOnClickListener {
            dialog.dismiss()
            pickCsvFile()
        }
        tvDownloadTemplate.setOnClickListener {
            dialog.dismiss()
            checkAndRequestStoragePermissionForTemplate()
        }
        dialog.show()
    }

    private fun pickCsvFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
        }
        pickCsvFileLauncher.launch(intent)
    }

    private fun launchBulkAddStudentsActivity(csvFileUri: Uri) {
        val intent = Intent(activity, BulkAddStudentsActivity::class.java).apply {
            putExtra("CSV_FILE_URI", csvFileUri.toString())
            putExtra("TEACHER_ID", currentTeacherId)
            putExtra("TEACHER_NAME", currentTeacherName)
        }
        bulkAddStudentsLauncher.launch(intent)
    }

    private fun checkAndRequestStoragePermissionForTemplate() {
        if (!isAdded || context == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadCsvTemplate()
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                downloadCsvTemplate()
            } else {
                requestWritePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun downloadCsvTemplate() {
        if (!isAdded || context == null) return
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, CSV_TEMPLATE_FILENAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/MadarsaReports"
                    )
                }
            }
            val uri = requireContext().contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )
            uri?.let {
                requireContext().contentResolver.openOutputStream(it)?.use { out ->
                    out.write(CSV_TEMPLATE_CONTENT.toByteArray())
                    Toast.makeText(
                        context,
                        "Template downloaded to Downloads/MadarsaReports",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving template: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupSearchView() {
        searchViewStudents.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                classStudentsAdapter.filter(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                classStudentsAdapter.filter(newText)
                return true
            }
        })
    }

    // --- THIS FUNCTION IS ALSO CORRECTED ---
    private fun showStudentOptionsDialog(student: StudentDetailsItem) {
        if (!isAdded) return
        val options = arrayOf(
            "Edit Student",
            "Inactivate Student",
            "Delete Student",
            "Move to Another Class",
            "View Monthly Attendance"
        )
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Student: ${student.studentName}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(activity, EditStudentActivity::class.java).apply {
                            putExtra("STUDENT_ID", student.id)
                        }
                        // Use the fragment's own launcher
                        studentActionLauncher.launch(intent)
                    }

                    1 -> confirmInactivateStudent(student)
                    2 -> (requireActivity() as MainActivity).confirmDeleteStudent(student)
                    3 -> showMoveStudentDialog(student)
                    4 -> {
                        val calendar = Calendar.getInstance()
                        val intent =
                            Intent(activity, StudentMonthlyAttendanceActivity::class.java).apply {
                                putExtra("STUDENT_ID", student.id)
                                putExtra("STUDENT_NAME", student.studentName)
                                putExtra("TEACHER_ID", currentTeacherId)
                                putExtra("TARGET_YEAR", calendar.get(Calendar.YEAR))
                                putExtra("TARGET_MONTH", calendar.get(Calendar.MONTH))
                            }
                        startActivity(intent)
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmInactivateStudent(student: StudentDetailsItem) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Inactivate Student")
            .setMessage("Are you sure you want to inactivate ${student.studentName}?")
            .setPositiveButton("Inactivate") { _, _ -> inactivateStudentInFirestore(student.id) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun inactivateStudentInFirestore(studentId: String) {
        if (currentOrganizationId == null) return
        progressBar.visibility = View.VISIBLE
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").document(studentId)
            .update("isActive", false)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(context, "Student inactivated", Toast.LENGTH_SHORT).show()
                    sharedViewModel.notifyStudentDataChanged()
                    viewModel.refreshStudents()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Error inactivating student", e)
                }
            }
            .addOnCompleteListener { if (isAdded) progressBar.visibility = View.GONE }
    }

    private fun showMoveStudentDialog(studentToMove: StudentDetailsItem) {
        if (currentOrganizationId == null) return
        progressBar.visibility = View.VISIBLE
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers").orderBy("teacherName").get()
            .addOnSuccessListener { teacherSnap ->
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE

                val teachers = teacherSnap.documents.mapNotNull { doc ->
                    if (doc.id != currentTeacherId) {
                        TeacherSpinnerItem(
                            doc.id,
                            doc.getString("teacherName") ?: "N/A",
                            doc.getString("profileImageUrl")
                        )
                    } else {
                        null
                    }
                }

                if (teachers.isEmpty()) {
                    Toast.makeText(
                        context,
                        "No other classes available to move to.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                val names = teachers.map { it.name }
                AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
                    .setTitle("Move ${studentToMove.studentName} to:")
                    .setItems(names.toTypedArray()) { _, i ->
                        moveStudentToNewClass(studentToMove, teachers[i])
                    }
                    .setNegativeButton("Cancel", null)
                    .show()

            }.addOnFailureListener {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Could not load classes.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun moveStudentToNewClass(student: StudentDetailsItem, newTeacher: TeacherSpinnerItem) {
        if (currentOrganizationId == null) return

        val loadingDialog = StatusDialogFragment.newInstance(true, "Moving student and updating records...").apply {
            isCancelable = false
        }
        loadingDialog.show(parentFragmentManager, "movingStudentDialog")

        lifecycleScope.launch {
            try {
                val batch = db.batch()
                val originalTeacherId = student.teacherId

                // 1. Update the student document
                val studentRef = db.collection("organizations").document(currentOrganizationId!!)
                    .collection("students").document(student.id)
                batch.update(studentRef, mapOf("teacherId" to newTeacher.id, "teacherName" to newTeacher.name))

                // 2. Find and update fee payments
                val feesSnapshot = db.collection("organizations").document(currentOrganizationId!!)
                    .collection("feePayments").whereEqualTo("studentId", student.id).get().await()
                feesSnapshot.documents.forEach { doc ->
                    batch.update(doc.reference, mapOf("teacherId" to newTeacher.id, "teacherName" to newTeacher.name))
                }

                // 3. Find and update exam results
                val examsSnapshot = db.collection("organizations").document(currentOrganizationId!!)
                    .collection("examResults").whereEqualTo("studentId", student.id).get().await()
                examsSnapshot.documents.forEach { doc ->
                    batch.update(doc.reference, mapOf("teacherId" to newTeacher.id, "teacherName" to newTeacher.name))
                }

                // 4. Find and update attendance records
                val attendanceSnapshot = db.collection("organizations").document(currentOrganizationId!!)
                    .collection("attendanceRecords")
                    .whereEqualTo("teacherId", originalTeacherId)
                    .get().await()

                for (doc in attendanceSnapshot.documents) {
                    val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>>
                    if (studentAttendances?.any { it["studentId"] == student.id } == true) {
                        val updatedStudentAttendances = studentAttendances.map {
                            if (it["studentId"] == student.id) {
                                it.toMutableMap().apply {
                                    this["teacherName"] = newTeacher.name
                                }
                            } else {
                                it
                            }
                        }
                        batch.update(doc.reference, mapOf(
                            "teacherId" to newTeacher.id,
                            "teacherName" to newTeacher.name,
                            "studentAttendances" to updatedStudentAttendances
                        ))
                    }
                }

                // 5. Commit all changes
                batch.commit().await()

                // Success
                if (isAdded) {
                    loadingDialog.dismiss()
                    Toast.makeText(context, "Student moved successfully!", Toast.LENGTH_SHORT).show()
                    viewModel.refreshStudents()
                    sharedViewModel.notifyStudentDataChanged()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error moving student and updating records", e)
                if (isAdded) {
                    loadingDialog.dismiss()
                    Toast.makeText(context, "Failed to move student: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}