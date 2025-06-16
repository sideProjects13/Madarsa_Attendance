package com.example.madarsa_attendance

import android.graphics.Color // Import for Color.BLACK, Color.WHITE etc.
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat // If you want to use color from R.color.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ManageTeachersAdapter(
    private var teachers: List<TeacherSpinnerItem>,
    private val onTeacherCardClick: (TeacherSpinnerItem) -> Unit,
    private val onEditTeacherClick: (TeacherSpinnerItem) -> Unit,
    private val onDeleteTeacherClick: (TeacherSpinnerItem) -> Unit
) : RecyclerView.Adapter<ManageTeachersAdapter.TeacherViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher_manage, parent, false)
        return TeacherViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = teachers[position]
        holder.bind(teacher, onTeacherCardClick, onEditTeacherClick, onDeleteTeacherClick)
    }

    override fun getItemCount(): Int = teachers.size

    fun updateData(newTeachers: List<TeacherSpinnerItem>) {
        teachers = newTeachers
        notifyDataSetChanged()
    }

    class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val teacherNameTextView: TextView = itemView.findViewById(R.id.tvTeacherNameManageItem)
        private val teacherSubtitleTextView: TextView = itemView.findViewById(R.id.tvTeacherSubtitleManageItem)
        private val teacherIconImageView: ImageView = itemView.findViewById(R.id.ivTeacherIconManageItem)
        private val menuIconImageView: ImageView = itemView.findViewById(R.id.ivTeacherItemMenu)

        fun bind(
            teacher: TeacherSpinnerItem,
            onCardClick: (TeacherSpinnerItem) -> Unit,
            onEditClick: (TeacherSpinnerItem) -> Unit,
            onDeleteClick: (TeacherSpinnerItem) -> Unit
        ) {
            teacherNameTextView.text = teacher.name
            teacherSubtitleTextView.text = "Tap for class options"

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

            itemView.findViewById<View>(R.id.cardTeacherItemContainer).setOnClickListener {
                onCardClick(teacher)
            }

            menuIconImageView.setOnClickListener { view ->
                showPopupMenu(view, teacher, onEditClick, onDeleteClick)
            }
        }

        private fun showPopupMenu(
            anchorView: View,
            teacher: TeacherSpinnerItem,
            onEdit: (TeacherSpinnerItem) -> Unit,
            onDelete: (TeacherSpinnerItem) -> Unit
        ) {
            // No ContextThemeWrapper needed for this approach
            val popup = PopupMenu(anchorView.context, anchorView)
            popup.menuInflater.inflate(R.menu.teacher_item_options_menu, popup.menu)

            // --- Directly set text color for menu items ---
            try {
                // Define your static text color here
                // val staticTextColor = ContextCompat.getColor(anchorView.context, R.color.mono_palette_black) // Using color resource
                val staticTextColor = Color.BLACK // Using direct Color constant for black

                for (i in 0 until popup.menu.size()) {
                    val menuItem = popup.menu.getItem(i)
                    val title = menuItem.title
                    if (title != null) { // Check for null title
                        val spannableTitle = SpannableString(title)
                        spannableTitle.setSpan(ForegroundColorSpan(staticTextColor), 0, spannableTitle.length, 0)
                        menuItem.title = spannableTitle
                    }
                }
            } catch (e: Exception) {
                // Log error if any issue with styling menu items programmatically
                Log.e("ManageTeachersAdapter", "Error styling popup menu items", e)
            }
            // --- End of direct text color setting ---

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