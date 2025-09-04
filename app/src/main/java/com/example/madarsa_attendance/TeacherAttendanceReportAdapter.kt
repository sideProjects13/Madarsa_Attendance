package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// A new data class to hold the combined information for the report
data class TeacherWithAttendanceStatus(
    val teacher: Teacher,
    val status: String // "Present", "Absent", or "Not Marked"
)

class TeacherAttendanceReportAdapter(
    private var items: List<TeacherWithAttendanceStatus>
) : RecyclerView.Adapter<TeacherAttendanceReportAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_teacher_attendance_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<TeacherWithAttendanceStatus>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.iv_teacher_icon)
        private val name: TextView = itemView.findViewById(R.id.tv_teacher_name)
        private val status: TextView = itemView.findViewById(R.id.tv_attendance_status)

        fun bind(item: TeacherWithAttendanceStatus) {
            name.text = item.teacher.teacherName
            status.text = item.status

            Glide.with(itemView.context)
                .load(item.teacher.profileImageUrl)
                .circleCrop()
                .placeholder(R.drawable.molana)
                .error(R.drawable.molana)
                .into(icon)

            when (item.status) {
                "Present" -> {
                    status.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_text_white))
                    status.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.status_paid_green))
                }
                "Absent" -> {
                    status.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_text_white))
                    status.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.status_unpaid_red))
                }
                else -> { // "Not Marked"
                    status.setTextColor(ContextCompat.getColor(itemView.context, R.color.mono_palette_grey_secondary_text))
                    status.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.mono_palette_background_subtle))
                }
            }
        }
    }
}