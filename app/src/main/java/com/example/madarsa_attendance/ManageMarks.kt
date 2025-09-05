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
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class ManageMarks : AppCompatActivity() {

    private companion object {
        private const val TAG = "ManageMarks"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var btnGenerateAll: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private lateinit var reportCardGenerator: ReportCardGenerator
    private lateinit var teacherId: String
    private lateinit var examId: String
    private var currentOrganizationId: String? = null

    private var allSubjects: List<SubjectItem> = emptyList()
    private var allStudentMarks: List<StudentMarks> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_marks)

        teacherId = intent.getStringExtra("EXTRA_TEACHER_ID") ?: ""
        examId = intent.getStringExtra("EXTRA_EXAM_ID") ?: ""
        val examName = intent.getStringExtra("EXTRA_EXAM_NAME") ?: "Enter Marks"
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)

        if (teacherId.isEmpty() || examId.isEmpty() || currentOrganizationId == null) {
            Toast.makeText(this, "Error: Missing required data.", Toast.LENGTH_LONG).show()
            finish()
            return
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
        if (currentOrganizationId == null) return

        progressBar.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE

        val studentsRef = db.collection("organizations").document(currentOrganizationId!!).collection("students").whereEqualTo("teacherId", teacherId)
        val subjectsRef = db.collection("organizations").document(currentOrganizationId!!).collection("subjects").whereEqualTo("teacherId", teacherId)
        val marksRef = db.collection("organizations").document(currentOrganizationId!!).collection("examResults").whereEqualTo("examId", examId).whereEqualTo("teacherId", teacherId)

        subjectsRef.get().addOnSuccessListener { subjectSnapshot ->
            this.allSubjects = subjectSnapshot.toObjects(SubjectItem::class.java)
            if (allSubjects.isEmpty()) {
                Toast.makeText(this, "No subjects found for this class. Please add subjects first.", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
                tvEmptyState.text = "No subjects found for this class."
                tvEmptyState.visibility = View.VISIBLE
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
            // --- THIS IS THE FIX ---
            // The generator now fetches the name and address itself. No need to pass them.
            reportCardGenerator.generateSingleReport(reportData)
            // --- END OF FIX ---
            progressBar.visibility = View.GONE
        }
    }

    // REPLACE THIS FUNCTION
    private fun generateBulkPdf() {
        if (allStudentMarks.isEmpty()) {
            Toast.makeText(this, "No data to generate report.", Toast.LENGTH_SHORT).show(); return
        }
        val reportDataList = allStudentMarks.map { ReportCardGenerator.ReportData(it.student, toolbar.title.toString(), it.marks, allSubjects) }
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            // --- THIS IS THE FIX ---
            // The generator now fetches the name and address itself. No need to pass them.
            reportCardGenerator.generateBulkReport(reportDataList)
            // --- END OF FIX ---
            progressBar.visibility = View.GONE
        }
    }
    private fun saveSingleStudentMarks(studentMark: StudentMarks) {
        if (currentOrganizationId == null) {
            StatusDialogFragment.newInstance(false, "Organization ID missing.").show(supportFragmentManager, "failureDialog")
            return
        }
        progressBar.visibility = View.VISIBLE

        // The document ID remains the same, which is good for overwriting/updating.
        val docId = "${examId}_${studentMark.student.id}"
        val docRef = db.collection("organizations").document(currentOrganizationId!!).collection("examResults").document(docId)

        // --- NEW LOGIC: Create the complete ExamResult object ---

        // 1. Create a snapshot of the subjects for this exam result
        val subjectSnapshots = allSubjects.map {
            SubjectSnapshot(subjectId = it.id, subjectName = it.subjectName)
        }

        // 2. Determine the academic year (you can make this more sophisticated later)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val academicYear = "${currentYear}-${currentYear + 1}"

        // 3. Build the complete ExamResult object with all historical context
        val examResultData = ExamResult(
            id = docId,
            examId = examId,
            examName = toolbar.title.toString(), // Get exam name from the toolbar title
            studentId = studentMark.student.id,
            studentName = studentMark.student.studentName,
            teacherId = teacherId,
            teacherName = studentMark.student.teacherName ?: "N/A", // Use the student's teacher at the time
            academicYear = academicYear,
            subjects = subjectSnapshots,
            marks = studentMark.marks,
            resultDate = Date()
        )
        // --- END OF NEW LOGIC ---

        // Save the entire object to Firestore.
        // Using .set() will create the document if it doesn't exist, or overwrite it if it does.
        docRef.set(examResultData).addOnSuccessListener {
            progressBar.visibility = View.GONE
            StatusDialogFragment.newInstance(true, "Marks Saved!")
                .show(supportFragmentManager, "successDialog")

        }.addOnFailureListener { e ->
            progressBar.visibility = View.GONE
            StatusDialogFragment.newInstance(false, "Save Failed")
                .show(supportFragmentManager, "failureDialog")
            Log.e(TAG, "Error saving marks for student ${studentMark.student.id}", e)
        }
    }
}