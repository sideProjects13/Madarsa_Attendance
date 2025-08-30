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
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by activityViewModels()

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var tvGreeting: TextView
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalTeachers: TextView
    private lateinit var tvFeesCollectedMonth: TextView
    private lateinit var tvFeesCollectedYear: TextView
    private lateinit var pieChart: PieChart
    private lateinit var tvPresentCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var tvNotMarkedCount: TextView
    private lateinit var absentCardSection: LinearLayout
    private lateinit var totalTeachersCard: MaterialCardView

    // --- NEW: View for the "Not Marked" section ---
    private lateinit var notMarkedCardSection: LinearLayout

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        setupObservers()

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }

        absentCardSection.setOnClickListener {
            startActivity(Intent(activity, AbsenteesActivity::class.java))
        }

        totalTeachersCard.setOnClickListener {
            startActivity(Intent(activity, ManageTeachersActivity::class.java))
        }

        // --- NEW: Set OnClickListener for the Not Marked Card ---
        notMarkedCardSection.setOnClickListener {
            viewModel.unmarkedTeachers.value?.let { teachers ->
                if (teachers.isNotEmpty()) {
                    showUnmarkedClassesDialog(teachers)
                } else {
                    Toast.makeText(context, "All classes have been marked for today!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupViews(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        shimmerLayout = view.findViewById(R.id.shimmer_view_container)
        mainContentLayout = view.findViewById(R.id.main_content_layout)
//        tvGreeting = view.findViewById(R.id.tv_greeting)
        tvTotalStudents = view.findViewById(R.id.tvTotalStudentsCount)
        tvTotalTeachers = view.findViewById(R.id.tvTotalTeachersCount)
        tvFeesCollectedMonth = view.findViewById(R.id.tvFeesCollectedMonth)
        tvFeesCollectedYear = view.findViewById(R.id.tvFeesCollectedYear)
        pieChart = view.findViewById(R.id.pie_chart_class_distribution)
        tvPresentCount = view.findViewById(R.id.tv_present_count)
        tvAbsentCount = view.findViewById(R.id.tv_absent_count)
        tvNotMarkedCount = view.findViewById(R.id.tv_not_marked_count)
        absentCardSection = view.findViewById(R.id.absent_card_section)
        totalTeachersCard = view.findViewById(R.id.totalTeachersCard)

        // --- NEW: Initialize the Not Marked Card ---
        notMarkedCardSection = view.findViewById(R.id.not_marked_card_section)

//        setGreeting()
    }

//    private fun setGreeting() {
//        val calendar = Calendar.getInstance()
//        val hour = calendar.get(Calendar.HOUR_OF_DAY)
//        tvGreeting.text = when (hour) {
//            in 0..11 -> "Good Morning!"
//            in 12..16 -> "Good Afternoon!"
//            else -> "Good Evening!"
//        }
//    }

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

        viewModel.totalStudents.observe(viewLifecycleOwner) { count -> tvTotalStudents.text = count.toString() }
        viewModel.totalTeachers.observe(viewLifecycleOwner) { count -> tvTotalTeachers.text = count.toString() }
        viewModel.feesThisMonth.observe(viewLifecycleOwner) { amount -> tvFeesCollectedMonth.text = currencyFormatter.format(amount) }
        viewModel.feesThisYear.observe(viewLifecycleOwner) { amount -> tvFeesCollectedYear.text = currencyFormatter.format(amount) }

        viewModel.classDistribution.observe(viewLifecycleOwner) { distribution ->
            if (distribution.isNotEmpty()) {
                setupPieChart(distribution)
            } else {
                pieChart.clear()
            }
        }

        viewModel.presentCount.observe(viewLifecycleOwner) { count -> tvPresentCount.text = count.toString() }
        viewModel.absentCount.observe(viewLifecycleOwner) { count -> tvAbsentCount.text = count.toString() }
        viewModel.notMarkedCount.observe(viewLifecycleOwner) { count -> tvNotMarkedCount.text = count.toString() }
    }

    // --- NEW: Function to show the dialog ---
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

    // In DashboardFragment.kt

    private fun setupPieChart(data: Map<String, Int>) {
        val entries = ArrayList<PieEntry>()
        data.forEach { (className, count) ->
            // We only add entries that have students to avoid clutter
            if (count > 0) {
                entries.add(PieEntry(count.toFloat(), className))
            }
        }

        if (entries.isEmpty()) {
            pieChart.visibility = View.GONE
            return
        } else {
            pieChart.visibility = View.VISIBLE
        }

        val dataSet = PieDataSet(entries, "").apply {
            // Use a vibrant and professional color palette
            colors = ColorTemplate.MATERIAL_COLORS.toList() + ColorTemplate.VORDIPLOM_COLORS.toList()
            valueTextColor = Color.BLACK
            valueTextSize = 12f
            sliceSpace = 2f
        }

        val pieData = PieData(dataSet).apply {
            // Format the value on the chart to show a percentage
            setValueFormatter(PercentFormatter(pieChart))
        }

        pieChart.apply {
            // Set the data and invalidate to redraw the chart
            this.data = pieData

            // General appearance settings
            description.isEnabled = false
            legend.isWordWrapEnabled = true
            isDrawHoleEnabled = true
            holeRadius = 45f
            transparentCircleRadius = 50f

            // Make the chart use percentage values
            setUsePercentValues(true)

            // Entry label (the text on the slices) styling
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(10f)

            // Animation
            animateY(1000)

            // Refresh the chart
            invalidate()
        }
    }
}