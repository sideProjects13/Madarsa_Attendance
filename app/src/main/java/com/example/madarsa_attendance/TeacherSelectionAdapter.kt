package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class TeacherSelectionAdapter(private val onTeacherSelected: (Teacher) -> Unit) :
    ListAdapter<Teacher, TeacherSelectionAdapter.TeacherViewHolder>(TeacherDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher_select, parent, false)
        return TeacherViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = getItem(position)
        holder.bind(teacher)
        holder.itemView.setOnClickListener {
            onTeacherSelected(teacher)
        }
    }

    class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.ivProfileImage)
        private val teacherName: TextView = itemView.findViewById(R.id.tvTeacherName)

        fun bind(teacher: Teacher) {
            teacherName.text = teacher.teacherName
            if (!teacher.profileImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(teacher.profileImageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.logo)
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.logo)
            }
        }
    }

    private class TeacherDiffCallback : DiffUtil.ItemCallback<Teacher>() {
        override fun areItemsTheSame(oldItem: Teacher, newItem: Teacher): Boolean {
            return oldItem.teacherId == newItem.teacherId
        }

        override fun areContentsTheSame(oldItem: Teacher, newItem: Teacher): Boolean {
            return oldItem == newItem
        }
    }
}