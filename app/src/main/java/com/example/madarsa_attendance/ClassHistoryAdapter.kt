package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ClassHistoryAdapter(private var history: List<ClassHistoryItem>) :
    RecyclerView.Adapter<ClassHistoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(history[position])
    }

    override fun getItemCount(): Int = history.size

    fun updateData(newHistory: List<ClassHistoryItem>) {
        this.history = newHistory
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val teacherName: TextView = itemView.findViewById(R.id.tv_history_teacher_name)
        private val academicYear: TextView = itemView.findViewById(R.id.tv_history_academic_year)
        private val duration: TextView = itemView.findViewById(R.id.tv_history_duration)

        fun bind(item: ClassHistoryItem) {
            teacherName.text = "Class: ${item.teacherName}"
            academicYear.text = "Year: ${item.academicYear}"
            duration.text = item.duration
        }
    }
}