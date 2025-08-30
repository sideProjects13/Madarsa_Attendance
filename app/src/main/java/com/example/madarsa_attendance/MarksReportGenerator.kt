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
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

object MarksReportGenerator {

    private const val TAG = "MarksReportGenerator"
    private const val PAGE_WIDTH = 595 // A4 Portrait
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    private data class MarksGrid(
        val students: List<StudentDetailsItem>,
        val subjects: List<SubjectItem>,
        val marksMap: Map<String, Map<String, String>> // StudentID -> (SubjectID -> Marks)
    )

    suspend fun generateReport(
        context: Context,
        db: FirebaseFirestore,
        organizationId: String,
        teacher: Teacher?, // Null for organization-wide report
        exam: Exam
    ): Uri? {
        val students = fetchStudents(db, organizationId, teacher)
        if (students.isEmpty()) {
            Log.w(TAG, "No students found for marks report.")
            return null
        }

        // Fetch subjects relevant to the selected scope (teacher or all)
        val subjects = fetchSubjects(db, organizationId, teacher, students)
        if (subjects.isEmpty()) {
            Log.w(TAG, "No subjects found for the selected students.")
            return null
        }

        val marksData = fetchMarksData(db, organizationId, teacher, exam)
        val grid = MarksGrid(students, subjects, marksData)

        return createPdf(context, grid, teacher, exam)
    }

    private suspend fun fetchStudents(db: FirebaseFirestore, orgId: String, teacher: Teacher?): List<StudentDetailsItem> {
        val query = if (teacher != null) {
            db.collection("organizations").document(orgId).collection("students")
                .whereEqualTo("teacherId", teacher.teacherId)
        } else {
            db.collection("organizations").document(orgId).collection("students")
        }
        return query.whereEqualTo("isActive", true).orderBy("studentName").get().await()
            .toObjects(StudentDetailsItem::class.java)
    }

    private suspend fun fetchSubjects(db: FirebaseFirestore, orgId: String, teacher: Teacher?, students: List<StudentDetailsItem>): List<SubjectItem> {
        // If a specific teacher is selected, only fetch their subjects.
        if (teacher != null) {
            return db.collection("organizations").document(orgId).collection("subjects")
                .whereEqualTo("teacherId", teacher.teacherId).orderBy("subjectName").get().await()
                .toObjects(SubjectItem::class.java)
        }
        // For an org-wide report, get all unique subjects for the fetched students.
        val teacherIds = students.map { it.teacherId }.distinct()
        if (teacherIds.isEmpty()) return emptyList()

        return db.collection("organizations").document(orgId).collection("subjects")
            .whereIn("teacherId", teacherIds).orderBy("subjectName").get().await()
            .toObjects(SubjectItem::class.java)
    }

    private suspend fun fetchMarksData(db: FirebaseFirestore, orgId: String, teacher: Teacher?, exam: Exam): Map<String, Map<String, String>> {
        val marksMap = mutableMapOf<String, Map<String, String>>()
        val query = if (teacher != null) {
            db.collection("organizations").document(orgId).collection("examResults")
                .whereEqualTo("teacherId", teacher.teacherId).whereEqualTo("examId", exam.id)
        } else {
            db.collection("organizations").document(orgId).collection("examResults")
                .whereEqualTo("examId", exam.id)
        }
        val results = query.get().await()
        for (doc in results.documents) {
            val studentId = doc.getString("studentId") ?: continue
            val marks = doc.get("marks") as? Map<String, String> ?: continue
            marksMap[studentId] = marks
        }
        return marksMap
    }

