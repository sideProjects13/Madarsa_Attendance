package com.example.madarsa_attendance

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// --- Your Existing Models (Unchanged) ---

data class TeacherSpinnerItem(
    val id: String,
    val name: String,
    val profileImageUrl: String? = null
) {
    override fun toString(): String = name
}

data class StudentDetailsItem(
    @DocumentId val id: String = "",
    val studentName: String = "",
    val teacherId: String = "",
    val teacherName: String? = null,
    val parentName: String? = null, // Corrected from String
    val parentMobileNumber: String? = null, // Corrected from String
    val profileImageUrl: String? = null,
    val createdAt: Timestamp? = null,
    val regNo: String? = null,
    val gender: String? = null,
    val admissionDate: String? = null,
    val birthDate: String? = null
)

data class StudentAttendanceItem(
    val id: String,
    val name: String,
    var status: String = "Present",
    val profileImageUrl: String? = null
)

data class LeaderboardItem(
    val studentId: String,
    val studentName: String,
    val presentDays: Int,
    val absentDays: Int,
    val totalMarkedDays: Int,
    val attendancePercentage: Double,
    val teacherName: String
)

data class StudentPaymentSummaryItem(
    val studentId: String,
    val studentName: String,
    val totalPaidThisMonth: Double,
    val paymentCountThisMonth: Int,
    val profileImageUrl: String? = null
)

data class FeePaymentItem(
    @DocumentId val id: String = "",
    val paymentAmount: Double = 0.0,
    val paymentDate: String = "",
    val paymentMode: String? = null,
    val notes: String? = null,
    val recordedAt: Timestamp? = null
)

data class DailyAttendanceStatus(
    val date: String,
    val status: String
)

// REVERTED: Kept your original SubjectItem name to avoid breaking changes.
data class SubjectItem(
    @DocumentId val id: String = "",
    val subjectName: String = "",
    val teacherId: String? = null,
    val description: String? = null
)

// --- Models for Exams & Marks Feature ---

/**
 * Represents a single Student document in Firestore.
 * This is a new, simplified model for this specific feature.
 */
data class Student(
    @DocumentId val id: String = "",
    val studentName: String = "",
    val teacherId: String = ""
)

/**
 * Represents a single Exam document in Firestore.
 */
data class Exam(
    @DocumentId val id: String = "",
    val name: String = ""
)

/**
 * A helper data class for the marks entry adapter.
 * It is NOT a direct Firestore model. It combines a student with their marks for a specific exam.
 */
data class StudentMarks(
val student: StudentDetailsItem, // Corrected from `Student` to `StudentDetailsItem`
var marks: MutableMap<String, String> = mutableMapOf()
)