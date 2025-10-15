package com.example.madarsa_attendance

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(),
    TeacherSelectionDialogFragment.TeacherSelectionListener,
    QuickFeesDialogFragment.FeeStudentSelectionListener {

    private val TAG = "MainActivity"

    private val sharedViewModel: TeacherDataViewModel by viewModels()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navRecyclerView: RecyclerView
    private lateinit var navAdapter: NavigationDrawerAdapter

    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val resultViewModel: ResultGeneratorViewModel by viewModels()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private lateinit var feesReportGenerator: FeesReportGenerator

    private var pendingTeacherAction: TeacherAction? = null
    private var pendingStudentAction: StudentAction? = null
    private var selectedTeacherForBulkAdd: Teacher? = null
    private var studentToMove: StudentDetailsItem? = null

    private var onPermissionGranted: (() -> Unit)? = null
    private val requestPermissionLauncherForDownloads =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                onPermissionGranted?.invoke()
            } else {
                Toast.makeText(this, "Storage permission is required to save files.", Toast.LENGTH_LONG).show()
            }
        }

    private companion object {
        private const val SAMPLE_CLASS_CSV_FILENAME = "Class_Sample_Students.csv"
        private const val SAMPLE_CLASS_CSV_CONTENT = "\"Student Name\",\"Parent Name\",\"Parent Mobile Number\",\"Registration Number\",\"Gender\",\"Birth Date (YYYY-MM-DD)\",\"Admission Date (YYYY-MM-DD)\",\"Monthly Fee\",\"Alternate Mobile Number (Optional)\",\"Address (Optional)\",\"Profile Image URL (Optional)\"\n" +
                "\"Ali Khan\",\"Fatima Khan\",\"9871234567\",\"C_REG001\",\"Male\",\"2012-01-01\",\"2023-09-01\",\"1000.00\",\"\",\"\",\"\"\n" +
                "\"Sara Ahmed\",\"Omar Ahmed\",\"9872345678\",\"C_REG002\",\"Female\",\"2013-03-10\",\"2023-09-01\",\"1200.00\",\"\",\"\",\"\""

        private const val SAMPLE_ORG_CSV_FILENAME = "Org_Sample_Students.csv"
        private const val SAMPLE_ORG_CSV_CONTENT = "\"Student Name\",\"Parent Name\",\"Parent Mobile Number\",\"Registration Number\",\"Gender\",\"Birth Date (YYYY-MM-DD)\",\"Admission Date (YYYY-MM-DD)\",\"Monthly Fee\",\"Alternate Mobile Number (Optional)\",\"Address (Optional)\",\"Profile Image URL (Optional)\",\"Teacher Name\"\n" +
                "\"Zaid Ali\",\"Aisha Ali\",\"9988776655\",\"O_REG001\",\"Male\",\"2011-05-20\",\"2023-08-15\",\"1500.00\",\"\",\"\",\"\",\"Muzir Khan\"\n" +
                "\"Hana Malik\",\"Imran Malik\",\"9988776644\",\"O_REG002\",\"Female\",\"2012-07-11\",\"2023-08-15\",\"1800.00\",\"\",\"\",\"\",\"Mufti Jameeel\""

        private const val SAMPLE_TEACHER_CSV_FILENAME = "Madarsa_Teacher_Template.csv"
        private const val SAMPLE_TEACHER_CSV_CONTENT = "\"Teacher Name\",\"Mobile Number\",\"Email\",\"Password\"\n" +
                "\"Ahmed Khan\",\"9876543210\",\"ahmed.khan@example.com\",\"password123\"\n" +
                "\"Fatima Ali\",\"9123456780\",\"fatima.ali@example.com\",\"teacherpass\""
    }

    private val profileUpdateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Returned from AdminProfileActivity with updates. Recreating MainActivity.")
            // This is the simplest and most effective way to "refresh" the app's main screen
            recreate()
        }
    }

    internal val studentDataChangeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Data changed. Forcing refresh of student list and dashboard.")
            // This forces the ViewModel to re-fetch from Firestore
            dashboardViewModel.fetchStudentListForSearch(forceRefresh = true)
            dashboardViewModel.refreshData()
        }
    }

    private val bulkMoveLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // When BulkMoveStudentsActivity finishes successfully, send the refresh signal
            Log.d(TAG, "Bulk move successful, notifying for refresh.")
            sharedViewModel.notifyStudentDataChanged()
            dashboardViewModel.refreshData()
        }
    }


    private val pickCsvFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->

                // This 'when' block reads the app's memory and routes to the correct screen.
                // It cannot be confused by null values anymore.
                val intent = when (pendingTeacherAction) {

                    // If the app remembers you chose BULK_ADD_TO_CLASS...
                    TeacherAction.BULK_ADD_TO_CLASS -> {
                        // ...it correctly launches the screen for a specific class.
                        Intent(this, BulkAddStudentsActivity::class.java).apply {
                            data = uri // Use intent.data to pass the file URI
                            putExtra("TEACHER_ID", selectedTeacherForBulkAdd?.teacherId)
                            putExtra("TEACHER_NAME", selectedTeacherForBulkAdd?.teacherName)
                        }
                    }

                    // If the app remembers you chose BULK_ADD_TO_ORG...
                    TeacherAction.BULK_ADD_TO_ORG -> {
                        // ...it correctly launches the screen for the whole organization.
                        Intent(this, BulkAddOrgActivity::class.java).apply {
                            data = uri
                        }
                    }

                    // A safety net for any other situation.
                    else -> {
                        Toast.makeText(this, "An unexpected error occurred. Please try again.", Toast.LENGTH_SHORT).show()
                        null
                    }
                }
                // Launch the correct intent that was just created.
                intent?.let { studentDataChangeLauncher.launch(it) }
            }
        }

        // Reset the memory for the next operation.
        pendingTeacherAction = null
        selectedTeacherForBulkAdd = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- START: MEMORY RESTORE LOGIC ---
        if (savedInstanceState != null) {
            // Restore the pending action from its saved name
            savedInstanceState.getString("PENDING_TEACHER_ACTION")?.let { actionName ->
                pendingTeacherAction = try {
                    TeacherAction.valueOf(actionName)
                } catch (e: IllegalArgumentException) {
                    null // Handle case where the name might be invalid
                }
            }
            // Restore the selected teacher's info
            val teacherId = savedInstanceState.getString("SELECTED_TEACHER_ID")
            val teacherName = savedInstanceState.getString("SELECTED_TEACHER_NAME")
            val teacherImageUrl = savedInstanceState.getString("SELECTED_TEACHER_IMAGE_URL")
            if (teacherId != null && teacherName != null) {
                selectedTeacherForBulkAdd = Teacher(
                    teacherId = teacherId,
                    teacherName = teacherName,
                    profileImageUrl = teacherImageUrl
                )
            }
        }
        // --- END: MEMORY RESTORE LOGIC ---

        // --- Startup routing logic ---
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null || !FirebaseAuthManager.isLoggedInAndOrgSelected(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val role = FirebaseAuthManager.getUserRole(this)
        when (role) {
            "superadmin" -> {
                // If the user is a super admin, send them to their dashboard
                startActivity(Intent(this, SuperAdminDashboardActivity::class.java))
                finish() // Finish MainActivity so they can't go back to it
                return   // Stop executing the rest of onCreate
            }
            "teacher" -> {
                // If the user is a teacher, send them to their dashboard
                startActivity(Intent(this, TeacherHomeActivity   ::class.java))
                finish()
                return
            }
            "admin" -> {
                // This is a regular admin. Check if they have an organization selected.
                if (!FirebaseAuthManager.isLoggedInAndOrgSelected(this)) {
                    // If not, send to login/org selection
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    return
                }
                // If they do, they can proceed to load MainActivity's content.
            }
            else -> {
                // If role is unknown or missing, send back to login
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        toolbar = findViewById(R.id.toolbar)
        appBarLayout = findViewById(R.id.app_bar_layout)
        feesReportGenerator = FeesReportGenerator(this, db)

        setSupportActionBar(toolbar)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.nav_open, R.string.nav_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupCustomNavigationDrawer()
        applyWindowInsets()
        setupSharedViewModelObserver() // Add this call here


        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DashboardFragment())
                .commit()
        }

        resultViewModel.generationStatus.observe(this) { event ->
            event.getContentIfNotHandled()?.let { (isSuccess, message) ->
                if (message.contains("...")) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                } else {
                    StatusDialogFragment.newInstance(isSuccess, message).show(supportFragmentManager, "statusDialog")
                }
            }
        }

        // --- THIS IS THE CORRECTED CODE BLOCK ---
        // It uses named arguments to avoid any confusion for the compiler.
        UpdateManager.checkForUpdate(
            context = this,
            onUpdateAvailable = { updateInfo ->
                showUpdateDialog(updateInfo)
            }
            // We don't need to provide onNoUpdate, as it has a default value.
        )
        // --- END OF CORRECTION ---
    }
    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        val message = "A new version (${updateInfo.versionName}) is available.\n\nWhat's New:\n${updateInfo.updateNotes}"

        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage(message)
            .setCancelable(false) // User must make a choice
            .setPositiveButton("Update Now") { dialog, _ ->
                // Open the Firebase App Distribution link in a browser
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.updateUrl))
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open update link.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }


    // In MainActivity.kt

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the pending action's name so we can restore it later
        pendingTeacherAction?.let {
            outState.putString("PENDING_TEACHER_ACTION", it.name)
        }
        // Save the selected teacher's info if it exists
        selectedTeacherForBulkAdd?.let {
            outState.putString("SELECTED_TEACHER_ID", it.teacherId)
            outState.putString("SELECTED_TEACHER_NAME", it.teacherName)
            outState.putString("SELECTED_TEACHER_IMAGE_URL", it.profileImageUrl)
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBarLayout.updatePadding(top = insets.top)
            windowInsets
        }
    }

    private fun setupCustomNavigationDrawer() {
        navRecyclerView = findViewById(R.id.navigation_drawer_recycler_view)
        navAdapter = NavigationDrawerAdapter { itemId ->
            handleNavigation(itemId)
        }
        navRecyclerView.adapter = navAdapter
        navAdapter.setMenuItems(getNavigationMenuItems())
    }

    private fun handleNavigation(itemId: Int) {
        when (itemId) {
            R.id.nav_quick_attendance -> {
                pendingTeacherAction = TeacherAction.VIEW_ATTENDANCE
                TeacherSelectionDialogFragment.newInstance("Select Teacher for Attendance")
                    .show(supportFragmentManager, "TeacherSelectionDialog")
            }

            R.id.nav_teacher_monthly_attendance -> {
                pendingTeacherAction = TeacherAction.VIEW_MONTHLY_ATTENDANCE // We'll need to add this action
                TeacherSelectionDialogFragment.newInstance("Select Teacher to View Report")
                    .show(supportFragmentManager, "TeacherMonthlyAttendanceDialog")
            }

            R.id.nav_teacher_attendance_report -> {
                startActivity(Intent(this, TeacherAttendanceReportActivity::class.java))
            }

            R.id.nav_teacher_attendance -> {
                startActivity(Intent(this, TeacherAttendanceActivity::class.java))
            }


            R.id.nav_student_profile -> {
            // This is the FIX. It now uses the QuickFeesDialogFragment to select a student.
            pendingStudentAction = StudentAction.VIEW_PROFILE
            QuickFeesDialogFragment.newInstance("Select Student to View Profile", StudentAction.VIEW_PROFILE)
                .show(supportFragmentManager, "StudentProfileSelectionDialog")
        }

            R.id.nav_dashboard -> {
                toolbar.title = "Dashboard"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, DashboardFragment())
                    .commit()
            }

            R.id.nav_manage_inventory -> {
                startActivity(Intent(this, ManageInventoryActivity::class.java))
            }

            R.id.nav_sell_item -> {
                // Set the pending action and launch the student search dialog
                pendingStudentAction = StudentAction.SELL_ITEM
                QuickFeesDialogFragment.newInstance("Sell Item To", StudentAction.SELL_ITEM)
                    .show(supportFragmentManager, "SellItemDialog")
            }

            R.id.nav_sales_history -> {
                // For Phase 3 - for now, you can create a placeholder activity or show a Toast
                 startActivity(Intent(this, SalesHistoryActivity::class.java))
//                Toast.makeText(this, "Sales History coming soon!", Toast.LENGTH_SHORT).show()
            }

            R.id.nav_donations_management -> {
                // For Phase 4 - for now, you can create a placeholder activity or show a Toast
                 startActivity(Intent(this, ManageDonationsActivity::class.java))
            }
            //

            R.id.nav_leaderboard -> startActivity(Intent(this, LeaderboardActivity::class.java))
            R.id.nav_quick_add_student -> {
                pendingTeacherAction = TeacherAction.ADD_STUDENT
                TeacherSelectionDialogFragment.newInstance("Select Class to Add Student")
                    .show(supportFragmentManager, "TeacherSelectionDialog")
            }
            R.id.nav_edit_student -> {
                QuickFeesDialogFragment.newInstance("Edit Student", StudentAction.EDIT_STUDENT)
                    .show(supportFragmentManager, "EditStudentDialog")
            }
            R.id.nav_delete_student -> {
                QuickFeesDialogFragment.newInstance("Delete Student", StudentAction.DELETE_STUDENT)
                    .show(supportFragmentManager, "DeleteStudentDialog")
            }
            R.id.nav_inactivate_student -> {
                QuickFeesDialogFragment.newInstance("Select Student to Inactivate", StudentAction.INACTIVATE_STUDENT)
                    .show(supportFragmentManager, "InactivateStudentDialog")
            }
            R.id.nav_move_student -> {
                QuickFeesDialogFragment.newInstance("Select Student to Move", StudentAction.MOVE_STUDENT)
                    .show(supportFragmentManager, "MoveStudentDialog")
            }
            R.id.nav_inactive_students_list -> {
                val intent = Intent(this, InactiveStudentsActivity::class.java)
                studentDataChangeLauncher.launch(intent)
            }
            R.id.nav_view_monthly_attendance -> {
                QuickFeesDialogFragment.newInstance("View Monthly Attendance", StudentAction.VIEW_MONTHLY_ATTENDANCE)
                    .show(supportFragmentManager, "StudentMonthlyAttendanceDialog")
            }
            R.id.nav_manage_class -> {
                pendingTeacherAction = TeacherAction.MANAGE_CLASS
                TeacherSelectionDialogFragment.newInstance("Select Class to Manage")
                    .show(supportFragmentManager, "ManageClassDialog")
            }
            R.id.nav_check_daily_attendance -> {
                pendingStudentAction = StudentAction.CHECK_DAILY_ATTENDANCE
                QuickFeesDialogFragment.newInstance("Check Student Attendance", StudentAction.CHECK_DAILY_ATTENDANCE)
                    .show(supportFragmentManager, "CheckAttendanceDialog")
            }
            R.id.nav_quick_add_teacher -> {
                val intent = Intent(this, AddTeacherActivity::class.java)
                studentDataChangeLauncher.launch(intent)
            }
            R.id.nav_edit_teacher -> {
                pendingTeacherAction = TeacherAction.EDIT_TEACHER
                TeacherSelectionDialogFragment.newInstance("Select Teacher to Edit")
                    .show(supportFragmentManager, "EditTeacherDialog")
            }
            R.id.nav_delete_teacher -> {
                pendingTeacherAction = TeacherAction.DELETE_TEACHER
                TeacherSelectionDialogFragment.newInstance("Select Teacher to Delete")
                    .show(supportFragmentManager, "DeleteTeacherDialog")
            }
            R.id.nav_manage_teachers -> startActivity(Intent(this, ManageTeachersActivity::class.java))
            R.id.nav_bulk_add_teachers -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/csv"
                }
                pickCsvFileLauncher.launch(intent)
            }
            R.id.nav_download_teacher_data -> {
                requestStoragePermission {
                    downloadAllTeacherData()
                }
            }
            R.id.nav_record_student_fee -> {
                QuickFeesDialogFragment.newInstance("Record Student Fee", StudentAction.VIEW_FEE_HISTORY)
                    .show(supportFragmentManager, "RecordFeeDialog")
            }
            R.id.nav_student_fee_history -> {
                QuickFeesDialogFragment.newInstance("Student Fee History", StudentAction.VIEW_FEE_HISTORY)
                    .show(supportFragmentManager, "QuickFeesDialog")
            }
            R.id.nav_class_fee_summary -> {
                pendingTeacherAction = TeacherAction.VIEW_CLASS_FEES
                TeacherSelectionDialogFragment.newInstance("Select Class for Fee Summary")
                    .show(supportFragmentManager, "TeacherSelectionDialog")
            }

            R.id.nav_teacher_attendance_report -> {
                startActivity(Intent(this, TeacherAttendanceReportActivity::class.java))
            }            R.id.nav_quick_add_subject -> {
                pendingTeacherAction = TeacherAction.ADD_SUBJECT
                TeacherSelectionDialogFragment.newInstance("Select Class Add Subject")
                    .show(supportFragmentManager, "TeacherSelectionDialog")


            }R.id.nav_manage_subjects -> {
            pendingTeacherAction = TeacherAction.MANAGE_SUBJECTS
            TeacherSelectionDialogFragment.newInstance("Select Class For Manage Subject")
                .show(supportFragmentManager, "TeacherSelectionDialog")
        }

            R.id.nav_fees_dashboard -> {
                startActivity(Intent(this, FeesDashboardActivity::class.java))
            }

            R.id.nav_download_fees_report_class -> {
                pendingTeacherAction = TeacherAction.DOWNLOAD_FEES_REPORT_CLASS
                TeacherSelectionDialogFragment.newInstance("Select Class for Fees Report")
                    .show(supportFragmentManager, "DownloadFeesReportClassDialog")
            }
            R.id.nav_bulk_add_class -> {
                pendingTeacherAction = TeacherAction.BULK_ADD_TO_CLASS
                TeacherSelectionDialogFragment.newInstance("Select Class for Bulk Add")
                    .show(supportFragmentManager, "TeacherSelectionDialog")
            }
            R.id.nav_bulk_add_org -> {
                pendingTeacherAction = TeacherAction.BULK_ADD_TO_ORG // Use the explicit state
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
                }
                pickCsvFileLauncher.launch(intent)
            }

            R.id.nav_bulk_move_students -> {
                pendingTeacherAction = TeacherAction.BULK_MOVE_STUDENTS
                TeacherSelectionDialogFragment.newInstance("Select Class to Move Students FROM")
                    .show(supportFragmentManager, "BulkMoveSourceClassDialog")
            }
            R.id.nav_download_class_sample -> {
                downloadCsvFile(SAMPLE_CLASS_CSV_FILENAME, SAMPLE_CLASS_CSV_CONTENT)
            }
            R.id.nav_download_org_sample -> {
                downloadCsvFile(SAMPLE_ORG_CSV_FILENAME, SAMPLE_ORG_CSV_CONTENT)
            }
            R.id.nav_download_teacher_sample -> {
                downloadCsvFile(SAMPLE_TEACHER_CSV_FILENAME, SAMPLE_TEACHER_CSV_CONTENT)
            }
            R.id.nav_download_org_students -> {
                requestStoragePermission {
                    downloadOrganizationStudentData()
                }
            }
            R.id.nav_download_class_students -> {
                pendingTeacherAction = TeacherAction.DOWNLOAD_CLASS_STUDENTS
                TeacherSelectionDialogFragment.newInstance("Select Class to Download Data")
                    .show(supportFragmentManager, "DownloadClassDataDialog")
            }
            R.id.nav_exams -> startActivity(Intent(this, ExamsActivity::class.java))
            R.id.nav_add_update_marks -> {
                pendingTeacherAction = TeacherAction.MANAGE_MARKS
                TeacherSelectionDialogFragment.newInstance("Select Class to Update Marks")
                    .show(supportFragmentManager, "TeacherSelectionDialog")
            }
            R.id.nav_generate_student_result -> {
                QuickFeesDialogFragment.newInstance("Generate Student Result", StudentAction.GENERATE_STUDENT_RESULT)
                    .show(supportFragmentManager, "StudentResultDialog")
            }
            R.id.nav_generate_class_result -> {
                pendingTeacherAction = TeacherAction.GENERATE_CLASS_RESULT
                TeacherSelectionDialogFragment.newInstance("Select Class for Result")
                    .show(supportFragmentManager, "TeacherResultDialog")
            }
            R.id.nav_download_marks_report -> {
                startActivity(Intent(this, MarksReportActivity::class.java))
            }


            R.id.nav_quick_add_subject -> {
                pendingTeacherAction = TeacherAction.ADD_SUBJECT
                TeacherSelectionDialogFragment.newInstance("Select Class to Add Subject")
                    .show(supportFragmentManager, "TeacherSelectionDialog")
            }
            R.id.nav_manage_subjects -> {
                pendingTeacherAction = TeacherAction.MANAGE_SUBJECTS
                TeacherSelectionDialogFragment.newInstance("Select Teacher to Manage Subjects")
                    .show(supportFragmentManager, "ManageSubjectsDialog")
            }
            R.id.nav_student_reports -> startActivity(Intent(this, ReportGeneratorActivity::class.java))
            R.id.nav_custom_student_info_report -> startActivity(Intent(this, MultiStudentReportActivity::class.java))
            R.id.nav_download_attendance_report -> {
                startActivity(Intent(this, AttendanceReportActivity::class.java))
            }
            R.id.nav_admin_profile -> {
                startActivity(Intent(this, AdminProfileActivity::class.java))
            }
            R.id.nav_logout -> showLogoutConfirmationDialog()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun getNavigationMenuItems(): List<NavigationItem> {
        return listOf(
            NavigationItem.SingleItem(R.id.nav_quick_attendance, "Attendance", R.drawable.ic_attendance_checklist),
            NavigationItem.SingleItem(R.id.nav_dashboard, "Dashboard", R.drawable.ic_dashboard),
            NavigationItem.SingleItem(R.id.nav_leaderboard, "Leaderboard", R.drawable.ic_leaderboard_trophy),
            NavigationItem.SingleItem(R.id.nav_student_profile, "Student Profile", R.drawable.ic_person_outlined),

            NavigationItem.Header(
                title = "Student Management",
                iconResId = R.drawable.ic_school,
                children = listOf(
                    NavigationItem.Child(R.id.nav_quick_add_student, "Add Student"),
                    NavigationItem.Child(R.id.nav_edit_student, "Edit Student"),
                    NavigationItem.Child(R.id.nav_delete_student, "Delete Student"),
                    NavigationItem.Child(R.id.nav_inactivate_student, "Inactivate a Student"),
                    NavigationItem.Child(R.id.nav_inactive_students_list, "View Inactive Students"),
                    NavigationItem.Child(R.id.nav_move_student, "Move Student to other Class"),
                    NavigationItem.Child(R.id.nav_view_monthly_attendance, "View Monthly Attendance"),
//                    NavigationItem.Child(R.id.nav_manage_class, "Class Management"),
                    NavigationItem.Child(R.id.nav_download_attendance_report, "Generate Attendance Report"),
                    NavigationItem.Child(R.id.nav_check_daily_attendance, "Check Attendance By Date")
                )
            ),
            NavigationItem.Header(
                title = "Teacher Management",
                iconResId = R.drawable.ic_manage_teachers,
                children = listOf(
                    NavigationItem.Child(R.id.nav_quick_add_teacher, "Add Teacher"),
                    NavigationItem.Child(R.id.nav_edit_teacher, "Edit Teacher"),
                    NavigationItem.Child(R.id.nav_teacher_attendance, "Mark Teacher Attendance"),
                    NavigationItem.Child(R.id.nav_delete_teacher, "Delete Teacher"),
                    NavigationItem.Child(R.id.nav_manage_teachers, "Manage Teachers"),
                    NavigationItem.Child(R.id.nav_teacher_monthly_attendance, "View Monthly Attendance"),
                    NavigationItem.Child(R.id.nav_teacher_attendance_report, "Teacher Attendance Report"),

//                    NavigationItem.Child(R.id.nav_bulk_add_teachers, "Bulk Add Teachers"),
                    NavigationItem.Child(R.id.nav_download_teacher_data, "Download Teacher Data")
                )
            ),
            NavigationItem.Header(
                title = "Fee Management",
                iconResId = R.drawable.ic_payments,
                children = listOf(
                    NavigationItem.Child(R.id.nav_fees_dashboard, "Fees Dashboard"),
                    NavigationItem.Child(R.id.nav_record_student_fee, "Record Student Fee"),
                    NavigationItem.Child(R.id.nav_student_fee_history, "Student Fee History"),
                    NavigationItem.Child(R.id.nav_class_fee_summary, "Class Fee Summary"),
                    NavigationItem.Child(R.id.nav_download_fees_report_class, "Download Fees Report (Class)")
                )
            ),
            NavigationItem.Header(
                title = "Exam Management",
                iconResId = R.drawable.ic_description,
                children = listOf(
                    NavigationItem.Child(R.id.nav_exams, "Manage Exams"),
                    NavigationItem.Child(R.id.nav_add_update_marks, "Add/Update Marks"),
                    NavigationItem.Child(R.id.nav_generate_student_result, "Generate Student Result"),
                    NavigationItem.Child(R.id.nav_generate_class_result, "Generate Class Result"),
                    NavigationItem.Child(R.id.nav_download_marks_report, "Marks Report")

                )
            ),
            NavigationItem.Header(
                title = "Store Management",
                iconResId = R.drawable.ic_store, // Use the new icon
                children = listOf(
                    NavigationItem.Child(R.id.nav_manage_inventory, "Manage Inventory"),
                    NavigationItem.Child(R.id.nav_sell_item, "Sell Item"),
                    NavigationItem.Child(R.id.nav_sales_history, "View Sales History")
                )
            ),
            NavigationItem.Header(
                title = "Bulk Actions",
                iconResId = R.drawable.ic_upload_file,
                children = listOf(
                    NavigationItem.Child(R.id.nav_bulk_add_class, "Bulk Add Students to Class"),
                    NavigationItem.Child(R.id.nav_bulk_add_org, "Bulk Add Students to Org"),
                    NavigationItem.Child(R.id.nav_bulk_move_students, "Bulk Move Students")

//                    NavigationItem.Child(R.id.nav_download_teacher_sample, "Download Teacher Sample CSV")
                )
            ),
            NavigationItem.Header(
                title = "Download Data",
                iconResId = R.drawable.ic_download,
                children = listOf(
                    NavigationItem.Child(R.id.nav_download_org_students, "Download All Students Data(Org)"),
                    NavigationItem.Child(R.id.nav_download_class_students, "Download Students Data(Class)"),
                    NavigationItem.Child(R.id.nav_download_class_sample, "Download Sample CSV File(Class Data)"),
                    NavigationItem.Child(R.id.nav_download_org_sample, "Download Sample CSV File(Org Data)"),
                )
            ),
            NavigationItem.Header(
                title = "Subject Management",
                iconResId = R.drawable.ic_subject_book,
                children = listOf(
                    NavigationItem.Child(R.id.nav_quick_add_subject, "Add Subject"),
                    NavigationItem.Child(R.id.nav_manage_subjects, "Manage Subject"),
                )
            ),
            NavigationItem.Header(
                title = "Prints",
                iconResId = R.drawable.ic_receipt,
                children = listOf(
                    NavigationItem.Child(R.id.nav_student_reports, "Student Info Reports"),
                    NavigationItem.Child(R.id.nav_custom_student_info_report, "Custom Student Info Report"),

                    )
            ),NavigationItem.Header(
                title = "Donation Mangement",
                iconResId = R.drawable.ic_receipt,
                children = listOf(
                    NavigationItem.Child(R.id.nav_donations_management, "Manage Donations"),
                    )
            ),
            NavigationItem.SingleItemWithDivider(R.id.nav_admin_profile,"Admin Profile",R.drawable.ic_person_outlined),
            NavigationItem.SingleItemWithDivider(R.id.nav_logout, "Logout", R.drawable.ic_logout)
        )
    }

    override fun onTeacherSelected(teacher: Teacher) {
        if (pendingStudentAction == StudentAction.MOVE_STUDENT && studentToMove != null) {
            if (teacher.teacherId == studentToMove!!.teacherId) {
                Toast.makeText(this, "Student is already in this class.", Toast.LENGTH_SHORT).show()
            } else {
                moveStudentToNewClass(studentToMove!!, TeacherSpinnerItem(teacher.teacherId, teacher.teacherName, teacher.profileImageUrl))
            }
            studentToMove = null
            pendingStudentAction = null
            return
        }

        when (pendingTeacherAction) {
            TeacherAction.BULK_ADD_TO_CLASS -> {
                selectedTeacherForBulkAdd = teacher
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
                }
                pickCsvFileLauncher.launch(intent)
            }
            TeacherAction.EDIT_TEACHER -> {
                val intent = Intent(this, EditTeacherActivity::class.java).apply {
                    putExtra("TEACHER_ID", teacher.teacherId)
                }
                studentDataChangeLauncher.launch(intent)
            }
            TeacherAction.DELETE_TEACHER -> {
                val teacherSpinnerItem = TeacherSpinnerItem(id = teacher.teacherId, name = teacher.teacherName, profileImageUrl = teacher.profileImageUrl)
                confirmDeleteTeacher(teacherSpinnerItem)
            }
            TeacherAction.VIEW_MONTHLY_ATTENDANCE -> {
                val calendar = Calendar.getInstance()
                val intent = Intent(this, TeacherMonthlyAttendanceActivity::class.java).apply {
                    putExtra("TEACHER_ID", teacher.teacherId)
                    putExtra("TEACHER_NAME", teacher.teacherName)
                    putExtra("TARGET_YEAR", calendar.get(Calendar.YEAR))
                    putExtra("TARGET_MONTH", calendar.get(Calendar.MONTH))
                }
                startActivity(intent)
            }

            TeacherAction.VIEW_ATTENDANCE -> {
                val intent = Intent(this, TeacherOptionsActivity::class.java).apply {
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_ID, teacher.teacherId)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_NAME, teacher.teacherName)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_IMAGE_URL, teacher.profileImageUrl)
                    putExtra(TeacherOptionsActivity.EXTRA_START_FRAGMENT, TeacherOptionsActivity.FRAGMENT_TAKE_ATTENDANCE)
                }
                startActivity(intent)
            }
            TeacherAction.ADD_STUDENT -> {
                val intent = Intent(this, AddStudentActivity::class.java).apply {
                    putExtra("PRESELECTED_TEACHER_ID", teacher.teacherId)
                    putExtra("PRESELECTED_TEACHER_NAME", teacher.teacherName)
                }
                studentDataChangeLauncher.launch(intent)
            }
            TeacherAction.ADD_SUBJECT -> {
                val intent = Intent(this, AddEditSubjectActivity::class.java).apply {
                    putExtra(AddEditSubjectActivity.EXTRA_TEACHER_ID_FOR_SUBJECT, teacher.teacherId)
                }
                startActivity(intent)
            }
            TeacherAction.VIEW_CLASS_FEES -> {
                val intent = Intent(this, ClassPaymentSummaryActivity::class.java).apply {
                    putExtra("TEACHER_ID", teacher.teacherId)
                    putExtra("TEACHER_NAME", teacher.teacherName)
                }
                startActivity(intent)
            }
            TeacherAction.MANAGE_MARKS, TeacherAction.GENERATE_CLASS_RESULT -> {
                showExamSelectionDialog(teacher = teacher, action = pendingTeacherAction)
            }

            TeacherAction.BULK_MOVE_STUDENTS -> {
                val intent = Intent(this, BulkMoveStudentsActivity::class.java).apply {
                    putExtra("SOURCE_TEACHER_ID", teacher.teacherId)
                    putExtra("SOURCE_TEACHER_NAME", teacher.teacherName)
                }
                studentDataChangeLauncher.launch(intent)            }
            TeacherAction.MANAGE_SUBJECTS -> {
                val intent = Intent(this, ManageSubjectsActivity::class.java).apply {
                    putExtra(ManageSubjectsActivity.EXTRA_TEACHER_ID_CONTEXT, teacher.teacherId)
                    putExtra(ManageSubjectsActivity.EXTRA_TEACHER_NAME_CONTEXT, teacher.teacherName)
                }
//                startActivity(intent)
                bulkMoveLauncher.launch(intent)
            }
            TeacherAction.MANAGE_CLASS -> {
                val intent = Intent(this, TeacherOptionsActivity::class.java).apply {
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_ID, teacher.teacherId)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_NAME, teacher.teacherName)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_IMAGE_URL, teacher.profileImageUrl)
                    putExtra(TeacherOptionsActivity.EXTRA_START_FRAGMENT, TeacherOptionsActivity.FRAGMENT_MANAGE_CLASS)
                }
                startActivity(intent)
            }
            TeacherAction.DOWNLOAD_FEES_REPORT_CLASS -> {
                val currentCalendar = Calendar.getInstance()
                showFeesReportDownloadDialog(teacher, currentCalendar.get(Calendar.MONTH), currentCalendar.get(Calendar.YEAR))
            }
            TeacherAction.DOWNLOAD_CLASS_STUDENTS -> {
                requestStoragePermission {
                    downloadClassStudentData(teacher)
                }
            }
            else -> Log.e(TAG, "onTeacherSelected called with unhandled TeacherAction: $pendingTeacherAction.")
        }
