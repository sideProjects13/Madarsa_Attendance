package com.example.madarsa_attendance

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ManageClassFragment : Fragment() {

    companion object {
        private const val TAG = "ManageClassFragment"
        private const val ARG_TEACHER_ID_MCF = "teacher_id_mcf"
        private const val ARG_TEACHER_NAME_MCF = "teacher_name_mcf"
        private const val INTRO_EXTEND_DELAY_ADD_STUDENT_FAB = 500L
        private const val INTRO_SHRINK_DELAY_ADD_STUDENT_FAB = 2500L

        private const val BULK_IMPORT_REQUEST_CODE = 1001
        private const val PICK_CSV_FILE_REQUEST_CODE = 1002

        // Constants for CSV Template
        private const val CSV_TEMPLATE_FILENAME = "Madarsa_Student_Template.csv"
        private const val CSV_TEMPLATE_CONTENT =
            "\"Student Name\",\"Parent Name\",\"Parent Mobile Number\",\"Registration Number\",\"Gender\",\"Birth Date (YYYY-MM-DD)\",\"Admission Date (YYYY-MM-DD)\",\"Monthly Fee\",\"Profile Image URL (Optional)\"\n" +
                    "\"Example Student\",\"Example Parent\",\"9876543210\",\"R101\",\"Male\",\"2010-01-01\",\"2015-09-01\",500.00,\"\"\n" +
                    "\"Another Student\",\"Another Parent\",\"0123456789\",\"R102\",\"Female\",\"2011-02-15\",\"2016-09-01\",450.00,\"https://example.com/image.jpg\""

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

    // Views
    private var _recyclerViewClassStudents: RecyclerView? = null
    private val recyclerViewClassStudents get() = _recyclerViewClassStudents!!
    private var _classStudentsAdapter: ClassStudentsAdapter? = null
    private val classStudentsAdapter get() = _classStudentsAdapter!!
    private var _fabAddStudentToClass: ExtendedFloatingActionButton? = null
    private val fabAddStudentToClass get() = _fabAddStudentToClass!!
    private var _progressBar: ProgressBar? = null
    private val progressBar get() = _progressBar!!
    private var _tvNoStudents: TextView? = null
    private val tvNoStudents get() = _tvNoStudents!!
    private var _searchViewStudents: SearchView? = null
    private val searchViewStudents get() = _searchViewStudents!!
    private var _swipeRefreshLayout: SwipeRefreshLayout? = null
    private val swipeRefreshLayout get() = _swipeRefreshLayout!!

    // Backend & Data
    private lateinit var db: FirebaseFirestore
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentOrganizationId: String? = null

    private lateinit var teacherDataViewModel: TeacherDataViewModel

    // Handlers & Launchers
    private val fabAddStudentIntroHandler = Handler(Looper.getMainLooper())
    private var introExtendAddStudentRunnable: Runnable? = null
    private var introShrinkAddStudentRunnable: Runnable? = null

    private val studentActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!isAdded) return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Student action (add/edit) returned OK. Reloading student list.")
            loadStudentsForClass()
        }
        _fabAddStudentToClass?.shrink()
    }

    private val pickCsvFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (!isAdded) return@registerForActivityResult
        if (uri != null) {
            Log.d(TAG, "CSV file selected: $uri")
            launchBulkAddStudentsActivity(uri)
        } else {
            Log.d(TAG, "CSV file selection cancelled.")
            Toast.makeText(context, "File selection cancelled.", Toast.LENGTH_SHORT).show()
        }
        _fabAddStudentToClass?.shrink()
    }

    private val bulkAddStudentsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!isAdded) return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Bulk add students returned OK. Reloading student list.")
            loadStudentsForClass()
            val successCount = result.data?.getIntExtra("SUCCESS_COUNT", 0) ?: 0
            val failureCount = result.data?.getIntExtra("FAILURE_COUNT", 0) ?: 0
            Toast.makeText(context, "Bulk import complete. Added: $successCount, Failed: $failureCount.", Toast.LENGTH_LONG).show()
        } else {
            Log.d(TAG, "Bulk add students returned CANCELED or other result.")
            Toast.makeText(context, "Bulk import cancelled or failed.", Toast.LENGTH_SHORT).show()
        }
        _fabAddStudentToClass?.shrink()
    }

    private val requestWritePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            downloadCsvTemplate()
        } else {
            Toast.makeText(context, "Storage permission is required to save the template.", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentTeacherId = it.getString(ARG_TEACHER_ID_MCF)
            currentTeacherName = it.getString(ARG_TEACHER_NAME_MCF)
        }
        db = FirebaseFirestore.getInstance()
        teacherDataViewModel = ViewModelProvider(requireActivity()).get(TeacherDataViewModel::class.java)
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(requireContext())

        Log.d(TAG, "onCreate - Teacher ID: $currentTeacherId, Org ID: $currentOrganizationId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView")
        val view = inflater.inflate(R.layout.fragment_manage_class, container, false)
        _recyclerViewClassStudents = view.findViewById(R.id.recyclerViewClassStudentsFrag)
        _fabAddStudentToClass = view.findViewById(R.id.fabAddStudentToClassFrag)
        _progressBar = view.findViewById(R.id.progressBarClassStudentsFrag)
        _tvNoStudents = view.findViewById(R.id.tvNoStudentsInClassFrag)
        _searchViewStudents = view.findViewById(R.id.searchViewStudents)
        _swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_students)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated - Initializing UI.")

        if (currentTeacherId == null || currentOrganizationId == null) {
            Toast.makeText(context, "Teacher or Organization info missing.", Toast.LENGTH_LONG).show()
            return
        }
        setupRecyclerView()
        setupFabInteraction()
        setupSearchView()
        setupSwipeToRefresh()

        _fabAddStudentToClass?.shrink()
        if (savedInstanceState == null) {
            _fabAddStudentToClass?.let { startAddStudentFabIntroAnimation(it) }
        }

        teacherDataViewModel.studentsDataMightHaveChanged.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Log.d(TAG, "Observed student data change from ViewModel. Reloading list.")
                loadStudentsForClass()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - ManageClassFragment")
        loadStudentsForClass()
        _fabAddStudentToClass?.let { if (it.isExtended && introExtendAddStudentRunnable == null) it.shrink() }
    }

    override fun onPause() {
        super.onPause()
        cancelAddStudentFabIntroAnimation()
        Log.d(TAG, "onPause - ManageClassFragment")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelAddStudentFabIntroAnimation()
        _recyclerViewClassStudents?.adapter = null
        _recyclerViewClassStudents = null
        _classStudentsAdapter = null
        _fabAddStudentToClass = null
        _progressBar = null
        _tvNoStudents = null
        _searchViewStudents?.setOnQueryTextListener(null)
        _searchViewStudents = null
        _swipeRefreshLayout?.setOnRefreshListener(null)
        _swipeRefreshLayout = null
        Log.d(TAG, "onDestroyView - ManageClassFragment")
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Swipe to refresh triggered.")
            loadStudentsForClass()
        }
    }

    private fun setupSearchView() {
        searchViewStudents.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                classStudentsAdapter.filter(query)
                searchViewStudents.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                classStudentsAdapter.filter(newText)
                return true
            }
        })
        searchViewStudents.setOnCloseListener {
            searchViewStudents.setQuery("", false)
            classStudentsAdapter.filter("")
            true
        }
    }

    // MODIFIED: FAB now shows a dialog
    private fun setupFabInteraction() {
        fabAddStudentToClass.setOnClickListener {
            cancelAddStudentFabIntroAnimation()
            showAddStudentOptionsDialog()
        }
    }

    // NEW METHOD: Show dialog for adding options
    private fun showAddStudentOptionsDialog() {
        if (!isAdded || context == null || currentOrganizationId == null) return

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_student_options, null)
        val btnAddSingle = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddSingleStudent)
        val btnAddBulk = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddBulkStudents)
        val tvDownloadTemplate = dialogView.findViewById<TextView>(R.id.tvDownloadTemplate)

        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setView(dialogView)
            .create()

        btnAddSingle.setOnClickListener {
            dialog.dismiss()
            launchAddStudentActivity() // Launch original single student add flow
        }

        btnAddBulk.setOnClickListener {
            dialog.dismiss()
            pickCsvFile() // Launch file picker for bulk add
        }

        tvDownloadTemplate.setOnClickListener {
            dialog.dismiss()
            checkAndRequestStoragePermissionForTemplate() // NEW: Check permissions before downloading
        }

        dialog.show()
    }


    // Original method (renamed for clarity if you wish, but kept name for minimal changes)
    private fun launchAddStudentActivity() {
        if (!isAdded || activity == null || currentOrganizationId == null) return
        val intent = Intent(activity, AddStudentActivity::class.java).apply {
            putExtra("PRESELECTED_TEACHER_ID", currentTeacherId)
            putExtra("PRESELECTED_TEACHER_NAME", currentTeacherName)
        }
        studentActionLauncher.launch(intent)
        _fabAddStudentToClass?.shrink()
    }

    // NEW METHOD: Pick a CSV file
    private fun pickCsvFile() {
        if (!isAdded || context == null || currentOrganizationId == null) {
            Toast.makeText(context, "Application not ready for file selection.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv" // Mime type for CSV
        }
        try {
            pickCsvFileLauncher.launch(arrayOf("text/csv"))
        } catch (e: Exception) {
            Log.e(TAG, "Error launching file picker: ${e.message}", e)
            Toast.makeText(context, "Could not open file picker. Check permissions.", Toast.LENGTH_LONG).show()
        }
    }

    // NEW METHOD: Launch BulkAddStudentsActivity
    private fun launchBulkAddStudentsActivity(csvFileUri: Uri) {
        if (!isAdded || activity == null || currentTeacherId == null || currentOrganizationId == null) {
            Toast.makeText(context, "Cannot proceed with bulk add: Context missing.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(activity, BulkAddStudentsActivity::class.java).apply {
            putExtra("CSV_FILE_URI", csvFileUri.toString())
            putExtra("TEACHER_ID", currentTeacherId)
            putExtra("TEACHER_NAME", currentTeacherName)
        }
        bulkAddStudentsLauncher.launch(intent)
    }

    // NEW METHOD: Check and request permissions for saving template
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

    // NEW METHOD: Download CSV Template
    private fun downloadCsvTemplate() {
        if (!isAdded || context == null) {
            Toast.makeText(context, "Application not ready to download template.", Toast.LENGTH_SHORT).show()
            return
        }

        val mimeType = "text/csv"
        val displayName = CSV_TEMPLATE_FILENAME

        try {
            val contentUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MadarsaReports")
                }
                context?.contentResolver?.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = java.io.File(downloadsDir, "MadarsaReports")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val file = java.io.File(appDir, displayName)
                Uri.fromFile(file)
            }

            contentUri?.let { uri ->
                context?.contentResolver?.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(CSV_TEMPLATE_CONTENT.toByteArray())
                    Toast.makeText(context, "Template downloaded to Downloads/MadarsaReports", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "CSV template saved to: $uri")
                }
            } ?: run {
                Toast.makeText(context, "Failed to create file URI for template.", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Failed to create file URI for template download.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving CSV template: ${e.message}", e)
            Toast.makeText(context, "Error saving template: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun startAddStudentFabIntroAnimation(fab: ExtendedFloatingActionButton) {
        if (!isAdded || activity == null || !fab.isAttachedToWindow) return
        fab.shrink()
        introExtendAddStudentRunnable = Runnable {
            if (isAdded && activity?.isFinishing == false && fab.isAttachedToWindow) {
                fab.extend()
                introShrinkAddStudentRunnable = Runnable {
                    if (isAdded && activity?.isFinishing == false && fab.isAttachedToWindow) {
                        fab.shrink()
                    }
                }
                fabAddStudentIntroHandler.postDelayed(introShrinkAddStudentRunnable!!, INTRO_SHRINK_DELAY_ADD_STUDENT_FAB)
            }
        }
        fabAddStudentIntroHandler.postDelayed(introExtendAddStudentRunnable!!, INTRO_EXTEND_DELAY_ADD_STUDENT_FAB)
    }

    private fun cancelAddStudentFabIntroAnimation() {
        introExtendAddStudentRunnable?.let { fabAddStudentIntroHandler.removeCallbacks(it); introExtendAddStudentRunnable = null }
        introShrinkAddStudentRunnable?.let { fabAddStudentIntroHandler.removeCallbacks(it); introShrinkAddStudentRunnable = null }
    }

    private fun setupRecyclerView() {
        if (!isAdded || context == null) return
        _classStudentsAdapter = ClassStudentsAdapter(ArrayList()) { selectedStudent ->
            showStudentOptionsDialog(selectedStudent)
        }
        recyclerViewClassStudents.layoutManager = LinearLayoutManager(context)
        recyclerViewClassStudents.adapter = _classStudentsAdapter
    }

    private fun showStudentOptionsDialog(student: StudentDetailsItem) {
        if (!isAdded || context == null || currentOrganizationId == null) return
        val options = arrayOf("Edit Student", "Inactivate Student", "Delete Student", "Move to Another Class", "View Monthly Attendance")
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Student: ${student.studentName}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(activity, EditStudentActivity::class.java).apply {
                            putExtra("STUDENT_ID", student.id)
                        }
                        studentActionLauncher.launch(intent)
                    }
                    1 -> confirmInactivateStudent(student)
                    2 -> confirmDeleteStudent(student)
                    3 -> showMoveStudentDialog(student)
                    4 -> {
                        val calendar = Calendar.getInstance()
                        val intent = Intent(activity, StudentMonthlyAttendanceActivity::class.java).apply {
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
        if (!isAdded || context == null || currentOrganizationId == null) return
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Inactivate Student")
            .setMessage("Are you sure you want to inactivate ${student.studentName}? They will be removed from this list and can be viewed in the 'Inactive Students' section.")
            .setPositiveButton("Inactivate") { _, _ ->
                inactivateStudentInFirestore(student.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun inactivateStudentInFirestore(studentId: String) {
        if (!isAdded || currentOrganizationId == null) return
        progressBar.visibility = View.VISIBLE
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").document(studentId)
            .update("isActive", false)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Student inactivated", Toast.LENGTH_SHORT).show()
                teacherDataViewModel.notifyStudentDataChanged()
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error inactivating: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmDeleteStudent(student: StudentDetailsItem) {
        if (!isAdded || context == null || currentOrganizationId == null) return
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Student")
            .setMessage("Are you sure you want to permanently delete ${student.studentName}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteStudentFromFirestore(student.id) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteStudentFromFirestore(studentId: String) {
        if (!isAdded || currentOrganizationId == null) return
        progressBar.visibility = View.VISIBLE
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").document(studentId).delete()
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Student deleted", Toast.LENGTH_SHORT).show()
                teacherDataViewModel.notifyStudentDataChanged()
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error deleting: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showMoveStudentDialog(studentToMove: StudentDetailsItem) {
        if (!isAdded || context == null || currentOrganizationId == null) return
        progressBar.visibility = View.VISIBLE
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers").orderBy("teacherName").get()
            .addOnSuccessListener { teacherSnap ->
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                val teachers = mutableListOf<TeacherSpinnerItem>()
                val names = mutableListOf<String>()
                teacherSnap.documents.filter { it.id != currentTeacherId }.forEach {
                    teachers.add(TeacherSpinnerItem(it.id, it.getString("teacherName") ?: "N/A", it.getString("profileImageUrl")))
                    names.add(it.getString("teacherName") ?: "N/A")
                }
                if (names.isEmpty()) {
                    Toast.makeText(context, "No other classes available to move to.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
                    .setTitle("Move ${studentToMove.studentName} to:").setItems(names.toTypedArray()) { _, i ->
                        moveStudentToNewClass(studentToMove.id, teachers[i])
                    }.setNegativeButton("Cancel", null).show()
            }.addOnFailureListener { if (isAdded) progressBar.visibility = View.GONE }
    }

    private fun moveStudentToNewClass(studentId: String, newTeacher: TeacherSpinnerItem) {
        if (!isAdded || currentOrganizationId == null) return
        progressBar.visibility = View.VISIBLE
        val updates = mapOf(
            "teacherId" to newTeacher.id,
            "teacherName" to newTeacher.name
        )
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").document(studentId).update(updates)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Student moved successfully.", Toast.LENGTH_SHORT).show()
                teacherDataViewModel.notifyStudentDataChanged()
            }
            .addOnFailureListener {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error moving student.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun loadStudentsForClass() {
        if (currentTeacherId.isNullOrEmpty() || currentOrganizationId.isNullOrEmpty() || !isAdded) {
            Log.w(TAG, "loadStudentsForClass skipped: conditions not met.")
            _swipeRefreshLayout?.isRefreshing = false
            return
        }

        Log.d(TAG, "Executing loadStudentsForClass for teacher ID: $currentTeacherId, Org ID: $currentOrganizationId")

        if (swipeRefreshLayout.isRefreshing == false) {
            progressBar.visibility = View.VISIBLE
        }
        tvNoStudents.visibility = View.GONE
        recyclerViewClassStudents.visibility = View.GONE

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students")
            .whereEqualTo("teacherId", currentTeacherId)
            .whereEqualTo("isActive", true)
            .orderBy("studentName")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!isAdded) return@addOnSuccessListener

                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false

                val studentList = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(StudentDetailsItem::class.java)?.copy(id = doc.id)
                }

                classStudentsAdapter.updateData(studentList)
                Log.d(TAG, "Successfully loaded ${studentList.size} students.")

                if (studentList.isNotEmpty()) {
                    recyclerViewClassStudents.visibility = View.VISIBLE
                    tvNoStudents.visibility = View.GONE
                } else {
                    recyclerViewClassStudents.visibility = View.GONE
                    tvNoStudents.text = "No active students in this class."
                    tvNoStudents.visibility = View.VISIBLE
                }
                searchViewStudents.setQuery("", false)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener

                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false

                tvNoStudents.text = "Error loading students."
                tvNoStudents.visibility = View.VISIBLE
                classStudentsAdapter.updateData(emptyList())
                Log.e(TAG, "Error loading students. Check Firestore index/rules.", e)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}