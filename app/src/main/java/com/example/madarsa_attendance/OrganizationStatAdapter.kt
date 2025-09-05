package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OrganizationStatAdapter(
    private var stats: List<OrganizationStat>
) : RecyclerView.Adapter<OrganizationStatAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_organization_stat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(stats[position])
    }

    override fun getItemCount() = stats.size

    fun updateData(newStats: List<OrganizationStat>) {
        this.stats = newStats
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val orgName: TextView = itemView.findViewById(R.id.tv_org_name)
        private val studentCount: TextView = itemView.findViewById(R.id.tv_student_count)
        private val teacherCount: TextView = itemView.findViewById(R.id.tv_teacher_count)

        fun bind(stat: OrganizationStat) {
            orgName.text = stat.orgName
            studentCount.text = "${stat.studentCount} Students"
            teacherCount.text = "${stat.teacherCount} Classes"
        }
    }
}