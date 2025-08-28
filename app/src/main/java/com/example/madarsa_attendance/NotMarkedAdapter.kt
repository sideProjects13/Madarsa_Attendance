package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotMarkedAdapter(
    private val teachers: List<Teacher>,
    private val onItemClick: (Teacher) -> Unit
) : RecyclerView.Adapter<NotMarkedAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val teacherName: TextView = itemView.findViewById(R.id.tv_teacher_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_not_marked_class, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val teacher = teachers[position]
        holder.teacherName.text = teacher.teacherName
        holder.itemView.setOnClickListener {
            onItemClick(teacher)
        }
    }

    override fun getItemCount(): Int = teachers.size
}