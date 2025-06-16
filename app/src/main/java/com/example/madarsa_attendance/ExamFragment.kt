package com.example.madarsa_attendance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ExamFragment : Fragment() {

    private val viewModel: ExamViewModel by viewModels()
    private lateinit var examAdapter: ExamAdapter

    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoExams: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_exam, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewExams)
        fab = view.findViewById(R.id.fabAddExam)
        progressBar = view.findViewById(R.id.progressBarExams)
        tvNoExams = view.findViewById(R.id.tvNoExams)

        setupRecyclerView()
        setupFab()
        observeViewModel()

        return view
    }

    private fun setupRecyclerView() {
        examAdapter = ExamAdapter(emptyList())
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = examAdapter
        }

        // Handle delete click
        examAdapter.onDeleteClick = { exam ->
            // Show confirmation dialog before deleting
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Exam")
                .setMessage("Are you sure you want to delete '${exam.name}'?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteExam(exam.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupFab() {
        fab.setOnClickListener {
            showAddExamDialog()
        }
    }

    private fun showAddExamDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_exam, null)
        val editText = dialogView.findViewById<EditText>(R.id.etExamName)

        AlertDialog.Builder(requireContext())
            .setTitle("Add New Exam")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val examName = editText.text.toString().trim()
                viewModel.addExam(examName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.exams.observe(viewLifecycleOwner) { exams ->
            if (exams.isEmpty()) {
                recyclerView.visibility = View.GONE
                tvNoExams.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                tvNoExams.visibility = View.GONE
            }
            examAdapter.updateData(exams)
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown() // Reset after showing
            }
        }
    }
}