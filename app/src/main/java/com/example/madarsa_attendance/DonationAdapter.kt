package com.example.madarsa_attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class DonationAdapter(
    private var donations: List<DonationRecord>,
    private val listener: OnDonationInteractionListener
) : RecyclerView.Adapter<DonationAdapter.DonationViewHolder>() {

    interface OnDonationInteractionListener {
        fun onEditClick(donation: DonationRecord)
        fun onDeleteClick(donation: DonationRecord)
        fun onShareClick(donation: DonationRecord) // New action
    }

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormatter = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DonationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_donation, parent, false)
        return DonationViewHolder(view)
    }

    override fun onBindViewHolder(holder: DonationViewHolder, position: Int) {
        holder.bind(donations[position])
    }

    override fun getItemCount(): Int = donations.size

    fun updateDonations(newDonations: List<DonationRecord>) {
        this.donations = newDonations
        notifyDataSetChanged()
    }

    inner class DonationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val donorName: TextView = itemView.findViewById(R.id.tv_donor_name)
        private val amount: TextView = itemView.findViewById(R.id.tv_donation_amount)
        private val purpose: TextView = itemView.findViewById(R.id.tv_donation_purpose)
        private val date: TextView = itemView.findViewById(R.id.tv_donation_date)
        private val options: ImageView = itemView.findViewById(R.id.iv_donation_options)

        fun bind(donation: DonationRecord) {
            donorName.text = donation.donorName
            amount.text = currencyFormatter.format(donation.amount)
            purpose.text = donation.purpose.takeIf { !it.isNullOrBlank() } ?: "No purpose specified."
            donation.donationDate?.let { date.text = dateFormatter.format(it) }

            options.setOnClickListener { showPopupMenu(it, donation) }
        }

        private fun showPopupMenu(view: View, donation: DonationRecord) {
            PopupMenu(view.context, view).apply {
                // Inflate a new menu that includes "Share"
                inflate(R.menu.menu_item_options_with_share)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_item -> {
                            listener.onEditClick(donation); true
                        }
                        R.id.action_delete_item -> {
                            listener.onDeleteClick(donation); true
                        }
                        R.id.action_share_item -> { // Handle share click
                            listener.onShareClick(donation); true
                        }
                        else -> false
                    }
                }
                show()
            }
        }
    }
}