package com.example.madarsa_attendance

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AttendanceDao {
    // Inserts a new record or updates it if it already exists (based on primary key)
    @Upsert
    suspend fun upsertAttendance(record: LocalAttendanceRecord)

    // Gets the saved attendance for a specific teacher on a specific date
    @Query("SELECT * FROM LocalAttendanceRecord WHERE date = :date AND teacherId = :teacherId LIMIT 1")
    suspend fun getAttendanceForDate(date: String, teacherId: String): LocalAttendanceRecord?

    // Gets all records that have not been synced to Firebase yet
    @Query("SELECT * FROM LocalAttendanceRecord WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<LocalAttendanceRecord>

    // Marks a specific record as synced after a successful upload
    @Query("UPDATE LocalAttendanceRecord SET isSynced = 1 WHERE date = :date AND teacherId = :teacherId")
    suspend fun markAsSynced(date: String, teacherId: String)
}