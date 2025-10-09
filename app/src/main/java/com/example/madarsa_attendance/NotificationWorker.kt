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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.madarsa_attendance.FirebaseAuthManager
import com.example.madarsa_attendance.R
import com.example.madarsa_attendance.TeacherDashboardActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
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
        val teacherDocumentId = inputData.getString(KEY_TEACHER_ID)
        val teacherName = inputData.getString(KEY_TEACHER_NAME)
        val organizationId = FirebaseAuthManager.getOrganizationId(applicationContext)

        if (teacherDocumentId.isNullOrBlank() || teacherName.isNullOrBlank() || organizationId.isNullOrBlank()) {
            Log.e(TAG, "Work failed: Missing required data (teacherId, teacherName, or orgId).")
            return Result.failure()
        }

        Log.d(TAG, "Running daily check for teacher: $teacherName ($teacherDocumentId)")

        try {
            val db = FirebaseFirestore.getInstance()
            val teacherDocRef = db.collection("organizations").document(organizationId)
                .collection("teachers").document(teacherDocumentId)

            val teacherSnapshot = teacherDocRef.get().await()

            if (!teacherSnapshot.exists()) {
                Log.e(TAG, "Teacher document $teacherDocumentId not found. Stopping and cancelling work.")
                // If the teacher has been deleted from Firestore, cancel this recurring job.
                WorkManager.getInstance(applicationContext).cancelUniqueWork("attendance_check_$teacherDocumentId")
                return Result.failure()
            }

            val startTime = teacherSnapshot.getString("startTime") ?: "" // "HH:mm" format
            val endTime = teacherSnapshot.getString("endTime") ?: ""     // "HH:mm" format

            // Check if the current time is within the teacher's class schedule
            if (!isCurrentTimeInWindow(startTime, endTime)) {
                Log.d(TAG, "Not within class time ($startTime - $endTime) for $teacherName. No notification needed now.")
                return Result.success() // Success, because the check was performed correctly.
            }

            // If it IS class time, now check if attendance has been marked
            val isAttendanceMarked = hasAttendanceBeenMarked(organizationId, teacherDocumentId)

            if (!isAttendanceMarked) {
                Log.d(TAG, "Attendance NOT marked for $teacherName. Showing notification.")
                showNotification(teacherName)
            } else {
                Log.d(TAG, "Attendance already marked for $teacherName. No notification needed.")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during notification work for $teacherName.", e)
            return Result.retry() // Retry if there was a network error, etc.
        }
    }

    /**
     * Checks if the current time is between the provided start and end time strings.
     * @param startTimeStr Time in "HH:mm" (24-hour) format.
     * @param endTimeStr Time in "HH:mm" (24-hour) format.
     */
    private fun isCurrentTimeInWindow(startTimeStr: String, endTimeStr: String): Boolean {
        if (startTimeStr.isBlank() || endTimeStr.isBlank() || !startTimeStr.contains(":") || !endTimeStr.contains(":")) {
            return false // Invalid time format, don't send notification
        }

        return try {
            val startHour = startTimeStr.split(":")[0].toInt()
            val startMinute = startTimeStr.split(":")[1].toInt()
            val endHour = endTimeStr.split(":")[0].toInt()
            val endMinute = endTimeStr.split(":")[1].toInt()

            val now = Calendar.getInstance()

            val startTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
            }

            val endTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, endHour)
                set(Calendar.MINUTE, endMinute)
                set(Calendar.SECOND, 0)
            }

            // Check if current time is after start time AND before end time
            now.after(startTime) && now.before(endTime)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing start/end time", e)
            false
        }
    }

    private suspend fun hasAttendanceBeenMarked(orgId: String, teacherDocId: String): Boolean {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val attendanceQuery = FirebaseFirestore.getInstance()
            .collection("organizations").document(orgId)
            .collection("attendanceRecords")
            .whereEqualTo("teacherId", teacherDocId) // IMPORTANT: This must be the document ID
            .whereEqualTo("date", todayStr)
            .limit(1)
            .get()
            .await()

        return !attendanceQuery.isEmpty
    }

    private fun showNotification(teacherName: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "attendance_reminder_channel"

        val intent = Intent(applicationContext, TeacherDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

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
            .setContentIntent(pendingIntent)
            .build()

        // Use teacherName hashcode to ensure each teacher gets their own unique notification
        notificationManager.notify(teacherName.hashCode(), notification)
    }
}