package com.example.madarsa_attendance

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class TeacherHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_home)

        // This code ensures that when the activity is created,
        // it loads your TeacherDashboardFragment into the container.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TeacherDashboardFragment())
                .commit()
        }
    }
}