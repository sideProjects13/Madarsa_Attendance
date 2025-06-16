package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // <<< ADD GLIDE IMPORT

class ClassStudentsAdapter(
    private var studentsFullList: List<StudentDetailsItem>, // Store the full list
    private val onStudentClick: (StudentDetailsItem) -> Unit
) : RecyclerView.Adapter<ClassStudentsAdapter.StudentViewHolder>() {

    // This list will hold the currently displayed/filtered items
    private var studentsFilteredList: MutableList<StudentDetailsItem> = studentsFullList.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_manage, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = studentsFilteredList[position] // Use filtered list
        holder.bind(student, onStudentClick)
    }

    override fun getItemCount(): Int = studentsFilteredList.size // Count from filtered list

    fun updateData(newStudents: List<StudentDetailsItem>) {
        studentsFullList = newStudents
        studentsFilteredList = newStudents.toMutableList() // Reset filtered list
        notifyDataSetChanged()
    }

    // --- SEARCH FUNCTIONALITY ---
    fun filter(query: String?) {
        studentsFilteredList.clear()
        if (query.isNullOrEmpty()) {
            studentsFilteredList.addAll(studentsFullList)
        } else {
            val lowerCaseQuery = query.lowercase().trim()
            for (student in studentsFullList) {
                if (student.studentName.lowercase().contains(lowerCaseQuery)) {
                    studentsFilteredList.add(student)
                }
            }
        }
        notifyDataSetChanged()
    }
    // --- END SEARCH FUNCTIONALITY ---


    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val studentNameTextView: TextView = itemView.findViewById(R.id.tvStudentNameManageItem)
        private val parentNameTextView: TextView = itemView.findViewById(R.id.tvParentNameManageItem)
        private val parentMobileTextView: TextView = itemView.findViewById(R.id.tvParentMobileManageItem)
        private val studentIconImageView: ImageView = itemView.findViewById(R.id.ivStudentIconManageItem)

        fun bind(student: StudentDetailsItem, onStudentClick: (StudentDetailsItem) -> Unit) {
            studentNameTextView.text = student.studentName

            // Load student profile image using Glide
            if (!student.profileImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(student.profileImageUrl)
                    .circleCrop() // Make it circular
                    .placeholder(R.drawable.student) // Your default student placeholder
                    .error(R.drawable.student)       // Fallback image on error
                    .into(studentIconImageView)
            } else {
                // Set default placeholder if no image URL
                studentIconImageView.setImageResource(R.drawable.student)
            }

            if (!student.parentName.isNullOrEmpty()) {
                parentNameTextView.text = "Parent: ${student.parentName}"
                parentNameTextView.visibility = View.VISIBLE
            } else {
                parentNameTextView.visibility = View.GONE
            }

            if (!student.parentMobileNumber.isNullOrEmpty()) {
                parentMobileTextView.text = "Mobile: ${student.parentMobileNumber}"
                parentMobileTextView.visibility = View.VISIBLE
            } else {
                parentMobileTextView.visibility = View.GONE
            }

            itemView.setOnClickListener { onStudentClick(student) }
        }
    }
}