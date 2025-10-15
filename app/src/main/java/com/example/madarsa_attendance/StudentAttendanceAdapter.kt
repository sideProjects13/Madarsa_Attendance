package com.example.madarsa_attendance

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class StudentAttendanceAdapter : ListAdapter<StudentAttendanceItem, StudentAttendanceAdapter.StudentViewHolder>(StudentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val studentNameTextView: TextView = itemView.findViewById(R.id.tvStudentNameAttendanceItem)
        private val ivStudentIcon: ImageView = itemView.findViewById(R.id.ivStudentIconAttendance)
        private val tvStudentRollNumber: TextView = itemView.findViewById(R.id.tvStudentRollNumber)
        private val toggleGroup: MaterialButtonToggleGroup = itemView.findViewById(R.id.toggleGroupAttendanceStatus)
        private val btnPresent: MaterialButton = itemView.findViewById(R.id.btnTogglePresent)
        private val btnAbsent: MaterialButton = itemView.findViewById(R.id.btnToggleAbsent)
        private var imageDialog: Dialog? = null

        private val colorSelectedBg: Int by lazy { ContextCompat.getColor(itemView.context, R.color.bw_theme_primary) }
        private val colorSelectedText: Int by lazy { ContextCompat.getColor(itemView.context, R.color.bw_theme_onPrimary) }
        private val colorUnselectedText: Int by lazy {
            val typedValue = TypedValue()
            itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            typedValue.data
        }
        private val colorUnselectedStroke: Int by lazy { colorUnselectedText }
        private val unselectedStrokeWidth: Int by lazy { itemView.context.resources.getDimensionPixelSize(R.dimen.toggle_button_stroke_width) }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(student: StudentAttendanceItem) {
            studentNameTextView.text = student.name
            tvStudentRollNumber.text = "Roll No: ${student.regNo}"

            Glide.with(itemView.context)
                .load(student.profileImageUrl).circleCrop()
                .placeholder(R.drawable.student).error(R.drawable.student)
                .into(ivStudentIcon)

            toggleGroup.clearOnButtonCheckedListeners()
            val initialCheckId = when (student.status) {
                "Present" -> R.id.btnTogglePresent
                "Absent" -> R.id.btnToggleAbsent
                else -> R.id.btnTogglePresent
            }
            toggleGroup.check(initialCheckId)
            applyCustomVisuals(initialCheckId)

            toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked && adapterPosition != RecyclerView.NO_POSITION) {
                    val clickedStudent = getItem(adapterPosition)
                    clickedStudent.status = if (checkedId == R.id.btnTogglePresent) "Present" else "Absent"
                    applyCustomVisuals(checkedId)
                }
            }

            if (student.isExpanded) {
                studentNameTextView.maxLines = Integer.MAX_VALUE
                studentNameTextView.ellipsize = null
            } else {
                studentNameTextView.maxLines = 2
                studentNameTextView.ellipsize = TextUtils.TruncateAt.END
            }
            studentNameTextView.setOnClickListener {
                student.isExpanded = !student.isExpanded
                if (student.isExpanded) {
                    studentNameTextView.maxLines = Integer.MAX_VALUE
                    studentNameTextView.ellipsize = null
                } else {
                    studentNameTextView.maxLines = 2
                    studentNameTextView.ellipsize = TextUtils.TruncateAt.END
                }
            }

            // --- THIS IS THE FIX ---
            // The image preview now uses the new, centered dialog layout.
            ivStudentIcon.setOnClickListener {
                // Inflate the new custom layout
                val dialogView = LayoutInflater.from(itemView.context).inflate(R.layout.dialog_image_preview, null)
                val imageView = dialogView.findViewById<ImageView>(R.id.preview_image_view)

                // Load the image into the dialog's ImageView
                Glide.with(itemView.context)
                    .load(student.profileImageUrl)
                    .placeholder(R.drawable.student)
                    .error(R.drawable.student)
                    .into(imageView)

                // Create and show the dialog
                val dialog = AlertDialog.Builder(itemView.context)
                    .setView(dialogView)
                    .create()

                // Make the dialog background transparent to see the card's rounded corners
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
            }
            // --- END OF FIX ---
        }

        private fun applyCustomVisuals(checkedButtonId: Int) {
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

class StudentDiffCallback : DiffUtil.ItemCallback<StudentAttendanceItem>() {
    override fun areItemsTheSame(oldItem: StudentAttendanceItem, newItem: StudentAttendanceItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: StudentAttendanceItem, newItem: StudentAttendanceItem): Boolean {
        return oldItem.name == newItem.name &&
                oldItem.regNo == newItem.regNo &&
                oldItem.status == newItem.status &&
                oldItem.profileImageUrl == newItem.profileImageUrl
    }
}