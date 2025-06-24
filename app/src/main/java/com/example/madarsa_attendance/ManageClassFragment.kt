package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar

class ManageClassFragment : Fragment() {

    companion object {
        private const val TAG = "ManageClassFragment"
        private const val ARG_TEACHER_ID_MCF = "teacher_id_mcf"
        private const val ARG_TEACHER_NAME_MCF = "teacher_name_mcf"
        private const val INTRO_EXTEND_DELAY_ADD_STUDENT_FAB = 500L
        private const val INTRO_SHRINK_DELAY_ADD_STUDENT_FAB = 2500L

        @JvmStatic
        fun newInstance(teacherId: String, teacherName: String): ManageClassFragment {
            return ManageClassFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEACHER_ID_MCF, teacherId)
                    putString(ARG_TEACHER_NAME_MCF, teacherName)
                }
            }
        }
    }

    // Views
    private var _recyclerViewClassStudents: RecyclerView? = null
    private val recyclerViewClassStudents get() = _recyclerViewClassStudents!!
    private var _classStudentsAdapter: ClassStudentsAdapter? = null
    private val classStudentsAdapter get() = _classStudentsAdapter!!
    private var _fabAddStudentToClass: ExtendedFloatingActionButton? = null
    private val fabAddStudentToClass get() = _fabAddStudentToClass!!
    private var _progressBar: ProgressBar? = null
    private val progressBar get() = _progressBar!!
    private var _tvNoStudents: TextView? = null
    private val tvNoStudents get() = _tvNoStudents!!
    private var _searchViewStudents: SearchView? = null
    private val searchViewStudents get() = _searchViewStudents!!
    private var _swipeRefreshLayout: SwipeRefreshLayout? = null
    private val swipeRefreshLayout get() = _swipeRefreshLayout!!

    // Backend & Data
    private lateinit var db: FirebaseFirestore
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    private lateinit var teacherDataViewModel: TeacherDataViewModel

    // Handlers & Launchers
    private val fabAddStudentIntroHandler = Handler(Looper.getMainLooper())
    private var introExtendAddStudentRunnable: Runnable? = null
    private var introShrinkAddStudentRunnable: Runnable? = null

    private val studentActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!isAdded) return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Student action (add/edit) returned OK. Reloading student list.")
            loadStudentsForClass()
        }
        _fabAddStudentToClass?.shrink()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentTeacherId = it.getString(ARG_TEACHER_ID_MCF)
            currentTeacherName = it.getString(ARG_TEACHER_NAME_MCF)
        }
        db = FirebaseFirestore.getInstance()
        teacherDataViewModel = ViewModelProvider(requireActivity()).get(TeacherDataViewModel::class.java)
        Log.d(TAG, "onCreate - Teacher ID: $currentTeacherId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView")
        val view = inflater.inflate(R.layout.fragment_manage_class, container, false)
        _recyclerViewClassStudents = view.findViewById(R.id.recyclerViewClassStudentsFrag)
        _fabAddStudentToClass = view.findViewById(R.id.fabAddStudentToClassFrag)
        _progressBar = view.findViewById(R.id.progressBarClassStudentsFrag)
        _tvNoStudents = view.findViewById(R.id.tvNoStudentsInClassFrag)
        _searchViewStudents = view.findViewById(R.id.searchViewStudents)
        _swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout_students)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated - Initializing UI.")

        if (currentTeacherId == null) {
            Toast.makeText(context, "Teacher info missing.", Toast.LENGTH_LONG).show()
            return
        }
        setupRecyclerView()
        setupFabInteraction()
        setupSearchView()
        setupSwipeToRefresh()

        _fabAddStudentToClass?.shrink()
        if (savedInstanceState == null) {
            _fabAddStudentToClass?.let { startAddStudentFabIntroAnimation(it) }
        }

        teacherDataViewModel.studentsDataMightHaveChanged.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Log.d(TAG, "Observed student data change from ViewModel. Reloading list.")
                loadStudentsForClass()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - ManageClassFragment")
        loadStudentsForClass()
        _fabAddStudentToClass?.let { if (it.isExtended && introExtendAddStudentRunnable == null) it.shrink() }
    }

    override fun onPause() {
        super.onPause()
        cancelAddStudentFabIntroAnimation()
        Log.d(TAG, "onPause - ManageClassFragment")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelAddStudentFabIntroAnimation()
        _recyclerViewClassStudents?.adapter = null
        _recyclerViewClassStudents = null
        _classStudentsAdapter = null
        _fabAddStudentToClass = null
        _progressBar = null
        _tvNoStudents = null
        _searchViewStudents?.setOnQueryTextListener(null)
        _searchViewStudents = null
        _swipeRefreshLayout?.setOnRefreshListener(null)
        _swipeRefreshLayout = null
        Log.d(TAG, "onDestroyView - ManageClassFragment")
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Swipe to refresh triggered.")
            loadStudentsForClass()
        }
    }

    private fun setupSearchView() {
        searchViewStudents.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                classStudentsAdapter.filter(query)
                searchViewStudents.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                classStudentsAdapter.filter(newText)
                return true
            }
        })
        searchViewStudents.setOnCloseListener {
            searchViewStudents.setQuery("", false)
            classStudentsAdapter.filter("")
            true
        }
    }

    private fun setupFabInteraction() {
        fabAddStudentToClass.setOnClickListener {
            cancelAddStudentFabIntroAnimation()
            launchAddStudentActivity()
        }
    }

    private fun launchAddStudentActivity() {
        if (!isAdded || activity == null) return
        val intent = Intent(activity, AddStudentActivity::class.java).apply {
            putExtra("PRESELECTED_TEACHER_ID", currentTeacherId)
            putExtra("PRESELECTED_TEACHER_NAME", currentTeacherName)
        }
        studentActionLauncher.launch(intent)
        _fabAddStudentToClass?.shrink()
    }

    private fun startAddStudentFabIntroAnimation(fab: ExtendedFloatingActionButton) {
        if (!isAdded || activity == null || !fab.isAttachedToWindow) return
        fab.shrink()
        introExtendAddStudentRunnable = Runnable {
            if (isAdded && activity?.isFinishing == false && fab.isAttachedToWindow) {
                fab.extend()
                introShrinkAddStudentRunnable = Runnable {
                    if (isAdded && activity?.isFinishing == false && fab.isAttachedToWindow) {
                        fab.shrink()
                    }
                }
                fabAddStudentIntroHandler.postDelayed(introShrinkAddStudentRunnable!!, INTRO_SHRINK_DELAY_ADD_STUDENT_FAB)
            }
        }
        fabAddStudentIntroHandler.postDelayed(introExtendAddStudentRunnable!!, INTRO_EXTEND_DELAY_ADD_STUDENT_FAB)
    }

    private fun cancelAddStudentFabIntroAnimation() {
        introExtendAddStudentRunnable?.let { fabAddStudentIntroHandler.removeCallbacks(it); introExtendAddStudentRunnable = null }
        introShrinkAddStudentRunnable?.let { fabAddStudentIntroHandler.removeCallbacks(it); introShrinkAddStudentRunnable = null }
    }

    private fun setupRecyclerView() {
        if (!isAdded || context == null) return
        _classStudentsAdapter = ClassStudentsAdapter(ArrayList()) { selectedStudent ->
            showStudentOptionsDialog(selectedStudent)
        }
        recyclerViewClassStudents.layoutManager = LinearLayoutManager(context)
        recyclerViewClassStudents.adapter = _classStudentsAdapter
    }

    private fun showStudentOptionsDialog(student: StudentDetailsItem) {
        if (!isAdded || context == null) return
        val options = arrayOf("Edit Student", "Inactivate Student", "Delete Student", "Move to Another Class", "View Monthly Attendance")
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Student: ${student.studentName}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Edit
                        val intent = Intent(activity, EditStudentActivity::class.java).apply {
                            putExtra("STUDENT_ID", student.id)
                        }
                        studentActionLauncher.launch(intent)
                    }
                    1 -> confirmInactivateStudent(student)
                    2 -> confirmDeleteStudent(student)
                    3 -> showMoveStudentDialog(student)
                    4 -> { // View Monthly Attendance
                        val calendar = Calendar.getInstance()
                        val intent = Intent(activity, StudentMonthlyAttendanceActivity::class.java).apply {
                            putExtra("STUDENT_ID", student.id)
                            putExtra("STUDENT_NAME", student.studentName)
                            putExtra("TEACHER_ID", currentTeacherId)
                            putExtra("TARGET_YEAR", calendar.get(Calendar.YEAR))
                            putExtra("TARGET_MONTH", calendar.get(Calendar.MONTH))
                        }
                        startActivity(intent)
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmInactivateStudent(student: StudentDetailsItem) {
        if (!isAdded || context == null) return
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Inactivate Student")
            .setMessage("Are you sure you want to inactivate ${student.studentName}? They will be removed from this list and can be viewed in the 'Inactive Students' section.")
            .setPositiveButton("Inactivate") { _, _ ->
                inactivateStudentInFirestore(student.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun inactivateStudentInFirestore(studentId: String) {
        if (!isAdded) return
        progressBar.visibility = View.VISIBLE
        db.collection("students").document(studentId)
            .update("isActive", false)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Student inactivated", Toast.LENGTH_SHORT).show()
                teacherDataViewModel.notifyStudentDataChanged()
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error inactivating: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmDeleteStudent(student: StudentDetailsItem) {
        if (!isAdded || context == null) return
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Student")
            .setMessage("Are you sure you want to permanently delete ${student.studentName}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteStudentFromFirestore(student.id) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteStudentFromFirestore(studentId: String) {
        if (!isAdded) return
        progressBar.visibility = View.VISIBLE
        db.collection("students").document(studentId).delete()
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Student deleted", Toast.LENGTH_SHORT).show()
                teacherDataViewModel.notifyStudentDataChanged()
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error deleting: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showMoveStudentDialog(studentToMove: StudentDetailsItem) {
        if (!isAdded || context == null) return
        progressBar.visibility = View.VISIBLE
        db.collection("teachers").orderBy("teacherName").get()
            .addOnSuccessListener { teacherSnap ->
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                val teachers = mutableListOf<TeacherSpinnerItem>()
                val names = mutableListOf<String>()
                teacherSnap.documents.filter { it.id != currentTeacherId }.forEach {
                    teachers.add(TeacherSpinnerItem(it.id, it.getString("teacherName") ?: "N/A", it.getString("profileImageUrl")))
                    names.add(it.getString("teacherName") ?: "N/A")
                }
                if (names.isEmpty()) {
                    Toast.makeText(context, "No other classes available to move to.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
                    .setTitle("Move ${studentToMove.studentName} to:").setItems(names.toTypedArray()) { _, i ->
                        moveStudentToNewClass(studentToMove.id, teachers[i])
                    }.setNegativeButton("Cancel", null).show()
            }.addOnFailureListener { if (isAdded) progressBar.visibility = View.GONE }
    }

    private fun moveStudentToNewClass(studentId: String, newTeacher: TeacherSpinnerItem) {
        if (!isAdded) return
        progressBar.visibility = View.VISIBLE
        val updates = mapOf(
            "teacherId" to newTeacher.id,
            "teacherName" to newTeacher.name
        )
        db.collection("students").document(studentId).update(updates)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Student moved successfully.", Toast.LENGTH_SHORT).show()
                teacherDataViewModel.notifyStudentDataChanged()
            }
            .addOnFailureListener {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error moving student.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun loadStudentsForClass() {
        if (currentTeacherId.isNullOrEmpty() || !isAdded) {
            Log.w(TAG, "loadStudentsForClass skipped: conditions not met.")
            _swipeRefreshLayout?.isRefreshing = false
            return
        }

        Log.d(TAG, "Executing loadStudentsForClass for teacher ID: $currentTeacherId")

        if (swipeRefreshLayout.isRefreshing == false) {
            progressBar.visibility = View.VISIBLE
        }
        tvNoStudents.visibility = View.GONE
        recyclerViewClassStudents.visibility = View.GONE

        db.collection("students")
            .whereEqualTo("teacherId", currentTeacherId)
            .whereEqualTo("isActive", true)
            .orderBy("studentName")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!isAdded) return@addOnSuccessListener

                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false

                val studentList = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(StudentDetailsItem::class.java)?.copy(id = doc.id)
                }

                classStudentsAdapter.updateData(studentList)
                Log.d(TAG, "Successfully loaded ${studentList.size} students.")

                if (studentList.isNotEmpty()) {
                    recyclerViewClassStudents.visibility = View.VISIBLE
                    tvNoStudents.visibility = View.GONE
                } else {
                    recyclerViewClassStudents.visibility = View.GONE
                    tvNoStudents.text = "No active students in this class."
                    tvNoStudents.visibility = View.VISIBLE
                }
                searchViewStudents.setQuery("", false)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener

                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false

                tvNoStudents.text = "Error loading students."
                tvNoStudents.visibility = View.VISIBLE
                classStudentsAdapter.updateData(emptyList())
                Log.e(TAG, "Error loading students. Check Firestore index/rules.", e)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}