package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.imageview.ShapeableImageView

// --- FIX #1: attendanceStatus is NO LONGER NULLABLE ---
// It will always be either "Present" or "Absent".
data class TeacherAttendanceItem(
    val id: String,
    val name: String,
    val profileImageUrl: String?,
    var attendanceStatus: String // Removed '?' and '= null'
)

class TeacherAttendanceAdapter(
    private var teachers: List<TeacherAttendanceItem>
) : RecyclerView.Adapter<TeacherAttendanceAdapter.TeacherViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_teacher_attendance, parent, false)
        return TeacherViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = teachers[position]
        holder.bind(teacher)
    }

    override fun getItemCount(): Int = teachers.size

    fun getTeachersList(): List<TeacherAttendanceItem> {
        return teachers
    }



    fun updateData(newTeachers: List<TeacherAttendanceItem>) {
        this.teachers = newTeachers
        notifyDataSetChanged()
    }

    inner class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProfile: ShapeableImageView = itemView.findViewById(R.id.ivTeacherProfile)
        private val tvName: TextView = itemView.findViewById(R.id.tvTeacherName)
        private val toggleGroup: MaterialButtonToggleGroup = itemView.findViewById(R.id.toggleGroupAttendance)
        private val btnPresent: MaterialButton = itemView.findViewById(R.id.btnPresent)
        private val btnAbsent: MaterialButton = itemView.findViewById(R.id.btnAbsent)

        fun bind(teacher: TeacherAttendanceItem) {
            tvName.text = teacher.name
            Glide.with(itemView.context)
                .load(teacher.profileImageUrl)
                .placeholder(R.drawable.teacher_placeholder)
                .error(R.drawable.teacher_placeholder)
                .circleCrop()
                .into(ivProfile)

            toggleGroup.removeOnButtonCheckedListener(listener)

            // --- FIX #2: SIMPLIFIED LOGIC ---
            // Since status can never be null, we don't need an 'else' block.
            when (teacher.attendanceStatus) {
                "Present" -> toggleGroup.check(R.id.btnPresent)
                "Absent" -> toggleGroup.check(R.id.btnAbsent)
            }
            toggleGroup.addOnButtonCheckedListener(listener)
        }

        private val listener =
            MaterialButtonToggleGroup.OnButtonCheckedListener { group, checkedId, isChecked ->
                // --- FIX #3: SIMPLIFIED LISTENER ---
                // We only care when a button IS checked. Un-checking is handled by the group.
                if (isChecked && adapterPosition != RecyclerView.NO_POSITION) {
                    val teacher = teachers[adapterPosition]
                    teacher.attendanceStatus = when (checkedId) {
                        R.id.btnPresent -> "Present"
                        R.id.btnAbsent -> "Absent"
                        // This 'else' is a safeguard, but shouldn't be reached
                        else -> teacher.attendanceStatus
                    }
                }
            }
    }
}