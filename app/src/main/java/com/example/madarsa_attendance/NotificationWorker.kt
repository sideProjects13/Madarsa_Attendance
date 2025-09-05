package com.example.madarsa_attendance.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.madarsa_attendance.FirebaseAuthManager
import com.example.madarsa_attendance.R
import com.example.madarsa_attendance.TeacherDashboardActivity // <-- ADD THIS IMPORT
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TEACHER_ID = "key_teacher_id"
        const val KEY_TEACHER_NAME = "key_teacher_name"
        private const val TAG = "NotificationWorker"
    }

    override suspend fun doWork(): Result {
        val teacherId = inputData.getString(KEY_TEACHER_ID)
        val teacherName = inputData.getString(KEY_TEACHER_NAME)
        val organizationId = FirebaseAuthManager.getOrganizationId(applicationContext)

        if (teacherId.isNullOrBlank() || teacherName.isNullOrBlank() || organizationId.isNullOrBlank()) {
            Log.e(TAG, "Work failed: Missing required data (teacherId, teacherName, or orgId).")
            return Result.failure()
        }

        Log.d(TAG, "Running check for teacher: $teacherName ($teacherId)")

        try {
            val isAttendanceMarked = hasAttendanceBeenMarked(organizationId, teacherId)

            if (!isAttendanceMarked) {
                Log.d(TAG, "Attendance NOT marked. Showing notification.")
                showNotification(teacherName)
            } else {
                Log.d(TAG, "Attendance already marked. No notification needed.")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during notification work.", e)
            return Result.retry()
        }
    }

    private suspend fun hasAttendanceBeenMarked(orgId: String, teacherId: String): Boolean {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val attendanceQuery = FirebaseFirestore.getInstance()
            .collection("organizations").document(orgId)
            .collection("attendanceRecords")
            .whereEqualTo("teacherId", teacherId)
            .whereEqualTo("date", todayStr)
            .limit(1)
            .get()
            .await()

        return !attendanceQuery.isEmpty
    }

    private fun showNotification(teacherName: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "attendance_reminder_channel"

        // --- NEW: Create an Intent to open TeacherDashboardActivity ---
        val intent = Intent(applicationContext, TeacherDashboardActivity::class.java).apply {
            // These flags ensure that tapping the notification brings the existing app to the front,
            // or creates a new one if it's not running.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // --- NEW: Create the PendingIntent ---
        // This wraps the intent, allowing the system's notification service to execute it.
        // The flags are important for ensuring the PendingIntent is created correctly.
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0, // Request code
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT // Recommended flags
        )
        // --- END OF NEW LOGIC ---

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Attendance Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to mark class attendance"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Attendance Reminder")
            .setContentText("Please mark attendance for your class: $teacherName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            // --- NEW: Set the PendingIntent on the notification ---
            .setContentIntent(pendingIntent)
            // --- END OF NEW ---
            .build()

        notificationManager.notify(teacherName.hashCode(), notification)
    }
}