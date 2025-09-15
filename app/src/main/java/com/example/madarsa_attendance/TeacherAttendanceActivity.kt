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

                // --- FIX #4: DEFAULT TO "PRESENT" ---
                // This is the key change. If a teacher has no record in the database for this day,
                // we now explicitly default their status to "Present", matching the non-nullable data class.
                val mergedList = allTeachers.map { teacher ->
                    TeacherAttendanceItem(
                        id = teacher.id,
                        name = teacher.name,
                        profileImageUrl = teacher.profileImageUrl,
                        attendanceStatus = teacherAttendanceMap[teacher.id]?.status ?: "Present"
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

        // --- FIX #5: REMOVE THE FILTER ---
        // Get the entire list from the adapter. No filtering is needed anymore
        // because every teacher has a valid status ("Present" or "Absent").
        val teachersToSave = adapter.getTeachersList()

        // This validation now correctly checks only if there are no teachers to save at all.
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
                    val attendanceRecord = TeacherAttendanceRecord(
                        teacherId = teacher.id,
                        teacherName = teacher.name,
                        date = dateStr,
                        // No need for '!!' as status is no longer nullable
                        status = teacher.attendanceStatus,
                        organizationId = currentOrganizationId!!
                    )

                    val existingDoc = existingRecordsMap[teacher.id]
                    if (existingDoc != null) {
                        batch.set(existingDoc.reference, attendanceRecord)
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
    }
}