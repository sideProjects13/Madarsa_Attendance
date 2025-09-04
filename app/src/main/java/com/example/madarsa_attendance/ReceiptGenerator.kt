package com.example.madarsa_attendance

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

/**
 * A flexible and reusable receipt image generator.
 * It uses a `ReceiptData` object to define the content, allowing it
 * to generate receipts for sales, fees, or any other transaction.
 */
object ReceiptGenerator {

    private const val TAG = "ReceiptGenerator"

    // --- Data class to hold all receipt information ---
    data class ReceiptData(
        val title: String,
        @DrawableRes val iconResId: Int,
        val details: List<Pair<String, String>>,
        val summary: List<Pair<String, String>>,
        val watermarkBitmap: Bitmap?,
        val featuredItemBitmap: Bitmap?,
        val studentNameForFilename: String
    )

    // --- Image Configuration ---
    private const val IMAGE_WIDTH = 800
    private const val MARGIN = 60f
    private const val CORNER_RADIUS = 40f
    private const val ROW_HEIGHT = 100f
    private const val HEADER_HEIGHT = 300f
    private const val FEATURED_IMAGE_HEIGHT = 400f // Includes padding
    private const val FOOTER_PADDING = 120f

    // --- Paint Objects ---
    private val backgroundPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val titlePaint = TextPaint().apply {
        isAntiAlias = true; color = Color.BLACK; textSize = 55f;
        textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val labelPaint = TextPaint().apply {
        isAntiAlias = true; color = Color.DKGRAY; textSize = 36f; textAlign = Paint.Align.LEFT
    }
    private val valuePaint = TextPaint().apply {
        isAntiAlias = true; color = Color.BLACK; textSize = 38f;
        textAlign = Paint.Align.RIGHT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
//    private val watermarkPaint = Paint().apply { alpha = 25; isAntiAlias = true }
    private val dividerPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f }

    /**
     * The main public function to generate a receipt image.
     * @param context The application context.
     * @param data The `ReceiptData` object containing all information to be drawn.
     * @return A content `Uri` to the saved image, or null on failure.
     */
    suspend fun generate(context: Context, data: ReceiptData): Uri? {
        val calculatedHeight = calculateImageHeight(data)
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, calculatedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var yPos = 0f

//        // 1. Draw Background and Watermark
        drawBackground(canvas, calculatedHeight.toFloat())
//        data.watermarkBitmap?.let { drawWatermark(canvas, it, calculatedHeight.toFloat()) }

        // 2. Draw Header
        yPos = drawHeader(canvas, context, data.title, data.iconResId)

        // 3. Draw Featured Item Image (if available)
        data.featuredItemBitmap?.let {
            yPos = drawFeaturedImage(canvas, yPos, it)
        }

        // 4. Draw Detail Rows
        yPos = drawSection(canvas, yPos, data.details, isSummary = false)

        // 5. Draw Divider
        canvas.drawLine(MARGIN + 40f, yPos, IMAGE_WIDTH - MARGIN - 40f, yPos, dividerPaint)
        yPos += 40f

        // 6. Draw Summary Rows
        drawSection(canvas, yPos, data.summary, isSummary = true)

        // 7. Save and return URI
        val fileName = "${data.title.replace(" ", "")}_${data.studentNameForFilename.replace(" ", "_")}_${System.currentTimeMillis()}.png"
        return saveImageAndGetUri(context, bitmap, fileName)
    }

    private fun calculateImageHeight(data: ReceiptData): Int {
        var height = HEADER_HEIGHT
        if (data.featuredItemBitmap != null) height += FEATURED_IMAGE_HEIGHT
        height += (data.details.size * ROW_HEIGHT)
        height += 40f // Divider padding
        height += (data.summary.size * ROW_HEIGHT)
        height += FOOTER_PADDING
        return height.toInt()
    }

    private fun drawBackground(canvas: Canvas, height: Float) {
        val rect = RectF(0f, 0f, IMAGE_WIDTH.toFloat(), height)
        canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, backgroundPaint)
    }

//    private fun drawWatermark(canvas: Canvas, logo: Bitmap, height: Float) {
//        val watermarkSize = IMAGE_WIDTH - (MARGIN * 4)
//        val scaledWatermark = Bitmap.createScaledBitmap(logo, watermarkSize.toInt(), watermarkSize.toInt(), true)
//        val x = (IMAGE_WIDTH - watermarkSize) / 2f
//        val y = (height - watermarkSize) / 2f
//        canvas.drawBitmap(scaledWatermark, x, y, watermarkPaint)
//    }

    private fun drawHeader(canvas: Canvas, context: Context, title: String, @DrawableRes iconResId: Int): Float {
        var yPos = MARGIN * 1.5f
        val icon = ContextCompat.getDrawable(context, iconResId)?.toBitmap(120, 120)
        icon?.let {
            canvas.drawBitmap(it, (IMAGE_WIDTH - it.width) / 2f, yPos, null)
            yPos += it.height + 40f
        }
        canvas.drawText(title, IMAGE_WIDTH / 2f, yPos, titlePaint)
        return yPos + 80f
    }

    private fun drawFeaturedImage(canvas: Canvas, startY: Float, itemBitmap: Bitmap): Float {
        val imageSize = 300
        val cornerRadius = 20f
        val x = (IMAGE_WIDTH - imageSize) / 2f
        val y = startY

        val rect = RectF(x, y, x + imageSize, y + imageSize)
        val path = Path().apply { addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW) }

        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(itemBitmap, null, rect, null)
        canvas.restore()

        return y + imageSize + (MARGIN * 1.5f)
    }

    private fun drawSection(canvas: Canvas, startY: Float, items: List<Pair<String, String>>, isSummary: Boolean): Float {
        var yPos = startY
        val lightGray = Color.parseColor("#F5F6F8")

        items.forEachIndexed { index, (label, value) ->
            val bgColor = if (index % 2 == 0) lightGray else Color.WHITE
            val rowRect = RectF(MARGIN, yPos, IMAGE_WIDTH - MARGIN, yPos + ROW_HEIGHT)
            canvas.drawRect(rowRect, Paint().apply { color = bgColor })

            canvas.drawText(label, MARGIN + 40f, yPos + (ROW_HEIGHT / 2f) + 15f, labelPaint)
            if (isSummary) { // Make summary values bolder
                canvas.drawText(value, IMAGE_WIDTH - MARGIN - 40f, yPos + (ROW_HEIGHT / 2f) + 15f, valuePaint)
            } else {
                canvas.drawText(value, IMAGE_WIDTH - MARGIN - 40f, yPos + (ROW_HEIGHT / 2f) + 15f, valuePaint.apply { typeface = Typeface.DEFAULT })
            }
            yPos += ROW_HEIGHT
        }
        return yPos
    }

    private fun saveImageAndGetUri(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val subfolder = "MadarsaReceipts"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + subfolder)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { resolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } }
                return uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), subfolder)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving receipt image", e)
            return null
        }
    }
}