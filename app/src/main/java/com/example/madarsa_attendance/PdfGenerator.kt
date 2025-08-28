package com.example.madarsa_attendance

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
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

    // =====================================================================================
    // I. CONSTANTS FOR A4 REPORTS (Existing)
    // =====================================================================================
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 40f
    private const val MARGIN_RIGHT = 40f
    private const val MARGIN_TOP = 50f
    private const val MARGIN_BOTTOM = 50f
    private const val LINE_SPACING_SMALL = 12f
    private const val LINE_SPACING_NORMAL = 18f
    private const val LINE_SPACING_LARGE = 24f
    private const val TEXT_SIZE_SMALL = 8f
    private const val TEXT_SIZE_NORMAL = 10f
    private const val TEXT_SIZE_SUBHEADER = 12f
    private const val TEXT_SIZE_HEADER = 14f
    private const val TEXT_SIZE_TITLE = 16f
    private const val COL_STUDENT_NAME_X = MARGIN_LEFT
    private const val COL_PAYMENTS_COUNT_X_RIGHT_EDGE = PAGE_WIDTH - MARGIN_RIGHT - 80f
    private const val COL_TOTAL_PAID_X_RIGHT_EDGE = PAGE_WIDTH - MARGIN_RIGHT
    private const val ORG_NAME_FULL = "Madarsa Aaisha Siddiqa Ta'alimul Quran"
    private const val ORG_ADDRESS_FULL = "BIBI AAISHA MASJID SARNI SOCIETY AHMEDABAD"


    // =====================================================================================
    // II. FUNCTIONS FOR A4 REPORTS (Restored)
    // =====================================================================================

    fun createMonthlyReportPdf(
        context: Context,
        madarsaName: String,
        className: String,
        year: Int,
        month: Int,
        studentSummaries: List<StudentPaymentSummaryItem>
    ): Uri? {
        val document = PdfDocument()
        val calendar = Calendar.getInstance().apply { set(year, month, 1) }
        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
        val reportTitle = "Monthly Fee Report - $monthName $year"
        val fileName = "Report_Monthly_${className.replace(" ", "_")}_${monthName}_$year.pdf"
        val totalPages = estimateTotalPages(studentSummaries.size)

        return generatePdf(context, document, fileName, reportTitle, ORG_NAME_FULL, ORG_ADDRESS_FULL, className, studentSummaries, totalPages)
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
        return generatePdf(context, document, fileName, reportTitle, ORG_NAME_FULL, ORG_ADDRESS_FULL, className, studentSummaries, totalPages)
    }

    private fun generatePdf(
        context: Context,
        document: PdfDocument,
        fileName: String,
        reportTitleText: String,
        madarsaName: String,
        madarsaAddress: String,
        className: String,
        studentSummaries: List<StudentPaymentSummaryItem>,
        totalPagesEstimate: Int
    ): Uri? {
        val paint = Paint().apply { isAntiAlias = true }
        val originalTextColor = Color.BLACK
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo)

        var currentPageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var yPosition = MARGIN_TOP

        fun drawHeaderOnPage() {
            drawWatermark(canvas, logoBitmap, PAGE_WIDTH, PAGE_HEIGHT)
            paint.textSize = TEXT_SIZE_TITLE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.color = originalTextColor
            canvas.drawText(madarsaName, PAGE_WIDTH / 2f, yPosition, paint)
            yPosition += LINE_SPACING_LARGE / 1.5f
            paint.textSize = TEXT_SIZE_SUBHEADER
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(madarsaAddress, PAGE_WIDTH / 2f, yPosition, paint)
            yPosition += LINE_SPACING_LARGE
            paint.textSize = TEXT_SIZE_HEADER
            canvas.drawText(reportTitleText, PAGE_WIDTH / 2f, yPosition, paint)
            yPosition += LINE_SPACING_NORMAL
            paint.textSize = TEXT_SIZE_SUBHEADER
            canvas.drawText("Class: $className", PAGE_WIDTH / 2f, yPosition, paint)
            yPosition += LINE_SPACING_LARGE * 1.5f
            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.LEFT
        }

        drawHeaderOnPage()

        paint.textSize = TEXT_SIZE_NORMAL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = originalTextColor
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Student Name", COL_STUDENT_NAME_X, yPosition, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Payments Made", COL_PAYMENTS_COUNT_X_RIGHT_EDGE, yPosition, paint)
        canvas.drawText("Total Paid", COL_TOTAL_PAID_X_RIGHT_EDGE, yPosition, paint)
        yPosition += LINE_SPACING_SMALL / 2
        val linePaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN_LEFT, yPosition, PAGE_WIDTH - MARGIN_RIGHT, yPosition, linePaint)
        yPosition += LINE_SPACING_NORMAL
        paint.typeface = Typeface.DEFAULT
        var totalCollection = 0.0

        for (summary in studentSummaries) {
            if (yPosition > PAGE_HEIGHT - MARGIN_BOTTOM - (LINE_SPACING_NORMAL * 2)) {
                drawPageNumber(canvas, currentPageNumber, totalPagesEstimate)
                document.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                yPosition = MARGIN_TOP
                drawHeaderOnPage()
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
            paint.textAlign = Paint.Align.LEFT
            paint.color = originalTextColor
            canvas.drawText(summary.studentName, COL_STUDENT_NAME_X, yPosition, paint)
            paint.textAlign = Paint.Align.RIGHT
            val paymentCountText = if (summary.paymentCountThisMonth > 0) "${summary.paymentCountThisMonth}" else "0"
            canvas.drawText(paymentCountText, COL_PAYMENTS_COUNT_X_RIGHT_EDGE, yPosition, paint)
            if (summary.totalPaidThisMonth == 0.0 && summary.paymentCountThisMonth == 0) {
                paint.color = Color.RED
            } else {
                paint.color = originalTextColor
            }
            canvas.drawText(currencyFormatter.format(summary.totalPaidThisMonth), COL_TOTAL_PAID_X_RIGHT_EDGE, yPosition, paint)
            paint.color = originalTextColor
            totalCollection += summary.totalPaidThisMonth
            yPosition += LINE_SPACING_NORMAL
        }
        if (yPosition < PAGE_HEIGHT - MARGIN_BOTTOM - (LINE_SPACING_LARGE * 2)) {
            yPosition += LINE_SPACING_NORMAL
        }
        if (yPosition > PAGE_HEIGHT - MARGIN_BOTTOM - LINE_SPACING_LARGE) {
            drawPageNumber(canvas, currentPageNumber, totalPagesEstimate)
            document.finishPage(page)
            currentPageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            yPosition = MARGIN_TOP
            drawWatermark(canvas, logoBitmap, PAGE_WIDTH, PAGE_HEIGHT)
        }
        canvas.drawLine(MARGIN_LEFT, yPosition - LINE_SPACING_SMALL, PAGE_WIDTH - MARGIN_RIGHT, yPosition - LINE_SPACING_SMALL, linePaint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = originalTextColor
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Total Collection for Period:", COL_PAYMENTS_COUNT_X_RIGHT_EDGE - 150f, yPosition, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(currencyFormatter.format(totalCollection), COL_TOTAL_PAID_X_RIGHT_EDGE, yPosition, paint)
        drawPageNumber(canvas, currentPageNumber, totalPagesEstimate)
        document.finishPage(page)
        return saveDocument(context, document, fileName, "MadarsaReports")
    }

    private fun drawPageNumber(canvas: Canvas, pageNum: Int, totalPages: Int) {
        val paint = Paint().apply {
            color = Color.DKGRAY
            textSize = TEXT_SIZE_SMALL
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Page $pageNum of $totalPages", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_BOTTOM / 2, paint)
    }

    private fun estimateTotalPages(studentSummariesCount: Int): Int {
        val headerHeight = TEXT_SIZE_TITLE + LINE_SPACING_LARGE / 1.5f +
                TEXT_SIZE_SUBHEADER + LINE_SPACING_LARGE +
                TEXT_SIZE_HEADER + LINE_SPACING_NORMAL +
                TEXT_SIZE_SUBHEADER + LINE_SPACING_LARGE * 1.5f +
                LINE_SPACING_SMALL / 2 + LINE_SPACING_NORMAL
        val footerHeight = LINE_SPACING_NORMAL
        val contentHeightPerPage = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM - headerHeight - footerHeight
        val itemsPerPage = (contentHeightPerPage / LINE_SPACING_NORMAL).toInt()
        if (itemsPerPage <= 0) return 1
        return (studentSummariesCount + itemsPerPage - 1) / itemsPerPage
    }


    // =====================================================================================
    // III. FEE RECEIPT FUNCTION AND HELPERS (COMPLETE AND CORRECTED)
    // =====================================================================================

    private const val RECEIPT_WIDTH = 250
    private const val RECEIPT_HEIGHT = 480
    private const val RECEIPT_MARGIN = 20f
    private const val RECEIPT_LINE_SPACING = 15f
    private const val RECEIPT_HEADER_SIZE = 12f
    private const val RECEIPT_SUBNAME_SIZE = 9f
    private const val RECEIPT_TITLE_SIZE = 11f
    private const val RECEIPT_DETAIL_LABEL_SIZE = 9f
    private const val RECEIPT_DETAIL_VALUE_SIZE = 10f
    private const val RECEIPT_AMOUNT_SIZE = 16f

    fun createSingleFeeReceiptPdf(
        context: Context,
        studentName: String,
        parentName: String,
        registrationNumber: String,
        teacherName: String,
        paymentDate: String,
        paymentMonth: String,
        amountPaid: Double
    ): Uri? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(RECEIPT_WIDTH, RECEIPT_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true; color = Color.BLACK }
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo)

        drawWatermark(canvas, logoBitmap, RECEIPT_WIDTH, RECEIPT_HEIGHT)

        var yPos = RECEIPT_MARGIN
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = RECEIPT_HEADER_SIZE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(ORG_NAME_FULL, RECEIPT_WIDTH / 2f, yPos, paint)
        yPos += RECEIPT_LINE_SPACING
        paint.textSize = RECEIPT_SUBNAME_SIZE
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(ORG_ADDRESS_FULL, RECEIPT_WIDTH / 2f, yPos, paint)
        yPos += RECEIPT_LINE_SPACING * 1.5f
        paint.textSize = RECEIPT_TITLE_SIZE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FEE RECEIPT", RECEIPT_WIDTH / 2f, yPos, paint)
        yPos += RECEIPT_LINE_SPACING * 1.5f
        val linePaint = Paint().apply { strokeWidth = 0.5f; color = Color.DKGRAY }
        canvas.drawLine(RECEIPT_MARGIN, yPos, RECEIPT_WIDTH - RECEIPT_MARGIN, yPos, linePaint)
        yPos += RECEIPT_LINE_SPACING

        paint.textAlign = Paint.Align.LEFT
        val receiptNumber = "R${System.currentTimeMillis()}"
        yPos = drawDetailRow(canvas, paint, yPos, "Receipt No:", receiptNumber)
        yPos = drawDetailRow(canvas, paint, yPos, "Payment Date:", paymentDate)
        yPos += RECEIPT_LINE_SPACING / 2
        canvas.drawLine(RECEIPT_MARGIN, yPos, RECEIPT_WIDTH - RECEIPT_MARGIN, yPos, linePaint)
        yPos += RECEIPT_LINE_SPACING

        yPos = drawDetailRow(canvas, paint, yPos, "Student Name:", studentName)
        yPos = drawDetailRow(canvas, paint, yPos, "Parent Name:", parentName)
        yPos = drawDetailRow(canvas, paint, yPos, "Registration No:", registrationNumber)
        yPos = drawDetailRow(canvas, paint, yPos, "Class Teacher:", teacherName)
        yPos += RECEIPT_LINE_SPACING / 2
        canvas.drawLine(RECEIPT_MARGIN, yPos, RECEIPT_WIDTH - RECEIPT_MARGIN, yPos, linePaint)
        yPos += RECEIPT_LINE_SPACING * 1.5f

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = RECEIPT_DETAIL_LABEL_SIZE
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Fee for the Month Of", RECEIPT_WIDTH / 2f, yPos, paint)
        yPos += RECEIPT_LINE_SPACING * 1.2f
        paint.textSize = RECEIPT_DETAIL_VALUE_SIZE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(paymentMonth, RECEIPT_WIDTH / 2f, yPos, paint)
        yPos += RECEIPT_LINE_SPACING * 1.5f

        paint.textSize = RECEIPT_DETAIL_LABEL_SIZE
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Amount Paid", RECEIPT_WIDTH / 2f, yPos, paint)
        yPos += RECEIPT_LINE_SPACING * 1.5f
        paint.textSize = RECEIPT_AMOUNT_SIZE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(currencyFormatter.format(amountPaid), RECEIPT_WIDTH / 2f, yPos, paint)
        yPos += RECEIPT_LINE_SPACING * 2

        paint.textSize = RECEIPT_SUBNAME_SIZE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Thank you for your payment!", RECEIPT_WIDTH / 2f, yPos, paint)

        val footerText = "This is a computer-generated receipt."
        val footerY = RECEIPT_HEIGHT - RECEIPT_MARGIN
        canvas.drawText(footerText, RECEIPT_WIDTH / 2f, footerY, paint)

        document.finishPage(page)
        val fileName = "FeeReceipt_${studentName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        return saveDocument(context, document, fileName, "MadarsaReceipts")
    }

    private fun drawWatermark(canvas: Canvas, logo: Bitmap, pageWidth: Int, pageHeight: Int) {
        val watermarkPaint = Paint().apply {
            alpha = 15
            isAntiAlias = true
        }
        val watermarkSize = pageWidth / 2
        val scaledWatermark = Bitmap.createScaledBitmap(logo, watermarkSize, watermarkSize, true)
        val x = (pageWidth - watermarkSize) / 2f
        val y = (pageHeight - watermarkSize) / 2f
        canvas.drawBitmap(scaledWatermark, x, y, watermarkPaint)
    }

    private fun saveDocument(context: Context, document: PdfDocument, fileName: String, subfolder: String): Uri? {
        try {
            val uri: Uri?
            val relativePath = Environment.DIRECTORY_DOCUMENTS + File.separator + subfolder
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        document.writeTo(outputStream)
                    } ?: throw IOException("Failed to get output stream.")
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), subfolder)
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: ${dir.absolutePath}")
                }
                val file = File(dir, fileName)
                FileOutputStream(file).use { outputStream ->
                    document.writeTo(outputStream)
                }
                uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
            Log.d(TAG, "PDF saved successfully: $uri")
            return uri
        } catch (e: IOException) {
            Log.e(TAG, "Error writing PDF", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "General error during PDF saving", e)
            return null
        } finally {
            document.close()
        }
    }

    private fun drawDetailRow(canvas: Canvas, paint: Paint, startY: Float, label: String, value: String): Float {
        val yPos = startY
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = RECEIPT_DETAIL_LABEL_SIZE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(label, RECEIPT_MARGIN, yPos, paint)
        paint.textSize = RECEIPT_DETAIL_VALUE_SIZE
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, RECEIPT_WIDTH - RECEIPT_MARGIN, yPos, paint)
        return yPos + RECEIPT_LINE_SPACING * 1.2f
    }
}