package com.example.madarsa_attendance

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FeesReportGenerator(private val context: Context, private val db: FirebaseFirestore) {

    private val TAG = "FeesReportGenerator"

    suspend fun generateAndSaveFeeReport(
        teacherId: String,
        teacherName: String,
        organizationId: String,
        reportType: String,
        year: Int,
        month: Int? = null
    ): Uri? {
        if (organizationId.isBlank()) {
            Log.e(TAG, "Organization ID is blank.")
            return null
        }
        try {
            val reportData = if (reportType == "Monthly" && month != null) {
                fetchReportDataForMonth(teacherId, organizationId, year, month)
            } else {
                fetchReportDataForYear(teacherId, organizationId, year)
            }

            if (reportData.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No payment data found for the selected period.", Toast.LENGTH_LONG).show()
                }
                return null
            }

            val madarsaName = FirebaseAuthManager.getOrganizationName(context) ?: "Madarsa Report"
            val madarsaAddress = FirebaseAuthManager.getOrganizationAddress(context) ?: ""
            val logoBitmap = LogoProvider.getActiveLogo(context)

            return if (reportType == "Monthly" && month != null) {
                PdfGenerator.createMonthlyReportPdf(
                    context, madarsaName, madarsaAddress, teacherName, year, month, reportData, logoBitmap
                )
            } else {
                PdfGenerator.createYearlyReportPdf(
                    context, madarsaName, madarsaAddress, teacherName, year, reportData, logoBitmap
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating fee report: ", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error generating fee report: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return null
        }
    }

    private suspend fun fetchReportDataForMonth(teacherId: String, organizationId: String, year: Int, month: Int): List<StudentPaymentSummaryItem> {
        val studentDetailsMap = mutableMapOf<String, StudentDetailsItem>()
        val studentMonthlyPaymentDetails = mutableMapOf<String, Pair<Double, Int>>()

        val studentsSnapshot = db.collection("organizations").document(organizationId)
            .collection("students")
            .whereEqualTo("teacherId", teacherId)
            .orderBy("studentName").get().await()

        if (studentsSnapshot.isEmpty) return emptyList()

        studentsSnapshot.forEach { doc ->
            val studentId = doc.id
            val student = doc.toObject(StudentDetailsItem::class.java)
            studentDetailsMap[studentId] = student
            studentMonthlyPaymentDetails[studentId] = Pair(0.0, 0)
        }

        val calendar = Calendar.getInstance().apply { set(year, month, 1) }
        val targetMonthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)

        val paymentsSnap = db.collection("organizations").document(organizationId)
            .collection("feePayments")
            .whereEqualTo("teacherId", teacherId)
            .whereEqualTo("paymentMonth", targetMonthYearStr)
            .get().await()

        if (!paymentsSnap.isEmpty) {
            paymentsSnap.forEach { pDoc ->
                val sId = pDoc.getString("studentId")
                val amt = pDoc.getDouble("paymentAmount") ?: 0.0
                if (sId != null && studentDetailsMap.containsKey(sId)) {
                    val currentDetails = studentMonthlyPaymentDetails[sId]!!
                    studentMonthlyPaymentDetails[sId] = Pair(currentDetails.first + amt, currentDetails.second + 1)
                }
            }
        }

        return studentDetailsMap.values.sortedBy { it.studentName }.map { studentDetail ->
            val paymentInfo = studentMonthlyPaymentDetails[studentDetail.id] ?: Pair(0.0, 0)
            StudentPaymentSummaryItem(
                studentId = studentDetail.id, studentName = studentDetail.studentName,
                totalPaidThisMonth = paymentInfo.first, paymentCountThisMonth = paymentInfo.second,
                profileImageUrl = studentDetail.profileImageUrl
            )
        }
    }

    private suspend fun fetchReportDataForYear(teacherId: String, organizationId: String, year: Int): List<StudentPaymentSummaryItem> {
        val studentDetailsMap = mutableMapOf<String, StudentDetailsItem>()
        val studentYearlyPaymentDetails = mutableMapOf<String, Pair<Double, Int>>()

        val studentsSnapshot = db.collection("organizations").document(organizationId)
            .collection("students")
            .whereEqualTo("teacherId", teacherId)
            .orderBy("studentName").get().await()

        if (studentsSnapshot.isEmpty) return emptyList()

        studentsSnapshot.forEach { doc ->
            val studentId = doc.id
            val student = doc.toObject(StudentDetailsItem::class.java)
            studentDetailsMap[studentId] = student
            studentYearlyPaymentDetails[studentId] = Pair(0.0, 0)
        }

        val paymentsSnap = db.collection("organizations").document(organizationId)
            .collection("feePayments")
            .whereEqualTo("teacherId", teacherId)
            .whereEqualTo("paymentYear", year)
            .get().await()

        if (!paymentsSnap.isEmpty) {
            paymentsSnap.forEach { pDoc ->
                val sId = pDoc.getString("studentId")
                val amt = pDoc.getDouble("paymentAmount") ?: 0.0
                if (sId != null && studentDetailsMap.containsKey(sId)) {
                    val currentDetails = studentYearlyPaymentDetails[sId]!!
                    studentYearlyPaymentDetails[sId] = Pair(currentDetails.first + amt, currentDetails.second + 1)
                }
            }
        }

        return studentDetailsMap.values.sortedBy { it.studentName }.map { studentDetail ->
            val paymentInfo = studentYearlyPaymentDetails[studentDetail.id] ?: Pair(0.0, 0)
            StudentPaymentSummaryItem(
                studentId = studentDetail.id, studentName = studentDetail.studentName,
                totalPaidThisMonth = paymentInfo.first, paymentCountThisMonth = paymentInfo.second,
                profileImageUrl = studentDetail.profileImageUrl
            )
        }
    }
}