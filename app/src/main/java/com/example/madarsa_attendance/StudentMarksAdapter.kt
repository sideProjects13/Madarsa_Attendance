package com.example.madarsa_attendance

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

class StudentMarksAdapter(
    private var studentMarksList: List<StudentMarks>,
    private val subjects: List<SubjectItem>,
    private val onSaveClick: (StudentMarks) -> Unit,
    private val onGenerateClick: (StudentMarks) -> Unit
) : RecyclerView.Adapter<StudentMarksAdapter.MarksViewHolder>() {

    private var expandedPosition = -1

    class MarksViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val header: View = itemView.findViewById(R.id.collapsed_view_header)
        val studentName: TextView = itemView.findViewById(R.id.tvStudentNameMarks)
        val marksSummary: TextView = itemView.findViewById(R.id.tvMarksSummary)
        val expandedContent: View = itemView.findViewById(R.id.expanded_view_content)
        val subjectsLayout: LinearLayout = itemView.findViewById(R.id.layoutSubjectMarks)
        val saveButton: MaterialButton = itemView.findViewById(R.id.btnSaveStudentMarks)
        val avatar: TextView = itemView.findViewById(R.id.tvAvatar)
        val expandArrow: ImageView = itemView.findViewById(R.id.ivExpandArrow)
        val generateButton: MaterialButton = itemView.findViewById(R.id.btnGenerateResult)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarksViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_marks, parent, false)
        return MarksViewHolder(view)
    }

    override fun getItemCount() = studentMarksList.size

    override fun onBindViewHolder(holder: MarksViewHolder, position: Int) {
        val currentStudentMarks = studentMarksList[position]
        val isExpanded = holder.adapterPosition == expandedPosition

        holder.studentName.text = currentStudentMarks.student.studentName
        holder.avatar.text = currentStudentMarks.student.studentName.firstOrNull()?.uppercase() ?: "?"
        val enteredCount = currentStudentMarks.marks.values.count { it.isNotBlank() }
        holder.marksSummary.text = "$enteredCount of ${subjects.size} marks entered"

        holder.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.expandArrow.rotation = if (isExpanded) 180f else 0f

        holder.subjectsLayout.removeAllViews()

        if (isExpanded) {
            subjects.forEach { subject ->
                val textInputLayout = TextInputLayout(holder.itemView.context).apply {
                    hint = subject.subjectName
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 12 }
                }
                val editText = EditText(holder.itemView.context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setText(currentStudentMarks.marks[subject.id])
                }
                editText.addTextChangedListener(object: TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                            studentMarksList[holder.adapterPosition].marks[subject.id] = s.toString()
                        }
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
                textInputLayout.addView(editText)
                holder.subjectsLayout.addView(textInputLayout)
            }
        }

        holder.header.setOnClickListener {
            val previousExpandedPosition = expandedPosition
            expandedPosition = if (isExpanded) -1 else holder.adapterPosition
            if (previousExpandedPosition != -1) notifyItemChanged(previousExpandedPosition)
            if (expandedPosition != -1) notifyItemChanged(expandedPosition)
        }
        holder.saveButton.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) onSaveClick(studentMarksList[holder.adapterPosition])
        }

        // <<< THE FIX IS HERE >>>
        // Replaced the incorrect `NO_SESSION_ID.toInt()` with the correct `NO_POSITION`.
        holder.generateButton.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) onGenerateClick(studentMarksList[holder.adapterPosition])
        }
    }
}