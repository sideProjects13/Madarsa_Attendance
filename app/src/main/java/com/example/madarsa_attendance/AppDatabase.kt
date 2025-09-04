package com.example.madarsa_attendance

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// 1. Only include the LocalAttendanceRecord entity.
// 2. Set the version back to 1.
@Database(
    entities = [LocalAttendanceRecord::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(StudentListConverter::class) // Only the converter for attendance is needed
abstract class AppDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "madarsa_database"
                )
                    // This will destroy the old, complex database (version 5)
                    // and create a new, simple one (version 1).
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}