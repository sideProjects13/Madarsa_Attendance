package com.example.madarsa_attendance

import androidx.room.Entity
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.madarsa_attendance.StudentAttendanceItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// This defines the table in your local SQLite database
@Entity(primaryKeys = ["date", "teacherId"])
data class LocalAttendanceRecord(
    val date: String,
    val teacherId: String,
    val teacherName: String,
    val organizationId: String,

    // We will store the list of students as a JSON string
    val studentAttendancesJson: String,

    // This flag tracks if the record has been uploaded to Firebase yet
    var isSynced: Boolean = false
)

// This class helps Room convert the list of students to/from a JSON string
class StudentListConverter {
    @TypeConverter
    fun fromString(value: String?): List<StudentAttendanceItem>? {
        if (value == null) return null
        val listType = object : TypeToken<List<StudentAttendanceItem>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<StudentAttendanceItem>?): String? {
        if (list == null) return null
        return Gson().toJson(list)
    }
}