package com.example.madarsa_attendance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TeacherSelectionDialogFragment : DialogFragment() {

    private val viewModel: TeacherDataViewModel by viewModels()
    private lateinit var teacherAdapter: TeacherSelectionAdapter

    companion object {
        private const val ARG_TITLE = "dialog_title"

        fun newInstance(title: String): TeacherSelectionDialogFragment {
            val fragment = TeacherSelectionDialogFragment()
            val args = Bundle()
            args.putString(ARG_TITLE, title)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_select_teacher, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleTextView: TextView = view.findViewById(R.id.dialog_title)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val recyclerView: RecyclerView = view.findViewById(R.id.rvTeachers)

        val dialogTitle = arguments?.getString(ARG_TITLE) ?: "Select Teacher"
        titleTextView.text = dialogTitle

        teacherAdapter = TeacherSelectionAdapter { selectedTeacher ->
            (activity as? TeacherSelectionListener)?.onTeacherSelected(selectedTeacher)
            dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = teacherAdapter

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.teachersList.observe(viewLifecycleOwner) { teachers ->
            recyclerView.visibility = if (teachers.isNullOrEmpty()) View.GONE else View.VISIBLE
            teacherAdapter.submitList(teachers)
        }

        // --- CORRECTED: Call the function without passing context ---
        viewModel.fetchTeachers()
    }

    interface TeacherSelectionListener {
        fun onTeacherSelected(teacher: Teacher)
    }
}