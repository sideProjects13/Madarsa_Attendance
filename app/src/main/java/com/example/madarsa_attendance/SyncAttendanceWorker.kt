package com.example.madarsa_attendance.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.madarsa_attendance.StudentAttendanceItem // <-- Make sure this is imported
import com.example.madarsa_attendance.AppDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken // <-- Import TypeToken
import kotlinx.coroutines.tasks.await

class SyncAttendanceWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val db = FirebaseFirestore.getInstance()
    private val attendanceDao = AppDatabase.getDatabase(appContext).attendanceDao()

    companion object {
        const val TAG = "SyncAttendanceWorker"
    }

    override suspend fun doWork(): Result {
        val unsyncedRecords = attendanceDao.getUnsyncedRecords()
        if (unsyncedRecords.isEmpty()) {
            Log.d(TAG, "No unsynced records found. Work complete.")
            return Result.success()
        }

        Log.d(TAG, "Found ${unsyncedRecords.size} unsynced records. Starting upload...")

        var allSucceeded = true
        for (record in unsyncedRecords) {
            try {
                // --- THIS IS THE CORRECTED LOGIC ---
                // 1. Define the correct type for Gson (a List of StudentAttendanceItem)
                val listType = object : TypeToken<List<StudentAttendanceItem>>() {}.type

                // 2. Deserialize the JSON string back into a list of our custom objects
                val studentAttendanceItems: List<StudentAttendanceItem> = Gson().fromJson(record.studentAttendancesJson, listType)

                // 3. Map our list of objects to a list of Maps, which is what Firestore expects
                val studentListForFirestore = studentAttendanceItems.map { item ->
                    mapOf(
                        "studentId" to item.id,
                        "studentName" to item.name,
                        "status" to item.status
                    )
                }
                // --- END OF CORRECTION ---

                val firestoreRecord = mapOf(
                    "date" to record.date,
                    "teacherId" to record.teacherId,
                    "teacherName" to record.teacherName,
                    "organizationId" to record.organizationId,
                    "studentAttendances" to studentListForFirestore, // Use the correctly formatted list
                    "lastUpdatedAt" to FieldValue.serverTimestamp()
                )

                // Check if a document already exists for this date to either set or add
                val existingDoc = db.collection("organizations").document(record.organizationId)
                    .collection("attendanceRecords")
                    .whereEqualTo("date", record.date)
                    .whereEqualTo("teacherId", record.teacherId)
                    .limit(1)
                    .get()
                    .await()

                if (existingDoc.isEmpty) {
                    // Add new document
                    db.collection("organizations").document(record.organizationId)
                        .collection("attendanceRecords").add(firestoreRecord).await()
                } else {
                    // Update existing document
                    val docId = existingDoc.documents[0].id
                    db.collection("organizations").document(record.organizationId)
                        .collection("attendanceRecords").document(docId).set(firestoreRecord).await()
                }

                // If upload is successful, mark it as synced in the local DB
                attendanceDao.markAsSynced(record.date, record.teacherId)
                Log.d(TAG, "Successfully synced record for ${record.date}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync record for ${record.date}. Will retry later.", e)
                allSucceeded = false
            }
        }

        return if (allSucceeded) Result.success() else Result.retry()
    }
}