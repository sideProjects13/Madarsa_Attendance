package com.example.madarsa_attendance

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog // Import AlertDialog
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
import com.google.android.material.button.MaterialButton // Import MaterialButton
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
    private lateinit var btnLogout: MaterialButton // NEW: Logout button

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
        setupLogoutButton() // NEW: Setup logout button
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
        btnLogout = view.findViewById(R.id.btnLogout) // NEW: Initialize logout button
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

        data.toSortedMap().forEach { (teacherName, count) ->
            entries.add(BarEntry(index, count.toFloat()))
            labels.add(teacherName.split(" ").first())
            index++
        }

        val dataSet = BarDataSet(entries, "Students")
        dataSet.color = ContextCompat.getColor(requireContext(), R.color.mono_palette_white)
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 12f

        barChart.data = BarData(dataSet)

        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setDrawValueAboveBar(true)
        barChart.setFitBars(true)
        barChart.animateY(1000)

        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.DKGRAY
        xAxis.textSize = 10f
        xAxis.labelRotationAngle = -45f

        barChart.axisLeft.axisMinimum = 0f
        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisRight.isEnabled = false

        barChart.invalidate()
    }

    // NEW METHOD: Setup Logout Button
    private fun setupLogoutButton() {
        btnLogout.setOnClickListener {
            if (!isAdded) return@setOnClickListener // Ensure fragment is still attached

            AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout") { _, _ ->
                    FirebaseAuthManager.logout(requireContext())
                    // Redirect to LoginActivity after logout
                    val intent = Intent(activity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear back stack
                    startActivity(intent)
                    activity?.finish() // Finish the hosting activity (MainActivity)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}