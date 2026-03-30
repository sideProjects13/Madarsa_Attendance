package com.example.madarsa_attendance

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout // NEW IMPORT
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PaymentSummaryFragment : Fragment() {

    companion object {
        private const val TAG = "PaymentSummaryFragment"
        private const val ARG_TEACHER_ID_PSF = "teacher_id_psf"
        private const val ARG_TEACHER_NAME_PSF = "teacher_name_psf"

        @JvmStatic
        fun newInstance(teacherId: String, teacherName: String): PaymentSummaryFragment {
            return PaymentSummaryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEACHER_ID_PSF, teacherId)
                    putString(ARG_TEACHER_NAME_PSF, teacherName)
                }
            }
        }
    }

    private var _swipeRefreshLayout: SwipeRefreshLayout? = null // NEW: SwipeRefreshLayout
    private val swipeRefreshLayout get() = _swipeRefreshLayout!!

    private var _spinnerMonth: Spinner? = null
    private val spinnerMonth get() = _spinnerMonth!!
    private var _spinnerYear: Spinner? = null
    private val spinnerYear get() = _spinnerYear!!
    private var _recyclerViewPayments: RecyclerView? = null
    private val recyclerViewPayments get() = _recyclerViewPayments!!
    private var _paymentSummaryAdapter: PaymentSummaryAdapter? = null
    private val paymentSummaryAdapter get() = _paymentSummaryAdapter!!
    private var _progressBar: ProgressBar? = null
    private val progressBar get() = _progressBar!!
    private var _tvNoData: TextView? = null
    private val tvNoData get() = _tvNoData!!
    private var _searchViewPaymentSummary: SearchView? = null
    private val searchViewPaymentSummary get() = _searchViewPaymentSummary!!

    private var _fabGenerateReport: ExtendedFloatingActionButton? = null
    private val fabGenerateReport get() = _fabGenerateReport!!


    private lateinit var db: FirebaseFirestore
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentOrganizationId: String? = null


    private val studentDetailsMap = mutableMapOf<String, StudentDetailsItem>()
    private val paymentSummaryDisplayList = mutableListOf<StudentPaymentSummaryItem>()

    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH)

    private var isCurrentPeriodDataLoaded = false
    private var spinnersFullyInitializedPayment = false

    private lateinit var teacherDataViewModel: TeacherDataViewModel
    private lateinit var studentPaymentHistoryLauncher: ActivityResultLauncher<Intent>

    private var reportTypeSelected: String = "Monthly" // Default
    private var reportYearSelected: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var reportMonthSelected: Int = Calendar.getInstance().get(Calendar.MONTH)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showReportOptionsDialog()
            } else {
                Toast.makeText(context, "Storage permission is required to generate PDF reports.", Toast.LENGTH_LONG).show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentTeacherId = it.getString(ARG_TEACHER_ID_PSF)
            currentTeacherName = it.getString(ARG_TEACHER_NAME_PSF)
        }
        db = FirebaseFirestore.getInstance()
        teacherDataViewModel = ViewModelProvider(requireActivity()).get(TeacherDataViewModel::class.java)
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(requireContext())
        studentPaymentHistoryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Returned from StudentPaymentHistoryActivity with RESULT_OK. Refreshing payment summary.")
                isCurrentPeriodDataLoaded = false
                if (checkPreConditionsAndLoad(calledFrom = "StudentPaymentHistoryLauncher")) { // Pass caller info
                    Log.d(TAG, "Launcher Result: Triggering data load.")
                }
            } else {
                Log.d(TAG, "Returned from StudentPaymentHistoryActivity with result code: ${result.resultCode}")
            }
        }
        Log.d(TAG, "onCreate: Initial selectedMonth=$selectedMonth, selectedYear=$selectedYear, Org ID: $currentOrganizationId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_payment_summary, container, false)
        _swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_payment_summary) // NEW: Initialize SwipeRefreshLayout
        _spinnerMonth = view.findViewById(R.id.spinnerMonthPaymentSummaryFrag)
        _spinnerYear = view.findViewById(R.id.spinnerYearPaymentSummaryFrag)
        _recyclerViewPayments = view.findViewById(R.id.recyclerViewPaymentSummaryFrag)
        _progressBar = view.findViewById(R.id.progressBarPaymentSummaryFrag)
        _tvNoData = view.findViewById(R.id.tvNoPaymentDataFrag)
        _searchViewPaymentSummary = view.findViewById(R.id.searchViewPaymentSummary)
        _fabGenerateReport = view.findViewById(R.id.fabGenerateReport)
        Log.d(TAG, "onCreateView completed")
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: TeacherID: $currentTeacherId, Org ID: $currentOrganizationId")
        if (currentTeacherId == null || currentOrganizationId == null) {
            Toast.makeText(context, "Teacher or Organization info missing.", Toast.LENGTH_LONG).show(); return
        }
        setupRecyclerView()
        setupSpinners(view)
        setupSearchView()

        fabGenerateReport.setOnClickListener {
            checkAndRequestStoragePermission()
        }

        // NEW: Setup SwipeRefreshListener
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Swipe to refresh triggered for payment summary.")
            isCurrentPeriodDataLoaded = false // Force reload
            _searchViewPaymentSummary?.setQuery("", false) // Clear search on refresh
            if (checkPreConditionsAndLoad(calledFrom = "SwipeRefresh")) {
                Log.d(TAG, "SwipeRefresh: Triggering data load.")
            } else {
                // If pre-conditions aren't met, stop refreshing immediately
                swipeRefreshLayout.isRefreshing = false
            }
        }


        teacherDataViewModel.studentsDataMightHaveChanged.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Log.d(TAG, "Observer: studentsDataMightHaveChanged event received.")
                isCurrentPeriodDataLoaded = false
                _searchViewPaymentSummary?.setQuery("", false)
                if (checkPreConditionsAndLoad(calledFrom = "StudentsDataChangedObserver")) { // Pass caller info
                    Log.d(TAG, "Observer: Triggering data load due to student list change.")
                } else {
                    Log.d(TAG, "Observer: student list change, but pre-conditions for load not met yet.")
                }
            }
        }
        if (!isCurrentPeriodDataLoaded) {
            Log.d(TAG, "onViewCreated: Data not yet loaded, will be handled by lifecycle or spinner init.")
        }
        Log.d(TAG, "onViewCreated completed")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: isCurrentPeriodDataLoaded=$isCurrentPeriodDataLoaded, spinnersFullyInitialized=$spinnersFullyInitializedPayment, isVisible=$isFragmentVisibleToUser")
        if (checkPreConditionsAndLoad(calledFrom = "onResume")) {
            Log.d(TAG, "onResume: Triggering data load.")
        }
    }

    private fun checkPreConditionsAndLoad(calledFrom: String = "unknown"): Boolean {
        Log.d(TAG, "checkPreConditionsAndLoad (from $calledFrom): currentTeacherId=$currentTeacherId, currentOrganizationId=$currentOrganizationId, !isCurrentPeriodDataLoaded=${!isCurrentPeriodDataLoaded}, spinnersFullyInitialized=$spinnersFullyInitializedPayment, isVisible=$isFragmentVisibleToUser")
        if (currentTeacherId != null && currentOrganizationId != null && !isCurrentPeriodDataLoaded && spinnersFullyInitializedPayment && isFragmentVisibleToUser) {
            loadPaymentSummaryData()
            return true
        }
        return false
    }

    private val isFragmentVisibleToUser: Boolean
        get() {
            return isVisible && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _spinnerMonth = null; _spinnerYear = null; _recyclerViewPayments = null
        _paymentSummaryAdapter = null; _progressBar = null; _tvNoData = null
        _searchViewPaymentSummary?.setOnQueryTextListener(null)
        _searchViewPaymentSummary = null
        _fabGenerateReport = null
        _swipeRefreshLayout?.setOnRefreshListener(null) // NEW: Clear listener
        _swipeRefreshLayout = null // NEW: Null out SwipeRefreshLayout
        Log.d(TAG, "onDestroyView")
    }

    private fun setupSearchView() {
        searchViewPaymentSummary.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                paymentSummaryAdapter.filter(query)
                searchViewPaymentSummary.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                paymentSummaryAdapter.filter(newText)
                return true
            }
        })
        searchViewPaymentSummary.setOnCloseListener {
            searchViewPaymentSummary.setQuery("", false)
            true
        }
    }

    private fun setupSpinners(fragmentView: View) {
        if (!isAdded || context == null || currentOrganizationId == null) {
            Log.w(TAG, "setupSpinners: Fragment not added, context null, or org ID null"); return
        }
        spinnersFullyInitializedPayment = false
        val staticSpinnerTextColor = ContextCompat.getColor(requireContext(), R.color.mono_palette_black)

        val months = SimpleDateFormat("MMMM", Locale.getDefault()).let { sdf ->
            (0..11).map {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1) // not reliable enough
                cal.set(Calendar.MONTH, it)
                sdf.format(cal.time)
            }
        }
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonth.adapter = monthAdapter
        spinnerMonth.setSelection(selectedMonth, false)

        val currentYearVal = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYearVal - 5..currentYearVal + 1).map { it.toString() }.reversed().toList()
        val yearAdapter = ColorableSpinnerAdapter(requireContext(), years, staticSpinnerTextColor)
        spinnerYear.adapter = yearAdapter
        spinnerYear.setSelection(years.indexOf(selectedYear.toString()), false)

        val itemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!spinnersFullyInitializedPayment) {
                    Log.d(TAG, "Spinner item selected but spinners not fully initialized yet. Ignoring.")
                    return
                }
                val newMonth = spinnerMonth.selectedItemPosition
                val newYear = spinnerYear.selectedItem.toString().toInt()
                if (newMonth != selectedMonth || newYear != selectedYear) {
                    Log.d(TAG, "Spinner selection changed. New month: $newMonth ($selectedMonth), New year: $newYear ($selectedYear)")
                    selectedMonth = newMonth
                    selectedYear = newYear
                    isCurrentPeriodDataLoaded = false
                    _searchViewPaymentSummary?.setQuery("", false)
                    if (checkPreConditionsAndLoad(calledFrom = "SpinnerSelect")) {
                        Log.d(TAG, "SpinnerSelect: Triggering data load.")
                    }
                } else {
                    Log.d(TAG, "Spinner item selected but month/year unchanged.")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerMonth.onItemSelectedListener = itemSelectedListener
        spinnerYear.onItemSelectedListener = itemSelectedListener

        fragmentView.post {
            if (isAdded) {
                Log.d(TAG, "Spinners fully initialized via post. CurrentMonth: $selectedMonth, CurrentYear: $selectedYear")
                spinnersFullyInitializedPayment = true
                if (checkPreConditionsAndLoad(calledFrom = "SpinnerPost")) {
                    Log.d(TAG, "SpinnerPost: Triggering data load.")
                }
            }
        }
    }

    private fun setupRecyclerView() {
        if (!isAdded || context == null || _recyclerViewPayments == null) { Log.w(TAG, "setupRecyclerView: pre-conditions not met"); return }
        _paymentSummaryAdapter = PaymentSummaryAdapter(ArrayList()) { studentSummaryItem ->
            if (!isAdded) return@PaymentSummaryAdapter
            val intent = Intent(activity, StudentPaymentHistoryActivity::class.java).apply {
                putExtra("STUDENT_ID", studentSummaryItem.studentId)
                putExtra("STUDENT_NAME", studentSummaryItem.studentName)
                putExtra("TEACHER_ID", currentTeacherId)
                putExtra("TEACHER_NAME", currentTeacherName)
            }
            studentPaymentHistoryLauncher.launch(intent)
        }
        recyclerViewPayments.layoutManager = LinearLayoutManager(context)
        recyclerViewPayments.adapter = paymentSummaryAdapter
        Log.d(TAG, "setupRecyclerView completed.")
    }

    private fun loadPaymentSummaryData() {
        Log.i(TAG, "loadPaymentSummaryData: CALLED for $selectedMonth/$selectedYear. isCurrentPeriodDataLoaded was false.")
        if (currentTeacherId == null || currentOrganizationId == null || !isAdded || _progressBar == null || _tvNoData == null || _recyclerViewPayments == null || _paymentSummaryAdapter == null) {
            Log.e(TAG, "loadPaymentSummaryData: CRITICAL PRE-CONDITIONS NOT MET. Aborting. " +
                    "teacherId=$currentTeacherId, orgId=$currentOrganizationId, isAdded=$isAdded, adapterNull=${_paymentSummaryAdapter==null}")
            if(isAdded) {
                _progressBar?.visibility = View.GONE
                _tvNoData?.text = "Error: Components not ready or organization info missing."
                _tvNoData?.visibility = View.VISIBLE
                _recyclerViewPayments?.visibility = View.GONE
            }
            isCurrentPeriodDataLoaded = false
            swipeRefreshLayout.isRefreshing = false // NEW: Stop refreshing on critical pre-condition failure
            return
        }

        // Only show progress bar if not initiated by swipe refresh (which shows its own indicator)
        if (!swipeRefreshLayout.isRefreshing) { // NEW: Conditional visibility
            progressBar.visibility = View.VISIBLE
        }
        tvNoData.visibility = View.GONE
        recyclerViewPayments.visibility = View.GONE

        val calendar = Calendar.getInstance(); calendar.set(selectedYear, selectedMonth, 1)
        val targetMonthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        Log.d(TAG, "loadPaymentSummaryData: Target month/year string: $targetMonthYearStr for teacher: $currentTeacherId, Org ID: $currentOrganizationId")

        studentDetailsMap.clear()
        val studentMonthlyPaymentDetails = mutableMapOf<String, Pair<Double, Int>>()

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").whereEqualTo("teacherId", currentTeacherId).orderBy("studentName").get()
            .addOnSuccessListener { studentsSnapshot ->
                if (!isAdded) {
                    Log.w(TAG, "Students fetched, but fragment not added.");
                    isCurrentPeriodDataLoaded = false;
                    swipeRefreshLayout.isRefreshing = false // NEW: Stop refreshing
                    return@addOnSuccessListener
                }
                Log.d(TAG, "Fetched ${studentsSnapshot.size()} student documents for teacher $currentTeacherId in Org ID: $currentOrganizationId.")

                if (studentsSnapshot.isEmpty) {
                    progressBar.visibility = View.GONE
                    tvNoData.text = "No students in this class."
                    tvNoData.visibility = View.VISIBLE
                    recyclerViewPayments.visibility = View.GONE
                    paymentSummaryAdapter.updateData(emptyList())
                    studentDetailsMap.clear()
                    isCurrentPeriodDataLoaded = true
                    swipeRefreshLayout.isRefreshing = false // NEW: Stop refreshing
                    return@addOnSuccessListener
                }

                studentsSnapshot.forEach { doc ->
                    val studentId = doc.id
                    studentDetailsMap[studentId] = StudentDetailsItem(
                        id = studentId,
                        studentName = doc.getString("studentName") ?: "N/A",
                        parentName = doc.getString("parentName"),
                        parentMobileNumber = doc.getString("parentMobileNumber"),
                        profileImageUrl = doc.getString("profileImageUrl")
                    )
                    studentMonthlyPaymentDetails[studentId] = Pair(0.0, 0)
                }
                Log.d(TAG, "Populated studentDetailsMap with ${studentDetailsMap.size} students.")

                db.collection("organizations").document(currentOrganizationId!!)
                    .collection("feePayments")
                    .whereEqualTo("teacherId", currentTeacherId)
                    .whereEqualTo("paymentMonth", targetMonthYearStr)
                    .get()
                    .addOnSuccessListener { paymentsSnap ->
                        if (!isAdded) {
                            Log.w(TAG, "Payments fetched, but fragment not added.");
                            isCurrentPeriodDataLoaded = false;
                            swipeRefreshLayout.isRefreshing = false // NEW: Stop refreshing
                            return@addOnSuccessListener
                        }

                        if (!paymentsSnap.isEmpty) {
                            Log.d(TAG, "Fetched ${paymentsSnap.size()} payments for $targetMonthYearStr in Org ID: $currentOrganizationId.")
                            paymentsSnap.forEach { pDoc ->
                                val sId = pDoc.getString("studentId")
                                val amt = pDoc.getDouble("paymentAmount") ?: 0.0
                                if (sId != null && studentDetailsMap.containsKey(sId)) {
                                    val currentDetails = studentMonthlyPaymentDetails[sId]!!
                                    studentMonthlyPaymentDetails[sId] = Pair(currentDetails.first + amt, currentDetails.second + 1)
                                } else {
                                    Log.w(TAG, "Payment found for studentId '$sId' not in current class map, or sId is null. PaymentMonth: $targetMonthYearStr, Org ID: $currentOrganizationId")
                                }
                            }
                        } else {
                            Log.d(TAG, "No payments found for $targetMonthYearStr in Org ID: $currentOrganizationId.")
                        }
                        processAndDisplaySummary(studentMonthlyPaymentDetails)
                        isCurrentPeriodDataLoaded = true
                        swipeRefreshLayout.isRefreshing = false // NEW: Stop refreshing on success
                    }.addOnFailureListener { e ->
                        if (!isAdded)
                            Log.e(TAG, "Error loading payments for $targetMonthYearStr in Org ID: $currentOrganizationId:", e)
                        progressBar.visibility = View.GONE
                        tvNoData.text = "Error loading payments: ${e.message}"
                        tvNoData.visibility = View.VISIBLE
                        recyclerViewPayments.visibility = View.GONE
                        isCurrentPeriodDataLoaded = false
                        swipeRefreshLayout.isRefreshing = false // NEW: Stop refreshing on failure
                    }
            }.addOnFailureListener { e ->
                if (!isAdded)
                    Log.e(TAG, "Error loading students for teacher $currentTeacherId in Org ID: $currentOrganizationId:", e)
                progressBar.visibility = View.GONE
                tvNoData.text = "Error loading students: ${e.message}"
                tvNoData.visibility = View.VISIBLE
                recyclerViewPayments.visibility = View.GONE
                paymentSummaryAdapter.updateData(emptyList())
                isCurrentPeriodDataLoaded = false
                swipeRefreshLayout.isRefreshing = false // NEW: Stop refreshing on failure
            }
    }

    private fun processAndDisplaySummary(
        studentMonthlyPaymentDetails: Map<String, Pair<Double, Int>>
    ) {
        if (!isAdded || _paymentSummaryAdapter == null) {
            Log.w(TAG, "processAndDisplaySummary: Fragment not added or adapter is null.")
            _progressBar?.visibility = View.GONE
            swipeRefreshLayout.isRefreshing = false // NEW: Stop refreshing
            return
        }
        Log.d(TAG, "processAndDisplaySummary: Processing ${studentDetailsMap.size} students from map.")
        paymentSummaryDisplayList.clear()

        studentDetailsMap.values.sortedBy { it.studentName }.forEach { studentDetail ->
            val paymentInfo = studentMonthlyPaymentDetails[studentDetail.id] ?: Pair(0.0, 0)
            paymentSummaryDisplayList.add(
                StudentPaymentSummaryItem(
                    studentId = studentDetail.id, studentName = studentDetail.studentName,
                    totalPaidThisMonth = paymentInfo.first, paymentCountThisMonth = paymentInfo.second,
                    profileImageUrl = studentDetail.profileImageUrl
                )
            )
        }
        Log.d(TAG, "Constructed paymentSummaryDisplayList with ${paymentSummaryDisplayList.size} items.")
        progressBar.visibility = View.GONE // Ensure progress bar is hidden

        if (studentDetailsMap.isEmpty()) {
            tvNoData.text = "No students in this class."
            tvNoData.visibility = View.VISIBLE
            recyclerViewPayments.visibility = View.GONE
        } else if (paymentSummaryDisplayList.isEmpty() && studentDetailsMap.isNotEmpty()){
            tvNoData.text = "No payments found for the selected period."
            tvNoData.visibility = View.VISIBLE
            recyclerViewPayments.visibility = View.GONE
        }
        else {
            tvNoData.visibility = View.GONE
            recyclerViewPayments.visibility = View.VISIBLE
        }

        paymentSummaryAdapter.updateData(paymentSummaryDisplayList)

        val currentQuery = searchViewPaymentSummary.query?.toString()
        if (!currentQuery.isNullOrEmpty()) {
            Log.d(TAG, "processAndDisplaySummary: Re-applying filter for query: '$currentQuery'")
            paymentSummaryAdapter.filter(currentQuery)
        } else {
            searchViewPaymentSummary.setQuery("", false)
        }
        Log.d(TAG, "processAndDisplaySummary: RecyclerView visible: ${recyclerViewPayments.visibility == View.VISIBLE}, Item count: ${paymentSummaryAdapter.itemCount}")
    }


    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            showReportOptionsDialog()
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    showReportOptionsDialog()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Permission Needed")
                        .setMessage("This app needs storage access to save PDF reports to your Documents folder.")
                        .setPositiveButton("OK") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun showReportOptionsDialog() {
        if (!isAdded || context == null || currentOrganizationId == null) {
            Log.w(TAG, "showReportOptionsDialog: Fragment not added, context null, or organization ID missing.")
            Toast.makeText(context, "Cannot generate report: Organization information missing.", Toast.LENGTH_SHORT).show()
            return
        }
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_report_options, null)
        val radioGroupReportType: RadioGroup = dialogView.findViewById(R.id.radioGroupReportType)
        val radioMonthly: RadioButton = dialogView.findViewById(R.id.radioMonthly)
        val radioYearly: RadioButton = dialogView.findViewById(R.id.radioYearly)
        val spinnerReportMonth: Spinner = dialogView.findViewById(R.id.spinnerDialogReportMonth)
        val spinnerReportYear: Spinner = dialogView.findViewById(R.id.spinnerDialogReportYear)

        val months = SimpleDateFormat("MMMM", Locale.getDefault()).let { sdf ->
            (0..11).map { monthIndex ->
                val cal = Calendar.getInstance()
                cal.set(2000, monthIndex, 1) // fixed year + day, no rollover
                sdf.format(cal.time)
            }
        }
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReportMonth.adapter = monthAdapter
        spinnerReportMonth.setSelection(reportMonthSelected)

        val currentYearVal = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYearVal - 5..currentYearVal + 1).map { it.toString() }.reversed().toList()
        val yearAdapterVals = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, years)
        yearAdapterVals.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReportYear.adapter = yearAdapterVals
        spinnerReportYear.setSelection(years.indexOf(reportYearSelected.toString()))

        if (reportTypeSelected == "Yearly") {
            radioYearly.isChecked = true
            spinnerReportMonth.visibility = View.GONE
        } else {
            radioMonthly.isChecked = true
            spinnerReportMonth.visibility = View.VISIBLE
        }

        radioGroupReportType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioMonthly) {
                spinnerReportMonth.visibility = View.VISIBLE
                reportTypeSelected = "Monthly"
            } else if (checkedId == R.id.radioYearly) {
                spinnerReportMonth.visibility = View.GONE
                reportTypeSelected = "Yearly"
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Generate Report")
            .setView(dialogView)
            .setPositiveButton("Generate") { _, _ ->
                reportYearSelected = spinnerReportYear.selectedItem.toString().toInt()
                if (reportTypeSelected == "Monthly" && currentOrganizationId != null) {
                    reportMonthSelected = spinnerReportMonth.selectedItemPosition
                    fetchDataAndGeneratePdf(reportTypeSelected, reportYearSelected, reportMonthSelected)
                } else if (currentOrganizationId != null) {
                    fetchDataAndGeneratePdf(reportTypeSelected, reportYearSelected)
                } else {
                    Toast.makeText(context, "Cannot generate report: Organization ID missing.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun fetchDataAndGeneratePdf(type: String, year: Int, month: Int? = null) {
        if (!isAdded || context == null || currentTeacherId == null || currentTeacherName == null || currentOrganizationId == null) {
            Toast.makeText(context, "Cannot generate report, essential data is missing.", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Generating Report...").apply { isCancelable = false }
        loadingDialog.show(parentFragmentManager, "loading")

        lifecycleScope.launch {
            try {
                // The FeesReportGenerator now handles everything, including fetching the logo
                val feesReportGenerator = FeesReportGenerator(requireContext(), db)
                val pdfUri = feesReportGenerator.generateAndSaveFeeReport(
                    teacherId = currentTeacherId!!,
                    teacherName = currentTeacherName!!,
                    organizationId = currentOrganizationId!!,
                    reportType = type,
                    year = year,
                    month = month
                )

                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    loadingDialog.dismiss()
                    if (pdfUri != null) {
                        StatusDialogFragment.newInstance(true, "Report Generated Successfully!").show(parentFragmentManager, "successDialog")
                        tryOpenPdf(pdfUri)
                    } else {
                        // The generator shows its own toast, but we can show a dialog too
                        StatusDialogFragment.newInstance(false, "No payment data found.").show(parentFragmentManager, "failureDialog")
                    }
                }
            } catch (e: Exception) {
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    loadingDialog.dismiss()
                    Log.e(TAG, "Error generating report: ", e)
                    StatusDialogFragment.newInstance(false, "Error: ${e.message}").show(parentFragmentManager, "failureDialog")
                }
            }
        }
    }

    private fun showLoadingDialog(message: String): AlertDialog {
        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            setPadding(0, 32, 0, 32)
        }
        val ll = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32,32,32,32)
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(progressBar)
            val tv = TextView(context).apply {
                text = message
                setPadding(32,0,0,0)
                textSize = 16f
            }
            addView(tv)
        }
        return AlertDialog.Builder(requireContext())
            .setTitle("Processing")
            .setView(ll)
            .setCancelable(false)
            .show()
    }

    private suspend fun fetchReportDataForMonth(teacherId: String, organizationId: String, year: Int, month: Int): List<StudentPaymentSummaryItem> {
        val studentDetailsMap = mutableMapOf<String, StudentDetailsItem>()
        val studentMonthlyPaymentDetails = mutableMapOf<String, Pair<Double, Int>>()
        val reportList = mutableListOf<StudentPaymentSummaryItem>()

        val studentsSnapshot = db.collection("organizations").document(organizationId)
            .collection("students")
            .whereEqualTo("teacherId", teacherId)
            .orderBy("studentName").get().await()

        if (studentsSnapshot.isEmpty) return emptyList()

        studentsSnapshot.forEach { doc ->
            val studentId = doc.id
            studentDetailsMap[studentId] = StudentDetailsItem(
                id = studentId, studentName = doc.getString("studentName") ?: "N/A",
                parentName = doc.getString("parentName"), parentMobileNumber = doc.getString("parentMobileNumber"),
                profileImageUrl = doc.getString("profileImageUrl")
            )
            studentMonthlyPaymentDetails[studentId] = Pair(0.0, 0)
        }

        val calendar = Calendar.getInstance(); calendar.set(year, month, 1)
        val targetMonthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)

        val paymentsSnap = db.collection("organizations").document(organizationId)
            .collection("feePayments")
            .whereEqualTo("teacherId", teacherId)
            .whereEqualTo("paymentMonth", targetMonthYearStr)
            .get().await()

        if (!paymentsSnap.isEmpty) {
            paymentsSnap.forEach { pDoc ->
                val sId = pDoc.getString("studentId")
                val amt = pDoc.getDouble("paymentAmount") ?: 0.0
                if (sId != null && studentDetailsMap.containsKey(sId)) {
                    val currentDetails = studentMonthlyPaymentDetails[sId]!!
                    studentMonthlyPaymentDetails[sId] = Pair(currentDetails.first + amt, currentDetails.second + 1)
                }
            }
        }

        studentDetailsMap.values.sortedBy { it.studentName }.forEach { studentDetail ->
            val paymentInfo = studentMonthlyPaymentDetails[studentDetail.id] ?: Pair(0.0, 0)
            reportList.add(
                StudentPaymentSummaryItem(
                    studentId = studentDetail.id, studentName = studentDetail.studentName,
                    totalPaidThisMonth = paymentInfo.first, paymentCountThisMonth = paymentInfo.second,
                    profileImageUrl = studentDetail.profileImageUrl
                )
            )
        }
        return reportList
    }

    private suspend fun fetchReportDataForYear(teacherId: String, organizationId: String, year: Int): List<StudentPaymentSummaryItem> {
        val studentDetailsMap = mutableMapOf<String, StudentDetailsItem>()
        val studentYearlyPaymentDetails = mutableMapOf<String, Pair<Double, Int>>()
        val reportList = mutableListOf<StudentPaymentSummaryItem>()

        val studentsSnapshot = db.collection("organizations").document(organizationId)
            .collection("students")
            .whereEqualTo("teacherId", teacherId)
            .orderBy("studentName").get().await()

        if (studentsSnapshot.isEmpty) return emptyList()

        studentsSnapshot.forEach { doc ->
            val studentId = doc.id
            studentDetailsMap[studentId] = StudentDetailsItem(
                id = studentId, studentName = doc.getString("studentName") ?: "N/A",
                parentName = doc.getString("parentName"), parentMobileNumber = doc.getString("parentMobileNumber"),
                profileImageUrl = doc.getString("profileImageUrl")
            )
            studentYearlyPaymentDetails[studentId] = Pair(0.0, 0)
        }

        val paymentsSnap = db.collection("organizations").document(organizationId)
            .collection("feePayments")
            .whereEqualTo("teacherId", teacherId)
            .whereEqualTo("paymentYear", year)
            .get().await()

        if (!paymentsSnap.isEmpty) {
            paymentsSnap.forEach { pDoc ->
                val sId = pDoc.getString("studentId")
                val amt = pDoc.getDouble("paymentAmount") ?: 0.0
                if (sId != null && studentDetailsMap.containsKey(sId)) {
                    val currentDetails = studentYearlyPaymentDetails[sId]!!
                    studentYearlyPaymentDetails[sId] = Pair(currentDetails.first + amt, currentDetails.second + 1)
                }
            }
        }

        studentDetailsMap.values.sortedBy { it.studentName }.forEach { studentDetail ->
            val paymentInfo = studentYearlyPaymentDetails[studentDetail.id] ?: Pair(0.0, 0)
            reportList.add(
                StudentPaymentSummaryItem(
                    studentId = studentDetail.id, studentName = studentDetail.studentName,
                    totalPaidThisMonth = paymentInfo.first, paymentCountThisMonth = paymentInfo.second,
                    profileImageUrl = studentDetail.profileImageUrl
                )
            )
        }
        return reportList
    }

    private fun tryOpenPdf(uri: Uri) {
        if (!isAdded || context == null) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(context, "No PDF viewer app found.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening PDF", e)
            Toast.makeText(context, "Could not open PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}