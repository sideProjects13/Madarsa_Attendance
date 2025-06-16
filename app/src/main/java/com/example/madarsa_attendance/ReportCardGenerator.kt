package com.example.madarsa_attendance

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.util.Log
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class ReportCardGenerator(private val context: Context) {

    // <<< THE FIX IS HERE >>>
    // This now correctly uses your `StudentDetailsItem` model.
    data class ReportData(
        val student: StudentDetailsItem, // Corrected from `Student`
        val examName: String,
        val marks: Map<String, String>,
        val subjects: List<SubjectItem>
    )

    private val A4_WIDTH = 595
    private val A4_HEIGHT = 842
    private val MARGIN = 40f

    suspend fun generateBulkReport(reportDataList: List<ReportData>, madarsaName: String, madarsaAddress: String): String? {
        val document = PdfDocument()
        reportDataList.forEachIndexed { index, reportData ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
            val page = document.startPage(pageInfo)
            drawReportPage(page.canvas, reportData, madarsaName, madarsaAddress)
            document.finishPage(page)
        }
        return savePdf(document, "ClassReport_${reportDataList.first().examName}.pdf")
    }

    suspend fun generateSingleReport(reportData: ReportData, madarsaName: String, madarsaAddress: String): String? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        drawReportPage(page.canvas, reportData, madarsaName, madarsaAddress)
        document.finishPage(page)
        return savePdf(document, "Result_${reportData.student.studentName}.pdf")
    }

    private suspend fun drawReportPage(canvas: Canvas, data: ReportData, madarsaName: String, madarsaAddress: String) {
        val logo = withContext(Dispatchers.IO) { BitmapFactory.decodeResource(context.resources, R.drawable.logo) }
        val studentPhoto = withContext(Dispatchers.IO) {
            try {
                Glide.with(context).asBitmap().load(data.student.profileImageUrl).circleCrop().submit(100, 100).get()
            } catch (e: Exception) { BitmapFactory.decodeResource(context.resources, R.drawable.student) }
        }
        var currentY = drawHeader(canvas, logo, studentPhoto, madarsaName, madarsaAddress, data.examName)
        currentY = drawStudentDetails(canvas, data.student, currentY + 20f)
        drawMarksTable(canvas, data, currentY + 20f)
        drawFooter(canvas, A4_HEIGHT - MARGIN)
    }

    private fun drawHeader(canvas: Canvas, logo: Bitmap, studentPhoto: Bitmap, madarsaName: String, madarsaAddress: String, examName: String): Float {
        val scaledLogo = Bitmap.createScaledBitmap(logo, 80, 80, false)
        canvas.drawBitmap(scaledLogo, MARGIN, MARGIN, null)
        canvas.drawBitmap(studentPhoto, A4_WIDTH - MARGIN - 100f, MARGIN, null)

        val titlePaint = TextPaint().apply { color = Color.BLACK; textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val addressPaint = TextPaint().apply { color = Color.DKGRAY; textSize = 12f; textAlign = Paint.Align.CENTER }
        val reportTitlePaint = TextPaint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }

        canvas.drawText(madarsaName, (A4_WIDTH / 2).toFloat(), MARGIN + 40f, titlePaint)
        canvas.drawText(madarsaAddress, (A4_WIDTH / 2).toFloat(), MARGIN + 60f, addressPaint)
        canvas.drawText("REPORT CARD - $examName", (A4_WIDTH / 2).toFloat(), MARGIN + 100f, reportTitlePaint)

        return MARGIN + 120f
    }

    private fun drawStudentDetails(canvas: Canvas, student: StudentDetailsItem, startY: Float): Float {
        val labelPaint = TextPaint().apply { color = Color.DKGRAY; textSize = 11f; }
        val valuePaint = TextPaint().apply { color = Color.BLACK; textSize = 12f; isFakeBoldText = true }

        val col1X = MARGIN; val col2X = A4_WIDTH / 2f
        var currentY = startY

        canvas.drawText("Registration No:", col1X, currentY, labelPaint)
        canvas.drawText(student.regNo ?: "N/A", col1X, currentY + 15, valuePaint)
        canvas.drawText("Student Name:", col2X, currentY, labelPaint)
        canvas.drawText(student.studentName, col2X, currentY + 15, valuePaint)
        currentY += 40

        canvas.drawText("Father's Name:", col1X, currentY, labelPaint)
        canvas.drawText(student.parentName ?: "N/A", col1X, currentY + 15, valuePaint)
        canvas.drawText("Gender:", col2X, currentY, labelPaint)
        canvas.drawText(student.gender ?: "N/A", col2X, currentY + 15, valuePaint)
        currentY += 40

        canvas.drawText("Date of Birth:", col1X, currentY, labelPaint)
        canvas.drawText(student.birthDate ?: "N/A", col1X, currentY + 15, valuePaint)
        canvas.drawText("Date of Admission:", col2X, currentY, labelPaint)
        canvas.drawText(student.admissionDate ?: "N/A", col2X, currentY + 15, valuePaint)

        return currentY + 40
    }

    private fun drawMarksTable(canvas: Canvas, data: ReportData, startY: Float) {
        val tableRect = RectF(MARGIN, startY, A4_WIDTH - MARGIN, startY + 300)
        val borderPaint = Paint().apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 1f }
        val headerPaint = TextPaint().apply { color = Color.WHITE; textSize = 12f; isFakeBoldText = true }
        val cellPaint = TextPaint().apply { color = Color.DKGRAY; textSize = 12f; }

        val headerBgPaint = Paint().apply { color = Color.parseColor("#424242"); style = Paint.Style.FILL }
        val rowHeight = 25f
        val headerY = startY + rowHeight

        canvas.drawRect(MARGIN, startY, A4_WIDTH - MARGIN, headerY, headerBgPaint)
        canvas.drawText("SUBJECT", MARGIN + 20, startY + 18, headerPaint)
        canvas.drawText("MARKS", A4_WIDTH - MARGIN - 100, startY + 18, headerPaint)

        var currentY = headerY
        var totalMarks = 0
        data.subjects.forEachIndexed { index, subject ->
            if (index % 2 != 0) {
                val rowBgPaint = Paint().apply { color = Color.parseColor("#F5F5F5"); style = Paint.Style.FILL }
                canvas.drawRect(MARGIN, currentY, A4_WIDTH - MARGIN, currentY + rowHeight, rowBgPaint)
            }
            canvas.drawText(subject.subjectName, MARGIN + 20, currentY + 18, cellPaint)
            val mark = data.marks[subject.id]?.toIntOrNull() ?: 0
            canvas.drawText(mark.toString(), A4_WIDTH - MARGIN - 95, currentY + 18, cellPaint)
            totalMarks += mark
            currentY += rowHeight
        }

        canvas.drawRect(MARGIN, startY, A4_WIDTH - MARGIN, currentY, borderPaint)

        val totalLabelPaint = TextPaint().apply { color = Color.BLACK; textSize = 13f; isFakeBoldText = true }
        val totalValuePaint = TextPaint().apply { color = Color.BLACK; textSize = 14f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }

        currentY += 30
        canvas.drawText("Total Marks:", A4_WIDTH - MARGIN - 150, currentY, totalLabelPaint)
        canvas.drawText(totalMarks.toString(), A4_WIDTH - MARGIN, currentY, totalValuePaint)

        currentY += 25
        val percentage = if(data.subjects.isNotEmpty()) (totalMarks.toDouble() / (data.subjects.size * 100.0)) * 100 else 0.0
        canvas.drawText("Percentage:", A4_WIDTH - MARGIN - 150, currentY, totalLabelPaint)
        canvas.drawText(String.format("%.2f %%", percentage), A4_WIDTH - MARGIN, currentY, totalValuePaint)
    }

    private fun drawFooter(canvas: Canvas, startY: Float) {
        val footerPaint = TextPaint().apply { color = Color.GRAY; textSize = 10f; textAlign = Paint.Align.CENTER }
        val linePaint = Paint().apply { color = Color.GRAY; strokeWidth = 0.5f }

        canvas.drawLine(MARGIN, startY - 20, A4_WIDTH - MARGIN, startY - 20, linePaint)
        canvas.drawText("Principal's Signature", A4_WIDTH - MARGIN - 80, startY, footerPaint)
    }

    private fun savePdf(document: PdfDocument, fileName: String): String? {
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                resolver.openOutputStream(uri)?.use { document.writeTo(it) }
            }
            document.close()
            return "Saved to Downloads folder"
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Error saving PDF", e); document.close(); return "Error: Could not save PDF"
        }
    }
}