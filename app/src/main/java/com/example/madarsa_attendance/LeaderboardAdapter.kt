package com.example.madarsa_attendance // <<< YOUR PACKAGE NAME

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView // <<< ADD IMPORT
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat

// Assumes LeaderboardItem is in DataModels.kt in the same package

class LeaderboardAdapter(
    private var leaderboardItems: List<LeaderboardItem>
) : RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {

    private val percentageFormat = DecimalFormat("#0.0") // For formatting percentage

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_student, parent, false)
        return LeaderboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val item = leaderboardItems[position]
        holder.bind(item, position + 1, percentageFormat) // Pass rank
    }

    override fun getItemCount(): Int = leaderboardItems.size

    fun updateData(newItems: List<LeaderboardItem>) {
        leaderboardItems = newItems
        notifyDataSetChanged()
    }

    class LeaderboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        private val tvStudentName: TextView = itemView.findViewById(R.id.tvStudentNameLeaderboard)
        private val tvPercentage: TextView = itemView.findViewById(R.id.tvAttendancePercentage)
        private val tvPresentDays: TextView = itemView.findViewById(R.id.tvPresentDays)
        private val tvAbsentDays: TextView = itemView.findViewById(R.id.tvAbsentDays)
        private val tvTotalMarkedDays: TextView = itemView.findViewById(R.id.tvTotalMarkedDays)
        private val ivRankIcon: ImageView = itemView.findViewById(R.id.ivRankIcon) // <<< GET ICON VIEW

        fun bind(item: LeaderboardItem, rank: Int, formatter: DecimalFormat) {
            tvRank.text = "$rank."
            tvStudentName.text = item.studentName
            tvPercentage.text = "${formatter.format(item.attendancePercentage)}%"
            tvPresentDays.text = "P: ${item.presentDays}"
            tvAbsentDays.text = "A: ${item.absentDays}"
            tvTotalMarkedDays.text = "Total: ${item.totalMarkedDays}"

            // Optional: Show rank icon for top ranks
            if (rank <= 3) { // Example: Show icon for top 3
                ivRankIcon.visibility = View.VISIBLE
                // You could even change the icon based on rank (e.g., gold, silver, bronze stars)
                // when (rank) {
                //     1 -> ivRankIcon.setImageResource(R.drawable.ic_gold_star)
                //     2 -> ivRankIcon.setImageResource(R.drawable.ic_silver_star)
                //     3 -> ivRankIcon.setImageResource(R.drawable.ic_bronze_star)
                // }
            } else {
                ivRankIcon.visibility = View.GONE // Or View.INVISIBLE if you want to keep space
            }
        }
    }
}