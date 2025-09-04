package com.example.madarsa_attendance

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.madarsa_attendance.worker.SyncAttendanceWorker
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TakeAttendanceFragment : Fragment() {

    companion object {
        private const val TAG = "TakeAttendanceFragment"
        private const val ARG_TEACHER_ID_TAF = "teacher_id_taf"
        private const val ARG_TEACHER_NAME_TAF = "teacher_name_taf"

        @JvmStatic
        fun newInstance(teacherId: String, teacherName: String): TakeAttendanceFragment {
            return TakeAttendanceFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEACHER_ID_TAF, teacherId)
                    putString(ARG_TEACHER_NAME_TAF, teacherName)
                }
            }
        }
    }

    private lateinit var tvClassName: TextView
    private lateinit var tvAttendanceDate: TextView
    private lateinit var btnChangeDate: ImageButton
    private lateinit var recyclerViewStudents: RecyclerView
    private lateinit var studentAdapter: StudentAttendanceAdapter
    private lateinit var btnSaveAttendance: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoStudents: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var onlineDb: FirebaseFirestore
    private lateinit var localDb: AppDatabase
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentOrganizationId: String? = null
    private lateinit var dateForAttendance: String
    private lateinit var teacherDataViewModel: TeacherDataViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentTeacherId = it.getString(ARG_TEACHER_ID_TAF)
            currentTeacherName = it.getString(ARG_TEACHER_NAME_TAF)
        }
        onlineDb = FirebaseFirestore.getInstance()
        localDb = AppDatabase.getDatabase(requireContext())
        dateForAttendance = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        teacherDataViewModel = ViewModelProvider(requireActivity()).get(TeacherDataViewModel::class.java)
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_take_attendance, container, false)
        tvClassName = view.findViewById(R.id.tvClassNameAttendance)
        tvAttendanceDate = view.findViewById(R.id.tvAttendanceDate)
        btnChangeDate = view.findViewById(R.id.btnChangeDate)
        recyclerViewStudents = view.findViewById(R.id.recyclerViewStudentsAttendance)
        btnSaveAttendance = view.findViewById(R.id.btnSaveAttendance)
        progressBar = view.findViewById(R.id.progressBarTakeAttendance)
        tvNoStudents = view.findViewById(R.id.tvNoStudentsForAttendance)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_attendance)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (currentTeacherId == null || currentOrganizationId == null) {
            Toast.makeText(context, "Teacher or Organization info missing.", Toast.LENGTH_LONG).show()
            return
        }
        tvClassName.text = "Class: ${currentTeacherName ?: "Unknown"}"
        updateDateDisplay()
        setupRecyclerView()
        setupSwipeToRefresh()
        btnChangeDate.setOnClickListener { showDatePicker() }
        btnSaveAttendance.setOnClickListener { saveAttendanceLocally() }

        teacherDataViewModel.studentsDataMightHaveChanged.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                loadData()
            }
        }
        loadData()
    }

    private fun setupRecyclerView() {
        // The callback is no longer needed as the adapter updates its own data
        studentAdapter = StudentAttendanceAdapter(mutableListOf())
        recyclerViewStudents.layoutManager = LinearLayoutManager(context)
        recyclerViewStudents.adapter = studentAdapter
    }

    private fun loadData() {
        lifecycleScope.launch {
            if (!swipeRefreshLayout.isRefreshing) progressBar.visibility = View.VISIBLE
            tvNoStudents.visibility = View.GONE
            recyclerViewStudents.visibility = View.GONE
            btnSaveAttendance.isEnabled = false

            try {
                // Step 1: ALWAYS fetch the master student roster for the class from Firestore.
                val studentRoster = fetchStudentRosterFromFirestore()
                if (!isAdded) return@launch

                if (studentRoster.isEmpty()) {
                    updateUiWithStudentList(emptyList())
                    return@launch
                }

                // Step 2: Check for a local (unsynced) attendance record for the selected date.
                val localRecord = withContext(Dispatchers.IO) {
                    localDb.attendanceDao().getAttendanceForDate(dateForAttendance, currentTeacherId!!)
                }

                val finalStudentList: List<StudentAttendanceItem>
                if (localRecord != null) {
                    Log.d(TAG, "Found local record. Merging with Firestore roster.")
                    val localAttendanceMap = Gson().fromJson(localRecord.studentAttendancesJson, Array<StudentAttendanceItem>::class.java)
                        .associateBy { it.id }

                    finalStudentList = studentRoster.map { rosterStudent ->
                        rosterStudent.copy(status = localAttendanceMap[rosterStudent.id]?.status ?: "Present")
                    }
                } else {
                    Log.d(TAG, "No local record. Checking Firestore for synced record.")
                    val firestoreRecord = fetchSyncedAttendanceFromFirestore()
                    if (firestoreRecord != null) {
                        Log.d(TAG, "Found synced Firestore record. Merging.")
                        val firestoreAttendanceMap = firestoreRecord.associateBy { it.id }
                        finalStudentList = studentRoster.map { rosterStudent ->
                            rosterStudent.copy(status = firestoreAttendanceMap[rosterStudent.id]?.status ?: "Present")
                        }
                    } else {
                        Log.d(TAG, "No records found anywhere. Using fresh roster.")
                        finalStudentList = studentRoster
                    }
                }

                studentAdapter.submitList(finalStudentList)
                updateUiWithStudentList(finalStudentList)

            } catch (e: Exception) {
                if (!isAdded) return@launch
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                tvNoStudents.text = "Error loading students. Check internet connection."
                tvNoStudents.visibility = View.VISIBLE
                Log.e(TAG, "Error in master loadData function", e)
            }
        }
    }

    private suspend fun fetchStudentRosterFromFirestore(): List<StudentAttendanceItem> {
        val studentSnap = onlineDb.collection("organizations").document(currentOrganizationId!!)
            .collection("students").whereEqualTo("teacherId", currentTeacherId)
            .whereEqualTo("isActive", true)
            .orderBy("studentName")
            .get().await()

        return studentSnap.documents.map { doc ->
            StudentAttendanceItem(
                id = doc.id,
                name = doc.getString("studentName") ?: "N/A",
                status = "Present",
                profileImageUrl = doc.getString("profileImageUrl")
            )
        }
    }

    private suspend fun fetchSyncedAttendanceFromFirestore(): List<StudentAttendanceItem>? {
        val attendanceSnap = onlineDb.collection("organizations").document(currentOrganizationId!!)
            .collection("attendanceRecords")
            .whereEqualTo("teacherId", currentTeacherId)
            .whereEqualTo("date", dateForAttendance)
            .limit(1)
            .get().await()

        if (attendanceSnap.isEmpty) {
            return null
        }

        val doc = attendanceSnap.documents[0]
        val studentAttendancesMap = doc.get("studentAttendances") as? List<Map<String, Any>>
        return studentAttendancesMap?.map { map ->
            StudentAttendanceItem(
                id = map["studentId"] as? String ?: "",
                name = map["studentName"] as? String ?: "N/A",
                status = map["status"] as? String ?: "Present"
            )
        }
    }

    private fun updateUiWithStudentList(list: List<StudentAttendanceItem>) {
        progressBar.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
        if (list.isNotEmpty()) {
            recyclerViewStudents.visibility = View.VISIBLE
            tvNoStudents.visibility = View.GONE
            btnSaveAttendance.isEnabled = true
        } else {
            recyclerViewStudents.visibility = View.GONE
            tvNoStudents.text = getString(R.string.no_students_in_class)
            tvNoStudents.visibility = View.VISIBLE
            btnSaveAttendance.isEnabled = false
        }
    }

    private fun saveAttendanceLocally() {
        val attendanceData = studentAdapter.getAttendanceData()
        if (attendanceData.isEmpty()) {
            Toast.makeText(context, "No students to save.", Toast.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE
        btnSaveAttendance.isEnabled = false
        val record = LocalAttendanceRecord(
            date = dateForAttendance,
            teacherId = currentTeacherId!!,
            teacherName = currentTeacherName ?: "?",
            organizationId = currentOrganizationId!!,
            studentAttendancesJson = Gson().toJson(attendanceData),
            isSynced = false
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                localDb.attendanceDao().upsertAttendance(record)
            }
            if (!isAdded) return@launch
            progressBar.visibility = View.GONE
            btnSaveAttendance.isEnabled = true
            StatusDialogFragment.newInstance(true, "Attendance Saved Locally!").show(parentFragmentManager, "successDialog")
            scheduleSync()
        }
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncAttendanceWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(requireContext()).enqueue(syncWorkRequest)
        Log.d(TAG, "Sync work request enqueued.")
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Swipe to refresh triggered.")
            loadData()
        }
    }

    private fun updateDateDisplay() {
        tvAttendanceDate.text = try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateForAttendance) ?: Date()
            "Date: ${SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(date)}"
        } catch (e: Exception) { "Date: $dateForAttendance" }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateForAttendance)?.let { calendar.time = it }
        } catch (e: Exception) { Log.e(TAG, "Error parsing date", e) }

        DatePickerDialog(requireContext(), R.style.DatePickerDialog_App_Monochrome,
            { _, year, month, dayOfMonth ->
                dateForAttendance = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, dayOfMonth)
                updateDateDisplay()
                loadData()
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}