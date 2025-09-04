package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class SalesHistoryAdapter(
    private var sales: List<SaleRecord>,
    private val listener: OnSaleInteractionListener
) : RecyclerView.Adapter<SalesHistoryAdapter.SaleViewHolder>() {

    interface OnSaleInteractionListener {
        fun onViewReceiptClick(saleRecord: SaleRecord)
    }

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormatter = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SaleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sale_history, parent, false)
        return SaleViewHolder(view)
    }

    override fun onBindViewHolder(holder: SaleViewHolder, position: Int) {
        holder.bind(sales[position])
    }

    override fun getItemCount(): Int = sales.size

    fun updateSales(newSales: List<SaleRecord>) {
        this.sales = newSales
        notifyDataSetChanged()
    }

    inner class SaleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val studentName: TextView = itemView.findViewById(R.id.tv_student_name)
        private val itemName: TextView = itemView.findViewById(R.id.tv_item_name)
        private val saleDate: TextView = itemView.findViewById(R.id.tv_sale_date)
        private val amountPaid: TextView = itemView.findViewById(R.id.tv_sale_amount_paid)
        private val viewReceiptBtn: Button = itemView.findViewById(R.id.btn_view_receipt)

        fun bind(sale: SaleRecord) {
            studentName.text = sale.studentName
            itemName.text = "Sold: ${sale.itemName}"
            sale.saleDate?.let { saleDate.text = dateFormatter.format(it) }
            amountPaid.text = currencyFormatter.format(sale.amountPaid)

            viewReceiptBtn.setOnClickListener {
                listener.onViewReceiptClick(sale)
            }
        }
    }
}