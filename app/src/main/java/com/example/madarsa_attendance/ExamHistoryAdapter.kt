package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ExamHistoryAdapter(
    private var history: List<ExamHistoryItem>,
    private val listener: OnExamHistoryInteractionListener
) : RecyclerView.Adapter<ExamHistoryAdapter.ViewHolder>() {

    interface OnExamHistoryInteractionListener {
        fun onGenerateReportClick(item: ExamHistoryItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exam_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(history[position])
    }

    override fun getItemCount(): Int = history.size

    fun updateData(newHistory: List<ExamHistoryItem>) {
        this.history = newHistory
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val examName: TextView = itemView.findViewById(R.id.tv_history_exam_name)
        private val examDetails: TextView = itemView.findViewById(R.id.tv_history_exam_details)
        private val generateBtn: Button = itemView.findViewById(R.id.btn_generate_report)

        fun bind(item: ExamHistoryItem) {
            examName.text = item.examName
            examDetails.text = "${item.academicYear} | Class: ${item.teacherName}"
            generateBtn.setOnClickListener {
                listener.onGenerateReportClick(item)
            }
        }
    }
}