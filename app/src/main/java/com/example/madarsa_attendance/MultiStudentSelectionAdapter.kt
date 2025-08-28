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
import com.google.android.material.checkbox.MaterialCheckBox

class MultiStudentSelectionAdapter(
    // Added a callback to inform the Activity when a selection changes
    private val onSelectionChanged: (StudentDetailsItem, Boolean) -> Unit
) : ListAdapter<MultiStudentSelectionAdapter.SelectableStudent, MultiStudentSelectionAdapter.StudentSelectionViewHolder>(StudentSelectionDiffCallback()) {

    data class SelectableStudent(val student: StudentDetailsItem, var isSelected: Boolean)

    // The ListAdapter now manages its own `currentList`, which is a filtered subset
    // selectAll/deselectAll will operate on this currentList and then dispatch updates.

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentSelectionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_multi_student_selection, parent, false)
        return StudentSelectionViewHolder(view, onSelectionChanged) // Pass callback to ViewHolder
    }

    override fun onBindViewHolder(holder: StudentSelectionViewHolder, position: Int) {
        val selectableStudent = getItem(position)
        holder.bind(selectableStudent)
    }

    fun selectAllVisibleStudents() {
        // Iterate through the currently displayed list and update selection status
        currentList.forEach { selectableStudent ->
            if (!selectableStudent.isSelected) { // Only change if not already selected
                selectableStudent.isSelected = true
                onSelectionChanged(selectableStudent.student, true)
            }
        }
        notifyDataSetChanged() // Refresh UI for all visible items
    }

    fun deselectAllVisibleStudents() {
        // Iterate through the currently displayed list and update selection status
        currentList.forEach { selectableStudent ->
            if (selectableStudent.isSelected) { // Only change if currently selected
                selectableStudent.isSelected = false
                onSelectionChanged(selectableStudent.student, false)
            }
        }
        notifyDataSetChanged() // Refresh UI for all visible items
    }

    fun getSelectedStudents(): List<StudentDetailsItem> {
        // This method will actually not be needed from the adapter directly if Activity manages the master list.
        // The Activity's master list will be filtered. However, for a generic adapter, this remains useful.
        return currentList.filter { it.isSelected }.map { it.student }
    }


    class StudentSelectionViewHolder(
        itemView: View,
        private val onSelectionChanged: (StudentDetailsItem, Boolean) -> Unit // Receive callback
    ) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: MaterialCheckBox = itemView.findViewById(R.id.cb_student_select)
        private val studentName: TextView = itemView.findViewById(R.id.tv_student_name_selection)
        private val studentClass: TextView = itemView.findViewById(R.id.tv_student_class_selection)

        fun bind(selectableStudent: SelectableStudent) {
            val student = selectableStudent.student

            // Unset previous listener to prevent triggering it when setting `isChecked`
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = selectableStudent.isSelected

            studentName.text = student.studentName
            studentClass.text = "Class: ${student.teacherName ?: "N/A"}"

            Glide.with(itemView.context)
                .load(student.profileImageUrl)
                .placeholder(R.drawable.student)
                .error(R.drawable.student)

            // Re-set listener for user interactions
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                selectableStudent.isSelected = isChecked // Update local object
                onSelectionChanged(student, isChecked) // Propagate change to Activity
            }
            itemView.setOnClickListener {
                checkBox.toggle() // This will trigger the OnCheckedChangeListener
            }
        }
    }
}

class StudentSelectionDiffCallback : DiffUtil.ItemCallback<MultiStudentSelectionAdapter.SelectableStudent>() {
    override fun areItemsTheSame(oldItem: MultiStudentSelectionAdapter.SelectableStudent, newItem: MultiStudentSelectionAdapter.SelectableStudent): Boolean {
        return oldItem.student.id == newItem.student.id
    }

    override fun areContentsTheSame(oldItem: MultiStudentSelectionAdapter.SelectableStudent, newItem: MultiStudentSelectionAdapter.SelectableStudent): Boolean {
        return oldItem == newItem // Data class handles equality checks for contents
    }
}