//        pendingTeacherAction = null
    }

    override fun onFeesReportGenerated(teacherId: String, teacherName: String, reportType: String, month: Int?, year: Int?) {
        // Intentionally left blank
    }

    override fun onFeeStudentSelected(student: StudentDetailsItem, action: StudentAction?) {
        when (action) {
            StudentAction.EDIT_STUDENT -> {
                val intent = Intent(this, EditStudentActivity::class.java).apply {
                    putExtra("STUDENT_ID", student.id)
                    putExtra("TEACHER_NAME", student.teacherName)
                }
                studentDataChangeLauncher.launch(intent)
            }
            StudentAction.VIEW_PROFILE -> {
                val intent = Intent(this, StudentProfileActivity::class.java).apply {
                    putExtra(StudentProfileActivity.EXTRA_STUDENT_ID, student.id)
                }
                startActivity(intent)
            }
            StudentAction.DELETE_STUDENT -> confirmDeleteStudent(student)
            StudentAction.INACTIVATE_STUDENT -> confirmInactivateStudent(student)
            StudentAction.MOVE_STUDENT -> {
                studentToMove = student
                pendingStudentAction = StudentAction.MOVE_STUDENT
                TeacherSelectionDialogFragment.newInstance("Move ${student.studentName} to:")
                    .show(supportFragmentManager, "SelectNewTeacherDialogForMove")
            }
            StudentAction.GENERATE_STUDENT_RESULT -> {
                showExamSelectionDialog(student = student, action = action)
            }

            StudentAction.SELL_ITEM -> {
                val intent = Intent(this, SellItemActivity::class.java).apply {
                    // Pass the entire selected student object to the next activity
                    putExtra(SellItemActivity.EXTRA_STUDENT, student)
                }
                startActivity(intent)
            }

            StudentAction.VIEW_FEE_HISTORY -> {
                val intent = Intent(this, StudentPaymentHistoryActivity::class.java).apply {
                    putExtra("STUDENT_ID", student.id)
                    putExtra("STUDENT_NAME", student.studentName)
                    putExtra("TEACHER_ID", student.teacherId)
                    putExtra("TEACHER_NAME", student.teacherName)
                }
                startActivity(intent)
            }
            StudentAction.VIEW_MONTHLY_ATTENDANCE -> {
                val calendar = Calendar.getInstance()
                val intent = Intent(this, StudentMonthlyAttendanceActivity::class.java).apply {
                    putExtra("STUDENT_ID", student.id)
                    putExtra("STUDENT_NAME", student.studentName)
                    putExtra("TEACHER_ID", student.teacherId)
                    putExtra("TARGET_YEAR", calendar.get(Calendar.YEAR))
                    putExtra("TARGET_MONTH", calendar.get(Calendar.MONTH))
                }
                startActivity(intent)
            }
            StudentAction.CHECK_DAILY_ATTENDANCE -> {
                showDatePickerForAttendanceCheck(student)
            }
            else -> Log.e(TAG, "onFeeStudentSelected called with unhandled action: $action")
        }
        if (action != StudentAction.MOVE_STUDENT) {
            pendingStudentAction = null
        }
    }

    private fun showDatePickerForAttendanceCheck(student: StudentDetailsItem) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }.time
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
                checkAttendanceStatus(student, dateStr)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun checkAttendanceStatus(student: StudentDetailsItem, dateStr: String) {
        val loadingDialog = StatusDialogFragment.newInstance(true, "Checking...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "loading")

        db.collection("organizations").document(FirebaseAuthManager.getOrganizationId(this)!!)
            .collection("attendanceRecords")
            .whereEqualTo("date", dateStr)
            .whereEqualTo("teacherId", student.teacherId)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                loadingDialog.dismiss()
                if (documents.isEmpty) {
                    StatusDialogFragment.newInstance(false, "Attendance not marked for this day.").show(supportFragmentManager, "statusDialog")
                } else {
                    val studentAttendances = documents.documents[0].get("studentAttendances") as? List<Map<String, Any>>
                    val studentStatus = studentAttendances?.find { it["studentId"] == student.id }
                    if (studentStatus == null) {
                        StatusDialogFragment.newInstance(false, "Student not found in this day's record.").show(supportFragmentManager, "statusDialog")
                    } else {
                        val status = studentStatus["status"] as? String
                        if (status == "Present") {
                            StatusDialogFragment.newInstance(true, "${student.studentName} was Present.").show(supportFragmentManager, "successDialog")
                        } else {
                            StatusDialogFragment.newInstance(false, "${student.studentName} was Absent.").show(supportFragmentManager, "failureDialog")
                        }
                    }
                }
            }
            .addOnFailureListener {
                loadingDialog.dismiss()
                StatusDialogFragment.newInstance(false, "Error checking status.").show(supportFragmentManager, "failureDialog")
            }
    }

    private fun showFeesReportDownloadDialog(teacher: Teacher, defaultMonth: Int, defaultYear: Int) {
        val organizationId = FirebaseAuthManager.getOrganizationId(this)
        if (organizationId == null) {
            Toast.makeText(this, "Organization ID missing.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_report_options, null)
        val radioGroupReportType: RadioGroup = dialogView.findViewById(R.id.radioGroupReportType)
        val spinnerReportMonth: Spinner = dialogView.findViewById(R.id.spinnerDialogReportMonth)
        val spinnerReportYear: Spinner = dialogView.findViewById(R.id.spinnerDialogReportYear)

        val currentCalendar = Calendar.getInstance()
        var selectedReportType = "Monthly" // Default to Monthly

        // Setup Month Spinner
        val months = SimpleDateFormat("MMMM", Locale.getDefault()).let { sdf ->
            (0..11).map {
                val cal = Calendar.getInstance()
                cal.set(Calendar.MONTH, it)
                sdf.format(cal.time)
            }
        }
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        spinnerReportMonth.adapter = monthAdapter
        spinnerReportMonth.setSelection(defaultMonth)

        // Setup Year Spinner
        val years = (currentCalendar.get(Calendar.YEAR) - 5..currentCalendar.get(Calendar.YEAR)).map { it.toString() }.reversed()
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        spinnerReportYear.adapter = yearAdapter
        val yearIndex = years.indexOf(defaultYear.toString())
        if (yearIndex != -1) {
            spinnerReportYear.setSelection(yearIndex)
        }

        // Handle RadioButton clicks to show/hide the month spinner
        radioGroupReportType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioMonthly) {
                spinnerReportMonth.visibility = View.VISIBLE
                selectedReportType = "Monthly"
            } else if (checkedId == R.id.radioYearly) {
                spinnerReportMonth.visibility = View.GONE
                selectedReportType = "Yearly"
            }
        }

        // Create and show the dialog
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Generate Fees Report for ${teacher.teacherName}")
            .setView(dialogView)
            .setPositiveButton("Generate") { _, _ ->
                // Correctly get the selected year and month
                val selectedYear = spinnerReportYear.selectedItem.toString().toInt()
                val selectedMonth = spinnerReportMonth.selectedItemPosition // This is the 0-indexed month

                val loadingDialog = StatusDialogFragment.newInstance(true, "Generating Report...").apply { isCancelable = false }
                loadingDialog.show(supportFragmentManager, "loading")

                lifecycleScope.launch {
                    val pdfUri = feesReportGenerator.generateAndSaveFeeReport(
                        teacher.teacherId,
                        teacher.teacherName,
                        organizationId,
                        selectedReportType, // Use the stateful variable
                        selectedYear,
                        // Only pass the month if the report type is Monthly
                        if (selectedReportType == "Monthly") selectedMonth else null
                    )

                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        loadingDialog.dismiss()
                        if (pdfUri != null) {
                            StatusDialogFragment.newInstance(true, "Report Generated!").show(supportFragmentManager, "successDialog")
                            openPdfFile(pdfUri)
                        } else {
                            // The Toast message is handled inside the generator,
                            // but we can also show a dialog for consistency.
                            StatusDialogFragment.newInstance(false, "No data found for the selected period.").show(supportFragmentManager, "failureDialog")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }
    private fun showExamSelectionDialog(teacher: Teacher? = null, student: StudentDetailsItem? = null, action: Any?) {
        val organizationId = FirebaseAuthManager.getOrganizationId(this) ?: return
        val title = when {
            teacher != null -> "Select Exam for ${teacher.teacherName}"
            student != null -> "Select Exam for ${student.studentName}"
            else -> "Select Exam"
        }
        db.collection("organizations").document(organizationId)
            .collection("exams").orderBy("name").get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "No exams found. Please add exams first.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                val examsList = documents.toObjects<Exam>()
                val examNames = examsList.map { it.name }
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setItems(examNames.toTypedArray()) { _, which ->
                        val selectedExam = examsList[which]
                        when (action) {
                            TeacherAction.MANAGE_MARKS -> {
                                val intent = Intent(this, ManageMarks::class.java).apply {
                                    putExtra("EXTRA_TEACHER_ID", teacher!!.teacherId)
                                    putExtra("EXTRA_EXAM_ID", selectedExam.id)
                                    putExtra("EXTRA_EXAM_NAME", selectedExam.name)
                                }
                                startActivity(intent)
                            }
                            TeacherAction.GENERATE_CLASS_RESULT -> resultViewModel.generateClassReport(teacher!!, selectedExam)
                            StudentAction.GENERATE_STUDENT_RESULT -> resultViewModel.generateSingleStudentReport(student!!, selectedExam)
                        }
                        pendingTeacherAction = null
                        pendingStudentAction = null
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .addOnFailureListener { e -> Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Logout") { _, _ ->
                FirebaseAuthManager.logout(this)
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun downloadCsvFile(fileName: String, content: String) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MadarsaReports")
                }
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outStream ->
                    outStream.write(content.toByteArray())
                    StatusDialogFragment.newInstance(true, "File Saved to Downloads!").show(supportFragmentManager, "successDialog")
                }
            } ?: run {
                StatusDialogFragment.newInstance(false, "Failed to Create File").show(supportFragmentManager, "failureDialog")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving sample CSV", e)
            StatusDialogFragment.newInstance(false, "Failed to Save File").show(supportFragmentManager, "failureDialog")
        }
    }

    private fun openPdfFile(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No application found to open PDF files.", Toast.LENGTH_SHORT).show()
        }
    }

    fun confirmDeleteTeacher(teacher: TeacherSpinnerItem) {
        val organizationId = FirebaseAuthManager.getOrganizationId(this) ?: return
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Teacher")
            .setMessage("Are you sure you want to delete ${teacher.name}? This will also delete all associated students and data!")
            .setPositiveButton("Delete") { _, _ -> deleteTeacherFromFirestore(teacher.id, teacher.profileImageUrl) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTeacherFromFirestore(teacherId: String, profileImageUrl: String?) {
        val organizationId = FirebaseAuthManager.getOrganizationId(this) ?: return
        val workingDialog = StatusDialogFragment.newInstance(true, "Deleting Teacher and Data...").apply {
            isCancelable = false
        }
        workingDialog.show(supportFragmentManager, "workingDialog")

        if (!profileImageUrl.isNullOrEmpty()) {
            try {
                storage.getReferenceFromUrl(profileImageUrl).delete()
            } catch (e: Exception) {
                Log.e(TAG, "Could not delete teacher image, proceeding with data deletion.", e)
            }
        }

        db.collection("organizations").document(organizationId)
            .collection("students").whereEqualTo("teacherId", teacherId).get()
            .addOnSuccessListener { studentSnapshot ->
                val batch = db.batch()
                studentSnapshot.documents.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().addOnCompleteListener {
                    db.collection("organizations").document(organizationId)
                        .collection("teachers").document(teacherId)
                        .delete()
                        .addOnSuccessListener {
                            workingDialog.dismiss()
                            StatusDialogFragment.newInstance(true, "Teacher Deleted Successfully").show(supportFragmentManager, "successDialog")
                            dashboardViewModel.fetchStudentListForSearch(forceRefresh = true)
                            supportFragmentManager.fragments.find { it is ManageTeachersFragment && it.isVisible }?.let {
                                (it as ManageTeachersFragment).loadTeachers()
                            }
                        }
                        .addOnFailureListener { e ->
                            workingDialog.dismiss()
                            StatusDialogFragment.newInstance(false, "Failed to Delete Teacher").show(supportFragmentManager, "failureDialog")
                            Log.e(TAG, "Error deleting teacher document", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                workingDialog.dismiss()
                StatusDialogFragment.newInstance(false, "Failed to Find Students").show(supportFragmentManager, "failureDialog")
                Log.e(TAG, "Error finding associated students for deletion", e)
            }
    }

    fun confirmDeleteStudent(student: StudentDetailsItem) {
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Student")
            .setMessage("Are you sure you want to permanently delete ${student.studentName}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteStudentFromFirestore(student.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteStudentFromFirestore(studentId: String) {
        val organizationId = FirebaseAuthManager.getOrganizationId(this) ?: return
        db.collection("organizations").document(organizationId)
            .collection("students").document(studentId).delete()
            .addOnSuccessListener {
                StatusDialogFragment.newInstance(true, "Student Deleted Successfully").show(supportFragmentManager, "successDialog")
                dashboardViewModel.fetchStudentListForSearch(forceRefresh = true)
                dashboardViewModel.refreshData()
            }
            .addOnFailureListener { e ->
                StatusDialogFragment.newInstance(false, "Failed to Delete Student").show(supportFragmentManager, "failureDialog")
                Log.e(TAG, "Error deleting student", e)
            }
    }

    private fun confirmInactivateStudent(student: StudentDetailsItem) {
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Inactivate Student")
            .setMessage("Are you sure you want to inactivate ${student.studentName}?")
            .setPositiveButton("Inactivate") { _, _ -> inactivateStudentInFirestore(student.id) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun inactivateStudentInFirestore(studentId: String) {
        val organizationId = FirebaseAuthManager.getOrganizationId(this) ?: return
        db.collection("organizations").document(organizationId)
            .collection("students").document(studentId)
            .update("isActive", false)
            .addOnSuccessListener {
                StatusDialogFragment.newInstance(true, "Student Inactivated").show(supportFragmentManager, "successDialog")
                dashboardViewModel.fetchStudentListForSearch(forceRefresh = true)
                dashboardViewModel.refreshData()
            }
            .addOnFailureListener { e ->
                StatusDialogFragment.newInstance(false, "Inactivation Failed").show(supportFragmentManager, "failureDialog")
                Log.e(TAG, "Error inactivating student", e)
            }
    }

    private fun moveStudentToNewClass(student: StudentDetailsItem, newTeacher: TeacherSpinnerItem) {
        val organizationId = FirebaseAuthManager.getOrganizationId(this) ?: return

        val loadingDialog = StatusDialogFragment.newInstance(true, "Moving student and updating all records...").apply {
            isCancelable = false
        }
        loadingDialog.show(supportFragmentManager, "movingStudentDialog")

        lifecycleScope.launch {
            try {
                val batch = db.batch()
                val originalTeacherId = student.teacherId

                // --- START: YOUR ORIGINAL, CORRECT LOGIC (RESTORED) ---

                // 1. Update the student document itself
                val studentRef = db.collection("organizations").document(organizationId)
                    .collection("students").document(student.id)
                val studentUpdates = mapOf("teacherId" to newTeacher.id, "teacherName" to newTeacher.name)
                batch.update(studentRef, studentUpdates)

                // 2. Find and update all fee payments
                val feesSnapshot = db.collection("organizations").document(organizationId)
                    .collection("feePayments").whereEqualTo("studentId", student.id).get().await()
                feesSnapshot.documents.forEach { doc ->
                    batch.update(doc.reference, mapOf("teacherId" to newTeacher.id, "teacherName" to newTeacher.name))
                }

                // 3. Find and update all exam results
                val examsSnapshot = db.collection("organizations").document(organizationId)
                    .collection("examResults").whereEqualTo("studentId", student.id).get().await()
                examsSnapshot.documents.forEach { doc ->
                    batch.update(doc.reference, mapOf("teacherId" to newTeacher.id, "teacherName" to newTeacher.name))
                }

                // 4. Find and update all relevant attendance records
                val attendanceSnapshot = db.collection("organizations").document(organizationId)
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
                        // This part of your logic was very clever and is restored.
                        // It correctly updates the teacher for the whole attendance record
                        // and the specific student's entry within the array.
                        batch.update(doc.reference, mapOf(
                            "teacherId" to newTeacher.id,
                            "teacherName" to newTeacher.name,
                            "studentAttendances" to updatedStudentAttendances
                        ))
                    }
                }
                // --- END: YOUR ORIGINAL, CORRECT LOGIC (RESTORED) ---


                // --- START: NEW CLASS HISTORY LOGIC (ADDED) ---

                val studentHistoryRef = db.collection("organizations").document(organizationId)
                    .collection("students").document(student.id)
                    .collection("studentClassHistory")

                // Find the last class record to set its end date
                val lastClassQuery = studentHistoryRef
                    .whereEqualTo("endDate", null)
                    .orderBy("startDate", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                if (!lastClassQuery.isEmpty) {
                    val lastClassDoc = lastClassQuery.documents.first()
                    batch.update(lastClassDoc.reference, "endDate", Date())
                }

                // Create the new history record for the new class
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val academicYear = "${currentYear}-${currentYear + 1}"
                val newHistoryRecord = StudentClassHistory(
                    teacherId = newTeacher.id,
                    teacherName = newTeacher.name,
                    academicYear = academicYear,
                    startDate = Date(),
                    endDate = null
                )
                val newHistoryDocRef = studentHistoryRef.document()
                batch.set(newHistoryDocRef, newHistoryRecord)
                // --- END: NEW CLASS HISTORY LOGIC (ADDED) ---

                // 5. Commit all the changes at once (both old and new logic)
                batch.commit().await()

                // Success
                if (!isFinishing) {
                    loadingDialog.dismiss()
                    StatusDialogFragment.newInstance(true, "Student moved successfully!").show(supportFragmentManager, "successDialog")
                    dashboardViewModel.refreshData()
                    sharedViewModel.notifyStudentDataChanged()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error moving student and updating records", e)
                if (!isFinishing) {
                    loadingDialog.dismiss()
                    StatusDialogFragment.newInstance(false, "Failed to move student: ${e.message}").show(supportFragmentManager, "failureDialog")
                }
            }
        }
    }

    private fun requestStoragePermission(onGrantedAction: () -> Unit) {
        this.onPermissionGranted = onGrantedAction
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onPermissionGranted?.invoke()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted?.invoke()
            } else {
                requestPermissionLauncherForDownloads.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun downloadOrganizationStudentData() {
        val organizationId = FirebaseAuthManager.getOrganizationId(this)
        if (organizationId == null) {
            StatusDialogFragment.newInstance(false, "Organization ID missing.").show(supportFragmentManager, "failureDialog")
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Exporting Data...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "loadingDialog")

        lifecycleScope.launch {
            val success = CsvDataExporter.exportOrganizationStudents(this@MainActivity, db, organizationId)
            loadingDialog.dismiss()
            if (success) {
                StatusDialogFragment.newInstance(true, "Data saved to Downloads!").show(supportFragmentManager, "successDialog")
            } else {
                StatusDialogFragment.newInstance(false, "Export Failed. No data found.").show(supportFragmentManager, "failureDialog")
            }
        }
    }

    private fun downloadClassStudentData(teacher: Teacher) {
        val organizationId = FirebaseAuthManager.getOrganizationId(this)
        if (organizationId == null) {
            StatusDialogFragment.newInstance(false, "Organization ID missing.").show(supportFragmentManager, "failureDialog")
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Exporting Class Data...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "loadingDialog")

        lifecycleScope.launch {
            val success = CsvDataExporter.exportClassStudents(this@MainActivity, db, organizationId, teacher)
            loadingDialog.dismiss()
            if (success) {
                StatusDialogFragment.newInstance(true, "Class data saved!").show(supportFragmentManager, "successDialog")
            } else {
                StatusDialogFragment.newInstance(false, "Export Failed. No data found.").show(supportFragmentManager, "failureDialog")
            }
        }
    }

    private fun downloadAllTeacherData() {
        val organizationId = FirebaseAuthManager.getOrganizationId(this)
        if (organizationId == null) {
            StatusDialogFragment.newInstance(false, "Organization ID missing.").show(supportFragmentManager, "failureDialog")
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Exporting Teacher Data...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "loadingDialog")

        lifecycleScope.launch {
            val success = CsvDataExporter.exportAllTeachers(this@MainActivity, db, organizationId)
            loadingDialog.dismiss()
            if (success) {
                StatusDialogFragment.newInstance(true, "Teacher data saved!").show(supportFragmentManager, "successDialog")
            } else {
                StatusDialogFragment.newInstance(false, "Export Failed. No data found.").show(supportFragmentManager, "failureDialog")
            }
        }
    }

    private fun setupSharedViewModelObserver() {
        sharedViewModel.studentsDataMightHaveChanged.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                // When a change is notified, force a refresh of the student list
                // which is used by the QuickFeesDialogFragment search.
                dashboardViewModel.fetchStudentListForSearch(forceRefresh = true)
            }
        }
    }
//    private fun updateNavHeader() {
//        try {
//            val customNavView = findViewById<View>(R.id.custom_nav_view)
////            val logoImageView = customNavView.findViewById<ImageView>(R.id.iv_nav_header_logo)
//
//            val logoUrl = FirebaseAuthManager.getOrganizationLogoUrl(this)
//
//            if (!logoUrl.isNullOrEmpty()) {
//                Glide.with(this)
//                    .load(logoUrl)
//                    .circleCrop()
//                    .placeholder(R.drawable.logo)
//                    .error(R.drawable.logo)
//                    .into(logoImageView)
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Could not update navigation header logo.", e)
//        }
}