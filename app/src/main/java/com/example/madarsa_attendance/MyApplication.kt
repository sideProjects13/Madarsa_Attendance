package com.example.madarsa_attendance

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cloudinary.android.MediaManager
import com.example.madarsa_attendance.worker.DailySchedulerWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import java.util.HashMap
import java.util.concurrent.TimeUnit

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MyApplication", "onCreate: Initializing...")

        // --- Your existing Cloudinary Initialization (Unchanged) ---
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

        // --- Your existing Secondary FirebaseApp Initialization (Unchanged) ---
        try {
            val options = FirebaseApp.getInstance().options
            Firebase.initialize(this, options, "secondary")
            Log.d("MyApplication", "Secondary FirebaseApp initialized successfully.")
        } catch (e: IllegalStateException) {
            Log.w("MyApplication", "Secondary FirebaseApp was already initialized.")
        }

        // --- NEW: Schedule the daily worker to set reminders ---
        scheduleDailyReminderScheduler()
        // --- END OF NEW LOGIC ---
    }

    // --- NEW FUNCTION TO SCHEDULE THE DAILY WORKER ---
    private fun scheduleDailyReminderScheduler() {
        // You can define constraints for the work, e.g., it should only run when connected to the internet.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create a periodic work request to run roughly once every 24 hours.
        // WorkManager will optimize this to save battery, so it might not be at the exact same time.
        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailySchedulerWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        // Enqueue the work as unique. This is very important.
        // It ensures that you don't accidentally schedule multiple copies of the same daily job.
        // `ExistingPeriodicWorkPolicy.KEEP` means if a job with this name is already scheduled, do nothing.
        // If it's not scheduled (e.g., after a fresh install or reboot), schedule it.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyAttendanceScheduler",
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest
        )

        Log.d("MyApplication", "Daily reminder scheduler work enqueued.")
    }
    // --- END OF NEW FUNCTION ---
}