package com.example.madarsa_attendance

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

object ReceiptImageGenerator {

    private const val TAG = "ReceiptImageGenerator"
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    // Image Dimensions
    private const val IMAGE_WIDTH = 800
    private const val IMAGE_HEIGHT = 1350
    private const val CORNER_RADIUS = 40f
    private const val MARGIN = 60f

    fun createFeeReceiptImage(
        context: Context,
        studentName: String,
        teacherName: String,
        registrationId: String,
        feeMonth: String,
        paymentDate: String,
        totalAmount: Double,
        depositAmount: Double,
        remainingAmount: Double,
        logoBitmap: Bitmap
    ): Uri? {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)
        drawWatermark(canvas, logoBitmap)
        val yPos = drawHeader(canvas, context) // Pass context to header

        drawDetails(
            canvas, yPos,
            registrationId,
            teacherName,
            studentName,
            feeMonth,
            paymentDate,
            totalAmount,
            depositAmount,
            remainingAmount
        )

        val fileName = "FeeReceipt_${studentName.replace(" ", "_")}_${System.currentTimeMillis()}.png"
        return saveImage(context, bitmap, fileName)
    }

    private fun drawBackground(canvas: Canvas) {
        val backgroundPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val rect = RectF(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat())
        canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, backgroundPaint)
    }

    private fun drawWatermark(canvas: Canvas, logo: Bitmap) {
        try {
            val watermarkPaint = Paint().apply {
                alpha = 25
                isAntiAlias = true
            }
            val watermarkSize = IMAGE_WIDTH - (MARGIN * 4)
            val scaledWatermark = Bitmap.createScaledBitmap(logo, watermarkSize.toInt(), watermarkSize.toInt(), true)
            val x = (IMAGE_WIDTH - watermarkSize) / 2f
            val y = (IMAGE_HEIGHT - watermarkSize) / 2f
            canvas.drawBitmap(scaledWatermark, x, y, watermarkPaint)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to draw watermark bitmap.", e)
        }
    }

    private fun drawHeader(canvas: Canvas, context: Context): Float {
        var yPos = MARGIN * 2

        // FIX for unresolved reference: ic_fees_paid
        val iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_receipt)
        val iconBitmap = iconDrawable?.toBitmap(120, 120, Bitmap.Config.ARGB_8888)
        if (iconBitmap != null) {
            canvas.drawBitmap(iconBitmap, (IMAGE_WIDTH - iconBitmap.width) / 2f, yPos, null)
            yPos += iconBitmap.height + 40f
        }

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.parseColor("#333333")
            textSize = 55f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText("Fees Paid Receipt", IMAGE_WIDTH / 2f, yPos, titlePaint)

        return yPos + 120f
    }

    private fun drawDetails(
        canvas: Canvas, startY: Float, regId: String, className: String,
        studentName: String, feeMonth: String, paymentDate: String,
        total: Double, deposit: Double, remaining: Double
    ) {
        var yPos = startY
        val rowHeight = 110f
        val itemBackgroundColor = Color.parseColor("#F5F6F8")

        yPos = drawDetailRow(canvas, yPos, rowHeight, "Registration ID", regId, itemBackgroundColor)
        yPos = drawDetailRow(canvas, yPos, rowHeight, "Name", studentName, Color.WHITE)
        yPos = drawDetailRow(canvas, yPos, rowHeight, "Class", className, itemBackgroundColor)
        yPos = drawDetailRow(canvas, yPos, rowHeight, "Fee Month", feeMonth, Color.WHITE)
        yPos = drawDetailRow(canvas, yPos, rowHeight, "Payment Date", paymentDate, itemBackgroundColor)

        // FIX for Type Mismatch: currencyFormatter.format returns a String
        yPos = drawDetailRow(canvas, yPos, rowHeight, "Deposit", currencyFormatter.format(deposit), Color.WHITE)
        drawDetailRow(canvas, yPos, rowHeight, "Remaining", currencyFormatter.format(remaining), itemBackgroundColor)
    }

    private fun drawDetailRow(canvas: Canvas, y: Float, height: Float, label: String, value: String, bgColor: Int): Float {
        val paint = Paint().apply { color = bgColor }
        canvas.drawRect(MARGIN, y, IMAGE_WIDTH - MARGIN, y + height, paint)

        val labelPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.parseColor("#8A8A8A")
            textSize = 36f
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(label, MARGIN + 40f, y + (height / 2f) + 15f, labelPaint)

        val valuePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.parseColor("#333333")
            textSize = 38f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText(value, IMAGE_WIDTH - MARGIN - 40f, y + (height / 2f) + 15f, valuePaint)

        return y + height
    }

    private fun saveImage(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val subfolder = "MadarsaReceipts"
        try {
            val uri: Uri?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + subfolder)
                }
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    } ?: throw IOException("Failed to get output stream.")
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), subfolder)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, fileName)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                // FIX for unresolved reference: FileProvider
                uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
            Log.d(TAG, "Image saved successfully: $uri")
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image", e)
            return null
        }
    }
}