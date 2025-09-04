package com.example.madarsa_attendance

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.madarsa_attendance.models.Organization
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

class ManageDonationsActivity : AppCompatActivity(), DonationAdapter.OnDonationInteractionListener {

    private lateinit var rvDonations: RecyclerView
    private lateinit var adapter: DonationAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoDonations: TextView
    private lateinit var tvTotalDonations: TextView
    private lateinit var fabAddDonation: FloatingActionButton

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var orgDetails: Organization? = null
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_donations)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_manage_donations)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rvDonations = findViewById(R.id.rv_donations)
        progressBar = findViewById(R.id.progressBarDonations)
        tvNoDonations = findViewById(R.id.tv_no_donations)
        tvTotalDonations = findViewById(R.id.tv_total_donations)
        fabAddDonation = findViewById(R.id.fab_add_donation)

        setupRecyclerView()
        loadOrganizationDetails()
        loadDonations()

        fabAddDonation.setOnClickListener {
            // This would launch your AddEditDonationActivity
            val intent = Intent(this, AddEditDonationActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        adapter = DonationAdapter(emptyList(), this)
        rvDonations.layoutManager = LinearLayoutManager(this)
        rvDonations.adapter = adapter
    }

    private fun loadOrganizationDetails() {
        if (organizationId == null) return
        db.collection("organizations").document(organizationId!!).get()
            .addOnSuccessListener { doc ->
                orgDetails = doc.toObject(Organization::class.java)
            }
    }

    private fun loadDonations() {
        if (organizationId == null) return
        progressBar.visibility = View.VISIBLE
        db.collection("organizations").document(organizationId!!)
            .collection("donations")
            .orderBy("donationDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                val donations = documents.toObjects<DonationRecord>()
                adapter.updateDonations(donations)
                tvNoDonations.visibility = if (donations.isEmpty()) View.VISIBLE else View.GONE
                val total = donations.sumOf { it.amount }
                tvTotalDonations.text = currencyFormatter.format(total)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onEditClick(donation: DonationRecord) {
        // Launch AddEditDonationActivity with donation.id
    }

    override fun onDeleteClick(donation: DonationRecord) {
        // Your existing delete logic
    }

    override fun onShareClick(donation: DonationRecord) {
        if (orgDetails == null) {
            Toast.makeText(this, "Organization details not loaded yet. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }
        if (donation.donorMobile.isNullOrBlank()) {
            Toast.makeText(this, "Donor mobile number is not available for this donation.", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Generating Receipt...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "receiptGen")

        lifecycleScope.launch {
            // Use the LogoProvider to get the correct, cached logo
            val logoBitmap = LogoProvider.getActiveLogo(applicationContext)
            val receiptUri = withContext(Dispatchers.IO) {
                DonationReceiptGenerator.createReceiptImage(
                    context = applicationContext,
                    orgDetails = orgDetails!!,
                    donation = donation,
                    logoBitmap = logoBitmap
                )
            }

            loadingDialog.dismiss()
            if (receiptUri != null) {
                shareReceiptViaWhatsApp(receiptUri, donation)
            } else {
                Toast.makeText(applicationContext, "Failed to create receipt image.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- THIS IS THE NEW WHATSAPP SHARING FUNCTION ---
    private fun shareReceiptViaWhatsApp(uri: Uri, donation: DonationRecord) {
        try {
            // Create a generic share intent first
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Thank you for your generous donation, ${donation.donorName}!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Create a specific intent for WhatsApp if the number is available
            val phoneNumber = donation.donorMobile!!.replace(Regex("[^0-9]"), "")
            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Thank you for your generous donation, ${donation.donorName}!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Add the phone number to target the specific chat
                putExtra("jid", "91$phoneNumber@s.whatsapp.net")
                setPackage("com.whatsapp")
            }

            // Use a chooser that prioritizes the direct WhatsApp chat
            val chooserIntent = Intent.createChooser(shareIntent, "Share Receipt Via")
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(whatsappIntent))
            startActivity(chooserIntent)

        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "An error occurred while trying to share.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}