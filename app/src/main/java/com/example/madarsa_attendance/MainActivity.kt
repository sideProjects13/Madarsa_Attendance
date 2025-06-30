package com.example.madarsa_attendance // <<< YOUR PACKAGE NAME

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This enables the edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set the content view ONCE
        setContentView(R.layout.activity_main)

        // --- START OF NEW CODE ---
        val mainContainer = findViewById<View>(R.id.main_container)
        val fragmentContainer = findViewById<View>(R.id.fragment_container)
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Apply insets listener to the root view
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 1. Apply top padding to the Fragment Container to push content down
            fragmentContainer.updatePadding(top = insets.top)

            // 2. Apply bottom padding to the Bottom Navigation to push it up
            bottomNavigationView.updatePadding(bottom = insets.bottom)

            // Return the insets so other views can also use them if needed
            windowInsets
        }
        // --- END OF NEW CODE ---

        bottomNavigationView.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    selectedFragment = DashboardFragment()
                }
                R.id.navigation_leaderboard -> {
                    selectedFragment = LeaderboardFragment()
                }
                R.id.navigation_manage_teachers -> {
                    selectedFragment = ManageTeachersFragment()
                }
                R.id.navigation_exam -> {
                    selectedFragment = ExamFragment()
                }
            }
            if (selectedFragment != null) {
                replaceFragment(selectedFragment)
            }
            true
        }

        // Set the default fragment on initial creation
        if (savedInstanceState == null) {
            bottomNavigationView.selectedItemId = R.id.navigation_dashboard
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}