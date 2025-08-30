package com.example.madarsa_attendance

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

object AttendanceReportGenerator {

    private const val TAG = "AttnReportGenerator"
    private const val PAGE_WIDTH = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 40f
    private val DATE_FORMAT_DB = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val DATE_FORMAT_HEADER = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private data class AttendanceGrid(
        val students: List<StudentDetailsItem>,
        val dates: List<Date>,
        val statusMap: Map<String, Map<String, String>>, // StudentID -> (DateString -> Status)
        // --- CHANGE 1: New data structure to track marked days per teacher ---
        val markedDaysByTeacher: Map<String, Set<String>> // TeacherID -> Set of DateStrings
    )

    suspend fun generateReport(
        context: Context,
        db: FirebaseFirestore,
        organizationId: String,
        teacher: Teacher?,
        startDate: Date,
        endDate: Date
    ): Uri? {
        val students = fetchStudents(db, organizationId, teacher)
        if (students.isEmpty()) {
            Log.w(TAG, "No students found for the selected scope.")
            return null
        }
        // --- CHANGE 2: Fetch the new markedDaysByTeacher map ---
        val (attendanceData, markedDaysByTeacher) = fetchAttendanceData(db, organizationId, teacher, startDate, endDate)
        val dateList = getDateRange(startDate, endDate)
        val grid = AttendanceGrid(students, dateList, attendanceData, markedDaysByTeacher)
        return createPdf(context, grid, teacher, startDate, endDate)
    }

