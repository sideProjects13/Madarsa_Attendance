package com.example.madarsa_attendance

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects

class TeacherOptionsActivity : AppCompatActivity() { // REMOVED NavigationView.OnNavigationItemSelectedListener

    companion object {
        private const val TAG = "TeacherOptionsActivity"
        const val EXTRA_TEACHER_ID = "TEACHER_ID"
        const val EXTRA_TEACHER_NAME = "TEACHER_NAME"
        const val EXTRA_TEACHER_IMAGE_URL = "TEACHER_IMAGE_URL"
        const val EXTRA_START_FRAGMENT = "START_FRAGMENT"
        const val FRAGMENT_MANAGE_CLASS = 0 // Index for ManageClassFragment
        const val FRAGMENT_TAKE_ATTENDANCE = 1
        const val FRAGMENT_FEE_SUMMARY = 2
    }

    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentTeacherEmail: String? = null
    private var currentTeacherProfileUrl: String? = null
    private var currentOrganizationId: String? = null

    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBarLayout: AppBarLayout
    // REMOVED DrawerLayout and NavigationView
    // REMOVED ActionBarDrawerToggle

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
        // REMOVED findViewById for drawerLayout and navigationView
        tabLayout = findViewById(R.id.tabLayoutTeacherOptions)
        viewPager = findViewById(R.id.viewPagerTeacherOptions)


        // Apply window insets to the AppBarLayout and ViewPager
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(viewPager) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Adjusted padding because AppBarLayout now takes care of top inset
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        setSupportActionBar(toolbar)

        val intentTeacherId = intent.getStringExtra(EXTRA_TEACHER_ID)
        val intentTeacherName = intent.getStringExtra(EXTRA_TEACHER_NAME)
        val intentTeacherImageUrl = intent.getStringExtra(EXTRA_TEACHER_IMAGE_URL)
        val startFragmentIndex = intent.getIntExtra(EXTRA_START_FRAGMENT, FRAGMENT_MANAGE_CLASS)
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

        // REMOVED ActionBarDrawerToggle initialization and drawer listener setup
        // REMOVED navigationView.setNavigationItemSelectedListener(this)
        // REMOVED navigationView.inflateHeaderView(R.layout.teacher_options_nav_header)

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
        supportActionBar?.title = currentTeacherName // Toolbar title
        setupViewPagerAndTabs(startFragmentIndex)
    }



    private fun setupViewPagerAndTabs(startIndex: Int) {
        pagerAdapter = TeacherOptionsPagerAdapter(this, currentTeacherId!!, currentTeacherName!!)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pagerAdapter.tabTitles[position]
        }.attach()

        if (startIndex < pagerAdapter.itemCount) {
            viewPager.post { viewPager.setCurrentItem(startIndex, false) }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle the Up/Home button click
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed() // Use modern onBackPressedDispatcher
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // REMOVED onNavigationItemSelected as there is no drawer
    // REMOVED onBackPressed() method if it only handled drawer open/close,
    // if it had other logic, it should be preserved. Assuming it was only for drawer.


    // Adapter for ViewPager2
    private inner class TeacherOptionsPagerAdapter(
        activity: AppCompatActivity,
        private val teacherId: String,
        private val teacherName: String
    ) : FragmentStateAdapter(activity) {

        val tabTitles = arrayOf("Manage Class", "Take Attendance", "Fee Summary")

        override fun getItemCount(): Int = tabTitles.size

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                FRAGMENT_MANAGE_CLASS -> ManageClassFragment.newInstance(teacherId, teacherName)
                FRAGMENT_TAKE_ATTENDANCE -> TakeAttendanceFragment.newInstance(teacherId, teacherName)
                FRAGMENT_FEE_SUMMARY -> PaymentSummaryFragment.newInstance(teacherId, teacherName)
                else -> throw IllegalStateException("Invalid position $position")
            }
        }
    }
}