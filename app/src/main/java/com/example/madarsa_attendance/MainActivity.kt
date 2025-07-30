package com.example.madarsa_attendance

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var bottomNavigationView: BottomNavigationView
    // No toolbar property needed anymore as it's removed from layout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting MainActivity.")

        // CRITICAL FIRST CHECK: If not logged in, redirect immediately.
        if (!FirebaseAuthManager.isLoggedInAndOrgSelected(this)) {
            Log.d(TAG, "onCreate: User not logged in or organization not selected. Redirecting to LoginActivity.")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return // Stop onCreate execution here
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate: Layout set.")

        val mainContainer = findViewById<View>(R.id.main_container)
        val fragmentContainer = findViewById<View>(R.id.fragment_container)
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // Apply insets listener to the root view
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply top padding directly to the Fragment Container for status bar space
            fragmentContainer.updatePadding(top = insets.top)

            // Apply bottom padding to the Bottom Navigation to push it up from gesture bar
            bottomNavigationView.updatePadding(bottom = insets.bottom)

            // Return the insets so other views can also use them if needed
            windowInsets
        }
        Log.d(TAG, "onCreate: Window insets listener set.")

        // Set up Bottom Navigation Listener
        bottomNavigationView.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            // No toolbar title update needed here as toolbar is removed

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
                R.id.navigation_inactive_students -> {
                    selectedFragment = InactiveStudentsFragment()
                }
            }
            if (selectedFragment != null) {
                replaceFragment(selectedFragment)
            }
            true
        }

        // Set the default fragment on initial creation
        if (savedInstanceState == null) {
            bottomNavigationView.selectedItemId = R.id.navigation_manage_teachers
            Log.d(TAG, "onCreate: Default fragment set to Dashboard.")
        }

        // IMPORTANT: If you need a logout button, you'll need to implement it within one of your fragments.
        // For example, add a 'Settings' or 'Profile' fragment with a logout button.
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // Removed onCreateOptionsMenu and onOptionsItemSelected methods as toolbar is gone.
}