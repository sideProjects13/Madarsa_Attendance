package com.example.madarsa_attendance.utils

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.madarsa_attendance.Teacher
import com.example.madarsa_attendance.worker.NotificationWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object AttendanceAlarmScheduler {

    private const val TAG = "AlarmScheduler"

    fun scheduleReminderForTeacher(context: Context, teacher: Teacher) {
        val workManager = WorkManager.getInstance(context)
        val teacherId = teacher.teacherId
        val startTime = teacher.startTime
        val endTime = teacher.endTime

        if (teacherId.isBlank() || startTime.isNullOrBlank() || endTime.isNullOrBlank()) {
            Log.w(TAG, "Cannot schedule reminder for ${teacher.teacherName}, missing data.")
            return
        }

        val midTimeDelayMillis = calculateMidTimeDelay(startTime, endTime)

        // If the calculated delay is in the past, don't schedule for today.
        if (midTimeDelayMillis < 0) {
            Log.d(TAG, "Mid-time for ${teacher.teacherName} has already passed for today. Skipping schedule.")
            return
        }

        // Pass teacher info to the NotificationWorker
        val data = Data.Builder()
            .putString(NotificationWorker.KEY_TEACHER_ID, teacherId)
            .putString(NotificationWorker.KEY_TEACHER_NAME, teacher.teacherName)
            .build()

        // Create a one-time work request that will trigger at the mid-time
        val reminderWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(midTimeDelayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(teacherId) // Tag the work with the teacher's ID
            .build()

        // Schedule the work. Use a unique name to prevent duplicates for the same teacher on the same day.
        // REPLACE ensures that if we schedule again for the same teacher today, the old one is replaced.
        val uniqueWorkName = "reminder_${teacherId}_${Calendar.getInstance().get(Calendar.DAY_OF_YEAR)}"
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            reminderWorkRequest
        )

        Log.d(TAG, "Scheduled reminder for ${teacher.teacherName} in ${midTimeDelayMillis / 60000} minutes.")
    }

    private fun calculateMidTimeDelay(startTime: String, endTime: String): Long {
        try {
            val now = Calendar.getInstance()

            val startCal = Calendar.getInstance().apply {
                val (hour, minute) = startTime.split(":").map { it.toInt() }
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

            val endCal = Calendar.getInstance().apply {
                val (hour, minute) = endTime.split(":").map { it.toInt() }
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

            val midTimeMillis = (startCal.timeInMillis + endCal.timeInMillis) / 2

            // Return the delay from now until the mid-time
            return midTimeMillis - now.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating mid-time delay for $startTime - $endTime", e)
            return -1 // Return a negative value to indicate an error or past time
        }
    }

    fun cancelReminderForTeacher(context: Context, teacherId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(teacherId)
        Log.d(TAG, "Canceled all reminders for teacher ID: $teacherId")
    }
}