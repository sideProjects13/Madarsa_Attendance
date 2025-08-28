package com.example.madarsa_attendance

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import java.util.Locale

class StudentAutocompleteAdapter(
    context: Context,
    private val studentsFullList: List<StudentDetailsItem>
) : ArrayAdapter<StudentDetailsItem>(context, 0, studentsFullList) {

    private var filteredStudents: List<StudentDetailsItem> = studentsFullList

    override fun getCount(): Int = filteredStudents.size

    override fun getItem(position: Int): StudentDetailsItem? = filteredStudents.getOrNull(position)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val student = getItem(position)
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_student_autocomplete_dropdown, parent, false)

        val tvRegNo: TextView = view.findViewById(R.id.tv_dropdown_reg_no)
        val tvStudentName: TextView = view.findViewById(R.id.tv_dropdown_student_name)
        val tvTeacherName: TextView = view.findViewById(R.id.tv_dropdown_teacher_name)

        if (student != null) {
            tvRegNo.text = "${student.regNo ?: "N/A"} -"
            tvStudentName.text = student.studentName
            tvTeacherName.text = "Class: ${student.teacherName ?: "N/A"}"
        }

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val suggestions = mutableListOf<StudentDetailsItem>()

                if (constraint.isNullOrEmpty()) {
                    suggestions.addAll(studentsFullList)
                } else {
                    val filterPattern = constraint.toString().lowercase(Locale.getDefault())
                    for (student in studentsFullList) {
                        if (student.studentName.lowercase(Locale.getDefault()).contains(filterPattern) ||
                            (student.regNo?.lowercase(Locale.getDefault())?.contains(filterPattern) == true) ||
                            (student.parentMobileNumber?.contains(filterPattern) == true)
                        ) {
                            suggestions.add(student)
                        }
                    }
                }
                results.values = suggestions
                results.count = suggestions.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                @Suppress("UNCHECKED_CAST")
                filteredStudents = results.values as? List<StudentDetailsItem> ?: emptyList()
                notifyDataSetChanged()
            }

            // --- FIX IS HERE: More robust string formatting ---
            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? StudentDetailsItem)?.let { student ->
                    // Check if regNo is not null or blank.
                    // If it has a value, prepend it. Otherwise, don't.
                    val regNoText = if (!student.regNo.isNullOrBlank()) {
                        "${student.regNo} - "
                    } else {
                        "" // If no regNo, prepend nothing.
                    }
                    // Construct the final string
                    "$regNoText${student.studentName} (${student.teacherName ?: "N/A"})"
                } ?: ""
            }
        }
    }
}