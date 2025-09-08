package com.example.madarsa_attendance

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.madarsa_attendance.models.Organization
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class ManageDonationsActivity : AppCompatActivity(), DonationAdapter.OnDonationInteractionListener {

    private lateinit var rvDonations: RecyclerView
    private lateinit var adapter: DonationAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoDonations: TextView
    private lateinit var tvTotalDonations: TextView
    private lateinit var fabAddDonation: FloatingActionButton

    // --- NEW: SwipeRefreshLayout ---
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    // --- END OF NEW ---

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var orgDetails: Organization? = null
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    // --- NEW: ActivityResultLauncher to handle refresh after add/edit ---
    private val addEditDonationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadDonations() // Refresh the list
        }
    }
    // --- END OF NEW ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_donations)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        initializeViews() // Call initializeViews before using them

        setSupportActionBar(findViewById(R.id.toolbar_manage_donations))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        loadOrganizationDetails()
        loadDonations()

        fabAddDonation.setOnClickListener {
            val intent = Intent(this, AddEditDonationActivity::class.java)
            addEditDonationLauncher.launch(intent) // Use the launcher
        }

        // --- NEW: Setup SwipeRefreshLayout ---
        swipeRefreshLayout.setOnRefreshListener {
            loadDonations()
        }
        // --- END OF NEW ---
    }

    private fun initializeViews() {
        rvDonations = findViewById(R.id.rv_donations)
        progressBar = findViewById(R.id.progressBarDonations)
        tvNoDonations = findViewById(R.id.tv_no_donations)
        tvTotalDonations = findViewById(R.id.tv_total_donations)
        fabAddDonation = findViewById(R.id.fab_add_donation)
        // --- NEW: Initialize SwipeRefreshLayout ---
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout_donations)
        // --- END OF NEW ---
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
        if (organizationId == null) {
            swipeRefreshLayout.isRefreshing = false
            return
        }
        if (!swipeRefreshLayout.isRefreshing) {
            progressBar.visibility = View.VISIBLE
        }
        db.collection("organizations").document(organizationId!!)
            .collection("donations")
            .orderBy("donationDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                val donations = documents.toObjects<DonationRecord>()
                adapter.updateDonations(donations)
                tvNoDonations.visibility = if (donations.isEmpty()) View.VISIBLE else View.GONE
                val total = donations.sumOf { it.amount }
                tvTotalDonations.text = currencyFormatter.format(total)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- RESTORED: onEditClick logic ---
    override fun onEditClick(donation: DonationRecord) {
        val intent = Intent(this, AddEditDonationActivity::class.java).apply {
            putExtra(AddEditDonationActivity.EXTRA_DONATION, donation)
        }
        addEditDonationLauncher.launch(intent)
    }
    // --- END OF RESTORED LOGIC ---

    // --- RESTORED: onDeleteClick logic ---
    override fun onDeleteClick(donation: DonationRecord) {
        AlertDialog.Builder(this)
            .setTitle("Delete Donation")
            .setMessage("Are you sure you want to delete the donation from ${donation.donorName}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteDonationFromFirestore(donation)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDonationFromFirestore(donation: DonationRecord) {
        if (organizationId.isNullOrBlank()) return

        db.collection("organizations").document(organizationId!!)
            .collection("donations").document(donation.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Donation deleted.", Toast.LENGTH_SHORT).show()
                loadDonations() // Refresh the list after deleting
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    // --- END OF RESTORED LOGIC ---

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

    private fun shareReceiptViaWhatsApp(uri: Uri, donation: DonationRecord) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Thank you for your generous donation, ${donation.donorName}!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val phoneNumber = donation.donorMobile!!.replace(Regex("[^0-9]"), "")
            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Thank you for your generous donation, ${donation.donorName}!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra("jid", "91$phoneNumber@s.whatsapp.net")
                setPackage("com.whatsapp")
            }

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