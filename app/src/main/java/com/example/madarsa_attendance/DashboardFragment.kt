package com.example.madarsa_attendance

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore

class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by activityViewModels()
    private lateinit var db: FirebaseFirestore

    // UI Views
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalTeachers: TextView
    private lateinit var tvTotalInactiveStudents: TextView
    private lateinit var tvHighAbsenceStudents: TextView

    // Clickable Cards (Top Grid)
    private lateinit var totalStudentsCard: MaterialCardView
    private lateinit var totalTeachersCard: MaterialCardView
    private lateinit var inactiveStudentsCard: MaterialCardView
    private lateinit var highAbsenceCard: MaterialCardView

    // Student Attendance Cards
    private lateinit var presentCardSection: MaterialCardView
    private lateinit var absentCardSection: MaterialCardView
    private lateinit var notMarkedCardSection: MaterialCardView

    // Teacher Attendance Cards (NEW)
    private lateinit var teacherPresentCard: MaterialCardView
    private lateinit var teacherAbsentCard: MaterialCardView
    private lateinit var teacherNotMarkedCard: MaterialCardView

    // Attendance Summary Text Views
    private lateinit var tvPresentCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var tvNotMarkedCount: TextView

    // Teacher Attendance Summary Text Views (NEW)
    private lateinit var tvTeacherPresentCount: TextView
    private lateinit var tvTeacherAbsentCount: TextView
    private lateinit var tvTeacherNotMarkedCount: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        db = FirebaseFirestore.getInstance()
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        setupClickListeners()
        setupObservers()
    }

    private fun setupViews(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        shimmerLayout = view.findViewById(R.id.shimmer_view_container)
        mainContentLayout = view.findViewById(R.id.main_content_layout)

        // Grid Cards
        totalStudentsCard = view.findViewById(R.id.totalStudentsCard)
        tvTotalStudents = view.findViewById(R.id.tvTotalStudentsCount)
        totalTeachersCard = view.findViewById(R.id.totalTeachersCard)
        tvTotalTeachers = view.findViewById(R.id.tvTotalTeachersCount)
        inactiveStudentsCard = view.findViewById(R.id.inactiveStudentsCard)
        tvTotalInactiveStudents = view.findViewById(R.id.tvTotalInactiveStudentsCount)
        highAbsenceCard = view.findViewById(R.id.highAbsenceCard)
        tvHighAbsenceStudents = view.findViewById(R.id.tvHighAbsenceStudentsCount)

        // Student Attendance Section
        presentCardSection = view.findViewById(R.id.present_card_section)
        tvPresentCount = view.findViewById(R.id.tv_present_count)
        absentCardSection = view.findViewById(R.id.absent_card_section)
        tvAbsentCount = view.findViewById(R.id.tv_absent_count)
        notMarkedCardSection = view.findViewById(R.id.not_marked_card_section)
        tvNotMarkedCount = view.findViewById(R.id.tv_not_marked_count)

        // Teacher Attendance Section (NEW)
        teacherPresentCard = view.findViewById(R.id.teacher_present_card)
        tvTeacherPresentCount = view.findViewById(R.id.tv_teacher_present_count)
        teacherAbsentCard = view.findViewById(R.id.teacher_absent_card)
        tvTeacherAbsentCount = view.findViewById(R.id.tv_teacher_absent_count)
        teacherNotMarkedCard = view.findViewById(R.id.teacher_not_marked_card)
        tvTeacherNotMarkedCount = view.findViewById(R.id.tv_teacher_not_marked_count)
    }

    private fun setupClickListeners() {
        swipeRefreshLayout.setOnRefreshListener { viewModel.refreshData() }

        absentCardSection.setOnClickListener {
            startActivity(Intent(activity, AbsenteesActivity::class.java))
        }

        totalTeachersCard.setOnClickListener {
            startActivity(Intent(activity, ManageTeachersActivity::class.java))
        }

        // For student not marked
        notMarkedCardSection.setOnClickListener {
            viewModel.unmarkedTeachers.value?.let { teachers ->
                if (teachers.isNotEmpty()) {
                    showUnmarkedClassesDialog(teachers)
                } else {
                    Toast.makeText(context, "All classes have marked attendance today!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        inactiveStudentsCard.setOnClickListener {
            startActivity(Intent(activity, InactiveStudentsActivity::class.java))
        }

        highAbsenceCard.setOnClickListener {
            viewModel.highAbsenceStudents.value?.let { students ->
                if (students.isNotEmpty()) {
                    showHighAbsenceStudentsDialog(students)
                } else {
                    Toast.makeText(context, "No students with high absenteeism.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // You can add click listeners for the new teacher cards here if needed
        // e.g., teacherNotMarkedCard.setOnClickListener { ... navigate to TeacherAttendanceActivity ... }
        teacherNotMarkedCard.setOnClickListener {
            startActivity(Intent(activity, TeacherAttendanceActivity::class.java))
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading && !swipeRefreshLayout.isRefreshing) {
                shimmerLayout.startShimmer()
                shimmerLayout.visibility = View.VISIBLE
                mainContentLayout.visibility = View.INVISIBLE
            } else if (!isLoading) {
                swipeRefreshLayout.isRefreshing = false
                shimmerLayout.stopShimmer()
                shimmerLayout.visibility = View.GONE
                mainContentLayout.visibility = View.VISIBLE
            }
        }

        // Basic Stats
        viewModel.totalStudents.observe(viewLifecycleOwner) { tvTotalStudents.text = it.toString() }
        viewModel.totalTeachers.observe(viewLifecycleOwner) { tvTotalTeachers.text = it.toString() }
        viewModel.totalInactiveStudents.observe(viewLifecycleOwner) { tvTotalInactiveStudents.text = it.toString() }
        viewModel.highAbsenceStudents.observe(viewLifecycleOwner) { tvHighAbsenceStudents.text = it.size.toString() }

        // Student Attendance Stats
        viewModel.presentCount.observe(viewLifecycleOwner) { tvPresentCount.text = it.toString() }
        viewModel.absentCount.observe(viewLifecycleOwner) { tvAbsentCount.text = it.toString() }
        viewModel.notMarkedCount.observe(viewLifecycleOwner) { tvNotMarkedCount.text = it.toString() }

        // Teacher Attendance Stats (NEW)
        viewModel.teacherPresentCount.observe(viewLifecycleOwner) { tvTeacherPresentCount.text = it.toString() }
        viewModel.teacherAbsentCount.observe(viewLifecycleOwner) { tvTeacherAbsentCount.text = it.toString() }
        viewModel.teacherNotMarkedCount.observe(viewLifecycleOwner) { tvTeacherNotMarkedCount.text = it.toString() }
    }

    private fun showUnmarkedClassesDialog(teachers: List<Teacher>) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_not_marked, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rv_not_marked_classes)

        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        val adapter = NotMarkedAdapter(teachers) { selectedTeacher ->
            val intent = Intent(activity, TeacherOptionsActivity::class.java).apply {
                putExtra(TeacherOptionsActivity.EXTRA_TEACHER_ID, selectedTeacher.teacherId)
                putExtra(TeacherOptionsActivity.EXTRA_TEACHER_NAME, selectedTeacher.teacherName)
                putExtra(TeacherOptionsActivity.EXTRA_START_FRAGMENT, TeacherOptionsActivity.FRAGMENT_TAKE_ATTENDANCE)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        recyclerView.adapter = adapter
        dialog.show()
    }

    private fun showHighAbsenceStudentsDialog(students: List<DashboardStudentItem>) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_not_marked, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rv_not_marked_classes)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        dialogTitle?.text = "High Absenteeism Students"

        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        val adapter = DashboardStudentAdapter().apply {
            submitList(students)
        }
        recyclerView.adapter = adapter
        dialog.show()
    }
}