    private suspend fun fetchStudents(db: FirebaseFirestore, orgId: String, teacher: Teacher?): List<StudentDetailsItem> {
        return try {
            val query = if (teacher != null) {
                db.collection("organizations").document(orgId).collection("students")
                    .whereEqualTo("teacherId", teacher.teacherId)
            } else {
                db.collection("organizations").document(orgId).collection("students")
            }
            query.whereEqualTo("isActive", true).orderBy("teacherName").orderBy("studentName").get().await().toObjects(StudentDetailsItem::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching students", e)
            emptyList()
        }
    }

    // --- CHANGE 3: Function now returns a Pair containing both maps ---
    private suspend fun fetchAttendanceData(db: FirebaseFirestore, orgId: String, teacher: Teacher?, startDate: Date, endDate: Date): Pair<Map<String, Map<String, String>>, Map<String, Set<String>>> {
        val statusMap = mutableMapOf<String, MutableMap<String, String>>()
        val markedDaysByTeacher = mutableMapOf<String, MutableSet<String>>()
        try {
            var query: Query = db.collection("organizations").document(orgId).collection("attendanceRecords")
            if (teacher != null) {
                query = query.whereEqualTo("teacherId", teacher.teacherId)
            }
            val records = query.whereGreaterThanOrEqualTo("date", DATE_FORMAT_DB.format(startDate))
                .whereLessThanOrEqualTo("date", DATE_FORMAT_DB.format(endDate))
                .get().await()

            for (doc in records.documents) {
                val dateStr = doc.getString("date") ?: continue
                val teacherId = doc.getString("teacherId") ?: continue

                // Populate the new map
                markedDaysByTeacher.getOrPut(teacherId) { mutableSetOf() }.add(dateStr)

                val studentAttendances = doc.get("studentAttendances") as? List<Map<String, Any>> ?: continue
                for (att in studentAttendances) {
                    val studentId = att["studentId"] as? String ?: continue
                    val status = att["status"] as? String ?: "A"
                    statusMap.getOrPut(studentId) { mutableMapOf() }[dateStr] = if (status == "Present") "P" else "A"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching attendance data", e)
        }
        return Pair(statusMap, markedDaysByTeacher)
    }

    private fun getDateRange(start: Date, end: Date): List<Date> {
        val dates = mutableListOf<Date>()
        val cal = Calendar.getInstance().apply { time = start }
        while (!cal.time.after(end)) {
            dates.add(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }

    private fun createPdf(context: Context, grid: AttendanceGrid, teacher: Teacher?, startDate: Date, endDate: Date): Uri? {
        val document = PdfDocument()

        // ... (All paint and layout variable initializations remain the same) ...

        val baseFont = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val boldFont = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val tableHeaderBgColor = Color.rgb(240, 240, 240)
        val holidayColor = Color.LTGRAY

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = boldFont; color = Color.BLACK; textAlign = Paint.Align.LEFT }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = baseFont; color = Color.DKGRAY }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = boldFont; color = Color.BLACK; textAlign = Paint.Align.CENTER }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = baseFont; color = Color.BLACK; textAlign = Paint.Align.CENTER }
        val absentCellPaint = TextPaint(cellPaint).apply { color = Color.RED; typeface = boldFont }
        val holidayPaint = TextPaint(cellPaint).apply { color = Color.GRAY }
        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 1f }
        val headerBgPaint = Paint().apply { color = tableHeaderBgColor; style = Paint.Style.FILL }

        val isOrgReport = teacher == null
        val rollNoColWidth = 50f
        val studentNameColWidth = 150f
        val teacherNameColWidth = if (isOrgReport) 100f else 0f
        val presentCountColWidth = 60f
        val absentCountColWidth = 60f
        val dateColWidth = 30f
        val rowHeight = 30f

        val totalWidth = MARGIN * 2 + rollNoColWidth + studentNameColWidth + teacherNameColWidth + presentCountColWidth + absentCountColWidth + (grid.dates.size * dateColWidth)
        val contentHeight = PAGE_HEIGHT - (MARGIN * 2) - 60f
        val rowsPerPage = (contentHeight / rowHeight).toInt() - 2
        val totalStudentPages = ceil(grid.students.size.toFloat() / rowsPerPage).toInt().coerceAtLeast(1)
        var pageNumber = 0

        val scopeTitle = teacher?.teacherName ?: "Entire Organization"

        for (studentPage in 0 until totalStudentPages) {
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(totalWidth.toInt(), PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            var yPos = MARGIN

            // ... (Drawing header text remains the same) ...
            canvas.drawColor(Color.WHITE)

            canvas.drawText("Attendance Register", MARGIN, yPos, titlePaint)
            yPos += 15f
            canvas.drawText(scopeTitle, MARGIN, yPos, subtitlePaint)
            val dateTitle = "Period: ${DATE_FORMAT_HEADER.format(startDate)} to ${DATE_FORMAT_HEADER.format(endDate)}"
            canvas.drawText(dateTitle, totalWidth - MARGIN, yPos, subtitlePaint.apply { textAlign = Paint.Align.RIGHT })
            yPos += 25f

            val headerTopY = yPos
            val headerMidY = headerTopY + rowHeight
            val headerBottomY = headerMidY + rowHeight
            canvas.drawRect(MARGIN, headerTopY, totalWidth - MARGIN, headerBottomY, headerBgPaint)

            var xPos = MARGIN
            val textOffsetY = -((headerPaint.descent() + headerPaint.ascent()) / 2)

            canvas.drawText("Roll No.", xPos + rollNoColWidth / 2, headerMidY + textOffsetY, headerPaint)
            xPos += rollNoColWidth
            canvas.drawText("Student Name", xPos + studentNameColWidth / 2, headerMidY + textOffsetY, headerPaint)
            xPos += studentNameColWidth
            if (isOrgReport) {
                canvas.drawText("Teacher", xPos + teacherNameColWidth / 2, headerMidY + textOffsetY, headerPaint)
                xPos += teacherNameColWidth
            }

            grid.dates.forEach { date ->
                val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.getDefault())
                canvas.drawText(dayOfWeekFormat.format(date), xPos + dateColWidth / 2, headerTopY + rowHeight / 2 + textOffsetY, headerPaint)
                val dayOfMonthFormat = SimpleDateFormat("dd", Locale.getDefault())
                canvas.drawText(dayOfMonthFormat.format(date), xPos + dateColWidth / 2, headerMidY + rowHeight / 2 + textOffsetY, headerPaint)
                xPos += dateColWidth
            }
            canvas.drawText("Days Present", xPos + presentCountColWidth / 2, headerMidY + textOffsetY, headerPaint)
            xPos += presentCountColWidth
            canvas.drawText("Days Absent", xPos + absentCountColWidth / 2, headerMidY + textOffsetY, headerPaint)

            yPos = headerBottomY

            val studentStartIndex = studentPage * rowsPerPage
            val studentEndIndex = (studentStartIndex + rowsPerPage - 1).coerceAtMost(grid.students.size - 1)
            val studentsForPage = grid.students.subList(studentStartIndex, studentEndIndex + 1)

            studentsForPage.forEachIndexed { indexOnPage, student ->
                val rowTopY = yPos
                val rowVerticalCenter = rowTopY + rowHeight / 2 + textOffsetY
                xPos = MARGIN

                canvas.drawText((studentStartIndex + indexOnPage + 1).toString(), xPos + rollNoColWidth / 2, rowVerticalCenter, cellPaint)
                xPos += rollNoColWidth
                canvas.drawText(student.studentName, xPos + 8f, rowVerticalCenter, cellPaint.apply { textAlign = Paint.Align.LEFT })
                xPos += studentNameColWidth
                if (isOrgReport) {
                    canvas.drawText(student.teacherName ?: "", xPos + 8f, rowVerticalCenter, cellPaint.apply { textAlign = Paint.Align.LEFT })
                    xPos += teacherNameColWidth
                }

                var presentCount = 0
                var absentCount = 0
                val studentAttendance = grid.statusMap[student.id]

                // --- CHANGE 4: The core logic fix is here ---
                grid.dates.forEach { date ->
                    val dateStr = DATE_FORMAT_DB.format(date)

                    // Check if THIS SPECIFIC student's teacher marked attendance on this day.
                    val wasMarkedByTeacher = grid.markedDaysByTeacher[student.teacherId]?.contains(dateStr) == true

                    if (wasMarkedByTeacher) {
                        val status = studentAttendance?.get(dateStr) ?: "A" // Default to Absent if marked but no record
                        if (status == "P") {
                            presentCount++
                            canvas.drawText(status, xPos + dateColWidth / 2, rowVerticalCenter, cellPaint)
                        } else {
                            absentCount++
                            canvas.drawText(status, xPos + dateColWidth / 2, rowVerticalCenter, absentCellPaint)
                        }
                    } else {
                        // This teacher did NOT mark attendance today.
                        canvas.drawText("-", xPos + dateColWidth / 2, rowVerticalCenter, holidayPaint)
                    }
                    xPos += dateColWidth
                }
                // --- END OF FIX ---

                cellPaint.apply { textAlign = Paint.Align.CENTER }
                canvas.drawText(presentCount.toString(), xPos + presentCountColWidth / 2, rowVerticalCenter, cellPaint)
                xPos += presentCountColWidth
                canvas.drawText(absentCount.toString(), xPos + absentCountColWidth / 2, rowVerticalCenter, cellPaint)

                yPos += rowHeight
            }
            // ... (Drawing table lines remains the same) ...

            val tableBottomY = yPos
            canvas.drawLine(MARGIN, headerTopY, totalWidth - MARGIN, headerTopY, linePaint)
            val dateColumnsStartX = MARGIN + rollNoColWidth + studentNameColWidth + teacherNameColWidth
            canvas.drawLine(dateColumnsStartX, headerMidY, totalWidth - MARGIN - presentCountColWidth - absentCountColWidth, headerMidY, linePaint)
            for (i in 0..studentsForPage.size) {
                canvas.drawLine(MARGIN, headerBottomY + (i * rowHeight), totalWidth - MARGIN, headerBottomY + (i * rowHeight), linePaint)
            }

            xPos = MARGIN
            canvas.drawLine(xPos, headerTopY, xPos, tableBottomY, linePaint)
            xPos += rollNoColWidth
            canvas.drawLine(xPos, headerTopY, xPos, tableBottomY, linePaint)
            xPos += studentNameColWidth
            canvas.drawLine(xPos, headerTopY, xPos, tableBottomY, linePaint)
            if (isOrgReport) {
                xPos += teacherNameColWidth
                canvas.drawLine(xPos, headerTopY, xPos, tableBottomY, linePaint)
            }
            grid.dates.forEach { _ ->
                xPos += dateColWidth
                canvas.drawLine(xPos, headerTopY, xPos, tableBottomY, linePaint)
            }
            canvas.drawLine(xPos, headerTopY, xPos, tableBottomY, linePaint)
            xPos += presentCountColWidth
            canvas.drawLine(xPos, headerTopY, xPos, tableBottomY, linePaint)
            xPos += absentCountColWidth
            canvas.drawLine(xPos, headerTopY, xPos, tableBottomY, linePaint)


            document.finishPage(page)
        }

        val fileName = "AttendanceRegister_${scopeTitle.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        return savePdf(context, document, fileName)
    }


}
    private fun savePdf(context: Context, document: PdfDocument, fileName: String): Uri? {
        val subfolder = "MadarsaReports"
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
                    resolver.openOutputStream(it)?.use { outputStream -> document.writeTo(outputStream) }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), subfolder)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { outputStream -> document.writeTo(outputStream) }
                uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
            document.close()
            return uri
        } catch (e: Exception) {
//            Log.e(TAG, "Error writing PDF", e)
            document.close()
            return null
        }
    }
