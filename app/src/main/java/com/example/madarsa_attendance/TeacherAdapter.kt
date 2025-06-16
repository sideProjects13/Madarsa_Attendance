package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class TeacherAdapter(
    private var teachers: List<TeacherSpinnerItem>,
    private val onItemClick: (TeacherSpinnerItem) -> Unit
) : RecyclerView.Adapter<TeacherAdapter.TeacherViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher_select, parent, false) // Layout for SelectClassActivity items
        return TeacherViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = teachers[position]
        holder.bind(teacher, onItemClick)
    }

    override fun getItemCount(): Int = teachers.size

    fun updateData(newTeachers: List<TeacherSpinnerItem>) {
        teachers = newTeachers
        notifyDataSetChanged()
    }

    class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Make sure these IDs match your item_teacher_select.xml
        private val teacherNameTextView: TextView = itemView.findViewById(R.id.tvTeacherNameItem)
        private val teacherSubtitleTextView: TextView = itemView.findViewById(R.id.tvClassSubtitleItem)
        private val teacherIconImageView: ImageView = itemView.findViewById(R.id.ivTeacherIconItem)

        fun bind(teacher: TeacherSpinnerItem, onItemClick: (TeacherSpinnerItem) -> Unit) {
            teacherNameTextView.text = teacher.name
            teacherSubtitleTextView.text = "Tap to take attendance" // Default subtitle for this screen

            if (!teacher.profileImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(teacher.profileImageUrl)
                    .placeholder(R.drawable.person) // Or your specific placeholder like R.drawable.molana
                    .error(R.drawable.person)       // Or R.drawable.molana
                    .circleCrop()
                    .into(teacherIconImageView)
            } else {
                teacherIconImageView.setImageResource(R.drawable.molana) // Default if no image
            }

            itemView.setOnClickListener { onItemClick(teacher) }
        }
    }
}