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
    // If you add a menu click listener, you'd add it here
) : RecyclerView.Adapter<TeacherAdapter.TeacherViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        // CORRECTED: Inflate the correct layout file for managing teachers
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher_manage, parent, false)
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
        // CORRECTED: Use the correct View IDs from item_teacher_manage.xml
        private val teacherNameTextView: TextView = itemView.findViewById(R.id.tvTeacherNameManageItem)
        private val teacherSubtitleTextView: TextView = itemView.findViewById(R.id.tvTeacherSubtitleManageItem)
        private val teacherIconImageView: ImageView = itemView.findViewById(R.id.ivTeacherIconManageItem)
        // You can also get the menu icon if you need to set a listener on it
        // private val menuIcon: ImageView = itemView.findViewById(R.id.ivTeacherItemMenu)

        fun bind(teacher: TeacherSpinnerItem, onItemClick: (TeacherSpinnerItem) -> Unit) {
            teacherNameTextView.text = teacher.name
            // You can customize this subtitle as needed
            teacherSubtitleTextView.text = "Tap for class options"

            if (!teacher.profileImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(teacher.profileImageUrl)
                    .placeholder(R.drawable.molana)
                    .error(R.drawable.molana)
                    .circleCrop()
                    .into(teacherIconImageView)
            } else {
                teacherIconImageView.setImageResource(R.drawable.molana)
            }

            itemView.setOnClickListener { onItemClick(teacher) }
            // Example for menu click: menuIcon.setOnClickListener { onMenuClick(teacher) }
        }
    }
}