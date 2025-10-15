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

// The constructor now correctly takes the unique DiffUtil.ItemCallback class
class StudentSelectionAdapter(
    private val onStudentClick: (StudentDetailsItem) -> Unit
) : ListAdapter<StudentDetailsItem, StudentSelectionAdapter.StudentViewHolder>(StudentDetailsListDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_selection, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = getItem(position)
        holder.bind(student)
        // Set the click listener here for simplicity
        holder.itemView.setOnClickListener {
            onStudentClick(student)
        }
    }

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // These IDs now match the updated layout file
        private val studentName: TextView = itemView.findViewById(R.id.tv_student_name_selection)
        private val teacherName: TextView = itemView.findViewById(R.id.tv_student_subtitle_selection)
        private val studentImage: ImageView = itemView.findViewById(R.id.iv_student_icon_selection)

        fun bind(student: StudentDetailsItem) {
            studentName.text = student.studentName
            teacherName.text = "Class: ${student.teacherName ?: "N/A"}"

            // The Glide code now works because the ImageView exists in the layout
            Glide.with(itemView.context)
                .load(student.profileImageUrl)
                .circleCrop()
                .placeholder(R.drawable.student)
                .error(R.drawable.student)
                .into(studentImage)
        }
    }
}

// This class now has a unique name to avoid errors
class StudentDetailsListDiffCallback : DiffUtil.ItemCallback<StudentDetailsItem>() {
    override fun areItemsTheSame(oldItem: StudentDetailsItem, newItem: StudentDetailsItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: StudentDetailsItem, newItem: StudentDetailsItem): Boolean {
        return oldItem == newItem
    }
}