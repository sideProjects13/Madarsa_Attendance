package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import com.cloudinary.android.MediaManager
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import java.util.HashMap

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MyApplication", "onCreate: Initializing...")

        // --- Your existing Cloudinary Initialization ---
        Log.d("MyApplication", "Initializing Cloudinary")
        val config = HashMap<String, String>()
        config["cloud_name"] = "dbvgevar0"
        config["api_key"] = "396932227925265"
        // config["api_secret"] = "your_api_secret_here" // It's good practice to keep this commented
        try {
            MediaManager.init(this, config)
            Log.d("MyApplication", "Cloudinary initialized successfully.")
        } catch (e: Exception) {
            Log.e("MyApplication", "Error initializing Cloudinary: ${e.message}", e)
        }

        // --- FIX: Secondary FirebaseApp Initialization ---
        // This is required to create teacher accounts without logging out the admin.
        try {
            // Get the configuration from the default, already-initialized Firebase app
            val options = FirebaseApp.getInstance().options

            // Initialize a new Firebase app with the same options but a unique name "secondary"
            Firebase.initialize(this, options, "secondary")
            Log.d("MyApplication", "Secondary FirebaseApp initialized successfully.")
        } catch (e: IllegalStateException) {
            // This can happen if the app process is recreated. It's safe to ignore.
            Log.w("MyApplication", "Secondary FirebaseApp was already initialized.")
        }
        // --- END OF FIX ---
    }
}