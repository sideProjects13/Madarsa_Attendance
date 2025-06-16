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
import androidx.appcompat.widget.SearchView // For search
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
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

    private lateinit var db: FirebaseFirestore
    private var currentTeacherId: String? = null
    private var currentTeacherName: String? = null
    // studentDisplayList is managed by adapter via updateData, adapter holds the source of truth for display

    private val fabAddStudentIntroHandler = Handler(Looper.getMainLooper())
    private var introExtendAddStudentRunnable: Runnable? = null
    private var introShrinkAddStudentRunnable: Runnable? = null

    private var isDataLoadedInitially = false
    private lateinit var teacherDataViewModel: TeacherDataViewModel

    private val studentActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!isAdded) return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Student action (add/edit) returned OK.")
            isDataLoadedInitially = false // Force reload
            teacherDataViewModel.notifyStudentDataChanged()
        }
        _fabAddStudentToClass?.shrink()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // <<< SUPER CALL
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

        if (_fabAddStudentToClass == null) Log.e(TAG, "fabAddStudentToClassFrag NOT FOUND!")
        if (_searchViewStudents == null) Log.e(TAG, "searchViewStudents NOT FOUND!")
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState) // <<< SUPER CALL
        Log.d(TAG, "onViewCreated - Initializing UI.")

        if (currentTeacherId == null) {
            Toast.makeText(context, "Teacher info missing.", Toast.LENGTH_LONG).show(); return
        }
        setupRecyclerView()
        setupFabInteraction()
        setupSearchView()

        _fabAddStudentToClass?.shrink()
        if (savedInstanceState == null) {
            _fabAddStudentToClass?.let { startAddStudentFabIntroAnimation(it) }
        }
        // Data will be loaded in onResume if !isDataLoadedInitially
    }

    override fun onResume() {
        super.onResume() // <<< SUPER CALL
        Log.d(TAG, "onResume - ManageClassFragment, isDataLoaded: $isDataLoadedInitially")
        if (currentTeacherId != null && !isDataLoadedInitially) {
            loadStudentsForClass()
        }
        _fabAddStudentToClass?.let { if (it.isExtended && introExtendAddStudentRunnable == null) it.shrink() }
    }

    private fun setupSearchView() {
        _searchViewStudents?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                _classStudentsAdapter?.filter(query)
                _searchViewStudents?.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                _classStudentsAdapter?.filter(newText)
                return true
            }
        })
        _searchViewStudents?.setOnCloseListener {
            _searchViewStudents?.setQuery("", false)
            _classStudentsAdapter?.filter("")
            true
        }
    }

    private fun setupFabInteraction() {
        _fabAddStudentToClass?.setOnClickListener {
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

    override fun onPause() {
        super.onPause() // <<< SUPER CALL
        cancelAddStudentFabIntroAnimation()
        Log.d(TAG, "onPause - ManageClassFragment")
    }

    override fun onDestroyView() {
        super.onDestroyView() // <<< SUPER CALL
        cancelAddStudentFabIntroAnimation()
        _recyclerViewClassStudents = null; _classStudentsAdapter = null
        _fabAddStudentToClass = null;  _progressBar = null; _tvNoStudents = null
        _searchViewStudents?.setOnQueryTextListener(null)
        _searchViewStudents = null
        Log.d(TAG, "onDestroyView - ManageClassFragment")
    }

    private fun setupRecyclerView() {
        if (!isAdded || context == null || _recyclerViewClassStudents == null) return
        _classStudentsAdapter = ClassStudentsAdapter(ArrayList()) { selectedStudent ->
            showStudentOptionsDialog(selectedStudent)
        }
        recyclerViewClassStudents?.layoutManager = LinearLayoutManager(context)
        recyclerViewClassStudents?.adapter = _classStudentsAdapter
    }

    private fun showStudentOptionsDialog(student: StudentDetailsItem) {
        if (!isAdded || context == null) return
        val options = arrayOf("Edit Student", "Delete Student", "Move to Another Class", "View Monthly Attendance") // Added new option
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Student: ${student.studentName}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Edit
                        val intent = Intent(activity, EditStudentActivity::class.java).apply {
                            putExtra("STUDENT_ID", student.id)
                            putExtra("TEACHER_NAME", currentTeacherName)
                            putExtra("STUDENT_PROFILE_IMAGE_URL", student.profileImageUrl) // Pass image for EditStudentActivity
                        }
                        studentActionLauncher.launch(intent)
                    }
                    1 -> confirmDeleteStudent(student) // Delete
                    2 -> showMoveStudentDialog(student) // Move
                    3 -> { // View Monthly Attendance
                        Log.d(TAG, "View Monthly Attendance for student: ${student.id}")
                        val calendar = Calendar.getInstance()
                        val intent = Intent(activity, StudentMonthlyAttendanceActivity::class.java).apply {
                            putExtra("STUDENT_ID", student.id)
                            putExtra("STUDENT_NAME", student.studentName)
                            putExtra("TEACHER_ID", currentTeacherId)
                            putExtra("TARGET_YEAR", calendar.get(Calendar.YEAR))
                            putExtra("TARGET_MONTH", calendar.get(Calendar.MONTH)) // 0-indexed
                        }
                        startActivity(intent)
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeleteStudent(student: StudentDetailsItem) {
        if (!isAdded || context == null) return
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Student")
            .setMessage("Are you sure you want to delete ${student.studentName}?")
            .setPositiveButton("Delete") { _, _ -> deleteStudentFromFirestore(student.id) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteStudentFromFirestore(studentId: String) {
        if (!isAdded || _progressBar == null) return
        progressBar?.visibility = View.VISIBLE
        db.collection("students").document(studentId).delete()
            .addOnSuccessListener { if (!isAdded) return@addOnSuccessListener
                progressBar?.visibility = View.GONE
                Toast.makeText(context, "Student deleted", Toast.LENGTH_SHORT).show()
                isDataLoadedInitially = false;
                teacherDataViewModel.notifyStudentDataChanged()
                loadStudentsForClass()
            }
            .addOnFailureListener { e -> if (!isAdded) return@addOnFailureListener
                progressBar?.visibility = View.GONE
                Toast.makeText(context, "Error deleting: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showMoveStudentDialog(studentToMove: StudentDetailsItem) {
        if (!isAdded || context == null || _progressBar == null) return
        progressBar?.visibility = View.VISIBLE
        db.collection("teachers").orderBy("teacherName").get()
            .addOnSuccessListener { teacherSnap -> if (!isAdded) return@addOnSuccessListener
                progressBar?.visibility = View.GONE
                val teachers = mutableListOf<TeacherSpinnerItem>()
                val names = mutableListOf<String>()
                teacherSnap.documents.filter { it.id != currentTeacherId }.forEach {
                    teachers.add(TeacherSpinnerItem(it.id, it.getString("teacherName")?:"N/A", it.getString("profileImageUrl")))
                    names.add(it.getString("teacherName")?:"N/A")
                }
                if (names.isEmpty()) { Toast.makeText(context, "No other classes.", Toast.LENGTH_LONG).show(); return@addOnSuccessListener }
                AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
                    .setTitle("Move ${studentToMove.studentName} to:").setItems(names.toTypedArray()) { _, i ->
                        moveStudentToNewClass(studentToMove.id, teachers[i])
                    }.setNegativeButton("Cancel", null).show()
            }.addOnFailureListener { if(isAdded) _progressBar?.visibility = View.GONE }
    }

    private fun moveStudentToNewClass(studentId: String, newTeacher: TeacherSpinnerItem) {
        if (!isAdded || _progressBar == null) return
        progressBar?.visibility = View.VISIBLE
        val updates = HashMap<String, Any?>()
        updates["teacherId"] = newTeacher.id
        updates["teacherName"] = newTeacher.name
        db.collection("students").document(studentId).update(updates)
            .addOnSuccessListener { if (!isAdded) return@addOnSuccessListener
                progressBar?.visibility = View.GONE
                Toast.makeText(context, "Student moved.", Toast.LENGTH_SHORT).show()
                isDataLoadedInitially = false;
                teacherDataViewModel.notifyStudentDataChanged()
                loadStudentsForClass()
            }.addOnFailureListener { if(isAdded) _progressBar?.visibility = View.GONE; Toast.makeText(context,"Error moving.", Toast.LENGTH_SHORT).show()}
    }

    private fun loadStudentsForClass() {
        if (currentTeacherId.isNullOrEmpty() || !isAdded || _progressBar == null || _tvNoStudents == null || _recyclerViewClassStudents == null || _classStudentsAdapter == null) {
            if(isAdded && _tvNoStudents != null && _classStudentsAdapter != null && (_classStudentsAdapter?.itemCount ?: 0) == 0 ) {
                _progressBar?.visibility = View.GONE
                _tvNoStudents?.text = "No students in this class."
                _tvNoStudents?.visibility = View.VISIBLE
                _recyclerViewClassStudents?.visibility = View.GONE
                _classStudentsAdapter?.updateData(emptyList())
            }
            return
        }
        progressBar?.visibility = View.VISIBLE
        tvNoStudents?.visibility = View.GONE
        recyclerViewClassStudents?.visibility = View.GONE

        db.collection("students").whereEqualTo("teacherId", currentTeacherId).orderBy("studentName").get()
            .addOnSuccessListener { querySnapshot ->
                if(!isAdded || _classStudentsAdapter == null) return@addOnSuccessListener
                progressBar?.visibility = View.GONE
                val tempList = mutableListOf<StudentDetailsItem>()
                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    querySnapshot.documents.forEach { doc ->
                        tempList.add(StudentDetailsItem(doc.id, doc.getString("studentName")?:"N/A", doc.getString("parentName"), doc.getString("parentMobileNumber"), doc.getString("profileImageUrl")))
                    }
                    recyclerViewClassStudents?.visibility = View.VISIBLE
                    tvNoStudents?.visibility = View.GONE
                } else {
                    tvNoStudents?.text = "No students in this class."
                    tvNoStudents?.visibility = View.VISIBLE
                    recyclerViewClassStudents?.visibility = View.GONE
                }
                classStudentsAdapter?.updateData(tempList)
                _searchViewStudents?.setQuery("", false)
                isDataLoadedInitially = true
            }
            .addOnFailureListener { e ->
                if(!isAdded) return@addOnFailureListener
                progressBar?.visibility = View.GONE
                tvNoStudents?.text = "Error loading students."
                tvNoStudents?.visibility = View.VISIBLE
                if(_classStudentsAdapter != null) _classStudentsAdapter?.updateData(emptyList())
                Log.e(TAG, "Error loading students", e)
                isDataLoadedInitially = false
            }
    }
}