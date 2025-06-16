package com.example.madarsa_attendance

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
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

    private var _tvClassName: TextView? = null
    private val tvClassName get() = _tvClassName!!
    // ... (other view backing properties as before)
    private var _tvAttendanceDate: TextView? = null
    private val tvAttendanceDate get() = _tvAttendanceDate!!
    private var _btnChangeDate: ImageButton? = null
    private val btnChangeDate get() = _btnChangeDate!!
    private var _recyclerViewStudents: RecyclerView? = null
    private val recyclerViewStudents get() = _recyclerViewStudents!!
    private var _studentAdapter: StudentAttendanceAdapter? = null
    private val studentAdapter get() = _studentAdapter!!
    private var _btnSaveAttendance: MaterialButton? = null
    private val btnSaveAttendance get() = _btnSaveAttendance!!
    private var _progressBar: ProgressBar? = null
    private val progressBar get() = _progressBar!!
    private var _tvNoStudents: TextView? = null
    private val tvNoStudents get() = _tvNoStudents!!


    private lateinit var db: FirebaseFirestore
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private lateinit var dateForAttendance: String
    private var existingAttendanceDocId: String? = null
    private var isAttendanceDataLoadedForCurrentDate = false
    private lateinit var teacherDataViewModel: TeacherDataViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentTeacherId = it.getString(ARG_TEACHER_ID_TAF)
            currentTeacherName = it.getString(ARG_TEACHER_NAME_TAF)
        }
        db = FirebaseFirestore.getInstance()
        dateForAttendance = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        teacherDataViewModel = ViewModelProvider(requireActivity()).get(TeacherDataViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_take_attendance, container, false)
        _tvClassName = view.findViewById(R.id.tvClassNameAttendanceFrag)
        _tvAttendanceDate = view.findViewById(R.id.tvAttendanceDateFrag)
        _btnChangeDate = view.findViewById(R.id.btnChangeDateAttendanceFrag)
        _recyclerViewStudents = view.findViewById(R.id.recyclerViewStudentsAttendanceFrag)
        _btnSaveAttendance = view.findViewById(R.id.btnSaveAttendanceFrag)
        _progressBar = view.findViewById(R.id.progressBarTakeAttendanceFrag)
        _tvNoStudents = view.findViewById(R.id.tvNoStudentsForAttendanceFrag)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (currentTeacherId == null) {
            Toast.makeText(context, "Teacher info missing.", Toast.LENGTH_LONG).show()
            _btnChangeDate?.isEnabled = false; _btnSaveAttendance?.isEnabled = false
            return
        }
        tvClassName.text = "Class: ${currentTeacherName ?: "Unknown"}"
        updateDateDisplay()
        setupRecyclerView()
        btnChangeDate.setOnClickListener { showDatePicker() }
        btnSaveAttendance.setOnClickListener { saveAttendance() }

        teacherDataViewModel.studentsDataMightHaveChanged.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                isAttendanceDataLoadedForCurrentDate = false
                if (currentTeacherId != null) loadStudentsAndCheckExistingAttendance()
            }
        }
        if (!isAttendanceDataLoadedForCurrentDate) {
            loadStudentsAndCheckExistingAttendance()
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentTeacherId != null && !isAttendanceDataLoadedForCurrentDate && isFragmentVisibleToUser) {
            loadStudentsAndCheckExistingAttendance()
        }
    }

    private val isFragmentVisibleToUser: Boolean
        get() = viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    override fun onDestroyView() {
        super.onDestroyView()
        _tvClassName = null; _tvAttendanceDate = null; _btnChangeDate = null
        _recyclerViewStudents = null; _studentAdapter = null; _btnSaveAttendance = null
        _progressBar = null; _tvNoStudents = null
    }

    private fun updateDateDisplay() { tvAttendanceDate.text = "Date: $dateForAttendance" }

    private fun showDatePicker() { /* ... (no change from previous correct version) ... */
        if (!isAdded || context == null) return
        val calendar = Calendar.getInstance()
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.parse(dateForAttendance)?.let { calendar.time = it }
        } catch (e: Exception) { Log.e(TAG, "Error parsing date $dateForAttendance", e) }

        DatePickerDialog(requireContext(), R.style.DatePickerDialog_App_Monochrome,
            { _, year, month, dayOfMonth ->
                val newSelectedDate = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, dayOfMonth)
                if (newSelectedDate != dateForAttendance) {
                    dateForAttendance = newSelectedDate
                    updateDateDisplay()
                    isAttendanceDataLoadedForCurrentDate = false
                    loadStudentsAndCheckExistingAttendance()
                }
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    private fun setupRecyclerView() { /* ... (no change from previous correct version) ... */
        if (!isAdded || context == null || _recyclerViewStudents == null) return
        _studentAdapter = StudentAttendanceAdapter(mutableListOf()) { _, _ -> }
        recyclerViewStudents.layoutManager = LinearLayoutManager(context)
        recyclerViewStudents.adapter = studentAdapter
    }
    private fun loadStudentsAndCheckExistingAttendance() { /* ... (no change from previous correct version) ... */
        if (currentTeacherId == null || !isAdded || _progressBar == null) return
        progressBar.visibility = View.VISIBLE
        _tvNoStudents?.visibility = View.GONE
        _recyclerViewStudents?.visibility = View.GONE
        _btnSaveAttendance?.isEnabled = false

        db.collection("attendanceRecords")
            .whereEqualTo("teacherId", currentTeacherId)
            .whereEqualTo("date", dateForAttendance).limit(1).get()
            .addOnSuccessListener { attendanceSnapshot ->
                if (!isAdded) return@addOnSuccessListener
                val statuses = mutableMapOf<String, String>()
                if (!attendanceSnapshot.isEmpty) {
                    existingAttendanceDocId = attendanceSnapshot.documents[0].id
                    (attendanceSnapshot.documents[0].get("studentAttendances") as? List<Map<String,Any>>)?.forEach {
                        val sId=it["studentId"] as? String; val st=it["status"] as? String
                        if(sId!=null && st!=null) statuses[sId]=st
                    }
                } else { existingAttendanceDocId = null }
                fetchStudentsForClass(statuses)
            }
            .addOnFailureListener { e -> if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Error checking existing attendance: ", e)
                Toast.makeText(context, "Could not check prior attendance. Marking fresh.", Toast.LENGTH_SHORT).show()
                fetchStudentsForClass(mutableMapOf())
            }
    }

    private fun fetchStudentsForClass(statuses: Map<String, String>) {
        if (currentTeacherId == null || !isAdded || _studentAdapter == null || _progressBar == null || _tvNoStudents == null || _recyclerViewStudents == null || _btnSaveAttendance == null) return
        Log.d(TAG, "Fetching students for class: $currentTeacherId for attendance list")

        db.collection("students").whereEqualTo("teacherId", currentTeacherId)
            .orderBy("studentName", Query.Direction.ASCENDING).get()
            .addOnSuccessListener { studentSnap ->
                if (!isAdded || _studentAdapter == null) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                val list = mutableListOf<StudentAttendanceItem>()
                if (!studentSnap.isEmpty) {
                    studentSnap.documents.forEach { doc ->
                        val sId = doc.id
                        val name = doc.getString("studentName") ?: "N/A"
                        val imageUrl = doc.getString("profileImageUrl") // <<< GET IMAGE URL
                        list.add(StudentAttendanceItem(sId, name, statuses[sId] ?: "Present", imageUrl)) // <<< PASS IMAGE URL
                    }
                    studentAdapter.submitList(list)
                    recyclerViewStudents.visibility = View.VISIBLE
                    tvNoStudents.visibility = View.GONE
                    btnSaveAttendance.isEnabled = true
                } else {
                    tvNoStudents.text = getString(R.string.no_students_in_class) // Ensure this string resource exists
                    tvNoStudents.visibility = View.VISIBLE
                    recyclerViewStudents.visibility = View.GONE
                    btnSaveAttendance.isEnabled = false
                    studentAdapter.submitList(emptyList())
                }
                isAttendanceDataLoadedForCurrentDate = true
            }
            .addOnFailureListener { e -> if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE; isAttendanceDataLoadedForCurrentDate = false
                _tvNoStudents?.apply { text = "Error loading students."; visibility = View.VISIBLE }
                _btnSaveAttendance?.isEnabled = false
                Log.e(TAG, "Error fetching students for attendance list", e)
            }
    }

    private fun saveAttendance() { /* ... (no change from previous correct version) ... */
        if (currentTeacherId == null || !isAdded || _studentAdapter == null || _progressBar == null || _btnSaveAttendance == null) return
        val data = studentAdapter.getAttendanceData()
        if (data.isEmpty()) { Toast.makeText(context, "No students to save.", Toast.LENGTH_SHORT).show(); return }
        progressBar.visibility = View.VISIBLE; btnSaveAttendance.isEnabled = false

        val attData = data.map { studentItem ->
            val studentMap = HashMap<String, Any?>()
            studentMap["studentId"] = studentItem.id
            studentMap["studentName"] = studentItem.name
            studentMap["status"] = studentItem.status
            studentMap
        }
        val record = HashMap<String, Any?>()
        record["date"] = dateForAttendance
        record["teacherId"] = currentTeacherId!!
        record["teacherName"] = currentTeacherName ?: "?"
        record["studentAttendances"] = attData
        record["lastUpdatedAt"] = FieldValue.serverTimestamp()

        val task = if(existingAttendanceDocId!=null) {
            db.collection("attendanceRecords").document(existingAttendanceDocId!!).set(record)
        } else {
            db.collection("attendanceRecords").add(record)
        }

        task.addOnSuccessListener { docRefOrVoid -> if(!isAdded) return@addOnSuccessListener
            progressBar.visibility = View.GONE; btnSaveAttendance.isEnabled = true
            Toast.makeText(context, "Attendance for $dateForAttendance saved!", Toast.LENGTH_SHORT).show()
            if(existingAttendanceDocId == null && docRefOrVoid is com.google.firebase.firestore.DocumentReference) {
                existingAttendanceDocId = docRefOrVoid.id
            }
        }.addOnFailureListener { e-> if(!isAdded) return@addOnFailureListener
            progressBar.visibility = View.GONE; btnSaveAttendance.isEnabled = true
            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}