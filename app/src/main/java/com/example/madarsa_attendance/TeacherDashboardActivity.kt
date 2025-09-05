package com.example.madarsa_attendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.madarsa_attendance.utils.AttendanceAlarmScheduler
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TeacherDashboardActivity : AppCompatActivity() {

    // --- Existing properties (Unchanged) ---
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ManageTeachersAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoClasses: TextView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var organizationId: String? = null
    private val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    // --- End of Existing properties ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_teacher_dashboard)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val appBarLayout: AppBarLayout = findViewById(R.id.app_bar_layout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }

        recyclerView = findViewById(R.id.recyclerViewTeacherClasses)
        progressBar = findViewById(R.id.progressBarTeacherDashboard)
        tvNoClasses = findViewById(R.id.tvNoClasses)

        setupRecyclerView()
        loadTeacherClasses()

        listenForAnnouncements()
    }

    // --- Existing listenForAnnouncements function (Unchanged) ---
    private fun listenForAnnouncements() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lastShownAnnouncementId = prefs.getString("last_announcement_id", null)

        db.collection("announcements")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { snapshot, error ->
                if (isDestroyed || isFinishing) return@addSnapshotListener
                if (error != null) {
                    Log.w("TeacherDashboard", "Listen for announcements failed.", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    val latestAnnouncement = snapshot.documents[0].toObject(Announcement::class.java)
                    if (latestAnnouncement != null && latestAnnouncement.id != lastShownAnnouncementId) {
                        AlertDialog.Builder(this)
                            .setTitle("Announcement")
                            .setMessage(latestAnnouncement.message)
                            .setPositiveButton("OK", null)
                            .show()

                        prefs.edit().putString("last_announcement_id", latestAnnouncement.id).apply()
                    }
                }
            }
    }

    // --- Existing setupRecyclerView function (Unchanged) ---
    private fun setupRecyclerView() {
        adapter = ManageTeachersAdapter(
            teachers = emptyList(),
            onTeacherCardClick = { teacher ->
                val intent = Intent(this, TeacherOptionsActivity::class.java).apply {
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_ID, teacher.id)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_NAME, teacher.name)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_IMAGE_URL, teacher.profileImageUrl)
                    putExtra(TeacherOptionsActivity.EXTRA_USER_ROLE, TeacherOptionsActivity.ROLE_TEACHER)
                }
                startActivity(intent)
            },
            onTeacherCardLongClick = { teacher ->
                showAttendanceDialog(teacher)
            },
            onEditTeacherClick = { /* No edit action from teacher dashboard */ },
            onDeleteTeacherClick = { /* No delete action from teacher dashboard */ }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    // --- Existing showAttendanceDialog function (Unchanged) ---
    private fun showAttendanceDialog(teacher: TeacherSpinnerItem) {
        val options = arrayOf("Mark Present", "Mark Absent")
        AlertDialog.Builder(this)
            .setTitle("Mark Today's Attendance for ${teacher.name}")
            .setItems(options) { _, which ->
                val status = if (which == 0) "Present" else "Absent"
                markTeacherAttendance(teacher, status)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Existing markTeacherAttendance function (Unchanged) ---
    private fun markTeacherAttendance(teacher: TeacherSpinnerItem, status: String) {
        if (organizationId == null) {
            Toast.makeText(this, "Error: Organization ID not found.", Toast.LENGTH_SHORT).show()
            return
        }
        val loadingDialog = StatusDialogFragment.newInstance(true, "Saving...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "savingAttendance")

        lifecycleScope.launch {
            try {
                val query = db.collection("organizations").document(organizationId!!)
                    .collection("teacherAttendance")
                    .whereEqualTo("teacherId", teacher.id)
                    .whereEqualTo("date", todayDateStr)
                    .limit(1)
                    .get().await()

                val attendanceRecord = hashMapOf(
                    "teacherId" to teacher.id,
                    "teacherName" to teacher.name,
                    "date" to todayDateStr,
                    "status" to status,
                    "organizationId" to organizationId!!
                )

                if (query.isEmpty) {
                    db.collection("organizations").document(organizationId!!)
                        .collection("teacherAttendance").add(attendanceRecord).await()
                } else {
                    val docId = query.documents[0].id
                    db.collection("organizations").document(organizationId!!)
                        .collection("teacherAttendance").document(docId).set(attendanceRecord).await()
                }

                loadingDialog.dismiss()
                StatusDialogFragment.newInstance(true, "Attendance marked as $status").show(supportFragmentManager, "successDialog")

            } catch (e: Exception) {
                loadingDialog.dismiss()
                StatusDialogFragment.newInstance(false, "Error: ${e.message}").show(supportFragmentManager, "errorDialog")
            }
        }
    }

    // --- MODIFIED loadTeacherClasses function ---
    private fun loadTeacherClasses() {
        val currentUser = auth.currentUser
        if (currentUser == null || organizationId == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        tvNoClasses.visibility = View.GONE

        db.collection("organizations").document(organizationId!!)
            .collection("teachers")
            .whereEqualTo("uid", currentUser.uid)
            .orderBy("teacherName", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                if (documents.isEmpty) {
                    tvNoClasses.visibility = View.VISIBLE
                } else {
                    // This part is the same
                    val teacherClassesForAdapter = documents.toObjects(Teacher::class.java).map {
                        TeacherSpinnerItem(id = it.teacherId, name = it.teacherName, profileImageUrl = it.profileImageUrl)
                    }
                    adapter.updateData(teacherClassesForAdapter)

                    // --- NEW: Schedule reminders for each class the teacher is assigned to ---
                    val teacherFullDetailsList = documents.toObjects(Teacher::class.java)
                    teacherFullDetailsList.forEach { teacher ->
                        AttendanceAlarmScheduler.scheduleReminderForTeacher(this, teacher)
                    }
                    // --- END OF NEW LOGIC ---
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                tvNoClasses.text = "Error loading classes."
                tvNoClasses.visibility = View.VISIBLE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    // --- END OF MODIFIED FUNCTION ---

    // --- Existing menu functions (Unchanged) ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.teacher_dashboard_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) {
            FirebaseAuthManager.logout(this)
            // The logout function already handles starting the LoginActivity
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}