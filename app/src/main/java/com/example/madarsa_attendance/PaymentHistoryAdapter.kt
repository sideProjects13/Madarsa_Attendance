package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class PaymentHistoryAdapter(
    private var payments: List<FeePaymentItem>,
    private val onItemLongClick: (FeePaymentItem) -> Unit
) : RecyclerView.Adapter<PaymentHistoryAdapter.PaymentHistoryViewHolder>() {

    // Formatter for the currency amount (e.g., ₹1,000.00)
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    // Formatter for the date the payment was recorded (e.g., 09 Nov, 2025)
    private val displayDateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

    // Parser to read the fee month string from Firestore (e.g., "2025-01")
    private val feeMonthParser = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    // Formatter to display the fee month in a readable format (e.g., "January, 2025")
    private val feeMonthFormatter = SimpleDateFormat("MMMM, yyyy", Locale.getDefault())


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
        // Getting references to the views in your item_fee_payment_history.xml
        private val tvPaymentDate: TextView = itemView.findViewById(R.id.tvPaymentDateHistory)
        private val tvPaymentAmount: TextView = itemView.findViewById(R.id.tvPaymentAmountHistory)
        private val btnOptions: ImageButton = itemView.findViewById(R.id.btn_share_receipt)

        fun bind(payment: FeePaymentItem) {

            // --- THIS IS THE UPDATED LOGIC ---

            // 1. Format the actual date the payment was made
            val formattedPaymentDate = payment.paymentDate?.let { displayDateFormat.format(it) } ?: "N/A"

            // 2. Parse the fee month string ("yyyy-MM") and then format it into a readable string
            val formattedFeeMonth = try {
                // Parse the string like "2025-01" into a Date object
                val feeMonthDate = feeMonthParser.parse(payment.paymentMonth)
                // Format the Date object into a string like "January, 2025"
                feeMonthDate?.let { feeMonthFormatter.format(it) } ?: "N/A"
            } catch (e: Exception) {
                // If parsing fails (e.g., payment.paymentMonth is null or invalid), show "N/A"
                "N/A"
            }

            // 3. Combine both pieces of information into one single string
            val fullDetailsText = "Paid on: $formattedPaymentDate  •  For: $formattedFeeMonth"

            // 4. Set this new, combined string to the TextView
            tvPaymentDate.text = fullDetailsText

            // --- END OF UPDATED LOGIC ---


            // This part for setting the amount and listeners remains the same
            tvPaymentAmount.text = currencyFormatter.format(payment.paymentAmount)

            // Set up the click listener for the options button
            btnOptions.setOnClickListener {
                onItemLongClick(payment)
            }

            // Set up the long click listener for the entire item view
            itemView.setOnLongClickListener {
                onItemLongClick(payment)
                true // Consume the long click event
            }
        }
    }
}