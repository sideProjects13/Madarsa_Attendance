package com.example.madarsa_attendance

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class TeacherOptionsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEACHER_ID = "teacher_id"
        const val EXTRA_TEACHER_NAME = "teacher_name"
        const val EXTRA_TEACHER_IMAGE_URL = "teacher_image_url"
        const val EXTRA_START_FRAGMENT = "start_fragment"
        const val EXTRA_USER_ROLE = "user_role"

        const val FRAGMENT_TAKE_ATTENDANCE = "take_attendance"
        const val FRAGMENT_MANAGE_CLASS = "manage_class"
        const val FRAGMENT_FEES_DATA = "fees_data"

        const val ROLE_ADMIN = "admin"
        const val ROLE_TEACHER = "teacher"
    }

    private lateinit var teacherId: String
    private lateinit var teacherName: String
    private lateinit var userRole: String
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var pagerAdapter: TeacherOptionsPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_options)

        teacherId = intent.getStringExtra(EXTRA_TEACHER_ID) ?: ""
        teacherName = intent.getStringExtra(EXTRA_TEACHER_NAME) ?: "Unknown"
        userRole = intent.getStringExtra(EXTRA_USER_ROLE) ?: ROLE_ADMIN
        val startFragment = intent.getStringExtra(EXTRA_START_FRAGMENT)

        initViews()
        setupToolbar()
        setupViewPager()

        if (startFragment != null) {
            val tabIndex = pagerAdapter.getFragmentIndex(startFragment)
            if (tabIndex != -1) viewPager.currentItem = tabIndex
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.teacher_options_toolbar)
        viewPager = findViewById(R.id.viewPagerTeacherOptions)
        tabLayout = findViewById(R.id.tabLayoutTeacherOptions)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = teacherName
    }

    private fun setupViewPager() {
        pagerAdapter = TeacherOptionsPagerAdapter(this, teacherId, teacherName, userRole)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pagerAdapter.getPageTitle(position)
        }.attach()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private class TeacherOptionsPagerAdapter(
        fragmentActivity: FragmentActivity,
        private val teacherId: String,
        private val teacherName: String,
        private val userRole: String
    ) : FragmentStateAdapter(fragmentActivity) {

        private val tabs = mutableListOf<Pair<String, String>>().apply {
            // 1. Attendance (Everyone sees this)
            add(FRAGMENT_TAKE_ATTENDANCE to "Attendance")

            // 2. Manage Class (Everyone sees this now - Teachers need to view their students)
            add(FRAGMENT_MANAGE_CLASS to "Manage Class")

            // 3. Fees Data (Only Admin sees this)
            if (userRole == ROLE_ADMIN) {
                add(FRAGMENT_FEES_DATA to "Fees Data")
            }
        }

        override fun getItemCount(): Int = tabs.size

        override fun createFragment(position: Int): Fragment {
            val (tag, _) = tabs[position]
            return when (tag) {
                FRAGMENT_TAKE_ATTENDANCE -> TakeAttendanceFragment.newInstance(teacherId, teacherName)
                FRAGMENT_MANAGE_CLASS -> ManageClassFragment.newInstance(teacherId, teacherName)
                FRAGMENT_FEES_DATA -> PaymentSummaryFragment.newInstance(teacherId, teacherName)
                else -> throw IllegalArgumentException("Invalid position")
            }
        }

        fun getPageTitle(position: Int): String = tabs[position].second
        fun getFragmentIndex(tag: String): Int = tabs.indexOfFirst { it.first == tag }
    }
}