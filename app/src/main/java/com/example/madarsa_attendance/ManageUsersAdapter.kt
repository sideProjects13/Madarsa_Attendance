package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.madarsa_attendance.models.AppUser
import com.google.android.material.chip.Chip

class ManageUsersAdapter(
    private var users: List<AppUser>,
    private val listener: UserActionListener
) : RecyclerView.Adapter<ManageUsersAdapter.ViewHolder>() {

    interface UserActionListener {
        fun onStatusChange(user: AppUser, newStatus: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manage_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount() = users.size

    fun updateData(newUsers: List<AppUser>) {
        this.users = newUsers
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tv_user_name)
        private val orgName: TextView = itemView.findViewById(R.id.tv_org_name)
        private val email: TextView = itemView.findViewById(R.id.tv_user_email)
        private val statusChip: Chip = itemView.findViewById(R.id.chip_status)
        private val actionsLayout: LinearLayout = itemView.findViewById(R.id.layout_actions)
        private val approveBtn: Button = itemView.findViewById(R.id.btn_approve)
        private val declineBtn: Button = itemView.findViewById(R.id.btn_decline)
        private val toggleBtn: Button = itemView.findViewById(R.id.btn_toggle_status)

        fun bind(user: AppUser) {
            name.text = user.name
            orgName.text = user.organizationName
            email.text = user.email

            when (user.accountStatus) {
                "pending" -> {
                    statusChip.text = "Pending Approval"
                    statusChip.setChipBackgroundColorResource(R.color.mono_palette_black) // Make sure this color exists
                    actionsLayout.visibility = View.VISIBLE
                    toggleBtn.visibility = View.GONE
                }
                "active" -> {
                    statusChip.text = "Active"
                    statusChip.setChipBackgroundColorResource(R.color.status_paid_green)
                    actionsLayout.visibility = View.GONE
                    toggleBtn.visibility = View.VISIBLE
                    toggleBtn.text = "Deactivate Account"
                }
                "inactive" -> {
                    statusChip.text = "Inactive"
                    statusChip.setChipBackgroundColorResource(R.color.status_unpaid_red)
                    actionsLayout.visibility = View.GONE
                    toggleBtn.visibility = View.VISIBLE
                    toggleBtn.text = "Activate Account"
                }
            }

            approveBtn.setOnClickListener { listener.onStatusChange(user, "active") }
            declineBtn.setOnClickListener { listener.onStatusChange(user, "inactive") }
            toggleBtn.setOnClickListener {
                val newStatus = if (user.accountStatus == "active") "inactive" else "active"
                listener.onStatusChange(user, newStatus)
            }
        }
    }
}