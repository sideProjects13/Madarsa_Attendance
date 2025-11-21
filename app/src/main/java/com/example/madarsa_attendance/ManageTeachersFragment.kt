package com.example.madarsa_attendance

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.madarsa_attendance.TeacherWithStudentCount
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManageTeachersFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ManageTeachersAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoTeachers: TextView
    private lateinit var fabAddTeacher: ExtendedFloatingActionButton
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val db = FirebaseFirestore.getInstance()
    private var organizationId: String? = null
    private val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_manage_teachers_fragment, container, false)
        organizationId = FirebaseAuthManager.getOrganizationId(requireContext())
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        setupRecyclerView()
        loadTeachers()
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerViewManageTeachers)
        progressBar = view.findViewById(R.id.progressBarManageTeachers)
        tvNoTeachers = view.findViewById(R.id.tvNoTeachersManage)
        fabAddTeacher = view.findViewById(R.id.fabAddTeacher)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_teachers)

        fabAddTeacher.setOnClickListener {
            startActivity(Intent(activity, AddTeacherActivity::class.java))
        }

        swipeRefreshLayout.setOnRefreshListener {
            loadTeachers()
        }
    }

    private fun setupRecyclerView() {
        adapter = ManageTeachersAdapter(
            emptyList(),
            onTeacherCardClick = { teacher ->
                // Single click opens the class options (Attendance, Students, etc.)
                openTeacherOptions(teacher)
            },
            onTeacherCardLongClick = { teacher ->
                // --- FIX: Long press now opens simple Present/Absent dialog ---
                showQuickAttendanceDialog(teacher)
            },
            onEditTeacherClick = { teacher -> editTeacher(teacher) },
            onDeleteTeacherClick = { teacher -> confirmDeleteTeacher(teacher) }
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    // --- NEW FUNCTION: Simple Dialog for Present/Absent ---
    private fun showQuickAttendanceDialog(teacher: TeacherWithStudentCount) {
        val options = arrayOf("Mark Present", "Mark Absent")
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Mark Attendance: ${teacher.name}")
            .setItems(options) { _, which ->
                val status = if (which == 0) "Present" else "Absent"
                markTeacherAttendance(teacher, status)
            }
            .show()
    }

    // --- NEW FUNCTION: Save the attendance to Firestore ---
    private fun markTeacherAttendance(teacher: TeacherWithStudentCount, status: String) {
        if (organizationId == null) return

        val loadingDialog = StatusDialogFragment.newInstance(true, "Saving...").apply { isCancelable = false }
        loadingDialog.show(parentFragmentManager, "savingAttendance")

        lifecycleScope.launch {
            try {
                // Check if a record already exists for today
                val query = db.collection("organizations").document(organizationId!!)
                    .collection("teacherAttendance")
                    .whereEqualTo("teacherId", teacher.id)
                    .whereEqualTo("date", todayDateStr)
                    .limit(1)
                    .get().await()

                val attendanceRecord = hashMapOf(
                    "teacherId" to teacher.id,
                    "teacherName" to teacher.name,
                    "date" to todayDateStr,
                    "status" to status,
                    "organizationId" to organizationId!!
                )

                if (query.isEmpty) {
                    // Create new record
                    db.collection("organizations").document(organizationId!!)
                        .collection("teacherAttendance").add(attendanceRecord).await()
                } else {
                    // Update existing record
                    val docId = query.documents[0].id
                    db.collection("organizations").document(organizationId!!)
                        .collection("teacherAttendance").document(docId).set(attendanceRecord).await()
                }

                loadingDialog.dismiss()
                StatusDialogFragment.newInstance(true, "${teacher.name} marked $status").show(parentFragmentManager, "successDialog")

            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openTeacherOptions(teacher: TeacherWithStudentCount) {
        val intent = Intent(activity, TeacherOptionsActivity::class.java).apply {
            putExtra(TeacherOptionsActivity.EXTRA_TEACHER_ID, teacher.id)
            putExtra(TeacherOptionsActivity.EXTRA_TEACHER_NAME, teacher.name)
            putExtra(TeacherOptionsActivity.EXTRA_TEACHER_IMAGE_URL, teacher.profileImageUrl)
            putExtra(TeacherOptionsActivity.EXTRA_USER_ROLE, "admin")
        }
        startActivity(intent)
    }

    fun loadTeachers() {
        if (organizationId == null) {
            Toast.makeText(context, "Organization ID not found.", Toast.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE
        tvNoTeachers.visibility = View.GONE
        recyclerView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val teachersSnapshot = db.collection("organizations").document(organizationId!!)
                    .collection("teachers").get().await()
                val teachersList = teachersSnapshot.toObjects(Teacher::class.java)

                if (teachersList.isEmpty()) {
                    tvNoTeachers.visibility = View.VISIBLE
                    adapter.updateData(emptyList())
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    return@launch
                }

                val studentsSnapshot = db.collection("organizations").document(organizationId!!)
                    .collection("students").whereEqualTo("isActive", true).get().await()
                val studentsList = studentsSnapshot.toObjects(Student::class.java)

                val studentsByTeacher = studentsList.groupBy { it.teacherId }

                val teachersWithCounts = teachersList.map { teacher ->
                    val studentCount = studentsByTeacher[teacher.teacherId]?.size ?: 0
                    TeacherWithStudentCount(
                        id = teacher.teacherId,
                        name = teacher.teacherName,
                        profileImageUrl = teacher.profileImageUrl,
                        studentCount = studentCount
                    )
                }

                adapter.updateData(teachersWithCounts)
                recyclerView.visibility = View.VISIBLE

            } catch (e: Exception) {
                if(isAdded) {
                    tvNoTeachers.text = "Failed to load teachers."
                    tvNoTeachers.visibility = View.VISIBLE
                }
            } finally {
                if(isAdded) {
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun editTeacher(teacher: TeacherWithStudentCount) {
        val intent = Intent(activity, EditTeacherActivity::class.java).apply {
            putExtra("TEACHER_ID", teacher.id)
        }
        startActivity(intent)
    }

    private fun confirmDeleteTeacher(teacher: TeacherWithStudentCount) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Teacher")
            .setMessage("Are you sure you want to delete ${teacher.name}? This will also delete all associated students and data!")
            .setPositiveButton("Delete") { _, _ -> deleteTeacher(teacher) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTeacher(teacher: TeacherWithStudentCount) {
        if (organizationId == null) return
        db.collection("organizations").document(organizationId!!)
            .collection("teachers").document(teacher.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Teacher deleted.", Toast.LENGTH_SHORT).show()
                loadTeachers()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error deleting teacher: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}