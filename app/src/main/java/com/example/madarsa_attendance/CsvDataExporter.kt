package com.example.madarsa_attendance

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvDataExporter {

    private const val TAG = "CsvDataExporter"

    suspend fun exportOrganizationStudents(context: Context, db: FirebaseFirestore, organizationId: String): Boolean {
        return try {
            val students = db.collection("organizations").document(organizationId)
                .collection("students")
                .whereEqualTo("isActive", true)
                .orderBy("teacherName")
                .orderBy("studentName")
                .get().await().toObjects(StudentDetailsItem::class.java)

            if (students.isEmpty()) {
                Log.w(TAG, "No active students found for organization export.")
                return false // Indicate failure if no data
            }

            val csvContent = generateStudentCsvContent(students, true) // Include teacher name
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "All_Students_${timestamp}.csv"
            saveCsvToFile(context, fileName, csvContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting organization students", e)
            false
        }
    }

    suspend fun exportClassStudents(context: Context, db: FirebaseFirestore, organizationId: String, teacher: Teacher): Boolean {
        return try {
            val students = db.collection("organizations").document(organizationId)
                .collection("students")
                .whereEqualTo("isActive", true)
                .whereEqualTo("teacherId", teacher.teacherId)
                .orderBy("studentName")
                .get().await().toObjects(StudentDetailsItem::class.java)

            if (students.isEmpty()) {
                Log.w(TAG, "No active students found for class export: ${teacher.teacherName}")
                return false
            }

            val csvContent = generateStudentCsvContent(students, false) // Exclude teacher name
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Class_${teacher.teacherName.replace(" ", "_")}_${timestamp}.csv"
            saveCsvToFile(context, fileName, csvContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting class students", e)
            false
        }
    }

    private fun generateStudentCsvContent(students: List<StudentDetailsItem>, includeTeacher: Boolean): String {
        val header = if (includeTeacher) {
            "\"Student Name\",\"Parent Name\",\"Parent Mobile\",\"Reg No\",\"Gender\",\"Birth Date\",\"Admission Date\",\"Monthly Fee\",\"Alternate Mobile\",\"Address\",\"Image URL\",\"Teacher Name\"\n"
        } else {
            "\"Student Name\",\"Parent Name\",\"Parent Mobile\",\"Reg No\",\"Gender\",\"Birth Date\",\"Admission Date\",\"Monthly Fee\",\"Alternate Mobile\",\"Address\",\"Image URL\"\n"
        }

        val rows = students.joinToString(separator = "\n") { student ->
            listOfNotNull(
                student.studentName,
                student.parentName,
                student.parentMobileNumber,
                student.regNo,
                student.gender,
                student.birthDate,
                student.admissionDate,
                student.monthlyFee?.toString(),
                student.alternateMobileNumber,
                student.address,
                student.profileImageUrl,
                if (includeTeacher) student.teacherName else null
            ).joinToString(separator = ",") { escapeCsvField(it) }
        }
        return header + rows
    }

    private fun escapeCsvField(data: String?): String {
        if (data == null) return ""
        val escapedData = data.replace("\"", "\"\"")
        return if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("\n")) {
            "\"$escapedData\""
        } else {
            escapedData
        }
    }

    private fun saveCsvToFile(context: Context, fileName: String, content: String): Boolean {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MadarsaReports")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create new MediaStore entry.")

            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: throw IOException("Failed to open output stream.")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error saving CSV file", e)
            false
        }
    }

    // ADD THIS FUNCTION TO CsvDataExporter.kt
    suspend fun exportAllTeachers(context: Context, db: FirebaseFirestore, organizationId: String): Boolean {
        return try {
            val teachers = db.collection("organizations").document(organizationId)
                .collection("teachers")
                .orderBy("teacherName")
                .get().await().toObjects(Teacher::class.java)

            if (teachers.isEmpty()) {
                Log.w(TAG, "No teachers found for organization export.")
                return false
            }

            val csvContent = generateTeacherCsvContent(teachers)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "All_Teachers_${timestamp}.csv"
            saveCsvToFile(context, fileName, csvContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting organization teachers", e)
            false
        }
    }

    // ADD THIS HELPER FUNCTION TO CsvDataExporter.kt
    private fun generateTeacherCsvContent(teachers: List<Teacher>): String {
        val header = "\"Teacher Name\",\"Mobile Number\",\"Email\"\n"
        val rows = teachers.joinToString(separator = "\n") { teacher ->
            listOfNotNull(
                teacher.teacherName,
                teacher.mobileNumber,
                teacher.email
            ).joinToString(separator = ",") { escapeCsvField(it) }
        }
        return header + rows
    }
}