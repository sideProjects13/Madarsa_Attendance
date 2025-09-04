package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.NumberFormat
import java.util.Locale

class InventoryAdapter(
    private var items: List<InventoryItem>,
    private val listener: OnItemActionClickListener
) : RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder>() {

    interface OnItemActionClickListener {
        fun onEditClick(item: InventoryItem)
        fun onDeleteClick(item: InventoryItem)
    }

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inventory, parent, false)
        return InventoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<InventoryItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class InventoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemName: TextView = itemView.findViewById(R.id.tv_item_name)
        private val itemStock: TextView = itemView.findViewById(R.id.tv_item_stock)
        private val itemPrice: TextView = itemView.findViewById(R.id.tv_item_price)
        private val itemImage: ImageView = itemView.findViewById(R.id.iv_item_image)
        private val optionsMenu: ImageView = itemView.findViewById(R.id.iv_item_options)

        fun bind(item: InventoryItem) {
            itemName.text = item.itemName
            itemStock.text = "Stock: ${item.stockQuantity}"
            itemPrice.text = currencyFormatter.format(item.sellingPrice)

            Glide.with(itemView.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_settings) // Add a placeholder drawable
                .error(R.drawable.ic_settings)
                .into(itemImage)

            optionsMenu.setOnClickListener { showPopupMenu(it, item) }
        }

        private fun showPopupMenu(view: View, item: InventoryItem) {
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.menu_item_options) // Create this menu file
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit_item -> {
                        listener.onEditClick(item)
                        true
                    }
                    R.id.action_delete_item -> {
                        listener.onDeleteClick(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}