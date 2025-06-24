package com.example.madarsa_attendance

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.NumberFormat
import java.util.Locale

class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by viewModels()

    // UI Views
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalTeachers: TextView
    private lateinit var tvFeesCollectedMonth: TextView
    private lateinit var tvFeesCollectedYear: TextView
    private lateinit var rvRecentlyJoined: RecyclerView
    private lateinit var rvAbsentToday: RecyclerView
    private lateinit var tvNoAbsentees: TextView
    private lateinit var barChart: BarChart

    // Adapters
    private lateinit var recentStudentsAdapter: DashboardStudentAdapter
    private lateinit var absentStudentsAdapter: DashboardStudentAdapter

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupRecyclerViews()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        shimmerLayout.startShimmer()
        viewModel.loadDashboardData()
    }

    override fun onPause() {
        shimmerLayout.stopShimmer()
        super.onPause()
    }

    private fun setupViews(view: View) {
        shimmerLayout = view.findViewById(R.id.shimmer_view_container)
        mainContentLayout = view.findViewById(R.id.main_content_layout)
        tvTotalStudents = view.findViewById(R.id.tvTotalStudentsCount)
        tvTotalTeachers = view.findViewById(R.id.tvTotalTeachersCount)
        tvFeesCollectedMonth = view.findViewById(R.id.tvFeesCollectedMonth)
        tvFeesCollectedYear = view.findViewById(R.id.tvFeesCollectedYear)
        rvRecentlyJoined = view.findViewById(R.id.rv_recently_joined)
        rvAbsentToday = view.findViewById(R.id.rv_absent_today)
        tvNoAbsentees = view.findViewById(R.id.tv_no_absentees)
        barChart = view.findViewById(R.id.bar_chart_class_distribution)
    }

    private fun setupRecyclerViews() {
        recentStudentsAdapter = DashboardStudentAdapter()
        rvRecentlyJoined.adapter = recentStudentsAdapter

        absentStudentsAdapter = DashboardStudentAdapter()
        rvAbsentToday.adapter = absentStudentsAdapter
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                shimmerLayout.visibility = View.VISIBLE
                mainContentLayout.visibility = View.GONE
                shimmerLayout.startShimmer()
            } else {
                shimmerLayout.stopShimmer()
                shimmerLayout.visibility = View.GONE
                mainContentLayout.visibility = View.VISIBLE
            }
        }

        viewModel.totalStudents.observe(viewLifecycleOwner) { count ->
            tvTotalStudents.text = count.toString()
        }

        viewModel.totalTeachers.observe(viewLifecycleOwner) { count ->
            tvTotalTeachers.text = count.toString()
        }

        viewModel.feesThisMonth.observe(viewLifecycleOwner) { amount ->
            tvFeesCollectedMonth.text = currencyFormatter.format(amount)
        }

        viewModel.feesThisYear.observe(viewLifecycleOwner) { amount ->
            tvFeesCollectedYear.text = currencyFormatter.format(amount)
        }

        viewModel.recentlyJoinedStudents.observe(viewLifecycleOwner) { students ->
            recentStudentsAdapter.submitList(students)
        }

        viewModel.absentStudents.observe(viewLifecycleOwner) { students ->
            if (students.isEmpty()) {
                rvAbsentToday.visibility = View.GONE
                tvNoAbsentees.visibility = View.VISIBLE
            } else {
                rvAbsentToday.visibility = View.VISIBLE
                tvNoAbsentees.visibility = View.GONE
                absentStudentsAdapter.submitList(students)
            }
        }

        viewModel.classDistribution.observe(viewLifecycleOwner) { distribution ->
            if (distribution.isNotEmpty()) {
                setupBarChart(distribution)
            }
        }
    }

    private fun setupBarChart(data: Map<String, Int>) {
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        var index = 0f

        // Sort by teacher name for consistent order
        data.toSortedMap().forEach { (teacherName, count) ->
            entries.add(BarEntry(index, count.toFloat()))
            // Use a shorter name if possible for better display
            labels.add(teacherName.split(" ").first())
            index++
        }

        val dataSet = BarDataSet(entries, "Students")
        dataSet.color = ContextCompat.getColor(requireContext(), R.color.mono_palette_white) // Or your app's primary color
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 12f

        barChart.data = BarData(dataSet)

        // Chart styling
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setDrawValueAboveBar(true)
        barChart.setFitBars(true)
        barChart.animateY(1000)

        // X-Axis
        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.DKGRAY
        xAxis.textSize = 10f
        xAxis.labelRotationAngle = -45f // Rotate labels to prevent overlap

        // Y-Axis
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisRight.isEnabled = false

        barChart.invalidate() // Refresh chart
    }
}