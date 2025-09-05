package com.example.madarsa_attendance.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.madarsa_attendance.FirebaseAuthManager
import com.example.madarsa_attendance.Teacher
import com.example.madarsa_attendance.utils.AttendanceAlarmScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.tasks.await

// This worker's job is to run once a day and schedule the specific reminders for the logged-in teacher.
class DailySchedulerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "DailySchedulerWorker"

    override suspend fun doWork(): Result {
        val organizationId = FirebaseAuthManager.getOrganizationId(applicationContext)
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

        if (organizationId.isNullOrBlank() || currentUserUid.isNullOrBlank()) {
            Log.d(TAG, "User not logged in or no org ID, skipping daily schedule.")
            return Result.success() // Succeed because there's nothing to do
        }

        Log.d(TAG, "Running daily scheduler for user: $currentUserUid")

        return try {
            val db = FirebaseFirestore.getInstance()
            val teacherQuery = db.collection("organizations").document(organizationId)
                .collection("teachers")
                .whereEqualTo("uid", currentUserUid)
                .get()
                .await()

            if (!teacherQuery.isEmpty) {
                val teachers = teacherQuery.toObjects<Teacher>()
                teachers.forEach { teacher ->
                    AttendanceAlarmScheduler.scheduleReminderForTeacher(applicationContext, teacher)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run daily scheduler.", e)
            Result.retry()
        }
    }
}