package com.example.madarsa_attendance // <<< YOUR PACKAGE NAME

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

// Adjust these imports if your fragments are

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.main_toolbar)
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNavigationView.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    selectedFragment = DashboardFragment()
                    toolbar.title = "Dashboard"
                }
                R.id.navigation_leaderboard -> {
                    selectedFragment = LeaderboardFragment()
                    toolbar.title = "Leaderboard"
                }
                R.id.navigation_manage_teachers -> {
                    selectedFragment = ManageTeachersFragment()
                    toolbar.title = "Manage Teachers"
                }
                R.id.navigation_exam -> {
                    selectedFragment = ExamFragment()
                    toolbar.title = "Exam"
                }
            }
            replaceFragment(selectedFragment!!)
            true
        }

        // Set the default fragment and toolbar title on initial creation
        if (savedInstanceState == null) {
            bottomNavigationView.selectedItemId = R.id.navigation_dashboard
            toolbar.title = "Dashboard"
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}