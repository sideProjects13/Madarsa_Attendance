    package com.example.madarsa_attendance

    import com.google.firebase.Timestamp
    import com.google.firebase.firestore.DocumentId
    import java.util.Date

    data class Teacher(
        @DocumentId val teacherId: String = "",
        val teacherName: String = "",
        val mobileNumber: String? = null, // Ensure this field exists
        val email: String? = null, // This is the login email
        val profileImageUrl: String? = null,
        val uid: String? = null // This will store the Firebase Auth UID
    )

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
        val parentName: String? = null,
        val parentMobileNumber: String? = null,
        val profileImageUrl: String? = null,
        val createdAt: Timestamp? = null,
        val regNo: String? = null,
        val gender: String? = null,
        val admissionDate: String? = null,
        val birthDate: String? = null,
        val isActive: Boolean = true,
        val monthlyFee: Double? = null,
        val alternateMobileNumber: String? = null,
        val address: String? = null
    )

    data class Student(
        @DocumentId val id: String = "",
        val studentName: String = "",
        val teacherId: String = ""
    )

    // --- CORRECTED DATA CLASS FOR FEE PAYMENTS ---
    // This version includes the missing 'paymentMode' and 'notes' fields.
    data class FeePaymentItem(
        @DocumentId val id: String = "",
        val studentId: String = "",
        val teacherId: String = "",
        val studentName: String? = null,
        val teacherName: String? = null,
        val paymentAmount: Double = 0.0,
        val paymentDate: Date? = null,
        val paymentMonth: String = "",
        val paymentYear: Int = 0,
        val paymentMode: String? = null, // ADDED BACK
        val notes: String? = null,       // ADDED BACK
        val recordedAt: Timestamp? = null
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

    data class DailyAttendanceStatus(
        val date: String,
        val status: String
    )

    data class SubjectItem(
        @DocumentId val id: String = "",
        val subjectName: String = "",
        val teacherId: String? = null,
        val description: String? = null
    )

    data class Exam(
        @DocumentId val id: String = "",
        val name: String = ""
    )

    data class StudentMarks(
        val student: StudentDetailsItem,
        var marks: MutableMap<String, String> = mutableMapOf()
    )

    data class DashboardStudentItem(
        val id: String,
        val name: String,
        val imageUrl: String?,
        val subtitle: String? = null
    )