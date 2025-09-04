package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import java.util.Locale

// 1. Add a listener to the constructor
class BulkMoveAdapter(
    private var students: List<StudentDetailsItem>,
    private val onSelectionChanged: (Int) -> Unit // This will report the new selection count
) : RecyclerView.Adapter<BulkMoveAdapter.ViewHolder>() {

    private val selectedStudents = mutableSetOf<StudentDetailsItem>()
    private var filteredStudents = students.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_bulk_move, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(filteredStudents[position])
    }

    override fun getItemCount() = filteredStudents.size

    fun getSelectedStudents(): List<StudentDetailsItem> {
        return selectedStudents.toList()
    }

    fun updateData(newStudents: List<StudentDetailsItem>) {
        students = newStudents
        selectedStudents.clear() // Clear selection when data changes
        onSelectionChanged(0) // Notify that selection is now 0
        filter(null)
    }

    fun filter(query: String?) {
        filteredStudents.clear()
        if (query.isNullOrEmpty()) {
            filteredStudents.addAll(students)
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            students.forEach {
                if (it.studentName.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    it.regNo?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true) {
                    filteredStudents.add(it)
                }
            }
        }
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: MaterialCheckBox = itemView.findViewById(R.id.checkbox_student)
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_student_name)
        private val regNoTextView: TextView = itemView.findViewById(R.id.tv_reg_no)

        fun bind(student: StudentDetailsItem) {
            nameTextView.text = student.studentName
            regNoTextView.text = "Reg No: ${student.regNo ?: "N/A"}"

            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = selectedStudents.contains(student)

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedStudents.add(student)
                } else {
                    selectedStudents.remove(student)
                }
                // 2. Call the listener to notify the activity of the change
                onSelectionChanged(selectedStudents.size)
            }

            itemView.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }
        }
    }
}