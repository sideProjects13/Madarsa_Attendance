package com.example.madarsa_attendance

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudentPdfGenerator(private val context: Context) {

    private companion object {
        const val TAG = "StudentPdfGenerator"
        const val PORTRAIT_WIDTH = 595
        const val PORTRAIT_HEIGHT = 842
        const val MARGIN = 40f

        // NEW: Organization details constants
        private const val ORG_NAME_FULL = "Madarsa Aaisha Siddiqa Ta'alimul Quran"
        private const val ORG_ADDRESS_FULL = "BIBI AAISHA MASJID SARNI SOCIETY AHMEDABAD"
    }

    private var canvas: Canvas? = null
    private var yPosition = 0f
    private var currentPage: PdfDocument.Page? = null
    private lateinit var document: PdfDocument
    private lateinit var pageInfo: PdfDocument.PageInfo
    private lateinit var logoBitmap: Bitmap
    private var pageNumber = 1
    private var pageWidth = 0
    private var pageHeight = 0

    suspend fun generatePdf(
        students: List<StudentDetailsItem>,
        columns: List<ReportColumn>,
        orientation: PageOrientation,
        reportName: String // NEW: Added report name parameter
    ): Uri? = withContext(Dispatchers.IO) {
        if (columns.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please select at least one column.", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }

        if (orientation == PageOrientation.PORTRAIT) {
            pageWidth = PORTRAIT_WIDTH
            pageHeight = PORTRAIT_HEIGHT
        } else {
            pageWidth = PORTRAIT_HEIGHT
            pageHeight = PORTRAIT_WIDTH
        }

        document = PdfDocument()
        logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo)
        pageNumber = 1

        var studentIndex = 0
        while (studentIndex < students.size) {
            startNewPage(reportName) // NEW: Pass reportName
            studentIndex = drawTableLayoutPage(students, columns, studentIndex)
            document.finishPage(currentPage!!)
        }

        return@withContext savePdfDocument()
    }

    // NEW: Updated startNewPage to accept reportName
    private fun startNewPage(reportName: String) {
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
        currentPage = document.startPage(pageInfo)
        canvas = currentPage!!.canvas
        yPosition = MARGIN
        drawWatermark()
        drawPageHeader(reportName) // NEW: Pass reportName
    }

    private fun drawTableLayoutPage(students: List<StudentDetailsItem>, columns: List<ReportColumn>, startIndex: Int): Int {
        val headerPaint = TextPaint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        val cellPaint = TextPaint().apply { color = Color.DKGRAY; textSize = 9f }
        val linePaint = Paint().apply { color = Color.GRAY; strokeWidth = 0.5f }
        val rowHeight = 25f
        val drawableWidth = pageWidth - (MARGIN * 2)
        val columnWidth = drawableWidth / columns.size

        var xPosition = MARGIN
        canvas?.drawLine(MARGIN, yPosition, pageWidth - MARGIN, yPosition, linePaint)
        yPosition += 15f
        columns.forEach { column ->
            canvas?.drawText(column.title, xPosition + 5, yPosition, headerPaint)
            xPosition += columnWidth
        }
        yPosition += 15f
        canvas?.drawLine(MARGIN, yPosition, pageWidth - MARGIN, yPosition, linePaint)
        yPosition += 5f

        var currentIndex = startIndex
        while (currentIndex < students.size) {
            if (yPosition + rowHeight > pageHeight - MARGIN) {
                return currentIndex
            }
            val student = students[currentIndex]
            xPosition = MARGIN
            canvas?.drawLine(MARGIN, yPosition - 5, pageWidth - MARGIN, yPosition - 5, linePaint)
            columns.forEach { column ->
                val cellData = getCellData(student, column)
                canvas?.drawText(cellData, xPosition + 5, yPosition + (rowHeight / 2), cellPaint)
                xPosition += columnWidth
            }
            yPosition += rowHeight
            currentIndex++
        }
        canvas?.drawLine(MARGIN, yPosition - 5, pageWidth - MARGIN, yPosition - 5, linePaint)
        return currentIndex
    }

    private fun getCellData(student: StudentDetailsItem, column: ReportColumn): String {
        return when (column) {
            ReportColumn.REG_NO -> student.regNo
            ReportColumn.STUDENT_NAME -> student.studentName
            ReportColumn.PARENT_NAME -> student.parentName
            ReportColumn.PARENT_MOBILE -> student.parentMobileNumber
            ReportColumn.ALTERNATE_MOBILE -> student.alternateMobileNumber
            ReportColumn.GENDER -> student.gender
            ReportColumn.DOB -> student.birthDate
            ReportColumn.ADMISSION_DATE -> student.admissionDate
            ReportColumn.MONTHLY_FEE -> student.monthlyFee?.toString()
            ReportColumn.TEACHER_NAME -> student.teacherName
        } ?: "N/A"
    }

    private fun drawWatermark() {
        val watermarkPaint = Paint().apply { alpha = 20; isAntiAlias = true }
        val watermarkSize = if (pageWidth > pageHeight) pageHeight / 2 else pageWidth / 2
        val scaledWatermark = Bitmap.createScaledBitmap(logoBitmap, watermarkSize, watermarkSize, true)
        val x = (pageWidth - watermarkSize) / 2f
        val y = (pageHeight - watermarkSize) / 2f
        canvas?.drawBitmap(scaledWatermark, x, y, watermarkPaint)
    }

    // NEW: Updated drawPageHeader to accept reportName
    private fun drawPageHeader(reportName: String) {
        val titlePaint = TextPaint().apply {
            color = Color.BLACK; textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        val addressPaint = TextPaint().apply {
            color = Color.DKGRAY; textSize = 11f; textAlign = Paint.Align.CENTER
        }
        val reportTitlePaint = TextPaint().apply {
            color = Color.BLACK; textSize = 14f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        val datePaint = TextPaint().apply {
            color = Color.DKGRAY; textSize = 10f; textAlign = Paint.Align.RIGHT
        }

        // Madarsa Name (main title)
        canvas?.drawText(ORG_NAME_FULL, (pageWidth / 2).toFloat(), yPosition, titlePaint)
        yPosition += 25f // Adjusted spacing

        // Madarsa Address
        canvas?.drawText(ORG_ADDRESS_FULL, (pageWidth / 2).toFloat(), yPosition, addressPaint)
        yPosition += 35f // Adjusted spacing

        // Report Name (if provided)
        if (reportName.isNotBlank()) {
            canvas?.drawText(reportName, (pageWidth / 2).toFloat(), yPosition, reportTitlePaint)
            yPosition += 30f // Extra space after report name
        }

        // Report Date
        val reportDate = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date())
        canvas?.drawText("Report Date: $reportDate", pageWidth - MARGIN, yPosition, datePaint)
        yPosition += 20f // Space before table data
    }

    private fun savePdfDocument(): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Student_Report_$timestamp.pdf"
        var fileUri: Uri? = null

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MadarsaReports") // Save to subfolder
                }
            }

            val resolver = context.contentResolver
            fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (fileUri != null) {
                resolver.openOutputStream(fileUri)?.use { outputStream ->
                    document.writeTo(outputStream)
                }
                Log.d(TAG, "PDF saved successfully to Downloads/MadarsaReports. URI: $fileUri")
            } else {
                throw Exception("MediaStore returned a null URI.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PDF", e)
            fileUri = null
        } finally {
            document.close()
        }
        return fileUri
    }
}