package com.example.madarsa_attendance

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class ManageMarks : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var btnGenerateAll: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private lateinit var reportCardGenerator: ReportCardGenerator
    private lateinit var teacherId: String
    private lateinit var examId: String

    private var allSubjects: List<SubjectItem> = emptyList()
    private var allStudentMarks: List<StudentMarks> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_marks)

        teacherId = intent.getStringExtra("EXTRA_TEACHER_ID") ?: ""
        examId = intent.getStringExtra("EXTRA_EXAM_ID") ?: ""
        val examName = intent.getStringExtra("EXTRA_EXAM_NAME") ?: "Enter Marks"

        if (teacherId.isEmpty() || examId.isEmpty()) {
            Toast.makeText(this, "Error: Missing data.", Toast.LENGTH_LONG).show(); finish(); return
        }

        reportCardGenerator = ReportCardGenerator(this)
        toolbar = findViewById(R.id.toolbar_manage_marks)
        recyclerView = findViewById(R.id.recyclerViewMarks)
        progressBar = findViewById(R.id.progressBarMarks)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        btnGenerateAll = findViewById(R.id.btnGenerateAllResults)

        toolbar.title = examName
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        (recyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false

        btnGenerateAll.setOnClickListener { generateBulkPdf() }
        loadInitialData()
    }

    private fun loadInitialData() {
        progressBar.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE

        val studentsRef = db.collection("students").whereEqualTo("teacherId", teacherId)
        val subjectsRef = db.collection("subjects").whereEqualTo("teacherId", teacherId)
        val marksRef = db.collection("examResults").whereEqualTo("examId", examId).whereEqualTo("teacherId", teacherId)

        subjectsRef.get().addOnSuccessListener { subjectSnapshot ->
            this.allSubjects = subjectSnapshot.toObjects(SubjectItem::class.java)
            if (allSubjects.isEmpty()) {
                Toast.makeText(this, "No subjects for this class.", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
                return@addOnSuccessListener
            }

            studentsRef.get().addOnSuccessListener { studentSnapshot ->
                val students = studentSnapshot.toObjects(StudentDetailsItem::class.java)

                if (students.isEmpty()) {
                    progressBar.visibility = View.GONE
                    tvEmptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    return@addOnSuccessListener
                } else {
                    tvEmptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }

                this.allStudentMarks = students.map { StudentMarks(it, mutableMapOf()) }

                marksRef.get().addOnSuccessListener { marksSnapshot ->
                    for (doc in marksSnapshot) {
                        val studentId = doc.getString("studentId") ?: continue
                        val marksMap = doc.get("marks") as? Map<String, String> ?: continue
                        allStudentMarks.find { it.student.id == studentId }?.marks = marksMap.toMutableMap()
                    }

                    val adapter = StudentMarksAdapter(allStudentMarks, allSubjects,
                        { studentMarksToSave -> saveSingleStudentMarks(studentMarksToSave) },
                            { studentMarksToGenerate -> generateSinglePdf(studentMarksToGenerate) }
                    )
                    recyclerView.adapter = adapter
                    progressBar.visibility = View.GONE
                    btnGenerateAll.visibility = View.VISIBLE
                }.addOnFailureListener { progressBar.visibility = View.GONE }
            }.addOnFailureListener { progressBar.visibility = View.GONE }
        }.addOnFailureListener { progressBar.visibility = View.GONE }
    }

    private fun generateSinglePdf(studentMarks: StudentMarks) {
        val reportData = ReportCardGenerator.ReportData(studentMarks.student, toolbar.title.toString(), studentMarks.marks, allSubjects)
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val result = reportCardGenerator.generateSingleReport(reportData, "Madarsa Aaisha Siddiqa talimul quran", "Sarni Society, Ahmedabad, Gujarat")
            progressBar.visibility = View.GONE
            Toast.makeText(this@ManageMarks, result ?: "Failed to generate PDF.", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateBulkPdf() {
        if (allStudentMarks.isEmpty()) {
            Toast.makeText(this, "No data to generate report.", Toast.LENGTH_SHORT).show(); return
        }
        val reportDataList = allStudentMarks.map { ReportCardGenerator.ReportData(it.student, toolbar.title.toString(), it.marks, allSubjects) }
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val result = reportCardGenerator.generateBulkReport(reportDataList, "Madarsa Aaisha Siddiqa talimul quran", "Your Madarsa Address, City, State")
            progressBar.visibility = View.GONE
            Toast.makeText(this@ManageMarks, result ?: "Failed to generate PDF.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveSingleStudentMarks(studentMark: StudentMarks) {
        progressBar.visibility = View.VISIBLE
        val docId = "${examId}_${studentMark.student.id}"
        val docRef = db.collection("examResults").document(docId)
        val data = hashMapOf(
            "examId" to examId,
            "teacherId" to teacherId,
            "studentId" to studentMark.student.id,
            "studentName" to studentMark.student.studentName,
            "marks" to studentMark.marks
        )
        docRef.set(data).addOnSuccessListener {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "${studentMark.student.studentName}'s marks saved!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}