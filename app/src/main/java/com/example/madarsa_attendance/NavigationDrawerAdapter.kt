package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NavigationDrawerAdapter(
    private val onItemClick: (itemId: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val displayList = mutableListOf<NavigationItem>()
    private var originalList = listOf<NavigationItem>()

    companion object {
        private const val TYPE_SINGLE = 0
        private const val TYPE_HEADER = 1
        private const val TYPE_CHILD = 2
        private const val TYPE_SINGLE_WITH_DIVIDER = 3
    }

    fun setMenuItems(items: List<NavigationItem>) {
        originalList = items
        buildDisplayList()
    }

    private fun buildDisplayList() {
        displayList.clear()
        originalList.forEach { item ->
            when (item) {
                is NavigationItem.SingleItem, is NavigationItem.SingleItemWithDivider -> displayList.add(item)
                is NavigationItem.Header -> {
                    displayList.add(item)
                    if (item.isExpanded) {
                        displayList.addAll(item.children)
                    }
                }
                is NavigationItem.Child -> {}
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayList[position]) {
            is NavigationItem.SingleItemWithDivider -> TYPE_SINGLE_WITH_DIVIDER
            is NavigationItem.SingleItem -> TYPE_SINGLE
            is NavigationItem.Header -> TYPE_HEADER
            is NavigationItem.Child -> TYPE_CHILD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SINGLE -> SingleItemViewHolder(inflater.inflate(R.layout.nav_item_single, parent, false))
            TYPE_SINGLE_WITH_DIVIDER -> SingleItemViewHolder(inflater.inflate(R.layout.nav_item_single_with_divider, parent, false))
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.nav_item_header, parent, false))
            TYPE_CHILD -> ChildViewHolder(inflater.inflate(R.layout.nav_item_child, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayList[position]) {
            is NavigationItem.SingleItem -> (holder as SingleItemViewHolder).bind(item)
            is NavigationItem.SingleItemWithDivider -> (holder as SingleItemViewHolder).bind(item)
            is NavigationItem.Header -> (holder as HeaderViewHolder).bind(item)
            is NavigationItem.Child -> (holder as ChildViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = displayList.size

    inner class SingleItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.single_item_icon)
        private val title: TextView = itemView.findViewById(R.id.single_item_title)

        fun bind(item: NavigationItem.SingleItem) {
            icon.setImageResource(item.iconResId)
            title.text = item.title
            itemView.setOnClickListener { onItemClick(item.id) }
        }

        fun bind(item: NavigationItem.SingleItemWithDivider) {
            icon.setImageResource(item.iconResId)
            title.text = item.title
            itemView.findViewById<View>(R.id.single_item_root_view).setOnClickListener { onItemClick(item.id) }
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.header_icon)
        private val title: TextView = itemView.findViewById(R.id.header_title)
        private val expandIcon: ImageView = itemView.findViewById(R.id.header_expand_icon)

        fun bind(header: NavigationItem.Header) {
            icon.setImageResource(header.iconResId)
            title.text = header.title
            expandIcon.rotation = if (header.isExpanded) 180f else 0f
            itemView.setOnClickListener {
                header.isExpanded = !header.isExpanded
                buildDisplayList()
            }
        }
    }

    // --- UPDATED ChildViewHolder ---
    inner class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // The icon view is no longer needed here
        private val title: TextView = itemView.findViewById(R.id.child_title)

        fun bind(child: NavigationItem.Child) {
            title.text = child.title
            // The code to set the icon is removed
            itemView.setOnClickListener {
                onItemClick(child.id)
            }
        }
    }
}