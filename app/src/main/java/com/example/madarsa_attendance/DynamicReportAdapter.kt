package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DynamicReportAdapter(
    private var students: List<StudentDetailsItem>,
    private var columns: List<ReportColumn>
) : RecyclerView.Adapter<DynamicReportAdapter.RowViewHolder>() {

    fun updateData(newStudents: List<StudentDetailsItem>) {
        this.students = newStudents
        notifyDataSetChanged()
    }

    fun updateColumns(newColumns: List<ReportColumn>) {
        this.columns = newColumns
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val student = students[position]
        holder.bind(student, columns)
    }

    override fun getItemCount(): Int = students.size

    class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowContainer: LinearLayout = itemView.findViewById(R.id.row_container)

        fun bind(student: StudentDetailsItem, columns: List<ReportColumn>) {
            rowContainer.removeAllViews() // Clear previous cells

            for (column in columns) {
                val cellView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.item_report_cell, rowContainer, false) as TextView

                val cellData = when (column) {
                    ReportColumn.REG_NO -> student.regNo
                    ReportColumn.STUDENT_NAME -> student.studentName
                    ReportColumn.PARENT_NAME -> student.parentName
                    ReportColumn.PARENT_MOBILE -> student.parentMobileNumber
                    ReportColumn.ALTERNATE_MOBILE -> student.alternateMobileNumber
                    ReportColumn.GENDER -> student.gender
                    ReportColumn.DOB -> student.birthDate
                    ReportColumn.ADMISSION_DATE -> student.admissionDate
                    ReportColumn.MONTHLY_FEE -> student.monthlyFee?.toString()
                    ReportColumn.TEACHER_NAME -> student.teacherName
                }
                cellView.text = cellData ?: "N/A"
                rowContainer.addView(cellView)
            }
        }
    }
}