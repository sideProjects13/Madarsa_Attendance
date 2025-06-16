package com.example.madarsa_attendance

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
// Removed unused imports: Context, SharedPreferences, Handler, Looper, FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_splash) // You can still have a layout for a brief visual if desired

        // Directly navigate to ManageTeachersActivity
        val intent = Intent(this, ManageTeachersActivity::class.java).apply {
            // Clear the task stack and start a new one for ManageTeachersActivity
            // This makes ManageTeachersActivity the root of the task.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)

        // Finish SplashActivity so the user cannot navigate back to it
        finish()
    }
}