package com.example.madarsa_attendance

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
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
import com.google.firebase.firestore.FieldValue
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

    private enum class AttendanceStatus {
        NOT_TAKEN,
        SAVED,
        MODIFIED
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
    private lateinit var searchView: SearchView
    private lateinit var layoutAttendanceStatus: LinearLayout
    private lateinit var ivAttendanceStatusIcon: ImageView
    private lateinit var tvAttendanceStatusText: TextView

    private lateinit var onlineDb: FirebaseFirestore
    private lateinit var localDb: AppDatabase
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private var currentOrganizationId: String? = null
    private lateinit var dateForAttendance: String
    private lateinit var teacherDataViewModel: TeacherDataViewModel

    private var allStudentsList: MutableList<StudentAttendanceItem> = mutableListOf()
    private var currentAttendanceStatus = AttendanceStatus.NOT_TAKEN

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
        searchView = view.findViewById(R.id.searchViewStudents)
        layoutAttendanceStatus = view.findViewById(R.id.layoutAttendanceStatus)
        ivAttendanceStatusIcon = view.findViewById(R.id.ivAttendanceStatusIcon)
        tvAttendanceStatusText = view.findViewById(R.id.tvAttendanceStatusText)
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
        setupSearchView()
        btnChangeDate.setOnClickListener { showDatePicker() }
        btnSaveAttendance.setOnClickListener { saveAttendance() }

        teacherDataViewModel.studentsDataMightHaveChanged.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                loadData()
            }
        }
        loadData()
    }

    private fun setupRecyclerView() {
        studentAdapter = StudentAttendanceAdapter { studentId, newStatus ->
            // --- START: THIS IS THE CRITICAL FIX ---
            // Instead of modifying the existing list directly, we create a new list with the updated student.
            // This ensures that ListAdapter works correctly and state is not shared between dates.

            // 1. Create a new list by mapping over the current one
            val updatedList = allStudentsList.map { student ->
                if (student.id == studentId) {
                    // If this is the student that was changed, create a new 'StudentAttendanceItem'
                    // object with the updated status.
                    student.copy(status = newStatus)
                } else {
                    // Otherwise, keep the student object as is.
                    student
                }
            }

            // 2. Replace the old master list with the new, updated list.
            allStudentsList = updatedList.toMutableList()

            // 3. Re-apply the search filter and submit the fresh list to the adapter.
            filterStudentList(searchView.query.toString())

            // 4. Update the UI state to "Modified" if it was previously "Saved".
            if (currentAttendanceStatus == AttendanceStatus.SAVED) {
                currentAttendanceStatus = AttendanceStatus.MODIFIED
                updateUiForAttendanceStatus()
            }
            // --- END OF THE CRITICAL FIX ---
        }
        recyclerViewStudents.layoutManager = LinearLayoutManager(context)
        recyclerViewStudents.adapter = studentAdapter
    }


    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterStudentList(newText)
                return true
            }
        })
    }

    private fun filterStudentList(query: String?) {
        val filteredList = if (query.isNullOrEmpty()) {
            allStudentsList
        } else {
            allStudentsList.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
        // Always submit a new list to the adapter
        studentAdapter.submitList(filteredList.toList())
    }

    private fun loadData() {
        lifecycleScope.launch {
            if (!swipeRefreshLayout.isRefreshing) progressBar.visibility = View.VISIBLE
            tvNoStudents.visibility = View.GONE
            recyclerViewStudents.visibility = View.GONE
            btnSaveAttendance.isEnabled = false

            try {
                val studentRoster = fetchStudentRosterFromFirestore()
                if (!isAdded) return@launch

                if (studentRoster.isEmpty()) {
                    updateUiWithStudentList(emptyList())
                    allStudentsList.clear()
                    studentAdapter.submitList(emptyList())
                    currentAttendanceStatus = AttendanceStatus.NOT_TAKEN
                    updateUiForAttendanceStatus()
                    return@launch
                }

                val localRecord = withContext(Dispatchers.IO) {
                    localDb.attendanceDao().getAttendanceForDate(dateForAttendance, currentTeacherId!!)
                }

                val finalStudentList: List<StudentAttendanceItem>
                var recordFound = false

                if (localRecord != null) {
                    recordFound = true
                    val localAttendanceMap = Gson().fromJson(localRecord.studentAttendancesJson, Array<StudentAttendanceItem>::class.java)
                        .associateBy { it.id }

                    finalStudentList = studentRoster.map { rosterStudent ->
                        rosterStudent.copy(status = localAttendanceMap[rosterStudent.id]?.status ?: "Present")
                    }
                } else {
                    val firestoreRecord = fetchSyncedAttendanceFromFirestore()
                    if (firestoreRecord != null) {
                        recordFound = true
                        val firestoreAttendanceMap = firestoreRecord.associateBy { it.id }
                        finalStudentList = studentRoster.map { rosterStudent ->
                            rosterStudent.copy(status = firestoreAttendanceMap[rosterStudent.id]?.status ?: "Present")
                        }
                    } else {
                        // For a new date, reset all students to "Present"
                        finalStudentList = studentRoster.map { it.copy(status = "Present") }
                    }
                }

                allStudentsList.clear()
                allStudentsList.addAll(finalStudentList)
                filterStudentList(searchView.query.toString())
                updateUiWithStudentList(allStudentsList)

                currentAttendanceStatus = if (recordFound) AttendanceStatus.SAVED else AttendanceStatus.NOT_TAKEN
                updateUiForAttendanceStatus()

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
                regNo = doc.getString("regNo") ?: "N/A",
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
                regNo = "",
                status = map["status"] as? String ?: "Present",
                profileImageUrl = null
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

    private fun saveAttendance() {
        val attendanceData = allStudentsList

        if (attendanceData.isEmpty()) {
            Toast.makeText(context, "No students to save.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSaveAttendance.isEnabled = false

        lifecycleScope.launch {
            if (NetworkUtils.isOnline(requireContext())) {
                saveToFirestoreAndLocal(attendanceData)
            } else {
                saveToLocalOnly(attendanceData)
            }
        }
    }

    private suspend fun saveToFirestoreAndLocal(attendanceData: List<StudentAttendanceItem>) {
        try {
            val studentListForFirestore = attendanceData.map {
                mapOf("studentId" to it.id, "studentName" to it.name, "status" to it.status)
            }
            val firestoreRecord = mapOf(
                "date" to dateForAttendance,
                "teacherId" to currentTeacherId!!,
                "teacherName" to (currentTeacherName ?: "?"),
                "organizationId" to currentOrganizationId!!,
                "studentAttendances" to studentListForFirestore,
                "lastUpdatedAt" to FieldValue.serverTimestamp()
            )

            val existingDoc = onlineDb.collection("organizations").document(currentOrganizationId!!)
                .collection("attendanceRecords")
                .whereEqualTo("date", dateForAttendance)
                .whereEqualTo("teacherId", currentTeacherId!!)
                .limit(1).get().await()

            if (existingDoc.isEmpty) {
                onlineDb.collection("organizations").document(currentOrganizationId!!)
                    .collection("attendanceRecords").add(firestoreRecord).await()
            } else {
                val docId = existingDoc.documents[0].id
                onlineDb.collection("organizations").document(currentOrganizationId!!)
                    .collection("attendanceRecords").document(docId).set(firestoreRecord).await()
            }

            val localRecord = LocalAttendanceRecord(
                date = dateForAttendance,
                teacherId = currentTeacherId!!,
                teacherName = currentTeacherName ?: "?",
                organizationId = currentOrganizationId!!,
                studentAttendancesJson = Gson().toJson(attendanceData),
                isSynced = true
            )
            localDb.attendanceDao().upsertAttendance(localRecord)

            if (isAdded) {
                val message = if (currentAttendanceStatus == AttendanceStatus.MODIFIED) "Attendance Updated!" else "Attendance Saved!"
                StatusDialogFragment.newInstance(true, message).show(parentFragmentManager, "successDialog")
                currentAttendanceStatus = AttendanceStatus.SAVED
                updateUiForAttendanceStatus()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving to Firestore, falling back to local save.", e)
            saveToLocalOnly(attendanceData)
        } finally {
            if (isAdded) {
                progressBar.visibility = View.GONE
                btnSaveAttendance.isEnabled = true
            }
        }
    }

    private suspend fun saveToLocalOnly(attendanceData: List<StudentAttendanceItem>) {
        val record = LocalAttendanceRecord(
            date = dateForAttendance,
            teacherId = currentTeacherId!!,
            teacherName = currentTeacherName ?: "?",
            organizationId = currentOrganizationId!!,
            studentAttendancesJson = Gson().toJson(attendanceData),
            isSynced = false
        )
        withContext(Dispatchers.IO) {
            localDb.attendanceDao().upsertAttendance(record)
        }
        if (isAdded) {
            progressBar.visibility = View.GONE
            btnSaveAttendance.isEnabled = true
            val message = if (currentAttendanceStatus == AttendanceStatus.MODIFIED) "Updated Locally (Offline)" else "Saved Locally (Offline)"
            StatusDialogFragment.newInstance(true, message).show(parentFragmentManager, "successDialog")
            currentAttendanceStatus = AttendanceStatus.SAVED
            updateUiForAttendanceStatus()
            scheduleSync()
        }
    }

    private fun updateUiForAttendanceStatus() {
        when (currentAttendanceStatus) {
            AttendanceStatus.NOT_TAKEN -> {
                layoutAttendanceStatus.visibility = View.GONE
                btnSaveAttendance.text = getString(R.string.save_attendance)
            }
            AttendanceStatus.SAVED -> {
                layoutAttendanceStatus.visibility = View.VISIBLE
                tvAttendanceStatusText.text = "Attendance Saved"
                btnSaveAttendance.text = "Update"
            }
            AttendanceStatus.MODIFIED -> {
                layoutAttendanceStatus.visibility = View.GONE
                btnSaveAttendance.text = "Save Changes"
            }
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
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateForAttendance)
            "Date: ${SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(date!!)}"
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
                // Clear the search query when changing dates to avoid confusion
                searchView.setQuery("", false)
                loadData()
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}