package com.example.madarsa_attendance

import android.util.Log // Import Log for debugging
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView

class SubjectAdapter(
    private var subjects: MutableList<SubjectItem>, // This list is initialized by the Activity/Fragment
    private val onEditClick: (SubjectItem) -> Unit,
    private val onDeleteClick: (SubjectItem) -> Unit,
    private val onItemClick: (SubjectItem) -> Unit
) : RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder>() {

    companion object {
        private const val ADAPTER_TAG = "SubjectAdapter" // Tag for logging from this adapter
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        // Ensure R.layout.item_subject (or R.layout.item_subjects) is your correct XML file name
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subjects, parent, false) // VERIFY THIS LAYOUT NAME
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val subject = subjects[position]
        holder.bind(subject, onEditClick, onDeleteClick, onItemClick)
    }

    override fun getItemCount(): Int {
        return subjects.size
    }

    /**
     * Updates the adapter's data with a new list of subjects.
     * The adapter clears its current list and adds all items from the new list.
     */
    fun updateData(newSubjects: List<SubjectItem>) {
        Log.d(ADAPTER_TAG, "updateData called. Current items: ${subjects.size}, New items received: ${newSubjects.size}")
        this.subjects.clear()
        this.subjects.addAll(newSubjects)
        notifyDataSetChanged() // Notifies the RecyclerView that the entire dataset has changed
        Log.d(ADAPTER_TAG, "updateData complete. Adapter now has: ${this.subjects.size} items. First item: ${this.subjects.firstOrNull()?.subjectName}")
    }

    /**
     * Removes a specific subject from the adapter's list and notifies the RecyclerView.
     */
    fun removeItem(subject: SubjectItem) {
        val position = subjects.indexOf(subject)
        if (position > -1) {
            subjects.removeAt(position)
            notifyItemRemoved(position)
            Log.d(ADAPTER_TAG, "removeItem: Removed '${subject.subjectName}' at position $position. New count: ${subjects.size}")
        } else {
            Log.w(ADAPTER_TAG, "removeItem: Subject '${subject.subjectName}' not found in adapter list.")
        }
    }

    class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Ensure these IDs match your item_subject.xml (or item_subjects.xml)
        private val ivSubjectIcon: ImageView = itemView.findViewById(R.id.ivSubjectIcon)
        private val tvSubjectName: TextView = itemView.findViewById(R.id.tvSubjectName)
        private val tvSubjectDescription: TextView = itemView.findViewById(R.id.tvSubjectDescription)
        private val btnSubjectMenu: ImageButton = itemView.findViewById(R.id.btnSubjectMenu)

        fun bind(
            subject: SubjectItem,
            onEditClick: (SubjectItem) -> Unit,
            onDeleteClick: (SubjectItem) -> Unit,
            onItemClick: (SubjectItem) -> Unit
        ) {
            tvSubjectName.text = subject.subjectName
            if (!subject.description.isNullOrEmpty()) {
                tvSubjectDescription.text = subject.description
                tvSubjectDescription.visibility = View.VISIBLE
            } else {
                tvSubjectDescription.visibility = View.GONE
            }

            // Example: You could set the icon based on subject type if you add that later
            // ivSubjectIcon.setImageResource(R.drawable.ic_subject_item) // Default icon

            itemView.setOnClickListener {
                Log.d(ADAPTER_TAG, "Item clicked: ${subject.subjectName}")
                onItemClick(subject)
            }

            btnSubjectMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                // Ensure R.menu.item_context_menu exists and has R.id.menu_edit, R.id.menu_delete
                try {
                    popup.menuInflater.inflate(R.menu.item_context_menu, popup.menu)
                } catch (e: Exception) {
                    Log.e(ADAPTER_TAG, "Error inflating context menu. Does R.menu.item_context_menu exist?", e)
                    // Fallback or show toast if menu inflation fails
                    Toast.makeText(view.context, "Error showing options", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.menu_edit -> {
                            onEditClick(subject)
                            true
                        }
                        R.id.menu_delete -> {
                            onDeleteClick(subject)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
}