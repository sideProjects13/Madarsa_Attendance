package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Import Glide
import java.text.NumberFormat
import java.util.Locale

class PaymentSummaryAdapter(
    private var fullSummaryItems: List<StudentPaymentSummaryItem>, // Store full list
    private val onItemClick: (StudentPaymentSummaryItem) -> Unit
) : RecyclerView.Adapter<PaymentSummaryAdapter.PaymentViewHolder>() {

    private var filteredSummaryItems: MutableList<StudentPaymentSummaryItem> = fullSummaryItems.toMutableList()
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_payment_summary, parent, false) // Ensure this layout is the card version
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val item = filteredSummaryItems[position]
        holder.bind(item, onItemClick, currencyFormatter)
    }

    override fun getItemCount(): Int = filteredSummaryItems.size

    fun updateData(newItems: List<StudentPaymentSummaryItem>) {
        fullSummaryItems = newItems
        filteredSummaryItems = newItems.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String?) {
        filteredSummaryItems.clear()
        if (query.isNullOrEmpty()) {
            filteredSummaryItems.addAll(fullSummaryItems)
        } else {
            val lowerCaseQuery = query.lowercase().trim()
            for (item in fullSummaryItems) {
                if (item.studentName.lowercase().contains(lowerCaseQuery)) {
                    filteredSummaryItems.add(item)
                }
            }
        }
        notifyDataSetChanged()
    }

    class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivStudentIcon: ImageView = itemView.findViewById(R.id.ivStudentIconPaymentSummary)
        private val tvStudentName: TextView = itemView.findViewById(R.id.tvStudentNamePaymentSummary)
        private val tvTotalPaid: TextView = itemView.findViewById(R.id.tvTotalPaidThisMonth)
        private val tvPaymentCount: TextView = itemView.findViewById(R.id.tvPaymentCountThisMonth)

        fun bind(item: StudentPaymentSummaryItem, onItemClick: (StudentPaymentSummaryItem) -> Unit, formatter: NumberFormat) {
            tvStudentName.text = item.studentName
            tvTotalPaid.text = formatter.format(item.totalPaidThisMonth)
            tvPaymentCount.text = if (item.paymentCountThisMonth > 0) {
                if (item.paymentCountThisMonth == 1) "1 payment" else "${item.paymentCountThisMonth} payments"
            } else {
                "No payments this month"
            }

            if (!item.profileImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(item.profileImageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.student) // Default student icon
                    .error(R.drawable.student)
                    .into(ivStudentIcon)
            } else {
                ivStudentIcon.setImageResource(R.drawable.student) // Default if no URL
            }

            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}