    private fun createPdf(context: Context, grid: MarksGrid, teacher: Teacher?, exam: Exam): Uri? {
        val document = PdfDocument()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = Color.BLACK }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = Color.DKGRAY }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE; textAlign = Paint.Align.CENTER }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK; textAlign = Paint.Align.CENTER }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val headerBgPaint = Paint().apply { color = Color.parseColor("#424242"); style = Paint.Style.FILL }

        val rollNoColWidth = 40f
        val nameColWidth = 120f
        val subjectColWidth = 65f
        val rowHeight = 30f

        val contentWidth = PAGE_WIDTH - (MARGIN * 2)
        val fixedColsWidth = rollNoColWidth + nameColWidth
        val dynamicColsWidth = contentWidth - fixedColsWidth
        val dynamicColWidth = if (grid.subjects.isNotEmpty()) dynamicColsWidth / grid.subjects.size else 0f

        val contentHeight = PAGE_HEIGHT - (MARGIN * 2) - 60f
        val rowsPerPage = (contentHeight / rowHeight).toInt() - 1
        val totalPages = ceil(grid.students.size.toFloat() / rowsPerPage).toInt().coerceAtLeast(1)

        for (pageIndex in 0 until totalPages) {
            val pageNumber = pageIndex + 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            var yPos = MARGIN

            canvas.drawColor(Color.WHITE)
            canvas.drawText(exam.name, MARGIN, yPos, titlePaint)
            yPos += 20
            val scope = teacher?.teacherName ?: "All Classes"
            canvas.drawText("Marks Report - $scope", MARGIN, yPos, subtitlePaint)
            yPos += 30

            val headerTop = yPos
            canvas.drawRect(MARGIN, headerTop, PAGE_WIDTH - MARGIN, headerTop + rowHeight, headerBgPaint)
            var xPos = MARGIN
            val textOffsetY = -((headerPaint.descent() + headerPaint.ascent()) / 2)

            canvas.drawText("No.", xPos + rollNoColWidth / 2, headerTop + rowHeight / 2 + textOffsetY, headerPaint)
            xPos += rollNoColWidth
            canvas.drawText("Student Name", xPos + nameColWidth / 2, headerTop + rowHeight / 2 + textOffsetY, headerPaint)
            xPos += nameColWidth
            grid.subjects.forEach { subject ->
                canvas.drawText(subject.subjectName, xPos + dynamicColWidth / 2, headerTop + rowHeight / 2 + textOffsetY, headerPaint)
                xPos += dynamicColWidth
            }
            yPos += rowHeight

            val studentStartIndex = pageIndex * rowsPerPage
            val studentEndIndex = (studentStartIndex + rowsPerPage).coerceAtMost(grid.students.size)
            val studentsOnPage = grid.students.subList(studentStartIndex, studentEndIndex)

            studentsOnPage.forEachIndexed { index, student ->
                xPos = MARGIN
                val rowVerticalCenter = yPos + rowHeight / 2 + textOffsetY
                if (index % 2 != 0) canvas.drawRect(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + rowHeight, Paint().apply { color = Color.parseColor("#F5F6F8") })

                canvas.drawText((studentStartIndex + index + 1).toString(), xPos + rollNoColWidth / 2, rowVerticalCenter, cellPaint)
                xPos += rollNoColWidth
                canvas.drawText(student.studentName, xPos + 5, rowVerticalCenter, cellPaint.apply { textAlign = Paint.Align.LEFT })
                xPos += nameColWidth

                val studentMarks = grid.marksMap[student.id]
                grid.subjects.forEach { subject ->
                    val marks = studentMarks?.get(subject.id) ?: "-"
                    canvas.drawText(marks, xPos + dynamicColWidth / 2, rowVerticalCenter, cellPaint.apply { textAlign = Paint.Align.CENTER })
                    xPos += dynamicColWidth
                }
                yPos += rowHeight
                canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, linePaint)
            }
            document.finishPage(page)
        }

        val fileName = "MarksReport_${exam.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        return savePdf(context, document, fileName)
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
                uri?.let { resolver.openOutputStream(it)?.use { outputStream -> document.writeTo(outputStream) } }
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
            document.close()
            return null
        }
    }
}