package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Assuming Glide for image loading

class DashboardStudentAdapter : ListAdapter<DashboardStudentItem, DashboardStudentAdapter.DashboardStudentViewHolder>(DashboardStudentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DashboardStudentViewHolder {
        // --- MODIFIED: Inflate the new layout for absentee students ---
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_absentee_student, parent, false)
        return DashboardStudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DashboardStudentViewHolder, position: Int) {
        val student = getItem(position)
        holder.bind(student)
    }

    class DashboardStudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // --- MODIFIED: Find new view IDs ---
        private val studentNameTextView: TextView = itemView.findViewById(R.id.tv_absentee_student_name)
        private val teacherNameTextView: TextView = itemView.findViewById(R.id.tv_absentee_teacher_name)
        private val studentImageView: ImageView = itemView.findViewById(R.id.iv_absentee_student_image)

        fun bind(student: DashboardStudentItem) {
            studentNameTextView.text = student.name
            // Display teacher name if available, otherwise hide the TextView
            if (!student.subtitle.isNullOrBlank()) {
                teacherNameTextView.text = "Class: ${student.subtitle}"
                teacherNameTextView.visibility = View.VISIBLE
            } else {
                teacherNameTextView.visibility = View.GONE
            }

            // Load image using Glide
            Glide.with(itemView.context)
                .load(student.imageUrl)
                .placeholder(R.drawable.student) // Default icon
                .error(R.drawable.student) // Error icon
                .into(studentImageView)
        }
    }
}

class DashboardStudentDiffCallback : DiffUtil.ItemCallback<DashboardStudentItem>() {
    override fun areItemsTheSame(oldItem: DashboardStudentItem, newItem: DashboardStudentItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DashboardStudentItem, newItem: DashboardStudentItem): Boolean {
        return oldItem == newItem
    }
}