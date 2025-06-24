package com.example.madarsa_attendance

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore

class TeacherOptionsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private companion object {
        private const val TAG = "TeacherOptionsActivity"
        const val EXTRA_TEACHER_ID = "TEACHER_ID"
        const val EXTRA_TEACHER_NAME = "TEACHER_NAME"
        const val EXTRA_TEACHER_IMAGE_URL = "TEACHER_IMAGE_URL"
    }

    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentTeacherEmail: String? = null
    private var currentTeacherProfileUrl: String? = null

    private lateinit var toolbar: MaterialToolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: TeacherOptionsPagerAdapter

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_options)

        db = FirebaseFirestore.getInstance()

        val intentTeacherId = intent.getStringExtra(EXTRA_TEACHER_ID)
        val intentTeacherName = intent.getStringExtra(EXTRA_TEACHER_NAME)
        val intentTeacherImageUrl = intent.getStringExtra(EXTRA_TEACHER_IMAGE_URL)

        if (intentTeacherId == null || intentTeacherName == null) {
            Log.e(TAG, "Critical: Teacher ID or Name not passed. Finishing.")
            Toast.makeText(this, "Error: Teacher information not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentTeacherId = intentTeacherId
        currentTeacherName = intentTeacherName
        currentTeacherProfileUrl = intentTeacherImageUrl

        toolbar = findViewById(R.id.teacher_options_toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout_teacher_options)
        navigationView = findViewById(R.id.navigation_view_teacher_options)

        actionBarDrawerToggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.nav_open, R.string.nav_close)
        drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)

        tabLayout = findViewById(R.id.tabLayoutTeacherOptions)
        viewPager = findViewById(R.id.viewPagerTeacherOptions)

        fetchTeacherDetailsFromFirestore(currentTeacherId!!)
    }

    private fun fetchTeacherDetailsFromFirestore(teacherIdToFetch: String) {
        db.collection("teachers").document(teacherIdToFetch).get()
            .addOnSuccessListener { document ->
                if (isDestroyed || isFinishing) return@addOnSuccessListener
                if (document != null && document.exists()) {
                    currentTeacherName = document.getString("teacherName") ?: currentTeacherName
                    currentTeacherEmail = document.getString("email")
                    currentTeacherProfileUrl = document.getString("profileImageUrl") ?: currentTeacherProfileUrl
                }
                initializeUiElements()
            }
            .addOnFailureListener {
                if (isDestroyed || isFinishing) return@addOnFailureListener
                Toast.makeText(this, "Error fetching teacher profile.", Toast.LENGTH_LONG).show()
                initializeUiElements()
            }
    }

    private fun initializeUiElements() {
        if (currentTeacherId == null || currentTeacherName == null) {
            finish()
            return
        }
        supportActionBar?.title = currentTeacherName
        updateNavHeader()
        setupViewPagerAndTabs()
    }

    private fun updateNavHeader() {
        val headerView = navigationView.getHeaderView(0)
        val navHeaderName: TextView = headerView.findViewById(R.id.tvNavHeaderName)
        val navHeaderEmail: TextView = headerView.findViewById(R.id.tvNavHeaderEmail)
        val navHeaderProfileImage: ImageView = headerView.findViewById(R.id.ivNavHeaderProfile)

        navHeaderName.text = currentTeacherName
        navHeaderEmail.text = currentTeacherEmail ?: "Email not available"

        if (!currentTeacherProfileUrl.isNullOrEmpty()) {
            Glide.with(this).load(currentTeacherProfileUrl).circleCrop()
                .placeholder(R.drawable.logo).error(R.drawable.logo).into(navHeaderProfileImage)
        } else {
            navHeaderProfileImage.setImageResource(R.drawable.logo)
        }
    }

    private fun setupViewPagerAndTabs() {
        pagerAdapter = TeacherOptionsPagerAdapter(this, currentTeacherId!!, currentTeacherName!!)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pagerAdapter.tabTitles[position]
        }.attach()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) true else super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (currentTeacherId == null || currentTeacherName == null) {
            Toast.makeText(this, "Error: Teacher context lost.", Toast.LENGTH_SHORT).show()
            drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }

        when (item.itemId) {
            R.id.nav_manage_subjects -> {
                val intent = Intent(this, ManageSubjectsActivity::class.java).apply {
                    putExtra(ManageSubjectsActivity.EXTRA_TEACHER_ID_CONTEXT, currentTeacherId)
                    putExtra(ManageSubjectsActivity.EXTRA_TEACHER_NAME_CONTEXT, currentTeacherName)
                }
                startActivity(intent)
            }
            R.id.nav_add_update_marks -> {
                showExamSelectionDialog()
            }
            R.id.nav_overall_leaderboard -> {
                val intent = Intent(this, ClassLeaderboardActivity::class.java).apply {
                    putExtra("TEACHER_ID", currentTeacherId)
                    putExtra("TEACHER_NAME", currentTeacherName)
                }
                startActivity(intent)
            }
            R.id.nav_inactive_students -> {
                val intent = Intent(this, InactiveStudentsActivity::class.java).apply {
                    putExtra("TEACHER_ID", currentTeacherId)
                    putExtra("TEACHER_NAME", currentTeacherName)
                }
                startActivity(intent)
            }
            R.id.nav_profile -> {
                val profileIntent = Intent(this, EditTeacherActivity::class.java).apply {
                    putExtra("TEACHER_ID", currentTeacherId)
                }
                startActivity(profileIntent)
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showExamSelectionDialog() {
        if (currentTeacherId == null) return

        val examsList = mutableListOf<Exam>()
        val examNames = mutableListOf<String>()

        db.collection("exams").orderBy("name").get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "No exams found. Add an exam first.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                documents.forEach { doc -> examsList.add(doc.toObject(Exam::class.java)) }
                examNames.addAll(examsList.map { it.name })

                AlertDialog.Builder(this)
                    .setTitle("Select Exam")
                    .setItems(examNames.toTypedArray()) { _, which ->
                        val selectedExam = examsList[which]
                        val intent = Intent(this, ManageMarks::class.java).apply {
                            putExtra("EXTRA_TEACHER_ID", currentTeacherId)
                            putExtra("EXTRA_EXAM_ID", selectedExam.id)
                            putExtra("EXTRA_EXAM_NAME", selectedExam.name)
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching exams", e)
                Toast.makeText(this, "Error fetching exams.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}