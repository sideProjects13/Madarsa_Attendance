package com.example.madarsa_attendance

import android.graphics.Color
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.madarsa_attendance.TeacherWithStudentCount

class ManageTeachersAdapter(
    private var teachers: List<TeacherWithStudentCount>,
    private val onTeacherCardClick: (TeacherWithStudentCount) -> Unit,
    private val onTeacherCardLongClick: (TeacherWithStudentCount) -> Unit,
    private val onEditTeacherClick: (TeacherWithStudentCount) -> Unit,
    private val onDeleteTeacherClick: (TeacherWithStudentCount) -> Unit
) : RecyclerView.Adapter<ManageTeachersAdapter.TeacherViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher_manage, parent, false)
        return TeacherViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = teachers[position]
        holder.bind(teacher, onTeacherCardClick, onTeacherCardLongClick, onEditTeacherClick, onDeleteTeacherClick)
    }

    override fun getItemCount(): Int = teachers.size

    fun updateData(newTeachers: List<TeacherWithStudentCount>) {
        teachers = newTeachers
        notifyDataSetChanged()
    }

    class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val teacherNameTextView: TextView = itemView.findViewById(R.id.tvTeacherNameManageItem)
        private val teacherSubtitleTextView: TextView = itemView.findViewById(R.id.tvTeacherSubtitleManageItem)
        private val teacherIconImageView: ImageView = itemView.findViewById(R.id.ivTeacherIconManageItem)
        private val menuIconImageView: ImageView = itemView.findViewById(R.id.ivTeacherItemMenu)
        private val studentCountTextView: TextView = itemView.findViewById(R.id.tvStudentCount)

        fun bind(
            teacher: TeacherWithStudentCount,
            onCardClick: (TeacherWithStudentCount) -> Unit,
            onCardLongClick: (TeacherWithStudentCount) -> Unit,
            onEditClick: (TeacherWithStudentCount) -> Unit,
            onDeleteClick: (TeacherWithStudentCount) -> Unit
        ) {
            teacherNameTextView.text = teacher.name
            teacherSubtitleTextView.text = "Tap for options, long press for menu"

            studentCountTextView.text = teacher.studentCount.toString()

            if (!teacher.profileImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(teacher.profileImageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.molana)
                    .error(R.drawable.molana)
                    .into(teacherIconImageView)
            } else {
                teacherIconImageView.setImageResource(R.drawable.molana)
            }

            val cardContainer = itemView.findViewById<View>(R.id.cardTeacherItemContainer)

            // Set the Click Listener
            cardContainer.setOnClickListener {
                onCardClick(teacher)
            }

            // Set the Long Click Listener
            cardContainer.setOnLongClickListener {
                onCardLongClick(teacher)
                true // Return true to indicate the long click was consumed
            }

            menuIconImageView.setOnClickListener { view ->
                showPopupMenu(view, teacher, onEditClick, onDeleteClick)
            }
        }

        private fun showPopupMenu(
            anchorView: View,
            teacher: TeacherWithStudentCount,
            onEdit: (TeacherWithStudentCount) -> Unit,
            onDelete: (TeacherWithStudentCount) -> Unit
        ) {
            val popup = PopupMenu(anchorView.context, anchorView)
            popup.menuInflater.inflate(R.menu.teacher_item_options_menu, popup.menu)

            try {
                val staticTextColor = Color.BLACK
                for (i in 0 until popup.menu.size()) {
                    val menuItem = popup.menu.getItem(i)
                    val title = menuItem.title
                    if (title != null) {
                        val spannableTitle = SpannableString(title)
                        spannableTitle.setSpan(ForegroundColorSpan(staticTextColor), 0, spannableTitle.length, 0)
                        menuItem.title = spannableTitle
                    }
                }
            } catch (e: Exception) {
                Log.e("ManageTeachersAdapter", "Error styling popup menu items", e)
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit_teacher -> {
                        onEdit(teacher)
                        true
                    }
                    R.id.action_delete_teacher -> {
                        onDelete(teacher)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}