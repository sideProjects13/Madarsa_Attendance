package com.example.madarsa_attendance

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
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
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton // Import for FAB
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
//   import org.jetbrains.kotlinx.coroutines.tasks.await // For .await()
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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

    // Changed from Button to ExtendedFloatingActionButton
    private var _fabGenerateReport: ExtendedFloatingActionButton? = null
    private val fabGenerateReport get() = _fabGenerateReport!!


    private lateinit var db: FirebaseFirestore
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null

    private val studentDetailsMap = mutableMapOf<String, StudentDetailsItem>()
    private val paymentSummaryDisplayList = mutableListOf<StudentPaymentSummaryItem>()

    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH)

    private var isCurrentPeriodDataLoaded = false
    private var spinnersFullyInitializedPayment = false

    private lateinit var teacherDataViewModel: TeacherDataViewModel
    private lateinit var studentPaymentHistoryLauncher: ActivityResultLauncher<Intent>

    // For PDF generation options
    private var reportTypeSelected: String = "Monthly" // Default
    private var reportYearSelected: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var reportMonthSelected: Int = Calendar.getInstance().get(Calendar.MONTH)

    // Permission Launcher
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

        studentPaymentHistoryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Returned from StudentPaymentHistoryActivity with RESULT_OK. Refreshing payment summary.")
                isCurrentPeriodDataLoaded = false
                if (checkPreConditionsAndLoad()) {
                    Log.d(TAG, "Launcher Result: Triggering data load.")
                }
            } else {
                Log.d(TAG, "Returned from StudentPaymentHistoryActivity with result code: ${result.resultCode}")
            }
        }
        Log.d(TAG, "onCreate: Initial selectedMonth=$selectedMonth, selectedYear=$selectedYear")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_payment_summary, container, false)
        _spinnerMonth = view.findViewById(R.id.spinnerMonthPaymentSummaryFrag)
        _spinnerYear = view.findViewById(R.id.spinnerYearPaymentSummaryFrag)
        _recyclerViewPayments = view.findViewById(R.id.recyclerViewPaymentSummaryFrag)
        _progressBar = view.findViewById(R.id.progressBarPaymentSummaryFrag)
        _tvNoData = view.findViewById(R.id.tvNoPaymentDataFrag)
        _searchViewPaymentSummary = view.findViewById(R.id.searchViewPaymentSummary)
        _fabGenerateReport = view.findViewById(R.id.fabGenerateReport) // Initialize FAB
        Log.d(TAG, "onCreateView completed")
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: TeacherID: $currentTeacherId")
        if (currentTeacherId == null) {
            Toast.makeText(context, "Teacher info missing.", Toast.LENGTH_LONG).show(); return
        }
        setupRecyclerView()
        setupSpinners(view)
        setupSearchView()

        fabGenerateReport.setOnClickListener { // Set listener on FAB
            checkAndRequestStoragePermission()
        }

        teacherDataViewModel.studentsDataMightHaveChanged.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Log.d(TAG, "Observer: studentsDataMightHaveChanged event received.")
                isCurrentPeriodDataLoaded = false
                _searchViewPaymentSummary?.setQuery("", false)
                if (checkPreConditionsAndLoad()) {
                    Log.d(TAG, "Observer: Triggering data load due to student list change.")
                } else {
                    Log.d(TAG, "Observer: student list change, but pre-conditions for load not met yet.")
                }
            }
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
        Log.d(TAG, "checkPreConditionsAndLoad (from $calledFrom): currentTeacherId=$currentTeacherId, !isCurrentPeriodDataLoaded=${!isCurrentPeriodDataLoaded}, spinnersFullyInitialized=$spinnersFullyInitializedPayment, isVisible=$isFragmentVisibleToUser")
        if (currentTeacherId != null && !isCurrentPeriodDataLoaded && spinnersFullyInitializedPayment && isFragmentVisibleToUser) {
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
        _fabGenerateReport = null // Clear FAB reference
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
        if (!isAdded || context == null) { Log.w(TAG, "setupSpinners: Fragment not added or context null"); return }
        spinnersFullyInitializedPayment = false
        val staticSpinnerTextColor = ContextCompat.getColor(requireContext(), R.color.mono_palette_black)

        val months = SimpleDateFormat("MMMM", Locale.getDefault()).let { sdf ->
            (0..11).map { val cal = Calendar.getInstance(); cal.set(Calendar.MONTH, it); sdf.format(cal.time) }
        }
        val monthAdapter = ColorableSpinnerAdapter(requireContext(), months, staticSpinnerTextColor)
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
        if (currentTeacherId == null || !isAdded || _progressBar == null || _tvNoData == null || _recyclerViewPayments == null || _paymentSummaryAdapter == null) {
            Log.e(TAG, "loadPaymentSummaryData: CRITICAL PRE-CONDITIONS NOT MET. Aborting. " +
                    "teacherId=$currentTeacherId, isAdded=$isAdded, adapterNull=${_paymentSummaryAdapter==null}")
            if(isAdded) {
                _progressBar?.visibility = View.GONE
                _tvNoData?.text = "Error: Components not ready."
                _tvNoData?.visibility = View.VISIBLE
                _recyclerViewPayments?.visibility = View.GONE
            }
            isCurrentPeriodDataLoaded = false
            return
        }

        progressBar.visibility = View.VISIBLE
        tvNoData.visibility = View.GONE
        recyclerViewPayments.visibility = View.GONE

        val calendar = Calendar.getInstance(); calendar.set(selectedYear, selectedMonth, 1)
        val targetMonthYearStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        Log.d(TAG, "loadPaymentSummaryData: Target month/year string: $targetMonthYearStr for teacher: $currentTeacherId")

        studentDetailsMap.clear()
        val studentMonthlyPaymentDetails = mutableMapOf<String, Pair<Double, Int>>()

        db.collection("students").whereEqualTo("teacherId", currentTeacherId).orderBy("studentName").get()
            .addOnSuccessListener { studentsSnapshot ->
                if (!isAdded) { Log.w(TAG, "Students fetched, but fragment not added."); isCurrentPeriodDataLoaded = false; return@addOnSuccessListener }
                Log.d(TAG, "Fetched ${studentsSnapshot.size()} student documents for teacher $currentTeacherId.")

                if (studentsSnapshot.isEmpty) {
                    progressBar.visibility = View.GONE
                    tvNoData.text = "No students in this class."
                    tvNoData.visibility = View.VISIBLE
                    recyclerViewPayments.visibility = View.GONE
                    paymentSummaryAdapter.updateData(emptyList())
                    studentDetailsMap.clear()
                    isCurrentPeriodDataLoaded = true
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

                db.collection("feePayments")
                    .whereEqualTo("teacherId", currentTeacherId)
                    .whereEqualTo("paymentMonth", targetMonthYearStr)
                    .get()
                    .addOnSuccessListener { paymentsSnap ->
                        if (!isAdded) { Log.w(TAG, "Payments fetched, but fragment not added."); isCurrentPeriodDataLoaded = false; return@addOnSuccessListener }

                        if (!paymentsSnap.isEmpty) {
                            Log.d(TAG, "Fetched ${paymentsSnap.size()} payments for $targetMonthYearStr.")
                            paymentsSnap.forEach { pDoc ->
                                val sId = pDoc.getString("studentId")
                                val amt = pDoc.getDouble("paymentAmount") ?: 0.0
                                if (sId != null && studentDetailsMap.containsKey(sId)) {
                                    val currentDetails = studentMonthlyPaymentDetails[sId]!!
                                    studentMonthlyPaymentDetails[sId] = Pair(currentDetails.first + amt, currentDetails.second + 1)
                                } else {
                                    Log.w(TAG, "Payment found for studentId '$sId' not in current class map, or sId is null. PaymentMonth: $targetMonthYearStr")
                                }
                            }
                        } else {
                            Log.d(TAG, "No payments found for $targetMonthYearStr.")
                        }
                        processAndDisplaySummary(studentMonthlyPaymentDetails)
                        isCurrentPeriodDataLoaded = true
                    }.addOnFailureListener { e ->
                        if (!isAdded) return@addOnFailureListener
                        Log.e(TAG, "Error loading payments for $targetMonthYearStr:", e)
                        progressBar.visibility = View.GONE
                        tvNoData.text = "Error loading payments: ${e.message}"
                        tvNoData.visibility = View.VISIBLE
                        recyclerViewPayments.visibility = View.GONE
                        isCurrentPeriodDataLoaded = false
                    }
            }.addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Error loading students for teacher $currentTeacherId:", e)
                progressBar.visibility = View.GONE
                tvNoData.text = "Error loading students: ${e.message}"
                tvNoData.visibility = View.VISIBLE
                recyclerViewPayments.visibility = View.GONE
                paymentSummaryAdapter.updateData(emptyList())
                isCurrentPeriodDataLoaded = false
            }
    }

    private fun processAndDisplaySummary(
        studentMonthlyPaymentDetails: Map<String, Pair<Double, Int>>
    ) {
        if (!isAdded || _paymentSummaryAdapter == null) {
            Log.w(TAG, "processAndDisplaySummary: Fragment not added or adapter is null.")
            _progressBar?.visibility = View.GONE
            return
        }
        Log.d(TAG, "processAndDisplaySummary: Processing ${studentDetailsMap.size} students from map.")
        paymentSummaryDisplayList.clear()

        studentDetailsMap.values.sortedBy { it.studentName }.forEach { studentDetail ->
            val paymentInfo = studentMonthlyPaymentDetails[studentDetail.id] ?: Pair(0.0, 0)
            paymentSummaryDisplayList.add(
                StudentPaymentSummaryItem(
                    studentId = studentDetail.id,
                    studentName = studentDetail.studentName,
                    totalPaidThisMonth = paymentInfo.first,
                    paymentCountThisMonth = paymentInfo.second,
                    profileImageUrl = studentDetail.profileImageUrl
                )
            )
        }
        Log.d(TAG, "Constructed paymentSummaryDisplayList with ${paymentSummaryDisplayList.size} items.")
        progressBar.visibility = View.GONE

        if (studentDetailsMap.isEmpty()) {
            tvNoData.text = "No students in this class."
            tvNoData.visibility = View.VISIBLE
            recyclerViewPayments.visibility = View.GONE
        } else if (paymentSummaryDisplayList.isEmpty() && studentDetailsMap.isNotEmpty()){
            tvNoData.text = "No students found (after processing). Check logs."
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
        if (!isAdded || context == null) {
            Log.w(TAG, "showReportOptionsDialog: Fragment not added or context is null.")
            return
        }
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_report_options, null)
        val radioGroupReportType: RadioGroup = dialogView.findViewById(R.id.radioGroupReportType)
        val radioMonthly: RadioButton = dialogView.findViewById(R.id.radioMonthly)
        val radioYearly: RadioButton = dialogView.findViewById(R.id.radioYearly)
        val spinnerReportMonth: Spinner = dialogView.findViewById(R.id.spinnerDialogReportMonth)
        val spinnerReportYear: Spinner = dialogView.findViewById(R.id.spinnerDialogReportYear)

        val months = SimpleDateFormat("MMMM", Locale.getDefault()).let { sdf ->
            (0..11).map { val cal = Calendar.getInstance(); cal.set(Calendar.MONTH, it); sdf.format(cal.time) }
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
                if (reportTypeSelected == "Monthly") {
                    reportMonthSelected = spinnerReportMonth.selectedItemPosition
                    fetchDataAndGeneratePdf(reportTypeSelected, reportYearSelected, reportMonthSelected)
                } else {
                    fetchDataAndGeneratePdf(reportTypeSelected, reportYearSelected)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun fetchDataAndGeneratePdf(type: String, year: Int, month: Int? = null) {
        if (!isAdded || context == null || currentTeacherId == null || currentTeacherName == null) {
            Toast.makeText(context, "Class information is missing or fragment not ready.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogProgressBar = showLoadingDialog("Generating report data...")

        lifecycleScope.launch {
            try {
                val reportData: List<StudentPaymentSummaryItem> = if (type == "Monthly" && month != null) {
                    fetchReportDataForMonth(currentTeacherId!!, year, month)
                } else {
                    fetchReportDataForYear(currentTeacherId!!, year)
                }
                dialogProgressBar.dismiss()

                if (reportData.isEmpty()) {
                    Toast.makeText(context, "No payment data found for the selected period.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val madarsaName = "Your Madarsa Name" // Replace with actual or fetch from settings

                val pdfUri = if (type == "Monthly" && month != null) {
                    PdfGenerator.createMonthlyReportPdf(
                        requireContext(),
                        madarsaName,
                        currentTeacherName!!,
                        year,
                        month,
                        reportData
                    )
                } else {
                    PdfGenerator.createYearlyReportPdf(
                        requireContext(),
                        madarsaName,
                        currentTeacherName!!,
                        year,
                        reportData
                    )
                }

                if (pdfUri != null) {
                    Toast.makeText(context, "Report saved. Check Documents/MadarsaReports.", Toast.LENGTH_LONG).show()
                    // Optionally, offer to open or share the PDF
                    // tryOpenPdf(pdfUri)
                } else {
                    Toast.makeText(context, "Failed to generate PDF report.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                dialogProgressBar.dismiss()
                Log.e(TAG, "Error generating report: ", e)
                Toast.makeText(context, "Error generating report: ${e.message}", Toast.LENGTH_SHORT).show()
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
                textSize = 16f // Make text a bit larger
            }
            addView(tv)
        }
        return AlertDialog.Builder(requireContext())
            .setTitle("Processing")
            .setView(ll)
            .setCancelable(false)
            .show()
    }

    private suspend fun fetchReportDataForMonth(teacherId: String, year: Int, month: Int): List<StudentPaymentSummaryItem> {
        val studentDetailsMap = mutableMapOf<String, StudentDetailsItem>()
        val studentMonthlyPaymentDetails = mutableMapOf<String, Pair<Double, Int>>()
        val reportList = mutableListOf<StudentPaymentSummaryItem>()

        val studentsSnapshot = db.collection("students")
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

        val paymentsSnap = db.collection("feePayments")
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

    private suspend fun fetchReportDataForYear(teacherId: String, year: Int): List<StudentPaymentSummaryItem> {
        val studentDetailsMap = mutableMapOf<String, StudentDetailsItem>()
        val studentYearlyPaymentDetails = mutableMapOf<String, Pair<Double, Int>>()
        val reportList = mutableListOf<StudentPaymentSummaryItem>()

        val studentsSnapshot = db.collection("students")
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

        val paymentsSnap = db.collection("feePayments")
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

    // Optional: Helper function to try opening the PDF
    private fun tryOpenPdf(uri: Uri) {
        if (!isAdded || context == null) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Important for content URIs
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            // Verify that an app exists to receive the intent
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