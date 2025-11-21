package com.example.madarsa_attendance

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TeacherAttendanceActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private var currentOrganizationId: String? = null

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvAttendanceDate: TextView
    private lateinit var btnChangeDate: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TeacherAttendanceAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoTeachers: TextView
    private lateinit var btnSave: MaterialButton

    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_attendance)

        db = FirebaseFirestore.getInstance()
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)

        if (currentOrganizationId == null) {
            Toast.makeText(this, "Organization ID missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        setupListeners()

        updateDateDisplay()
        loadTeachersAndAttendance()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar_teacher_attendance)
        tvAttendanceDate = findViewById(R.id.tvAttendanceDate)
        btnChangeDate = findViewById(R.id.btnChangeDate)
        recyclerView = findViewById(R.id.recyclerViewTeachersAttendance)
        progressBar = findViewById(R.id.progressBarTeacherAttendance)
        tvNoTeachers = findViewById(R.id.tvNoTeachersForAttendance)
        btnSave = findViewById(R.id.btnSaveAttendance)
    }

    private fun setupRecyclerView() {
        adapter = TeacherAttendanceAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        btnChangeDate.setOnClickListener { showDatePicker() }
        btnSave.setOnClickListener { saveAttendance() }
    }

    private fun showDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            selectedDate.set(Calendar.YEAR, year)
            selectedDate.set(Calendar.MONTH, month)
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateDisplay()
            loadTeachersAndAttendance()
        }

        DatePickerDialog(
            this,
            dateSetListener,
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
        tvAttendanceDate.text = "Date: ${sdf.format(selectedDate.time)}"
    }

    private fun getSelectedDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(selectedDate.time)
    }

    private fun loadTeachersAndAttendance() {
        if (currentOrganizationId == null) return
        setLoadingState(true)

        lifecycleScope.launch {
            try {
                // Fetch all teachers and attendance for the date in parallel
                val teachersDeferred = async { fetchAllTeachers() }
                val attendanceDeferred = async { fetchAttendanceForDate(getSelectedDateString()) }

                val allTeachers = teachersDeferred.await()
                val attendanceRecords = attendanceDeferred.await()

                if (allTeachers.isEmpty()) {
                    setEmptyState(true)
                    setLoadingState(false)
                    return@launch
                }

                val teacherAttendanceMap = attendanceRecords.associateBy { it.teacherId }

                val mergedList = allTeachers.map { teacher ->
                    // --- KEY CHANGE HERE ---
                    // Determine status from record.
                    // 1. If 'status' field exists (legacy), use it.
                    // 2. If 'classesTaken' > 0, consider "Present".
                    // 3. If 'classesMissed' > 0 and 'classesTaken' == 0, consider "Absent".
                    // 4. Default (no record) is "Present".

                    val record = teacherAttendanceMap[teacher.id]
                    val derivedStatus = if (record != null) {
                        when {
                            // Prioritize explicit status if available
                            record.status.isNotEmpty() -> record.status
                            // Fallback to counts
                            record.classesTaken > 0 -> "Present"
                            record.classesMissed > 0 -> "Absent"
                            else -> "Present"
                        }
                    } else {
                        "Present" // Default if no record found
                    }

                    TeacherAttendanceItem(
                        id = teacher.id,
                        name = teacher.name,
                        profileImageUrl = teacher.profileImageUrl,
                        attendanceStatus = derivedStatus
                    )
                }

                adapter.updateData(mergedList)
                setLoadingState(false)
                setEmptyState(false)

            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@TeacherAttendanceActivity, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun fetchAllTeachers(): List<TeacherSpinnerItem> {
        val querySnapshot = db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers")
            .orderBy("teacherName", Query.Direction.ASCENDING)
            .get().await()
        return querySnapshot.map { doc ->
            TeacherSpinnerItem(
                id = doc.id,
                name = doc.getString("teacherName") ?: "N/A",
                profileImageUrl = doc.getString("profileImageUrl")
            )
        }
    }

    private suspend fun fetchAttendanceForDate(date: String): List<TeacherAttendanceRecord> {
        val querySnapshot = db.collection("organizations").document(currentOrganizationId!!)
            .collection("teacherAttendance")
            .whereEqualTo("date", date)
            .get().await()
        return querySnapshot.toObjects(TeacherAttendanceRecord::class.java)
    }

    private fun saveAttendance() {
        if (currentOrganizationId == null) return

        val teachersToSave = adapter.getTeachersList()

        if (teachersToSave.isEmpty()) {
            Toast.makeText(this, "There are no teachers to mark attendance for.", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Saving...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "savingAttendance")

        lifecycleScope.launch {
            try {
                val batch: WriteBatch = db.batch()
                val dateStr = getSelectedDateString()
                val attendanceCollection = db.collection("organizations").document(currentOrganizationId!!)
                    .collection("teacherAttendance")

                val existingRecordsQuery = attendanceCollection.whereEqualTo("date", dateStr).get().await()
                val existingRecordsMap = existingRecordsQuery.documents.associateBy { it.getString("teacherId") }

                for (teacher in teachersToSave) {
                    // When saving from THIS screen, we only set the simple status.
                    // We don't overwrite class counts if they exist, ideally, but for this bulk tool
                    // we typically just set status.
                    // If you want to be safe, you could fetch existing counts and preserve them,
                    // but typically a bulk "Present" implies standard attendance.

                    val attendanceRecord = hashMapOf(
                        "teacherId" to teacher.id,
                        "teacherName" to teacher.name,
                        "date" to dateStr,
                        "status" to teacher.attendanceStatus,
                        "organizationId" to currentOrganizationId!!,
                        // If marking from here, we assume defaults for counts if they don't exist
                        // Or you can leave them out to not overwrite existing fields if using update
                        "classesTaken" to if(teacher.attendanceStatus == "Present") 1 else 0,
                        "classesMissed" to if(teacher.attendanceStatus == "Absent") 1 else 0
                    )

                    val existingDoc = existingRecordsMap[teacher.id]
                    if (existingDoc != null) {
                        batch.update(existingDoc.reference, attendanceRecord as Map<String, Any>)
                    } else {
                        batch.set(attendanceCollection.document(), attendanceRecord)
                    }
                }

                batch.commit().await()
                loadingDialog.dismiss()
                StatusDialogFragment.newInstance(true, "Attendance Saved Successfully!").show(supportFragmentManager, "saveSuccess")

            } catch (e: Exception) {
                loadingDialog.dismiss()
                StatusDialogFragment.newInstance(false, "Failed to save: ${e.message}").show(supportFragmentManager, "saveError")
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
        btnSave.isEnabled = !isLoading
        if (isLoading) {
            tvNoTeachers.visibility = View.GONE
        }
    }

    private fun setEmptyState(isEmpty: Boolean) {
        tvNoTeachers.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        btnSave.isEnabled = !isEmpty
    }
}