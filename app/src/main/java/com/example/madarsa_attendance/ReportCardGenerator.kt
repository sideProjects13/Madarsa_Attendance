package com.example.madarsa_attendance

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReportCardGenerator(private val context: Context) {

    data class ReportData(
        val student: StudentDetailsItem,
        val examName: String,
        val marks: Map<String, String>,
        val subjects: List<SubjectItem>
    )

    private val A4_WIDTH = 595
    private val A4_HEIGHT = 842
    private val MARGIN = 40f

    // NEW: Organization details constants
    private companion object {
        private const val ORG_NAME_FULL = "Madarsa Aaisha Siddiqa Ta'alimul Quran"
        private const val ORG_ADDRESS_FULL = "BIBI AAISHA MASJID SARNI SOCIETY AHMEDABAD"
    }

    suspend fun generateBulkReport(reportDataList: List<ReportData>, madarsaName: String, madarsaAddress: String) { // NEW: madarsaAddress parameter
        if (reportDataList.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No student data to generate report.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val document = PdfDocument()
        val logo = withContext(Dispatchers.IO) { BitmapFactory.decodeResource(context.resources, R.drawable.logo) } // Load logo once
        reportDataList.forEachIndexed { index, reportData ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
            val page = document.startPage(pageInfo)
            // NEW: Pass madarsaAddress and logo
            drawReportPage(page.canvas, reportData, madarsaName, madarsaAddress, logo)
            document.finishPage(page)
        }
        val examName = reportDataList.first().examName.replace(" ", "_")
        val className = reportDataList.first().student.teacherName?.replace(" ", "_") ?: "Class"
        saveAndOpenFile(document, "ClassReport_${className}_${examName}.pdf")
    }

    suspend fun generateSingleReport(reportData: ReportData, madarsaName: String, madarsaAddress: String) { // NEW: madarsaAddress parameter
        val document = PdfDocument()
        val logo = withContext(Dispatchers.IO) { BitmapFactory.decodeResource(context.resources, R.drawable.logo) } // Load logo once
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        // NEW: Pass madarsaAddress and logo
        drawReportPage(page.canvas, reportData, madarsaName, madarsaAddress, logo)
        document.finishPage(page)
        val studentName = reportData.student.studentName.replace(" ", "_")
        saveAndOpenFile(document, "Result_${studentName}.pdf")
    }

    // NEW: Added logo parameter
    private suspend fun drawReportPage(canvas: Canvas, data: ReportData, madarsaName: String, madarsaAddress: String, logo: Bitmap) {
        val studentPhoto: Bitmap? = withContext(Dispatchers.IO) {
            if (data.student.profileImageUrl.isNullOrEmpty()) {
                null
            } else {
                try {
                    Glide.with(context).asBitmap().load(data.student.profileImageUrl).submit(100, 120).get()
                } catch (e: Exception) {
                    Log.e("ReportCardGenerator", "Failed to load student image", e)
                    null
                }
            }
        }
        drawWatermark(canvas, logo) // Ensure watermark is always drawn
        // NEW: Pass madarsaAddress
        var currentY = drawHeader(canvas, logo, studentPhoto, madarsaName, madarsaAddress, data.examName)
        currentY = drawStudentDetails(canvas, data.student, currentY + 25f)
        drawMarksTable(canvas, data, currentY + 25f)
        drawFooter(canvas, A4_HEIGHT - MARGIN - 20f)
    }

    private suspend fun saveAndOpenFile(document: PdfDocument, fileName: String) {
        var fileUri: Uri? = null
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MadarsaReports") // Save to subfolder
                }
            }
            fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (fileUri != null) {
                resolver.openOutputStream(fileUri)?.use { document.writeTo(it) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Report saved to Downloads/MadarsaReports.", Toast.LENGTH_LONG).show()
                    openPdfFile(fileUri)
                }
            } else {
                throw Exception("MediaStore returned null URI")
            }
        } catch (e: Exception) {
            Log.e("ReportCardGenerator", "Error saving PDF", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: Could not save PDF.", Toast.LENGTH_LONG).show()
            }
        } finally {
            document.close()
        }
    }

    private fun openPdfFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No application found to open PDF files.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawWatermark(canvas: Canvas, logo: Bitmap) {
        val watermarkPaint = Paint().apply {
            alpha = 30
            isAntiAlias = true
        }
        val watermarkSize = A4_WIDTH / 2
        val scaledWatermark = Bitmap.createScaledBitmap(logo, watermarkSize, watermarkSize, true)
        val x = (A4_WIDTH - watermarkSize) / 2f
        val y = (A4_HEIGHT - watermarkSize) / 2f
        canvas.drawBitmap(scaledWatermark, x, y, watermarkPaint)
    }

    // NEW: Added madarsaAddress parameter
    private fun drawHeader(canvas: Canvas, logo: Bitmap, studentPhoto: Bitmap?, madarsaName: String, madarsaAddress: String, examName: String): Float {
        val titlePaint = TextPaint().apply { color = Color.BLACK; textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val addressPaint = TextPaint().apply { color = Color.DKGRAY; textSize = 11f; textAlign = Paint.Align.CENTER }
        val reportTitlePaint = TextPaint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val photoBorderPaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 1f }

        val scaledLogo = Bitmap.createScaledBitmap(logo, 70, 70, true)
        canvas.drawBitmap(scaledLogo, MARGIN, MARGIN, null)

        val photoWidth = 80f
        val photoHeight = 100f
        val photoX = A4_WIDTH - MARGIN - photoWidth
        studentPhoto?.let {
            val scaledPhoto = Bitmap.createScaledBitmap(it, photoWidth.toInt(), photoHeight.toInt(), true)
            canvas.drawBitmap(scaledPhoto, photoX, MARGIN, null)
            canvas.drawRect(photoX, MARGIN, photoX + photoWidth, MARGIN + photoHeight, photoBorderPaint)
        }

        val textCenterX = (A4_WIDTH / 2).toFloat()
        canvas.drawText(madarsaName, textCenterX, MARGIN + 35f, titlePaint)
        canvas.drawText(madarsaAddress, textCenterX, MARGIN + 55f, addressPaint) // NEW: Draw address
        canvas.drawText("REPORT CARD - $examName", textCenterX, MARGIN + 95f, reportTitlePaint)

        val lineY = MARGIN + 120f
        canvas.drawLine(MARGIN, lineY, A4_WIDTH - MARGIN, lineY, photoBorderPaint)
        return lineY
    }

    private fun drawStudentDetails(canvas: Canvas, student: StudentDetailsItem, startY: Float): Float {
        val labelPaint = TextPaint().apply { color = Color.DKGRAY; textSize = 10f; }
        val valuePaint = TextPaint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true }
        val col1X = MARGIN + 5f
        val col2X = A4_WIDTH / 2f + 20f
        var currentY = startY
        val rowSpacing = 35f

        canvas.drawText("Registration No:", col1X, currentY, labelPaint)
        canvas.drawText(student.regNo ?: "N/A", col1X, currentY + 15, valuePaint)
        canvas.drawText("Student Name:", col2X, currentY, labelPaint)
        canvas.drawText(student.studentName, col2X, currentY + 15, valuePaint)
        currentY += rowSpacing

        canvas.drawText("Father's Name:", col1X, currentY, labelPaint)
        canvas.drawText(student.parentName ?: "N/A", col1X, currentY + 15, valuePaint)
        canvas.drawText("Gender:", col2X, currentY, labelPaint)
        canvas.drawText(student.gender ?: "N/A", col2X, currentY + 15, valuePaint)
        currentY += rowSpacing

        canvas.drawText("Date of Birth:", col1X, currentY, labelPaint)
        canvas.drawText(student.birthDate ?: "N/A", col1X, currentY + 15, valuePaint)
        canvas.drawText("Date of Admission:", col2X, currentY, labelPaint)
        canvas.drawText(student.admissionDate ?: "N/A", col2X, currentY + 15, valuePaint)

        return currentY + 15f
    }

    private fun drawMarksTable(canvas: Canvas, data: ReportData, startY: Float): Float {
        val borderPaint = Paint().apply { style = Paint.Style.STROKE; color = Color.DKGRAY; strokeWidth = 1f }
        val headerPaint = TextPaint().apply { color = Color.WHITE; textSize = 12f; isFakeBoldText = true; }
        val cellPaint = TextPaint().apply { color = Color.BLACK; textSize = 11f; }
        val headerBgPaint = Paint().apply { color = Color.parseColor("#37474F"); style = Paint.Style.FILL }
        val rowEvenPaint = Paint().apply { color = Color.parseColor("#ECEFF1"); style = Paint.Style.FILL }
        val rowOddPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }

        val tableWidth = A4_WIDTH - (MARGIN * 2)
        val marksColWidth = 120f
        val subjectColWidth = tableWidth - marksColWidth
        val rowHeight = 28f
        val subjectTextX = MARGIN + 20f
        val marksTextX = MARGIN + subjectColWidth + (marksColWidth / 2)

        val headerY = startY + rowHeight
        canvas.drawRect(MARGIN, startY, A4_WIDTH - MARGIN, headerY, headerBgPaint)
        canvas.drawText("SUBJECT", subjectTextX, startY + 19, headerPaint)
        headerPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("MARKS (out of 100)", marksTextX, startY + 19, headerPaint)
        headerPaint.textAlign = Paint.Align.LEFT

        var currentY = headerY
        var totalMarks = 0
        val maxMarksPerSubject = 100.0

        data.subjects.forEachIndexed { index, subject ->
            val bgPaint = if (index % 2 == 0) rowOddPaint else rowEvenPaint
            canvas.drawRect(MARGIN, currentY, A4_WIDTH - MARGIN, currentY + rowHeight, bgPaint)
            canvas.drawText(subject.subjectName, subjectTextX, currentY + 19, cellPaint)
            val mark = data.marks[subject.id]?.toIntOrNull() ?: 0
            cellPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(mark.toString(), marksTextX, currentY + 19, cellPaint)
            cellPaint.textAlign = Paint.Align.LEFT
            totalMarks += mark
            currentY += rowHeight
        }

        canvas.drawRect(MARGIN, startY, A4_WIDTH - MARGIN, currentY, borderPaint)
        canvas.drawLine(MARGIN + subjectColWidth, startY, MARGIN + subjectColWidth, currentY, borderPaint)

        currentY += 25
        val totalLabelPaint = TextPaint().apply { color = Color.DKGRAY; textSize = 12f; textAlign = Paint.Align.RIGHT }
        val totalValuePaint = TextPaint().apply { color = Color.BLACK; textSize = 13f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
        val totalMarksX = A4_WIDTH - MARGIN
        val totalLabelX = totalMarksX - 100

        canvas.drawText("Total Marks:", totalLabelX, currentY, totalLabelPaint)
        canvas.drawText(totalMarks.toString(), totalMarksX, currentY, totalValuePaint)

        currentY += 20
        val totalPossibleMarks = data.subjects.size * maxMarksPerSubject
        val percentage = if (totalPossibleMarks > 0) (totalMarks.toDouble() / totalPossibleMarks) * 100 else 0.0
        canvas.drawText("Percentage:", totalLabelX, currentY, totalLabelPaint)
        canvas.drawText(String.format("%.2f %%", percentage), totalMarksX, currentY, totalValuePaint)

        return currentY
    }

    private fun drawFooter(canvas: Canvas, startY: Float) {
        val signaturePaint = TextPaint().apply { color = Color.DKGRAY; textSize = 11f; textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 1f }
        val signatureLineXStart = A4_WIDTH - MARGIN - 200f
        val signatureLineXEnd = A4_WIDTH - MARGIN
        canvas.drawLine(signatureLineXStart, startY, signatureLineXEnd, startY, linePaint)
        canvas.drawText("Principal's Signature", signatureLineXEnd, startY + 15, signaturePaint)
    }
}