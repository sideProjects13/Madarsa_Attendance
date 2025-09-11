package com.example.madarsa_attendance

import android.content.Intent
import android.graphics.Color
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
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.Locale

class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by activityViewModels()

    // UI Views
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalTeachers: TextView
    private lateinit var tvTotalInactiveStudents: TextView
    private lateinit var tvHighAbsenceStudents: TextView

    // Clickable Cards
    private lateinit var totalStudentsCard: MaterialCardView
    private lateinit var totalTeachersCard: MaterialCardView
    private lateinit var inactiveStudentsCard: MaterialCardView
    private lateinit var highAbsenceCard: MaterialCardView

    // --- CORRECTED: These are the clickable attendance summary cards ---
    private lateinit var presentCardSection: MaterialCardView
    private lateinit var absentCardSection: MaterialCardView
    private lateinit var notMarkedCardSection: MaterialCardView
    // --- END OF CORRECTION ---

    // Chart
    private lateinit var barChart: BarChart

    // Attendance Summary Views
    private lateinit var tvPresentCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var tvNotMarkedCount: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        setupClickListeners() // Separated click listeners for clarity
        setupObservers()
    }

    private fun setupViews(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        shimmerLayout = view.findViewById(R.id.shimmer_view_container)
        mainContentLayout = view.findViewById(R.id.main_content_layout)

        // Main Cards
        totalStudentsCard = view.findViewById(R.id.totalStudentsCard) // Corrected initialization
        tvTotalStudents = view.findViewById(R.id.tvTotalStudentsCount)
        totalTeachersCard = view.findViewById(R.id.totalTeachersCard)
        tvTotalTeachers = view.findViewById(R.id.tvTotalTeachersCount)
        inactiveStudentsCard = view.findViewById(R.id.inactiveStudentsCard)
        tvTotalInactiveStudents = view.findViewById(R.id.tvTotalInactiveStudentsCount)
        highAbsenceCard = view.findViewById(R.id.highAbsenceCard)
        tvHighAbsenceStudents = view.findViewById(R.id.tvHighAbsenceStudentsCount)

        // Attendance Summary
        presentCardSection = view.findViewById(R.id.present_card_section) // Corrected initialization
        tvPresentCount = view.findViewById(R.id.tv_present_count)
        absentCardSection = view.findViewById(R.id.absent_card_section)
        tvAbsentCount = view.findViewById(R.id.tv_absent_count)
        notMarkedCardSection = view.findViewById(R.id.not_marked_card_section)
        tvNotMarkedCount = view.findViewById(R.id.tv_not_marked_count)

        // Chart
        barChart = view.findViewById(R.id.bar_chart_class_distribution)
    }

    private fun setupClickListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }

        // --- ALL CLICK LISTENERS ARE NOW CORRECTLY SET ---
        absentCardSection.setOnClickListener {
            startActivity(Intent(activity, AbsenteesActivity::class.java))
        }

        totalTeachersCard.setOnClickListener {
            startActivity(Intent(activity, ManageTeachersActivity::class.java))
        }

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
                    Toast.makeText(context, "No students with high absenteeism this month.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        // --- END OF CLICK LISTENERS ---
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

        // Observers for the card data (unchanged)
        viewModel.totalStudents.observe(viewLifecycleOwner) { count -> tvTotalStudents.text = count.toString() }
        viewModel.totalTeachers.observe(viewLifecycleOwner) { count -> tvTotalTeachers.text = count.toString() }
        viewModel.totalInactiveStudents.observe(viewLifecycleOwner) { count -> tvTotalInactiveStudents.text = count.toString() }
        viewModel.highAbsenceStudents.observe(viewLifecycleOwner) { students -> tvHighAbsenceStudents.text = students.size.toString() }

        // Observer for the chart data
        viewModel.classDistribution.observe(viewLifecycleOwner) { distribution ->
            if (distribution.isNotEmpty()) {
                setupBarChart(distribution)
            } else {
                barChart.clear()
                barChart.visibility = View.GONE
            }
        }

        // Observers for attendance summary (unchanged)
        viewModel.presentCount.observe(viewLifecycleOwner) { count -> tvPresentCount.text = count.toString() }
        viewModel.absentCount.observe(viewLifecycleOwner) { count -> tvAbsentCount.text = count.toString() }
        viewModel.notMarkedCount.observe(viewLifecycleOwner) { count -> tvNotMarkedCount.text = count.toString() }
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

    private fun setupBarChart(data: Map<String, Int>) {
        val filteredData = data.filter { it.value > 0 }
        if (filteredData.isEmpty()) {
            barChart.visibility = View.GONE
            return
        } else {
            barChart.visibility = View.VISIBLE
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        var index = 0f

        for ((className, count) in filteredData) {
            entries.add(BarEntry(index, count.toFloat()))
            labels.add(if (className.length > 8) className.substring(0, 8) + ".." else className)
            index++
        }

        val dataSet = BarDataSet(entries, "Students per Class").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextColor = Color.BLACK
            valueTextSize = 10f
        }

        val barData = BarData(dataSet)
        barChart.data = barData

        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setDrawValueAboveBar(true)
        barChart.setFitBars(true)

        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)

        val leftAxis = barChart.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY

        barChart.axisRight.isEnabled = false

        barChart.animateY(1000)
        barChart.invalidate()
    }
}