package com.example.madarsa_attendance

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class AdmissionFormGenerator(private val context: Context) {

    // A4 Size (72 dpi)
    private val A4_WIDTH = 595
    private val A4_HEIGHT = 842

    // Margins
    private val MARGIN_OUTER = 24f
    private val MARGIN_CONTENT = 40f

    suspend fun generateAdmissionForm(student: StudentDetailsItem): Uri? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // --- 1. Set Background ---
        canvas.drawColor(Color.WHITE)

        // --- 2. Load Resources ---
        val logo = awaitLogo()
        val studentPhoto = awaitStudentPhoto(student.profileImageUrl)
        val orgName = FirebaseAuthManager.getOrganizationName(context) ?: "MADARSA NAME"
        val orgAddress = FirebaseAuthManager.getOrganizationAddress(context) ?: "Address, City"

        // --- 3. Draw Watermark ---
        if (logo != null) {
            drawWatermark(canvas, logo)
        }

        // --- 4. Draw Border ---
        drawPageBorder(canvas)

        // --- 5. Draw Header (Fixed Centering) ---
        var currentY = drawHeader(canvas, logo, studentPhoto, orgName, orgAddress)

        // --- 6. Draw Title ---
        currentY = drawSectionTitle(canvas, "STUDENT ADMISSION FORM", currentY + 30f)

        // --- 7. Draw Details (Fixed Alignment) ---
        drawStudentDetails(canvas, student, currentY + 30f)

        // --- 8. Draw Footer ---
        drawFooter(canvas, A4_HEIGHT - MARGIN_CONTENT - 40f)

        document.finishPage(page)

        val fileName = "AdmissionForm_${student.studentName.replace(" ", "_")}_${student.regNo}.pdf"
        return savePdf(document, fileName)
    }

    // --- DRAWING FUNCTIONS ---

    private fun drawPageBorder(canvas: Canvas) {
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val innerBorderPaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        canvas.drawRect(MARGIN_OUTER, MARGIN_OUTER, A4_WIDTH - MARGIN_OUTER, A4_HEIGHT - MARGIN_OUTER, borderPaint)
        canvas.drawRect(MARGIN_OUTER + 3f, MARGIN_OUTER + 3f, A4_WIDTH - MARGIN_OUTER - 3f, A4_HEIGHT - MARGIN_OUTER - 3f, innerBorderPaint)
    }

    private fun drawHeader(
        canvas: Canvas,
        logo: Bitmap?,
        studentPhoto: Bitmap?,
        orgName: String,
        orgAddress: String
    ): Float {
        val startY = MARGIN_CONTENT + 15f
        val headerHeight = 110f

        // --- 1. Draw Logo (Left) ---
        if (logo != null) {
            val logoMaxH = 70f
            val logoMaxW = 70f
            val aspectRatio = logo.width.toFloat() / logo.height.toFloat()
            val destWidth = if (aspectRatio > 1) logoMaxW else logoMaxH * aspectRatio
            val destHeight = if (aspectRatio > 1) logoMaxW / aspectRatio else logoMaxH

            val logoY = startY + (headerHeight - destHeight) / 2
            val destRect = RectF(MARGIN_CONTENT, logoY, MARGIN_CONTENT + destWidth, logoY + destHeight)
            canvas.drawBitmap(logo, null, destRect, null)
        }

        // --- 2. Draw Photo (Right) ---
        val photoW = 90f
        val photoH = 110f
        val photoX = A4_WIDTH - MARGIN_CONTENT - photoW
        val photoRect = RectF(photoX, startY, photoX + photoW, startY + photoH)
        val borderPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f }

        if (studentPhoto != null) {
            canvas.drawBitmap(studentPhoto, null, photoRect, null)
            canvas.drawRect(photoRect, borderPaint)
        } else {
            val bgPaint = Paint().apply { color = Color.parseColor("#F0F0F0"); style = Paint.Style.FILL }
            val placeholderPaint = TextPaint().apply { color = Color.GRAY; textSize = 10f; textAlign = Paint.Align.CENTER }
            canvas.drawRect(photoRect, bgPaint)
            canvas.drawRect(photoRect, borderPaint)
            canvas.drawText("Photo", photoRect.centerX(), photoRect.centerY(), placeholderPaint)
        }

        // --- 3. Draw Text (Centered & Wrapped) ---

        // Calculate the safe width between Logo and Photo
        // Logo ends approx at 110px, Photo starts approx at 465px.
        // We make a text box of width 320px to fit safely in the middle.
        val textWidth = 320

        // IMPORTANT: For StaticLayout, the Paint alignment MUST be LEFT.
        // The StaticLayout itself handles the centering via ALIGN_CENTER.
        val namePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT  // <--- CRITICAL FIX: Keep this LEFT
            isAntiAlias = true
        }

        val addressPaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 12f
            textAlign = Paint.Align.CENTER // This is fine because we use canvas.drawText for address
            isAntiAlias = true
        }

        // Create Layout that wraps text within 320px and centers lines
        val nameLayout = createStaticLayout(
            orgName.uppercase(),
            namePaint,
            textWidth,
            Layout.Alignment.ALIGN_CENTER
        )

        val totalTextHeight = nameLayout.height + 20f
        val textStartY = startY + (headerHeight - totalTextHeight) / 2f

        // Calculate X to center the 320px box on the page
        val textStartX = (A4_WIDTH - textWidth) / 2f

        canvas.save()
        canvas.translate(textStartX, textStartY)
        nameLayout.draw(canvas)
        canvas.restore()

        // Draw Address
        canvas.drawText(orgAddress, A4_WIDTH / 2f, textStartY + nameLayout.height + 15f, addressPaint)

        return startY + headerHeight + 10f
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, startY: Float): Float {
        val paint = Paint().apply { color = Color.parseColor("#333333"); style = Paint.Style.FILL }
        val textPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            letterSpacing = 0.1f
        }

        val rectHeight = 35f
        val rect = RectF(MARGIN_CONTENT, startY, A4_WIDTH - MARGIN_CONTENT, startY + rectHeight)
        canvas.drawRoundRect(rect, 4f, 4f, paint)

        val fontMetrics = textPaint.fontMetrics
        val textY = rect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2
        canvas.drawText(title, A4_WIDTH / 2f, textY, textPaint)

        return startY + rectHeight + 30f
    }

    private fun drawStudentDetails(canvas: Canvas, student: StudentDetailsItem, startY: Float) {
        val labelPaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT // Right Align Labels
            isAntiAlias = true
        }
        val valuePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT // Left Align Values
            isAntiAlias = true
        }
        val colonPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            textAlign = Paint.Align.CENTER // Center Align Colon
            isAntiAlias = true
        }
        val linePaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 1f }

        var currentY = startY
        val lineHeight = 35f

        // --- 3-COLUMN ALIGNMENT CONFIGURATION ---
        // Label Column ends at 35% of page width
        val labelEndX = A4_WIDTH * 0.35f
        // Colon Column sits exactly at 38%
        val colonX = A4_WIDTH * 0.38f
        // Value Column starts at 41%
        val valueStartX = A4_WIDTH * 0.41f

        // Line ends at right margin
        val lineEndX = A4_WIDTH - MARGIN_CONTENT - 10f

        fun drawField(label: String, value: String?) {
            // 1. Label (Right Aligned)
            canvas.drawText(label, labelEndX, currentY, labelPaint)

            // 2. Colon (Centered)
            canvas.drawText(":", colonX, currentY, colonPaint)

            // 3. Value (Left Aligned)
            val displayValue = value ?: "-"

            // Check if address is long (manual wrapping for address)
            if (label == "Address" && displayValue.length > 30) {
                val addressWidth = (lineEndX - valueStartX).toInt()
                val addressLayout = createStaticLayout(displayValue, TextPaint(valuePaint), addressWidth, Layout.Alignment.ALIGN_NORMAL)

                canvas.save()
                canvas.translate(valueStartX, currentY - 10f) // Adjust top
                addressLayout.draw(canvas)
                canvas.restore()

                // Draw line below the whole address block
                val blockHeight = addressLayout.height.toFloat()
                canvas.drawLine(valueStartX, currentY - 10f + blockHeight + 5f, lineEndX, currentY - 10f + blockHeight + 5f, linePaint)

                // Advance Y based on height
                currentY += blockHeight + 15f // extra gap after multiline
            } else {
                // Standard single line value
                canvas.drawText(displayValue, valueStartX, currentY, valuePaint)
                canvas.drawLine(valueStartX, currentY + 8f, lineEndX, currentY + 8f, linePaint)
                currentY += lineHeight
            }
        }

        drawField("Registration No", student.regNo)
        drawField("Admission Date", student.admissionDate)

        currentY += 10f

        drawField("Student Name", student.studentName)
        drawField("Father's Name", student.parentName)
        drawField("Date of Birth", student.birthDate)
        drawField("Gender", student.gender)

        currentY += 10f

        drawField("Mobile Number", student.parentMobileNumber)
        if (!student.alternateMobileNumber.isNullOrEmpty()) {
            drawField("Alternate Mobile", student.alternateMobileNumber)
        }

        drawField("Address", student.address) // Handles multiline internally now

        val feeFormatted = if (student.monthlyFee != null)
            String.format(Locale.getDefault(), "%.0f", student.monthlyFee)
        else "N/A"
        drawField("Monthly Fee", "$feeFormatted / month")
    }

    private fun drawFooter(canvas: Canvas, startY: Float) {
        val textPaint = TextPaint().apply { color = Color.BLACK; textSize = 12f; textAlign = Paint.Align.CENTER }
        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 1f }

        val quarterWidth = A4_WIDTH / 3.5f
        val signatureY = startY

        // Parent Signature
        val parentLineStart = MARGIN_CONTENT + 20f
        val parentLineEnd = parentLineStart + quarterWidth
        canvas.drawLine(parentLineStart, signatureY, parentLineEnd, signatureY, linePaint)
        canvas.drawText("Parent's Signature", (parentLineStart + parentLineEnd) / 2f, signatureY + 20f, textPaint)

        // Principal Signature
        val principalLineEnd = A4_WIDTH - MARGIN_CONTENT - 20f
        val principalLineStart = principalLineEnd - quarterWidth
        canvas.drawLine(principalLineStart, signatureY, principalLineEnd, signatureY, linePaint)
        canvas.drawText("Principal's Signature", (principalLineStart + principalLineEnd) / 2f, signatureY + 20f, textPaint)

        // Timestamp
        val datePaint = TextPaint().apply { color = Color.LTGRAY; textSize = 9f; textAlign = Paint.Align.LEFT }
        canvas.drawText("Generated: ${java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(java.util.Date())}", MARGIN_CONTENT + 10f, A4_HEIGHT - MARGIN_OUTER - 10f, datePaint)
    }

    private fun createStaticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment
    ): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, alignment, 1.0f, 0.0f, false)
        }
    }

    private fun drawWatermark(canvas: Canvas, logo: Bitmap) {
        val watermarkPaint = Paint().apply { alpha = 15; isAntiAlias = true }
        val watermarkSize = A4_WIDTH * 0.5f
        val aspectRatio = logo.height.toFloat() / logo.width.toFloat()
        val height = watermarkSize * aspectRatio

        val x = (A4_WIDTH - watermarkSize) / 2f
        val y = (A4_HEIGHT - height) / 2f + 50f

        canvas.drawBitmap(logo, null, RectF(x, y, x + watermarkSize, y + height), watermarkPaint)
    }

    private suspend fun awaitLogo(): Bitmap? = withContext(Dispatchers.IO) {
        val logoUrl = FirebaseAuthManager.getOrganizationLogoUrl(context)
        if (logoUrl.isNullOrEmpty()) return@withContext BitmapFactory.decodeResource(context.resources, R.drawable.logo)
        try {
            Glide.with(context).asBitmap().load(logoUrl).submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get()
        } catch (e: Exception) {
            BitmapFactory.decodeResource(context.resources, R.drawable.logo)
        }
    }

    private suspend fun awaitStudentPhoto(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrEmpty()) return@withContext null
        try {
            Glide.with(context).asBitmap().load(url).submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get()
        } catch (e: Exception) { null }
    }

    private suspend fun savePdf(document: PdfDocument, fileName: String): Uri? {
        var fileUri: Uri? = null
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MadarsaAdmissionForms")
                }
            }
            fileUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            fileUri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
            }
        } catch (e: Exception) {
            Log.e("AdmissionForm", "Error saving PDF", e)
        } finally {
            document.close()
        }
        return fileUri
    }
}