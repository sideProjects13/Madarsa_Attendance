package com.example.madarsa_attendance

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore

class TeacherOptionsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TeacherOptionsActivity"
        const val EXTRA_TEACHER_ID = "TEACHER_ID"
        const val EXTRA_TEACHER_NAME = "TEACHER_NAME"
        const val EXTRA_TEACHER_IMAGE_URL = "TEACHER_IMAGE_URL"
        const val EXTRA_START_FRAGMENT = "START_FRAGMENT"

        // Constants for identifying the user's role
        const val EXTRA_USER_ROLE = "USER_ROLE"
        const val ROLE_ADMIN = "ADMIN"
        const val ROLE_TEACHER = "TEACHER"

        // Fragment indices for Admin view
        const val FRAGMENT_MANAGE_CLASS = 0
        const val FRAGMENT_TAKE_ATTENDANCE = 1
        const val FRAGMENT_FEE_SUMMARY = 2
    }

    private var userRole: String = ROLE_ADMIN // Default to Admin for safety
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentTeacherEmail: String? = null
    private var currentTeacherProfileUrl: String? = null
    private var currentOrganizationId: String? = null

    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: TeacherOptionsPagerAdapter

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_teacher_options)

        db = FirebaseFirestore.getInstance()

        toolbar = findViewById(R.id.teacher_options_toolbar)
        appBarLayout = findViewById(R.id.app_bar_layout_teacher_options)
        tabLayout = findViewById(R.id.tabLayoutTeacherOptions)
        viewPager = findViewById(R.id.viewPagerTeacherOptions)

        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(viewPager) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        setSupportActionBar(toolbar)

        val intentTeacherId = intent.getStringExtra(EXTRA_TEACHER_ID)
        val intentTeacherName = intent.getStringExtra(EXTRA_TEACHER_NAME)
        val intentTeacherImageUrl = intent.getStringExtra(EXTRA_TEACHER_IMAGE_URL)
        var startFragmentIndex = intent.getIntExtra(EXTRA_START_FRAGMENT, FRAGMENT_MANAGE_CLASS)

        userRole = intent.getStringExtra(EXTRA_USER_ROLE) ?: ROLE_ADMIN
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)

        if (intentTeacherId == null || intentTeacherName == null) {
            Log.e(TAG, "Critical: Teacher ID or Name not passed. Finishing.")
            Toast.makeText(this, "Error: Teacher information not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (currentOrganizationId == null) {
            Toast.makeText(this, "Organization information missing. Please log in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentTeacherId = intentTeacherId
        currentTeacherName = intentTeacherName
        currentTeacherProfileUrl = intentTeacherImageUrl

        // Adjust start fragment index for teachers, as their tab order is different
        if (userRole == ROLE_TEACHER) {
            startFragmentIndex = when (startFragmentIndex) {
                FRAGMENT_TAKE_ATTENDANCE -> 0 // Teacher's first tab
                FRAGMENT_MANAGE_CLASS -> 1 // Teacher's second tab
                else -> 0 // Default to first tab for teacher
            }
        }

        fetchTeacherDetailsFromFirestore(currentTeacherId!!, startFragmentIndex)
    }

    private fun fetchTeacherDetailsFromFirestore(teacherIdToFetch: String, startFragmentIndex: Int) {
        if (currentOrganizationId == null) {
            Log.e(TAG, "fetchTeacherDetailsFromFirestore: Org ID is null, cannot fetch teacher details.")
            Toast.makeText(this, "Organization context missing for teacher profile.", Toast.LENGTH_LONG).show()
            initializeUiElements(startFragmentIndex)
            return
        }
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers").document(teacherIdToFetch).get()
            .addOnSuccessListener { document ->
                if (isDestroyed || isFinishing) return@addOnSuccessListener
                if (document != null && document.exists()) {
                    currentTeacherName = document.getString("teacherName") ?: currentTeacherName
                    currentTeacherEmail = document.getString("email")
                    currentTeacherProfileUrl = document.getString("profileImageUrl") ?: currentTeacherProfileUrl
                }
                initializeUiElements(startFragmentIndex)
            }
            .addOnFailureListener {
                if (isDestroyed || isFinishing) return@addOnFailureListener
                Toast.makeText(this, "Error fetching teacher profile.", Toast.LENGTH_LONG).show()
                initializeUiElements(startFragmentIndex)
            }
    }

    private fun initializeUiElements(startFragmentIndex: Int) {
        if (currentTeacherId == null || currentTeacherName == null) {
            finish()
            return
        }
        supportActionBar?.title = currentTeacherName
        setupViewPagerAndTabs(startFragmentIndex)
    }

    private fun setupViewPagerAndTabs(startIndex: Int) {
        pagerAdapter = TeacherOptionsPagerAdapter(this, currentTeacherId!!, currentTeacherName!!, userRole)
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pagerAdapter.getTabTitle(position)
        }.attach()

        if (startIndex < pagerAdapter.itemCount) {
            viewPager.post { viewPager.setCurrentItem(startIndex, false) }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private inner class TeacherOptionsPagerAdapter(
        activity: AppCompatActivity,
        private val teacherId: String,
        private val teacherName: String,
        private val role: String
    ) : FragmentStateAdapter(activity) {

        private val adminTabTitles = arrayOf("Manage Class", "Take Attendance", "Fee Summary")
        private val teacherTabTitles = arrayOf("Take Attendance", "Manage Class")

        fun getTabTitle(position: Int): String {
            return if (role == ROLE_ADMIN) {
                adminTabTitles[position]
            } else {
                teacherTabTitles[position]
            }
        }

        override fun getItemCount(): Int {
            return if (role == ROLE_ADMIN) {
                adminTabTitles.size
            } else {
                teacherTabTitles.size
            }
        }

        override fun createFragment(position: Int): Fragment {
            return if (role == ROLE_ADMIN) {
                // Admin logic: 3 tabs
                when (position) {
                    FRAGMENT_MANAGE_CLASS -> ManageClassFragment.newInstance(teacherId, teacherName)
                    FRAGMENT_TAKE_ATTENDANCE -> TakeAttendanceFragment.newInstance(teacherId, teacherName)
                    FRAGMENT_FEE_SUMMARY -> PaymentSummaryFragment.newInstance(teacherId, teacherName)
                    else -> throw IllegalStateException("Invalid position $position for Admin")
                }
            } else {
                // Teacher logic: 2 tabs
                when (position) {
                    0 -> TakeAttendanceFragment.newInstance(teacherId, teacherName)
                    1 -> ManageClassFragment.newInstance(teacherId, teacherName)
                    else -> throw IllegalStateException("Invalid position $position for Teacher")
                }
            }
        }
    }
}