    package com.example.madarsa_attendance

    import com.google.firebase.Timestamp
    import com.google.firebase.firestore.DocumentId
    import java.io.Serializable
    import java.util.Date

    data class Teacher(
        @DocumentId var teacherId: String = "",
        val teacherName: String = "",
        val mobileNumber: String? = null, // Ensure this field exists
        val email: String? = null, // This is the login email
        val profileImageUrl: String? = null,
        val uid: String? = null, // This will store the Firebase Auth UID
        val createdAt: Timestamp? = null // <-- ADD THIS LINE

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
        val createdAt: Date? = null,
        val regNo: String? = null,
        val gender: String? = null,
        val admissionDate: String? = null,
        val birthDate: String? = null,
        val isActive: Boolean = true,
        val monthlyFee: Double? = null,
        val alternateMobileNumber: String? = null,
        val address: String? = null
    ): Serializable

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
        val paymentDate: Date? = null,      // The date the transaction was made (e.g., today)
        val paymentMonth: String = "",   // The month the fee is FOR (e.g., "2025-06")
        val paymentYear: Int = 0,        // The year the fee is FOR (e.g., 2025)
        val paymentMode: String? = null,
        val notes: String? = null,
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

    data class InventoryItem(
        @DocumentId var id: String = "",
        val itemName: String = "",
        var stockQuantity: Int = 0,
        val sellingPrice: Double = 0.0,
        val imageUrl: String? = null,
        val createdAt: Date? = null
    ) : Serializable

    data class SaleRecord(
        @DocumentId val id: String = "",
        val studentId: String = "",
        val studentName: String = "",
        val studentRegNo: String? = null,
        val parentName: String? = null,
        val itemId: String = "",
        val itemName: String = "",
        val itemImageUrl: String? = null,
        val quantitySold: Int = 1,
        val pricePerItem: Double = 0.0,
        val totalAmount: Double = 0.0,
        val amountPaid: Double = 0.0,
        val amountDue: Double = 0.0,
        val saleDate: Date? = null
    ) : Serializable

    data class DonationRecord(
        @DocumentId val id: String = "",
        val donorName: String = "",
        val amount: Double = 0.0,
        val purpose: String? = null,
        val donorMobile: String? = null,
        val donationDate: Date? = null
    ): Serializable

//    data class Donation(
//        @DocumentId val id: String = "",
//        val donorName: String = "",
//        val donorMobile: String = "",
//        val amount: Double = 0.0,
//        val paymentDate: Date? = null,
//        val paymentMode: String? = null, // e.g., "Cash", "Online"
//        val notes: String? = null,
//        val recordedAt: Timestamp? = null
//    )

    data class TeacherAttendanceRecord(
        @DocumentId val id: String = "",
        val teacherId: String = "", // The ID from the 'teachers' collection
        val teacherName: String = "",
        val date: String = "", // Format "YYYY-MM-DD"
        val status: String = "", // "Present", "Absent"
        val organizationId: String = ""
    )

    data class OrganizationStat(
        val orgName: String = "Unknown Organization",
        val studentCount: Int = 0,
        val teacherCount: Int = 0
    )

    // Add this class to hold the latest announcement from Firestore
    data class Announcement(
        @DocumentId val id: String = "",
        val message: String = "",
        val timestamp: Timestamp? = null
    )

    data class ExamResult(
        @DocumentId val id: String = "", // examId_studentId
        val examId: String = "",
        val examName: String = "",
        val studentId: String = "",
        val studentName: String = "",
        val teacherId: String = "",
        val teacherName: String = "",
        val academicYear: String = "", // e.g., "2023-2024"
        val subjects: List<SubjectSnapshot> = emptyList(), // Snapshot of subjects
        val marks: Map<String, String> = emptyMap(), // subjectId -> mark
        val resultDate: Date? = null
    )

    data class SubjectSnapshot(
        val subjectId: String = "",
        val subjectName: String = ""
    )

    data class StudentClassHistory(
        @DocumentId val id: String = "",
        val teacherId: String = "",
        val teacherName: String = "",
        val academicYear: String = "",
        val startDate: Date? = null,
        var endDate: Date? = null // 'var' because we will update it
    )

    data class ClassHistoryItem(
        val teacherName: String,
        val academicYear: String,
        val duration: String // e.g., "Sep 2023 - Present"
    )

    data class ExamHistoryItem(
        val examName: String,
        val academicYear: String,
        val teacherName: String,
        val fullResult: ExamResult // The complete document, needed for report generation
    )

