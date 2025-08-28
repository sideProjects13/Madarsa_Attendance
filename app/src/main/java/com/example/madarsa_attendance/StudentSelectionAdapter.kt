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

class StudentSelectionAdapter(
    private val onStudentClick: (StudentDetailsItem) -> Unit
) : ListAdapter<StudentDetailsItem, StudentSelectionAdapter.StudentViewHolder>(StudentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_selection, parent, false)
        return StudentViewHolder(view, onStudentClick)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StudentViewHolder(
        itemView: View,
        private val onStudentClick: (StudentDetailsItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val studentName: TextView = itemView.findViewById(R.id.tv_student_name_selection_item)
        private val teacherName: TextView = itemView.findViewById(R.id.tv_teacher_name_selection_item)

        fun bind(student: StudentDetailsItem) {
            studentName.text = student.studentName
            teacherName.text = "Class: ${student.teacherName ?: "N/A"}"
            Glide.with(itemView.context)
                .load(student.profileImageUrl)
                .placeholder(R.drawable.student)
                .error(R.drawable.student)
//                .into(studentImage)
            itemView.setOnClickListener { onStudentClick(student) }
        }
    }
}

class StudentDiffCallback : DiffUtil.ItemCallback<StudentDetailsItem>() {
    override fun areItemsTheSame(oldItem: StudentDetailsItem, newItem: StudentDetailsItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: StudentDetailsItem, newItem: StudentDetailsItem): Boolean {
        return oldItem == newItem
    }
}