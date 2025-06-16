package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ExamAdapter(
    private var exams: List<Exam>
) : RecyclerView.Adapter<ExamAdapter.ExamViewHolder>() {

    // Lambda to be triggered when the delete button is clicked
    var onDeleteClick: ((Exam) -> Unit)? = null

    class ExamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val examName: TextView = itemView.findViewById(R.id.tvExamName)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btnDeleteExam)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exam, parent, false)
        return ExamViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        val exam = exams[position]
        holder.examName.text = exam.name
        holder.deleteButton.setOnClickListener {
            onDeleteClick?.invoke(exam)
        }
    }

    override fun getItemCount() = exams.size

    fun updateData(newExams: List<Exam>) {
        this.exams = newExams
        notifyDataSetChanged()
    }
}