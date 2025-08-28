package com.example.madarsa_attendance

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ManageTeachersAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoClasses: TextView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var organizationId: String? = null

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
                // When a teacher clicks on one of their classes, open the options activity
                val intent = Intent(this, TeacherOptionsActivity::class.java).apply {
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_ID, teacher.id)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_NAME, teacher.name)
                    putExtra(TeacherOptionsActivity.EXTRA_TEACHER_IMAGE_URL, teacher.profileImageUrl)
                }
                startActivity(intent)
            },
            onEditTeacherClick = { /* No edit action from teacher dashboard */ },
            onDeleteTeacherClick = { /* No delete action from teacher dashboard */ }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
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