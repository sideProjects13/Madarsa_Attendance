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
import androidx.fragment.app.viewModels
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

class TeacherDashboardFragment : Fragment() {

    private val viewModel: TeacherDashboardViewModel by viewModels()

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalClasses: TextView
    private lateinit var tvTotalInactiveStudents: TextView
    private lateinit var tvHighAbsenceStudents: TextView
    private lateinit var notMarkedCardSection: MaterialCardView
    private lateinit var barChart: BarChart
    private lateinit var tvPresentCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var tvNotMarkedCount: TextView
    private lateinit var cardMyClasses: MaterialCardView
    private lateinit var cardAbsent: MaterialCardView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_dashboard, container, false)
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

        tvTotalStudents = view.findViewById(R.id.tvTotalStudentsCount)
        tvTotalClasses = view.findViewById(R.id.tvTotalClassesCount)
        tvTotalInactiveStudents = view.findViewById(R.id.tvTotalInactiveStudentsCount)
        tvHighAbsenceStudents = view.findViewById(R.id.tvHighAbsenceStudentsCount)

        cardMyClasses = view.findViewById(R.id.totalClassesCard)
        cardAbsent = view.findViewById(R.id.absent_card_section)

        tvPresentCount = view.findViewById(R.id.tv_present_count)
        tvAbsentCount = view.findViewById(R.id.tv_absent_count)
        tvNotMarkedCount = view.findViewById(R.id.tv_not_marked_count)
        notMarkedCardSection = view.findViewById(R.id.not_marked_card_section)

        barChart = view.findViewById(R.id.bar_chart_class_distribution)
    }

    private fun setupClickListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }

        notMarkedCardSection.setOnClickListener {
            viewModel.unmarkedClasses.value?.let { classes ->
                if (classes.isNotEmpty()) {
                    showUnmarkedClassesDialog(classes)
                } else {
                    Toast.makeText(context, "All classes have marked attendance today!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cardMyClasses.setOnClickListener {
            val intent = Intent(activity, TeacherDashboardActivity::class.java)
            startActivity(intent)
        }

        cardAbsent.setOnClickListener {
            val intent = Intent(activity, AbsenteesActivity::class.java).apply {
                putExtra(AbsenteesActivity.EXTRA_USER_ROLE, "teacher")
            }
            startActivity(intent)
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

        viewModel.totalStudents.observe(viewLifecycleOwner) { tvTotalStudents.text = it.toString() }
        viewModel.totalClasses.observe(viewLifecycleOwner) { tvTotalClasses.text = it.toString() }
        viewModel.totalInactiveStudents.observe(viewLifecycleOwner) { tvTotalInactiveStudents.text = it.toString() }
        viewModel.highAbsenceStudents.observe(viewLifecycleOwner) { tvHighAbsenceStudents.text = it.size.toString() }
        viewModel.presentCount.observe(viewLifecycleOwner) { tvPresentCount.text = it.toString() }
        viewModel.absentCount.observe(viewLifecycleOwner) { tvAbsentCount.text = it.toString() }
        viewModel.notMarkedCount.observe(viewLifecycleOwner) { tvNotMarkedCount.text = it.toString() }

        viewModel.classDistribution.observe(viewLifecycleOwner) { distribution ->
            if (distribution.isNotEmpty()) {
                setupBarChart(distribution)
            } else {
                barChart.clear()
                barChart.visibility = View.GONE
            }
        }
    }

    // --- MODIFIED: THIS FUNCTION NOW PASSES THE TEACHER ROLE ---
    private fun showUnmarkedClassesDialog(classes: List<Teacher>) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_not_marked, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rv_not_marked_classes)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        dialogTitle?.text = "Unmarked Classes"

        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        val adapter = NotMarkedAdapter(classes) { selectedClass ->
            val intent = Intent(activity, TeacherOptionsActivity::class.java).apply {
                putExtra(TeacherOptionsActivity.EXTRA_TEACHER_ID, selectedClass.teacherId)
                putExtra(TeacherOptionsActivity.EXTRA_TEACHER_NAME, selectedClass.teacherName)
                putExtra(TeacherOptionsActivity.EXTRA_START_FRAGMENT, TeacherOptionsActivity.FRAGMENT_TAKE_ATTENDANCE)
                // --- THIS IS THE FIX ---
                // We explicitly tell the next screen to open in "Teacher" mode.
                putExtra(TeacherOptionsActivity.EXTRA_USER_ROLE, TeacherOptionsActivity.ROLE_TEACHER)
                // --- END OF FIX ---
            }
            startActivity(intent)
            dialog.dismiss()
        }
        recyclerView.adapter = adapter
        dialog.show()
    }
    // --- END OF MODIFICATION ---

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