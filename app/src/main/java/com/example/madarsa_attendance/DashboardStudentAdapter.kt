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

class DashboardStudentAdapter : ListAdapter<DashboardStudentItem, DashboardStudentAdapter.StudentViewHolder>(StudentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dashboard_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProfile: ImageView = itemView.findViewById(R.id.iv_dashboard_student_profile)
        private val tvName: TextView = itemView.findViewById(R.id.tv_dashboard_student_name)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tv_dashboard_student_subtitle)

        fun bind(item: DashboardStudentItem) {
            tvName.text = item.name

            if (item.subtitle.isNullOrEmpty()) {
                tvSubtitle.visibility = View.GONE
            } else {
                tvSubtitle.visibility = View.VISIBLE
                tvSubtitle.text = item.subtitle
            }

            Glide.with(itemView.context)
                .load(item.imageUrl)
                .circleCrop()
                .placeholder(R.drawable.student) // Make sure you have a 'student.xml' or other placeholder drawable
                .error(R.drawable.student)
                .into(ivProfile)
        }
    }

    class StudentDiffCallback : DiffUtil.ItemCallback<DashboardStudentItem>() {
        override fun areItemsTheSame(oldItem: DashboardStudentItem, newItem: DashboardStudentItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DashboardStudentItem, newItem: DashboardStudentItem): Boolean {
            return oldItem == newItem
        }
    }
}