package com.example.madarsa_attendance

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.example.madarsa_attendance.models.Organization
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

object DonationReceiptGenerator {

    fun createReceiptImage(
        context: Context,
        orgDetails: Organization,
        donation: DonationRecord,
        logoBitmap: Bitmap?
    ): Uri? {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // --- 1. DEFINE PAINTS FOR BETTER STYLING ---
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val orgNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f // Larger font for Org Name
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val addressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 28f // Smaller font for Address
            textAlign = Paint.Align.LEFT
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 28f
            textAlign = Paint.Align.LEFT // Align labels to the left
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT // Align values to the left
        }
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 25
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 2f
        }
        // --- END OF PAINTS ---

        // Background
        canvas.drawColor(Color.WHITE)

        // Watermark
        if (logoBitmap != null) {
            val maxWatermarkWidth = 500
            val maxWatermarkHeight = 500
            val scale = min(maxWatermarkWidth.toFloat() / logoBitmap.width, maxWatermarkHeight.toFloat() / logoBitmap.height)
            val scaledWidth = (logoBitmap.width * scale).toInt()
            val scaledHeight = (logoBitmap.height * scale).toInt()
            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, scaledWidth, scaledHeight, true)
            val centerX = (canvas.width - scaledWidth) / 2f
            val centerY = (canvas.height - scaledHeight) / 2f
            canvas.drawBitmap(scaledLogo, centerX, centerY, watermarkPaint)
        }

        // --- 2. RESTRUCTURED DRAWING LOGIC FOR PERFECT ALIGNMENT ---
        val leftMargin = 80f
        val valueMargin = 350f // X-position where the values start
        var yPos = 120f

        // Title
        canvas.drawText("Donation Receipt", width / 2f, yPos, titlePaint)
        yPos += 100

        // Organization Details (Centered)
        orgDetails.organizationName?.let {
            canvas.drawText(it, width / 2f, yPos, orgNamePaint)
            yPos += 50
        }
        orgDetails.address?.let {
            val addressLayout = StaticLayout.Builder.obtain(it, 0, it.length, addressPaint, width - 160).build()
            canvas.save()
            // Center the static layout for the address
            canvas.translate((width - addressLayout.width) / 2f, yPos)
            addressLayout.draw(canvas)
            canvas.restore()
            yPos += addressLayout.height + 60
        }

        // Divider
        canvas.drawLine(leftMargin, yPos, width - leftMargin, yPos, linePaint)
        yPos += 60

        // Donation Details (Two-column layout)
        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val dateFormat = SimpleDateFormat("dd MMM, yyyy hh:mm a", Locale.getDefault())

        canvas.drawText("Received from:", leftMargin, yPos, labelPaint)
        canvas.drawText(donation.donorName, valueMargin, yPos, valuePaint)
        yPos += 60

        donation.donorMobile?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("Mobile Number:", leftMargin, yPos, labelPaint)
            canvas.drawText(it, valueMargin, yPos, valuePaint)
            yPos += 60
        }

        canvas.drawText("Amount:", leftMargin, yPos, labelPaint)
        canvas.drawText(currencyFormatter.format(donation.amount), valueMargin, yPos, valuePaint)
        yPos += 60


        donation.donationDate?.let {
            canvas.drawText("Date:", leftMargin, yPos, labelPaint)
            canvas.drawText(dateFormat.format(it), valueMargin, yPos, labelPaint) // Use body paint for date value
            yPos += 60
        }

        donation.purpose?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("Purpose:", leftMargin, yPos, labelPaint)
            val purposeLayout = StaticLayout.Builder.obtain(it, 0, it.length, valuePaint, width - 400).build()
            canvas.save()
            canvas.translate(valueMargin, yPos)
            purposeLayout.draw(canvas)
            canvas.restore()
            yPos += purposeLayout.height + 30
        }

        canvas.drawText("Receipt No:", leftMargin, yPos, labelPaint)
        canvas.drawText(donation.id.take(8).uppercase(), valueMargin, yPos, labelPaint) // Use body paint for receipt no
        yPos += 120

        // Thank you message
        addressPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Thank you for your generous donation.", width / 2f, yPos, addressPaint)
        // --- END OF RESTRUCTURED DRAWING ---

        return saveImage(context, bitmap)
    }

    private fun saveImage(context: Context, image: Bitmap): Uri? {
        val imagesFolder = File(context.cacheDir, "receipts")
        var uri: Uri? = null
        try {
            imagesFolder.mkdirs()
            val file = File(imagesFolder, "donation_receipt_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            image.compress(Bitmap.CompressFormat.PNG, 90, stream)
            stream.flush()
            stream.close()
            uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: IOException) {
            Log.d("DonationReceiptGenerator", "IOException while trying to write file for sharing: " + e.message)
        }
        return uri
    }
}