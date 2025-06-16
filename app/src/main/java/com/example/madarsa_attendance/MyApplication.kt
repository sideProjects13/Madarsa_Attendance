package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import com.cloudinary.android.MediaManager
import java.util.HashMap // Ensure this import is present


class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MyApplication", "onCreate: Initializing Cloudinary")

        // Initialize Cloudinary
        // !! IMPORTANT: For security, do NOT hardcode API Secret in client-side code for production.
        // For production, you'd typically use "unsigned uploads" or have a backend generate signatures.
        // For development/learning, you can include it here, but be aware of the risk.
        val config = HashMap<String, String>()
        config["cloud_name"] = "dbvgevar0"
        config["api_key"] = "396932227925265"
        //config["api_secret"] = "BxY9cTH6W4elZRyOcQ9fEpLOazs" // BE CAREFUL WITH THIS
        try {
            MediaManager.init(this, config)
            Log.d("MyApplication", "Cloudinary initialized successfully.")
        } catch (e: Exception) {
            Log.e("MyApplication", "Error initializing Cloudinary: ${e.message}", e)
        }
    }
}