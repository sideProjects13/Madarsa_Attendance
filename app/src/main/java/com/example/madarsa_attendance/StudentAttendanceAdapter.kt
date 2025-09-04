package com.example.madarsa_attendance

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class StudentAttendanceAdapter(
    private var studentsInternal: MutableList<StudentAttendanceItem>
) : RecyclerView.Adapter<StudentAttendanceAdapter.StudentViewHolder>() {

    private companion object {
        private const val ADAPTER_TAG = "StudentAttendAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(studentsInternal[position])
    }

    override fun getItemCount(): Int = studentsInternal.size

    fun getAttendanceData(): List<StudentAttendanceItem> = studentsInternal.toList()

    fun submitList(newStudents: List<StudentAttendanceItem>) {
        studentsInternal.clear()
        studentsInternal.addAll(newStudents)
        notifyDataSetChanged()
    }

    inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val studentNameTextView: TextView = itemView.findViewById(R.id.tvStudentNameAttendanceItem)
        private val ivStudentIcon: ImageView = itemView.findViewById(R.id.ivStudentIconAttendance)
        private val toggleGroup: MaterialButtonToggleGroup = itemView.findViewById(R.id.toggleGroupAttendanceStatus)
        private val btnPresent: MaterialButton = itemView.findViewById(R.id.btnTogglePresent)
        private val btnAbsent: MaterialButton = itemView.findViewById(R.id.btnToggleAbsent)

        private val colorSelectedBg: Int by lazy { ContextCompat.getColor(itemView.context, R.color.bw_theme_primary) }
        private val colorSelectedText: Int by lazy { ContextCompat.getColor(itemView.context, R.color.bw_theme_onPrimary) }
        private val colorUnselectedText: Int by lazy {
            val typedValue = TypedValue()
            itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            typedValue.data
        }
        private val colorUnselectedStroke: Int by lazy { colorUnselectedText }
        private val unselectedStrokeWidth: Int by lazy { itemView.context.resources.getDimensionPixelSize(R.dimen.toggle_button_stroke_width) }

        fun bind(student: StudentAttendanceItem) {
            studentNameTextView.text = student.name

            if (!student.profileImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(student.profileImageUrl).circleCrop()
                    .placeholder(R.drawable.student).error(R.drawable.student)
                    .into(ivStudentIcon)
            } else {
                ivStudentIcon.setImageResource(R.drawable.student)
            }

            toggleGroup.clearOnButtonCheckedListeners()

            val initialCheckId = when (student.status) {
                "Present" -> R.id.btnTogglePresent
                "Absent" -> R.id.btnToggleAbsent
                else -> R.id.btnTogglePresent // Default to Present
            }
            toggleGroup.check(initialCheckId)
            applyCustomVisuals(initialCheckId)

            toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
                if (isChecked) {
                    val currentPosition = adapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        val clickedStudent = studentsInternal[currentPosition]
                        val newStatus = if (checkedId == R.id.btnTogglePresent) "Present" else "Absent"

                        // --- THIS IS THE CRITICAL FIX ---
                        // Update the status in the master list directly.
                        clickedStudent.status = newStatus
                        // --- END OF FIX ---

                        applyCustomVisuals(checkedId)
                    }
                }
            }
        }

        private fun applyCustomVisuals(checkedButtonId: Int) {
            // Present Button
            btnPresent.isSelected = (checkedButtonId == R.id.btnTogglePresent)
            if (btnPresent.isSelected) {
                btnPresent.backgroundTintList = ColorStateList.valueOf(colorSelectedBg)
                btnPresent.setTextColor(colorSelectedText); btnPresent.strokeWidth = 0
                btnPresent.iconTint = ColorStateList.valueOf(colorSelectedText)
            } else {
                btnPresent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btnPresent.setTextColor(colorUnselectedText)
                btnPresent.strokeColor = ColorStateList.valueOf(colorUnselectedStroke)
                btnPresent.strokeWidth = unselectedStrokeWidth
                btnPresent.iconTint = ColorStateList.valueOf(colorUnselectedText)
            }
            // Absent Button
            btnAbsent.isSelected = (checkedButtonId == R.id.btnToggleAbsent)
            if (btnAbsent.isSelected) {
                btnAbsent.backgroundTintList = ColorStateList.valueOf(colorSelectedBg)
                btnAbsent.setTextColor(colorSelectedText); btnAbsent.strokeWidth = 0
                btnAbsent.iconTint = ColorStateList.valueOf(colorSelectedText)
            } else {
                btnAbsent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btnAbsent.setTextColor(colorUnselectedText)
                btnAbsent.strokeColor = ColorStateList.valueOf(colorUnselectedStroke)
                btnAbsent.strokeWidth = unselectedStrokeWidth
                btnAbsent.iconTint = ColorStateList.valueOf(colorUnselectedText)
            }
        }
    }
}