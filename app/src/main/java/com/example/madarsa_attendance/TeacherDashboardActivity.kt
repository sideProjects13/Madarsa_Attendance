package com.example.madarsa_attendance

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
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ManageTeachersAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoClasses: TextView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var organizationId: String? = null
    private val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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
    }

    private fun setupRecyclerView() {
        adapter = ManageTeachersAdapter(
            teachers = emptyList(),
            onTeacherCardClick = { teacher ->
                val intent = Intent(this, TeacherOptionsActivity::class.java).apply {
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_ID, teacher.id)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_NAME, teacher.name)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_IMAGE_URL, teacher.profileImageUrl)
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
                    val teacherClasses = documents.toObjects(Teacher::class.java).map {
                        TeacherSpinnerItem(id = it.teacherId, name = it.teacherName, profileImageUrl = it.profileImageUrl)
                    }
                    adapter.updateData(teacherClasses)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                tvNoClasses.text = "Error loading classes."
                tvNoClasses.visibility = View.VISIBLE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.teacher_dashboard_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) {
            FirebaseAuthManager.logout(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}