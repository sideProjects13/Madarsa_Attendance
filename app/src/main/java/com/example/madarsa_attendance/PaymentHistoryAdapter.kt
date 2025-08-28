package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class PaymentHistoryAdapter(
    private var payments: List<FeePaymentItem>,
    private val onItemLongClick: (FeePaymentItem) -> Unit // Changed to onItemLongClick
) : RecyclerView.Adapter<PaymentHistoryAdapter.PaymentHistoryViewHolder>() {

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val displayDateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fee_payment_history, parent, false)
        return PaymentHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentHistoryViewHolder, position: Int) {
        holder.bind(payments[position])
    }

    override fun getItemCount(): Int = payments.size

    fun updateData(newPayments: List<FeePaymentItem>) {
        this.payments = newPayments
        notifyDataSetChanged()
    }

    inner class PaymentHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPaymentDate: TextView = itemView.findViewById(R.id.tvPaymentDateHistory)
        private val tvPaymentAmount: TextView = itemView.findViewById(R.id.tvPaymentAmountHistory)
        private val btnOptions: ImageButton = itemView.findViewById(R.id.btn_share_receipt) // Renamed for clarity

        fun bind(payment: FeePaymentItem) {
            tvPaymentDate.text = payment.paymentDate?.let { displayDateFormat.format(it) } ?: "No Date"
            tvPaymentAmount.text = currencyFormatter.format(payment.paymentAmount)

            // Use long click on the whole item for options
            itemView.setOnLongClickListener {
                onItemLongClick(payment)
                true // Consume the event
            }

            // Or a simple click on the options button
            btnOptions.setOnClickListener {
                onItemLongClick(payment)
            }
        }
    }
}