package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date // For parsing and reformatting timestamp

class PaymentHistoryAdapter(
    private var payments: List<FeePaymentItem>
) : RecyclerView.Adapter<PaymentHistoryAdapter.PaymentHistoryViewHolder>() {

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    // Input format from Firestore for paymentDate "yyyy-MM-dd"
    private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    // Desired display format
    private val displayDateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fee_payment_history, parent, false)
        return PaymentHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentHistoryViewHolder, position: Int) {
        val payment = payments[position]
        holder.bind(payment, currencyFormatter, inputDateFormat, displayDateFormat)
    }

    override fun getItemCount(): Int = payments.size

    fun updateData(newPayments: List<FeePaymentItem>) {
        payments = newPayments
        notifyDataSetChanged()
    }

    class PaymentHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPaymentIcon: ImageView = itemView.findViewById(R.id.ivPaymentIconHistory) // Added
        private val tvPaymentDate: TextView = itemView.findViewById(R.id.tvPaymentDateHistory)
        private val tvPaymentAmount: TextView = itemView.findViewById(R.id.tvPaymentAmountHistory)
        private val tvPaymentMode: TextView = itemView.findViewById(R.id.tvPaymentModeHistory)
        private val tvPaymentNotes: TextView = itemView.findViewById(R.id.tvPaymentNotesHistory)

        fun bind(payment: FeePaymentItem, currencyFormatter: NumberFormat, inputDateFmt: SimpleDateFormat, displayDateFmt: SimpleDateFormat) {
            // ivPaymentIcon.setImageResource(R.drawable.ic_receipt) // Or based on payment mode

            try {
                val date: Date? = inputDateFmt.parse(payment.paymentDate)
                tvPaymentDate.text = if (date != null) displayDateFmt.format(date) else payment.paymentDate
            } catch (e: Exception) {
                tvPaymentDate.text = payment.paymentDate // Fallback to raw date
            }

            tvPaymentAmount.text = currencyFormatter.format(payment.paymentAmount)

            if (!payment.paymentMode.isNullOrEmpty()) {
                tvPaymentMode.text = "Mode: ${payment.paymentMode}"
                tvPaymentMode.visibility = View.VISIBLE
            } else {
                tvPaymentMode.visibility = View.GONE
            }

            if (!payment.notes.isNullOrEmpty()) {
                tvPaymentNotes.text = "Notes: ${payment.notes}"
                tvPaymentNotes.visibility = View.VISIBLE
            } else {
                tvPaymentNotes.visibility = View.GONE
            }
        }
    }
}