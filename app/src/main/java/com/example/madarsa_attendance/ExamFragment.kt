package com.example.madarsa_attendance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ExamFragment : Fragment() {

    private val viewModel: ExamViewModel by viewModels()
    private lateinit var adapter: ExamAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddExam: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoExams: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_exam, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewExams)
        fabAddExam = view.findViewById(R.id.fabAddExam)
        progressBar = view.findViewById(R.id.progressBarExams)
        tvNoExams = view.findViewById(R.id.tvNoExams)

        setupRecyclerView()
        setupObservers()

        fabAddExam.setOnClickListener {
            showAddExamDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = ExamAdapter { exam ->
            confirmDeleteExam(exam)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.exams.observe(viewLifecycleOwner) { exams ->
            adapter.submitList(exams)
            if (exams.isEmpty()) {
                tvNoExams.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                tvNoExams.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }

        // This is the corrected part: Observe 'operationStatus' instead of 'toastMessage'
        viewModel.operationStatus.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { (isSuccess, message) ->
                StatusDialogFragment.newInstance(isSuccess, message)
                    .show(parentFragmentManager, "statusDialog")
            }
        }
    }

    private fun showAddExamDialog() {
        // Using a simple EditText in an AlertDialog
        val editText = EditText(requireContext()).apply {
            hint = "e.g., Mid-Term Exam"
            setPadding(60, 40, 60, 40) // Add some padding
        }

        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Add New Exam")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val examName = editText.text.toString().trim()
                viewModel.addExam(examName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteExam(exam: Exam) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Exam")
            .setMessage("Are you sure you want to delete '${exam.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteExam(exam.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}


// --- Adapter Class for the RecyclerView ---

class ExamAdapter(
    private val onDeleteClick: (Exam) -> Unit
) : RecyclerView.Adapter<ExamAdapter.ExamViewHolder>() {

    private var exams: List<Exam> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exam, parent, false)
        return ExamViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        holder.bind(exams[position])
    }

    override fun getItemCount(): Int = exams.size

    fun submitList(newExams: List<Exam>) {
        exams = newExams
        notifyDataSetChanged()
    }

    inner class ExamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvExamName: TextView = itemView.findViewById(R.id.tvExamName)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteExam)

        fun bind(exam: Exam) {
            tvExamName.text = exam.name
            btnDelete.setOnClickListener {
                onDeleteClick(exam)
            }
        }
    }
}