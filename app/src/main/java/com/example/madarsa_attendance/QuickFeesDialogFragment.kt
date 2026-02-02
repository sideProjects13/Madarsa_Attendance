package com.example.madarsa_attendance

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class QuickFeesDialogFragment : DialogFragment() {

    private val viewModel: DashboardViewModel by activityViewModels()
    // --- THIS IS THE FIX: Use your existing TeacherDataViewModel ---
    private val sharedViewModel: TeacherDataViewModel by activityViewModels()
    private val TAG = "QuickFeesDialog"

    companion object {
        private const val ARG_TITLE = "dialog_title"
        private const val ARG_STUDENT_ACTION = "student_action_type"

        @JvmStatic
        fun newInstance(title: String, actionType: StudentAction): QuickFeesDialogFragment {
            return QuickFeesDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putSerializable(ARG_STUDENT_ACTION, actionType)
                }
            }
        }
    }

    private var dialogTitle: String = "Select Student"
    private var studentAction: StudentAction? = null

    private lateinit var searchEditText: TextInputEditText
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyMessage: TextView
    private lateinit var studentSelectionAdapter: StudentSelectionAdapter

    private var searchJob: Job? = null

    interface FeeStudentSelectionListener {
        fun onFeeStudentSelected(student: StudentDetailsItem, action: StudentAction?)
        fun onFeesReportGenerated(teacherId: String, teacherName: String, reportType: String, month: Int?, year: Int?)
    }
    private var listener: FeeStudentSelectionListener? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_App_Dialog_FullScreen)

        arguments?.let {
            dialogTitle = it.getString(ARG_TITLE, "Select Student")
            studentAction = it.getSerializable(ARG_STUDENT_ACTION) as? StudentAction
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_quick_fees, container, false)

        searchEditText = view.findViewById(R.id.etStudentSearch)
        searchResultsRecyclerView = view.findViewById(R.id.rvStudentSearchResults)
        progressBar = view.findViewById(R.id.progressBarFeesDialog)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessageDialog)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle(dialogTitle)

        if (context is FeeStudentSelectionListener) {
            listener = context as FeeStudentSelectionListener
        }

        setupRecyclerView()
        setupSearch()
        setupObservers()

        viewModel.fetchStudentListForSearch(forceRefresh = true)
    }

    private fun setupRecyclerView() {
        studentSelectionAdapter = StudentSelectionAdapter { student ->
            listener?.onFeeStudentSelected(student, studentAction)
            dismiss()
        }
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        searchResultsRecyclerView.adapter = studentSelectionAdapter
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
                    filterList(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupObservers() {
        viewModel.isStudentListLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            searchEditText.isEnabled = !isLoading
            if (isLoading) {
                tvEmptyMessage.text = "Loading students..."
                tvEmptyMessage.visibility = View.VISIBLE
                searchResultsRecyclerView.visibility = View.GONE
            }
        }

        viewModel.allStudentsList.observe(viewLifecycleOwner) { students ->
            if (viewModel.isStudentListLoading.value == false) {
                if (students.isNullOrEmpty()) {
                    tvEmptyMessage.text = "No active students found."
                    tvEmptyMessage.visibility = View.VISIBLE
                    searchResultsRecyclerView.visibility = View.GONE
                } else {
                    tvEmptyMessage.text = "Start typing to search for a student."
                    tvEmptyMessage.visibility = View.VISIBLE
                    searchResultsRecyclerView.visibility = View.GONE
                    filterList(searchEditText.text.toString())
                }
            }
        }

        // --- THIS IS THE CORRECTED OBSERVER ---
        sharedViewModel.studentsDataMightHaveChanged.observe(viewLifecycleOwner) { event ->
            // We get the content of the Event wrapper
            val shouldRefresh = event.getContentIfNotHandled()
            // And check if it's not null (meaning it's a fresh event)
            if (shouldRefresh != null) {
                viewModel.fetchStudentListForSearch(forceRefresh = true)
            }
        }
    }

    private fun filterList(query: String) {
        val allStudents = viewModel.allStudentsList.value ?: emptyList()

        if (query.isBlank()) {
            studentSelectionAdapter.submitList(emptyList())
            if (allStudents.isNotEmpty()) {
                tvEmptyMessage.text = "Start typing to search for a student."
                tvEmptyMessage.visibility = View.VISIBLE
                searchResultsRecyclerView.visibility = View.GONE
            }
            return
        }

        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        val filtered = allStudents.filter {
            it.studentName.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    (it.regNo?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true)
        }
        studentSelectionAdapter.submitList(filtered)

        if (filtered.isEmpty()) {
            tvEmptyMessage.text = "No students found matching '$query'."
            tvEmptyMessage.visibility = View.VISIBLE
            searchResultsRecyclerView.visibility = View.GONE
        } else {
            tvEmptyMessage.visibility = View.GONE
            searchResultsRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FeeStudentSelectionListener) {
            listener = context
        } else {
            val parentFrag = parentFragment
            if (parentFrag is FeeStudentSelectionListener) {
                listener = parentFrag
            } else {
                Log.e(TAG, "${context.javaClass.simpleName} or ${parentFrag?.javaClass?.simpleName} must implement FeeStudentSelectionListener for QuickFeesDialogFragment.")
                listener = null
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}