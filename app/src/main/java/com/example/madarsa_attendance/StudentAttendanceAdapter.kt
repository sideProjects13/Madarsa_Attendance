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
import com.bumptech.glide.Glide // GLIDE IMPORT
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

// StudentAttendanceItem should be defined in DataModels.kt (or similar)
// data class StudentAttendanceItem(
//    val id: String,
//    val name: String,
//    var status: String = "Present",
//    val profileImageUrl: String? = null // Make sure this is present
// )

class StudentAttendanceAdapter(
    private var studentsInternal: MutableList<StudentAttendanceItem>,
    private val onStatusChangeCallback: (studentId: String, newStatus: String) -> Unit
) : RecyclerView.Adapter<StudentAttendanceAdapter.StudentViewHolder>() {

    private companion object {
        private const val ADAPTER_TAG = "StudentAttendAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        Log.d(ADAPTER_TAG, "onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance, parent, false) // Ensure this layout has ivStudentIconAttendance
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        if (position < 0 || position >= studentsInternal.size) {
            Log.e(ADAPTER_TAG, "onBindViewHolder: Invalid position $position for students size ${studentsInternal.size}")
            return
        }
        val student = studentsInternal[position]
        Log.d(ADAPTER_TAG, "Binding student: ${student.name}, Status: ${student.status}, Image: ${student.profileImageUrl}")
        holder.bind(student, onStatusChangeCallback)
    }

    override fun getItemCount(): Int {
        return studentsInternal.size
    }

    fun getAttendanceData(): List<StudentAttendanceItem> {
        return studentsInternal.toList()
    }

    fun submitList(newStudents: List<StudentAttendanceItem>) {
        val oldSize = studentsInternal.size
        studentsInternal.clear()
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize)
        }
        studentsInternal.addAll(newStudents)
        if (newStudents.isNotEmpty()) {
            notifyItemRangeInserted(0, newStudents.size)
        }
    }

    inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val studentNameTextView: TextView = itemView.findViewById(R.id.tvStudentNameAttendanceItem)
        private val ivStudentIcon: ImageView = itemView.findViewById(R.id.ivStudentIconAttendance) // From item_student_attendance.xml
        private val toggleGroup: MaterialButtonToggleGroup = itemView.findViewById(R.id.toggleGroupAttendanceStatus)
        private val btnPresent: MaterialButton = itemView.findViewById(R.id.btnTogglePresent)
        private val btnAbsent: MaterialButton = itemView.findViewById(R.id.btnToggleAbsent)

        // Colors (assuming these are defined in your colors.xml and theme)
        private val colorSelectedBg: Int by lazy { ContextCompat.getColor(itemView.context, R.color.bw_theme_primary) }
        private val colorSelectedText: Int by lazy { ContextCompat.getColor(itemView.context, R.color.bw_theme_onPrimary) }
        private val colorUnselectedText: Int by lazy {
            val typedValue = TypedValue()
            itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            typedValue.data.takeIf { typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT }
                ?: ContextCompat.getColor(itemView.context, R.color.mono_palette_black)
        }
        private val colorUnselectedStroke: Int by lazy { colorUnselectedText }
        private val unselectedStrokeWidth: Int by lazy { itemView.context.resources.getDimensionPixelSize(R.dimen.toggle_button_stroke_width) }


        fun bind(
            student: StudentAttendanceItem,
            onExternalStatusChange: (studentId: String, newStatus: String) -> Unit
        ) {
            studentNameTextView.text = student.name

            // Load student profile image using Glide
            if (!student.profileImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(student.profileImageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.student) // Your default student placeholder
                    .error(R.drawable.student)       // Fallback image on error
                    .into(ivStudentIcon)
            } else {
                ivStudentIcon.setImageResource(R.drawable.student) // Default if no URL
            }

            toggleGroup.clearOnButtonCheckedListeners()
            val initialCheckId = when (student.status) {
                "Present" -> R.id.btnTogglePresent
                "Absent" -> R.id.btnToggleAbsent
                else -> { student.status = "Present"; R.id.btnTogglePresent }
            }
            toggleGroup.check(initialCheckId)
            applyCustomVisuals(toggleGroup.checkedButtonId)

            toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val newStatus = when (checkedId) {
                        R.id.btnTogglePresent -> "Present"
                        R.id.btnToggleAbsent -> "Absent"
                        else -> student.status
                    }
                    if (student.status != newStatus) {
                        student.status = newStatus
                        onExternalStatusChange(student.id, newStatus)
                    }
                    applyCustomVisuals(checkedId)
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
                btnAbsent.backgroundTintList = ColorStateList.valueOf(colorSelectedBg) // Or a different color for absent selected
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