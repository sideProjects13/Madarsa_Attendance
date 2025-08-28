package com.example.madarsa_attendance

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class InactiveStudentsFragment : Fragment() {

    private companion object {
        const val TAG = "InactiveStudentsFrag"
    }


    private var _recyclerView: RecyclerView? = null
    private val recyclerView get() = _recyclerView!!
    private var _adapter: InactiveStudentAdapter? = null
    private val adapter get() = _adapter!!
    private var _progressBar: ProgressBar? = null
    private val progressBar get() = _progressBar!!
    private var _tvNoInactive: TextView? = null
    private val tvNoInactive get() = _tvNoInactive!!

    private lateinit var db: FirebaseFirestore
    private lateinit var teacherDataViewModel: TeacherDataViewModel // Shared ViewModel
    private var currentOrganizationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FirebaseFirestore.getInstance()
        teacherDataViewModel = ViewModelProvider(requireActivity())[TeacherDataViewModel::class.java]
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(requireContext())

        if (currentOrganizationId == null) {
            Toast.makeText(context, "Organization information missing. Please log in.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_inactive_students, container, false)

        _recyclerView = view.findViewById(R.id.rv_inactive_students)
        _progressBar = view.findViewById(R.id.progress_bar_inactive)
        _tvNoInactive = view.findViewById(R.id.tv_no_inactive_students)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setupRecyclerView()
        loadInactiveStudents() // Load students immediately when view is created
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _recyclerView?.adapter = null
        _recyclerView = null
        _adapter = null
        _progressBar = null
        _tvNoInactive = null
        Log.d(TAG, "onDestroyView: Views nulled.")
    }

    private fun setupRecyclerView() {
        _adapter = InactiveStudentAdapter { student ->
            confirmReactivateStudent(student)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        Log.d(TAG, "RecyclerView setup complete.")
    }

    private fun loadInactiveStudents() {
        if (!isAdded || currentOrganizationId == null) {
            Log.w(TAG, "loadInactiveStudents skipped: fragment not added or organization ID missing.")
            return
        }

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvNoInactive.visibility = View.GONE
        Log.d(TAG, "Loading all inactive students for organization ID: $currentOrganizationId...")

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students")
            .whereEqualTo("isActive", false)
            .orderBy("teacherName", Query.Direction.ASCENDING)
            .orderBy("studentName", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!isAdded) return@addOnSuccessListener

                progressBar.visibility = View.GONE
                if (querySnapshot.isEmpty) {
                    tvNoInactive.visibility = View.VISIBLE
                    tvNoInactive.text = "No inactive students found across all classes."
                    adapter.submitList(emptyList())
                    Log.d(TAG, "No inactive students found for organization ID: $currentOrganizationId.")
                } else {
                    val students = querySnapshot.documents.mapNotNull { doc ->
                        doc.toObject(StudentDetailsItem::class.java)?.copy(id = doc.id)
                    }
                    adapter.submitList(students)
                    recyclerView.visibility = View.VISIBLE
                    Log.d(TAG, "Successfully loaded ${students.size} inactive students for organization ID: $currentOrganizationId.")
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener

                progressBar.visibility = View.GONE
                tvNoInactive.text = "Error loading students."
                tvNoInactive.visibility = View.VISIBLE
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error loading inactive students for organization ID: $currentOrganizationId. CHECK FIRESTORE INDEXES!", e)
            }
    }

    private fun confirmReactivateStudent(student: StudentDetailsItem) {
        if (!isAdded || context == null || currentOrganizationId == null) return
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Reactivate Student")
            .setMessage("Are you sure you want to reactivate ${student.studentName}? They will appear in the active class list for ${student.teacherName}.")
            .setPositiveButton("Reactivate") { _, _ ->
                reactivateStudentInFirestore(student)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reactivateStudentInFirestore(student: StudentDetailsItem) {
        if (!isAdded || currentOrganizationId == null) return
        progressBar.visibility = View.VISIBLE

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").document(student.id)
            .update("isActive", true)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener

                // --- CHANGE ---
                StatusDialogFragment.newInstance(true, "${student.studentName} has been reactivated!")
                    .show(parentFragmentManager, "successDialog")

                // Notify other parts of the app that data has changed
                teacherDataViewModel.notifyStudentDataChanged()
                requireActivity().setResult(Activity.RESULT_OK)

                // Reload the list to remove the reactivated student from this screen
                loadInactiveStudents()
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                // --- CHANGE ---
                StatusDialogFragment.newInstance(false, "Reactivation Failed")
                    .show(parentFragmentManager, "failureDialog")
                Log.e(TAG, "Failed to reactivate student ${student.id}", e)
            }
    }
}