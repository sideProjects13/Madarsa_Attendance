package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class QuickAction(
    val title: String,
    val iconResId: Int,
    val actionId: Int // This will be the menu item ID, e.g., R.id.nav_quick_attendance
)

class QuickActionAdapter(
    private val actions: List<QuickAction>,
    private val onActionClick: (Int) -> Unit
) : RecyclerView.Adapter<QuickActionAdapter.ActionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_action, parent, false)
        return ActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        val action = actions[position]
        holder.bind(action)
        holder.itemView.setOnClickListener { onActionClick(action.actionId) }
    }

    override fun getItemCount(): Int = actions.size

    class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.iv_action_icon)
        private val title: TextView = itemView.findViewById(R.id.tv_action_title)

        fun bind(action: QuickAction) {
            icon.setImageResource(action.iconResId)
            title.text = action.title
        }
    }
}