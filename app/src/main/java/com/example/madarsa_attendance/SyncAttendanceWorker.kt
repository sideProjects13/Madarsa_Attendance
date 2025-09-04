package com.example.madarsa_attendance.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.madarsa_attendance.AppDatabase
import com.example.madarsa_attendance.StudentAttendanceItem
import com.example.madarsa_attendance.StudentDetailsItem
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
                val listType = object : TypeToken<List<StudentAttendanceItem>>() {}.type
                val studentAttendanceItems: List<StudentAttendanceItem> = Gson().fromJson(record.studentAttendancesJson, listType)

                // --- NEW LOGIC: Refresh student names before uploading ---
                val studentIds = studentAttendanceItems.map { it.id }
                val studentsSnapshot = db.collection("organizations").document(record.organizationId)
                    .collection("students").whereIn("id", studentIds).get().await()
                val studentNamesMap = studentsSnapshot.toObjects(StudentDetailsItem::class.java).associateBy { it.id }
                // --- END OF NEW LOGIC ---

                val studentListForFirestore = studentAttendanceItems.map { item ->
                    mapOf(
                        "studentId" to item.id,
                        // Use the latest name from Firestore, or fall back to the locally stored name
                        "studentName" to (studentNamesMap[item.id]?.studentName ?: item.name),
                        "status" to item.status
                    )
                }

                val firestoreRecord = mapOf(
                    "date" to record.date,
                    "teacherId" to record.teacherId,
                    "teacherName" to record.teacherName,
                    "organizationId" to record.organizationId,
                    "studentAttendances" to studentListForFirestore,
                    "lastUpdatedAt" to FieldValue.serverTimestamp()
                )

                val existingDoc = db.collection("organizations").document(record.organizationId)
                    .collection("attendanceRecords")
                    .whereEqualTo("date", record.date)
                    .whereEqualTo("teacherId", record.teacherId)
                    .limit(1)
                    .get()
                    .await()

                if (existingDoc.isEmpty) {
                    db.collection("organizations").document(record.organizationId)
                        .collection("attendanceRecords").add(firestoreRecord).await()
                } else {
                    val docId = existingDoc.documents[0].id
                    db.collection("organizations").document(record.organizationId)
                        .collection("attendanceRecords").document(docId).set(firestoreRecord).await()
                }

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