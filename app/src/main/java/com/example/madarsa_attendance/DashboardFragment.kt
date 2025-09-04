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
import java.util.Locale

class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by activityViewModels()

    // UI Views
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalTeachers: TextView

    // --- 1. COMMENTED OUT: Fee-related TextViews are no longer in the layout ---
    // private lateinit var tvFeesCollectedMonth: TextView
    // private lateinit var tvFeesCollectedYear: TextView
    // --- END OF COMMENTED OUT CODE ---

    // --- 2. NEW: Views for the new dashboard cards ---
    private lateinit var tvTotalInactiveStudents: TextView
    private lateinit var tvHighAbsenceStudents: TextView
    private lateinit var inactiveStudentsCard: MaterialCardView
    private lateinit var highAbsenceCard: MaterialCardView
    // --- END OF NEW CODE ---

    private lateinit var pieChart: PieChart
    private lateinit var tvPresentCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var tvNotMarkedCount: TextView
    private lateinit var absentCardSection: LinearLayout
    private lateinit var totalTeachersCard: MaterialCardView
    private lateinit var notMarkedCardSection: LinearLayout

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

        notMarkedCardSection.setOnClickListener {
            viewModel.unmarkedTeachers.value?.let { teachers ->
                if (teachers.isNotEmpty()) {
                    showUnmarkedClassesDialog(teachers)
                } else {
                    Toast.makeText(context, "All classes have been marked for today!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- 3. NEW: Click listeners for the new cards ---
        inactiveStudentsCard.setOnClickListener {
            // Navigate to the activity showing the list of all inactive students
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
        // --- END OF NEW CODE ---
    }

    private fun setupViews(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        shimmerLayout = view.findViewById(R.id.shimmer_view_container)
        mainContentLayout = view.findViewById(R.id.main_content_layout)
        tvTotalStudents = view.findViewById(R.id.tvTotalStudentsCount)
        tvTotalTeachers = view.findViewById(R.id.tvTotalTeachersCount)

        // --- 4. COMMENTED OUT: Finding fee-related views ---
        // tvFeesCollectedMonth = view.findViewById(R.id.tvFeesCollectedMonth)
        // tvFeesCollectedYear = view.findViewById(R.id.tvFeesCollectedYear)
        // --- END OF COMMENTED OUT CODE ---

        // --- 5. NEW: Finding the new views by their IDs from the XML ---
        tvTotalInactiveStudents = view.findViewById(R.id.tvTotalInactiveStudentsCount)
        tvHighAbsenceStudents = view.findViewById(R.id.tvHighAbsenceStudentsCount)
        inactiveStudentsCard = view.findViewById(R.id.inactiveStudentsCard)
        highAbsenceCard = view.findViewById(R.id.highAbsenceCard)
        // --- END OF NEW CODE ---

        pieChart = view.findViewById(R.id.pie_chart_class_distribution)
        tvPresentCount = view.findViewById(R.id.tv_present_count)
        tvAbsentCount = view.findViewById(R.id.tv_absent_count)
        tvNotMarkedCount = view.findViewById(R.id.tv_not_marked_count)
        absentCardSection = view.findViewById(R.id.absent_card_section)
        totalTeachersCard = view.findViewById(R.id.totalTeachersCard)
        notMarkedCardSection = view.findViewById(R.id.not_marked_card_section)
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

        viewModel.totalStudents.observe(viewLifecycleOwner) { count -> tvTotalStudents.text = count.toString() }
        viewModel.totalTeachers.observe(viewLifecycleOwner) { count -> tvTotalTeachers.text = count.toString() }

        // --- 6. COMMENTED OUT: Observers for fee-related LiveData ---
        /*
            val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            viewModel.feesThisMonth.observe(viewLifecycleOwner) { amount -> tvFeesCollectedMonth.text = currencyFormatter.format(amount) }
            viewModel.feesThisYear.observe(viewLifecycleOwner) { amount -> tvFeesCollectedYear.text = currencyFormatter.format(amount) }
            */
            // --- END OF COMMENTED OUT CODE ---

        // --- 7. NEW: Observers for the new LiveData ---
        viewModel.totalInactiveStudents.observe(viewLifecycleOwner) { count ->
            tvTotalInactiveStudents.text = count.toString()
        }
        viewModel.highAbsenceStudents.observe(viewLifecycleOwner) { students ->
            tvHighAbsenceStudents.text = students.size.toString()
        }
        // --- END OF NEW CODE ---

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

    // --- 8. NEW: Dialog to display the list of students with high absenteeism ---
    private fun showHighAbsenceStudentsDialog(students: List<DashboardStudentItem>) {
        // We can re-use the 'dialog_not_marked.xml' layout as it likely contains a RecyclerView.
        // We'll just change the dialog's title programmatically.
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_not_marked, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rv_not_marked_classes)
        // Assuming your dialog layout has a title TextView, e.g., with id 'dialog_title'
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        dialogTitle?.text = "High Absenteeism Students" // Set a more appropriate title

        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        // The DashboardStudentAdapter is perfect for displaying this list.
        val adapter = DashboardStudentAdapter().apply {
            submitList(students)
        }
        recyclerView.adapter = adapter
        dialog.show()
    }
    // --- END OF NEW CODE ---


    private fun setupPieChart(data: Map<String, Int>) {
        val entries = ArrayList<PieEntry>()
        data.forEach { (className, count) ->
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
            colors = ColorTemplate.MATERIAL_COLORS.toList() + ColorTemplate.VORDIPLOM_COLORS.toList()
            valueTextColor = Color.BLACK
            valueTextSize = 12f
            sliceSpace = 2f
        }

        val pieData = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(pieChart))
        }

        pieChart.apply {
            this.data = pieData
            description.isEnabled = false
            legend.isWordWrapEnabled = true
            isDrawHoleEnabled = true
            holeRadius = 45f
            transparentCircleRadius = 50f
            setUsePercentValues(true)
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(10f)
            animateY(1000)
            invalidate()
        }
    }
}