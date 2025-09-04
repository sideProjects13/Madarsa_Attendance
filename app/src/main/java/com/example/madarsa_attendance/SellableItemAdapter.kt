package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.NumberFormat
import java.util.Locale

class SellableItemAdapter(
    private var items: List<InventoryItem>,
    private val onItemClick: (InventoryItem) -> Unit
) : RecyclerView.Adapter<SellableItemAdapter.SellableViewHolder>() {

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SellableViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inventory_for_sale, parent, false)
        return SellableViewHolder(view)
    }

    override fun onBindViewHolder(holder: SellableViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<InventoryItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    class SellableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemName: TextView = itemView.findViewById(R.id.tv_item_name)
        private val itemStock: TextView = itemView.findViewById(R.id.tv_item_stock)
        private val itemPrice: TextView = itemView.findViewById(R.id.tv_item_price)
        private val itemImage: ImageView = itemView.findViewById(R.id.iv_item_image)

        fun bind(item: InventoryItem) {
            itemName.text = item.itemName
            itemStock.text = "Stock: ${item.stockQuantity}"
            itemPrice.text = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(item.sellingPrice)

            Glide.with(itemView.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_upload_file)
                .error(R.drawable.ic_upload_file)
                .into(itemImage)
        }
    }
}