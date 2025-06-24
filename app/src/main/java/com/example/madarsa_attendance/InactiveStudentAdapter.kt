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
import com.google.android.material.button.MaterialButton

// Adapter takes a lambda function to handle clicks, passing the student item back to the Activity
class InactiveStudentAdapter(
    private val onReactivateClick: (StudentDetailsItem) -> Unit
) : ListAdapter<StudentDetailsItem, InactiveStudentAdapter.InactiveViewHolder>(StudentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InactiveViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inactive_student, parent, false) // Use a dedicated layout for this
        return InactiveViewHolder(view)
    }

    override fun onBindViewHolder(holder: InactiveViewHolder, position: Int) {
        val student = getItem(position)
        holder.bind(student, onReactivateClick)
    }

    class InactiveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_inactive_student_name)
        private val parentInfoTextView: TextView = itemView.findViewById(R.id.tv_inactive_parent_info)
        private val profileImageView: ImageView = itemView.findViewById(R.id.iv_inactive_student_icon)
        private val reactivateButton: MaterialButton = itemView.findViewById(R.id.btn_reactivate)

        fun bind(student: StudentDetailsItem, onReactivateClick: (StudentDetailsItem) -> Unit) {
            nameTextView.text = student.studentName
            parentInfoTextView.text = "Parent: ${student.parentName ?: "N/A"}"

            if (!student.profileImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(student.profileImageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.student)
                    .error(R.drawable.student)
                    .into(profileImageView)
            } else {
                profileImageView.setImageResource(R.drawable.student)
            }

            // Set the click listener for the button, passing the specific student item
            reactivateButton.setOnClickListener {
                onReactivateClick(student)
            }
        }
    }

    // DiffUtil helps ListAdapter determine which items have changed, improving performance
    class StudentDiffCallback : DiffUtil.ItemCallback<StudentDetailsItem>() {
        override fun areItemsTheSame(oldItem: StudentDetailsItem, newItem: StudentDetailsItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StudentDetailsItem, newItem: StudentDetailsItem): Boolean {
            return oldItem == newItem
        }
    }
}