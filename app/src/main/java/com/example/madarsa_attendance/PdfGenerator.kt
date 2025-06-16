package com.example.madarsa_attendance

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface // For bold text
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider // Keep for potential future use or pre-Q sharing
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PdfGenerator {
    private const val TAG = "PdfGenerator"
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    // A4 Page Dimensions in points (1 inch = 72 points)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    // Margins
    private const val MARGIN_LEFT = 40f
    private const val MARGIN_RIGHT = 40f
    private const val MARGIN_TOP = 50f
    private const val MARGIN_BOTTOM = 50f

    // Line Spacing & Text Sizes
    private const val LINE_SPACING_SMALL = 12f
    private const val LINE_SPACING_NORMAL = 18f
    private const val LINE_SPACING_LARGE = 24f

    private const val TEXT_SIZE_SMALL = 8f
    private const val TEXT_SIZE_NORMAL = 10f
    private const val TEXT_SIZE_SUBHEADER = 12f
    private const val TEXT_SIZE_HEADER = 14f
    private const val TEXT_SIZE_TITLE = 16f

    // Column X positions (adjust these as needed for your desired layout)
    private const val COL_STUDENT_NAME_X = MARGIN_LEFT
    // For right-aligned columns, these X positions represent the RIGHT edge of the column
    private const val COL_PAYMENTS_COUNT_X_RIGHT_EDGE = PAGE_WIDTH - MARGIN_RIGHT - 80f // Allocate space from right
    private const val COL_TOTAL_PAID_X_RIGHT_EDGE = PAGE_WIDTH - MARGIN_RIGHT


    private fun drawPageNumber(canvas: Canvas, pageNum: Int, totalPages: Int) {
        val paint = Paint().apply {
            color = Color.DKGRAY
            textSize = TEXT_SIZE_SMALL
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Page $pageNum of $totalPages", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_BOTTOM / 2, paint)
    }

    // Pre-calculate total pages (approximate, can be refined if exact is needed before drawing)
    private fun estimateTotalPages(studentSummariesCount: Int): Int {
        val contentHeightPerPage = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM - (LINE_SPACING_LARGE * 3) // Approx header space
        val itemsPerPage = (contentHeightPerPage / LINE_SPACING_NORMAL).toInt()
        if (itemsPerPage <= 0) return 1 // Should not happen
        return (studentSummariesCount + itemsPerPage -1) / itemsPerPage // Ceiling division
    }


    fun createMonthlyReportPdf(
        context: Context,
        madarsaName: String,
        className: String,
        year: Int,
        month: Int, // 0-indexed
        studentSummaries: List<StudentPaymentSummaryItem>
    ): Uri? {
        val document = PdfDocument()
        val calendar = Calendar.getInstance().apply { set(year, month, 1) }
        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
        val reportTitle = "Monthly Fee Report - $monthName $year"
        val fileName = "Report_Monthly_${className.replace(" ", "_")}_${monthName}_$year.pdf"
        val totalPages = estimateTotalPages(studentSummaries.size)

        return generatePdf(context, document, fileName, reportTitle, madarsaName, className, studentSummaries, totalPages)
    }

    fun createYearlyReportPdf(
        context: Context,
        madarsaName: String,
        className: String,
        year: Int,
        studentSummaries: List<StudentPaymentSummaryItem>
    ): Uri? {
        val document = PdfDocument()
        val reportTitle = "Yearly Fee Report - $year"
        val fileName = "Report_Yearly_${className.replace(" ", "_")}_$year.pdf"
        val totalPages = estimateTotalPages(studentSummaries.size)
        return generatePdf(context, document, fileName, reportTitle, madarsaName, className, studentSummaries, totalPages)
    }

    private fun generatePdf(
        context: Context,
        document: PdfDocument,
        fileName: String,
        reportTitleText: String,
        madarsaName: String,
        className: String,
        studentSummaries: List<StudentPaymentSummaryItem>,
        totalPagesEstimate: Int
    ): Uri? {
        val paint = Paint().apply { isAntiAlias = true } // Enable anti-aliasing for smoother text
        val originalTextColor = Color.BLACK // Store original color

        var currentPageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var yPosition = MARGIN_TOP

        // Function to draw header on each new page
        fun drawHeaderOnPage() {
            // Madarsa Name - Centered, Bold
            paint.textSize = TEXT_SIZE_TITLE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.color = originalTextColor
            canvas.drawText(madarsaName, PAGE_WIDTH / 2f, yPosition, paint)
            yPosition += LINE_SPACING_LARGE

            // Report Title - Centered
            paint.textSize = TEXT_SIZE_HEADER
            paint.typeface = Typeface.DEFAULT // Normal
            canvas.drawText(reportTitleText, PAGE_WIDTH / 2f, yPosition, paint)
            yPosition += LINE_SPACING_NORMAL

            // Class Name - Centered, Smaller
            paint.textSize = TEXT_SIZE_SUBHEADER
            canvas.drawText("Class: $className", PAGE_WIDTH / 2f, yPosition, paint)
            yPosition += LINE_SPACING_LARGE * 1.5f // Extra space before table

            // Reset paint for table content
            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.LEFT
        }

        drawHeaderOnPage() // Draw header on the first page

        // Table Headers
        paint.textSize = TEXT_SIZE_NORMAL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = originalTextColor

        // Student Name (Left Aligned)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Student Name", COL_STUDENT_NAME_X, yPosition, paint)

        // Payments Made (Right Aligned within its column space)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Payments Made", COL_PAYMENTS_COUNT_X_RIGHT_EDGE, yPosition, paint)

        // Total Paid (Right Aligned)
        canvas.drawText("Total Paid", COL_TOTAL_PAID_X_RIGHT_EDGE, yPosition, paint)

        yPosition += LINE_SPACING_SMALL / 2 // Space before line
        // Draw a line under headers
        val linePaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN_LEFT, yPosition, PAGE_WIDTH - MARGIN_RIGHT, yPosition, linePaint)
        yPosition += LINE_SPACING_NORMAL // Space after line

        // Reset for data rows
        paint.typeface = Typeface.DEFAULT
        var totalCollection = 0.0

        for (summary in studentSummaries) {
            // Check if new page is needed BEFORE drawing the item
            if (yPosition > PAGE_HEIGHT - MARGIN_BOTTOM - (LINE_SPACING_NORMAL * 2)) { // Space for item + footer
                drawPageNumber(canvas, currentPageNumber, totalPagesEstimate)
                document.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                yPosition = MARGIN_TOP
                drawHeaderOnPage() // Redraw header on new page

                // Redraw table headers
                paint.textSize = TEXT_SIZE_NORMAL
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = originalTextColor
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("Student Name", COL_STUDENT_NAME_X, yPosition, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("Payments Made", COL_PAYMENTS_COUNT_X_RIGHT_EDGE, yPosition, paint)
                canvas.drawText("Total Paid", COL_TOTAL_PAID_X_RIGHT_EDGE, yPosition, paint)
                yPosition += LINE_SPACING_SMALL / 2
                canvas.drawLine(MARGIN_LEFT, yPosition, PAGE_WIDTH - MARGIN_RIGHT, yPosition, linePaint)
                yPosition += LINE_SPACING_NORMAL
                paint.typeface = Typeface.DEFAULT
            }

            // Student Name (Left Aligned)
            paint.textAlign = Paint.Align.LEFT
            paint.color = originalTextColor // Default color
            canvas.drawText(summary.studentName, COL_STUDENT_NAME_X, yPosition, paint)

            // Payments Made (Right Aligned)
            paint.textAlign = Paint.Align.RIGHT
            val paymentCountText = if (summary.paymentCountThisMonth > 0) "${summary.paymentCountThisMonth}" else "0"
            canvas.drawText(paymentCountText, COL_PAYMENTS_COUNT_X_RIGHT_EDGE, yPosition, paint)

            // Total Paid (Right Aligned)
            // Highlight in RED if total paid is zero
            if (summary.totalPaidThisMonth == 0.0 && summary.paymentCountThisMonth == 0) { // Check count too, as 0.0 might be a valid small payment
                paint.color = Color.RED
            } else {
                paint.color = originalTextColor
            }
            canvas.drawText(currencyFormatter.format(summary.totalPaidThisMonth), COL_TOTAL_PAID_X_RIGHT_EDGE, yPosition, paint)
            paint.color = originalTextColor // Reset color for next items

            totalCollection += summary.totalPaidThisMonth
            yPosition += LINE_SPACING_NORMAL
        }

        // Draw Total Collection Footer
        // Add some space if not enough before footer
        if (yPosition < PAGE_HEIGHT - MARGIN_BOTTOM - (LINE_SPACING_LARGE * 2)) {
            yPosition += LINE_SPACING_NORMAL // Add a bit more space before total
        }
        // Ensure total is not drawn off page if it's the only thing left
        if (yPosition > PAGE_HEIGHT - MARGIN_BOTTOM - LINE_SPACING_LARGE) {
            drawPageNumber(canvas, currentPageNumber, totalPagesEstimate)
            document.finishPage(page)
            currentPageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            yPosition = MARGIN_TOP
            // No need to redraw full header, just position for total or a simplified footer header
        }


        canvas.drawLine(MARGIN_LEFT, yPosition - LINE_SPACING_SMALL, PAGE_WIDTH - MARGIN_RIGHT, yPosition - LINE_SPACING_SMALL, linePaint) // Line above total

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = originalTextColor
        paint.textAlign = Paint.Align.LEFT // For the label
        canvas.drawText("Total Collection for Period:", COL_PAYMENTS_COUNT_X_RIGHT_EDGE - 150f, yPosition, paint) // Adjust X for label

        paint.textAlign = Paint.Align.RIGHT // For the amount
        canvas.drawText(currencyFormatter.format(totalCollection), COL_TOTAL_PAID_X_RIGHT_EDGE, yPosition, paint)

        drawPageNumber(canvas, currentPageNumber, totalPagesEstimate)
        document.finishPage(page)

        // Save the document (same saving logic as before)
        try {
            val uri: Uri?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + File.separator + "MadarsaReports")
                }
                uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        document.writeTo(outputStream)
                    } ?: throw IOException("Failed to get output stream.")
                }
            } else {
                // For pre-Q, ensure you have WRITE_EXTERNAL_STORAGE permission handled
                val reportsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MadarsaReports")
                if (!reportsDir.exists() && !reportsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: ${reportsDir.absolutePath}")
                    // Fallback or throw error
                }
                val file = File(reportsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    document.writeTo(outputStream)
                }
                // For older versions, you might want to scan the file to make it visible immediately
                // MediaScannerConnection.scanFile(context, arrayOf(file.toString()), null, null)
                // If sharing via FileProvider (recommended for pre-Q if opening with other apps):
                uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                // Else, for just showing path or if targetSDK is low enough:
                // uri = Uri.fromFile(file)
            }
            Log.d(TAG, "PDF saved successfully: $uri")
            return uri
        } catch (e: IOException) {
            Log.e(TAG, "Error writing PDF", e)
            return null
        } catch (e: Exception) { // Catch other potential errors like FileProvider issues
            Log.e(TAG, "General error during PDF saving", e)
            return null
        }
        finally {
            document.close()
        }
    }
}