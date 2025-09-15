package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyTeacherAttendanceAdapter(
    private var dailyRecords: List<DailyAttendanceStatus>
) : RecyclerView.Adapter<DailyTeacherAttendanceAdapter.ViewHolder>() {

    private val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val outputFormat = SimpleDateFormat("dd MMM, yyyy (EEEE)", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_teacher_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = dailyRecords[position]
        holder.bind(record, inputFormat, outputFormat)
    }

    override fun getItemCount(): Int = dailyRecords.size

    fun updateData(newRecords: List<DailyAttendanceStatus>) {
        dailyRecords = newRecords
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDailyDate)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvDailyStatus)

        fun bind(record: DailyAttendanceStatus, inputFmt: SimpleDateFormat, outputFmt: SimpleDateFormat) {
            try {
                val dateObj: Date? = inputFmt.parse(record.date)
                tvDate.text = if (dateObj != null) outputFmt.format(dateObj) else record.date
            } catch (e: Exception) {
                tvDate.text = record.date
            }

            tvStatus.text = record.status
            when (record.status.lowercase(Locale.getDefault())) {
                "present" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_text_white))
                    tvStatus.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.status_paid_green))
                }
                "absent" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_text_white))
                    tvStatus.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.status_unpaid_red))
                }
                else -> { // "Not Marked"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.mono_palette_grey_secondary_text))
                    tvStatus.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.mono_palette_background_subtle))
                }
            }
        }
    }
}