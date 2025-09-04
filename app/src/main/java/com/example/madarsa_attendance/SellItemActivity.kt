package com.example.madarsa_attendance

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SellItemActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STUDENT = "EXTRA_STUDENT"
    }

    // UI Views
    private lateinit var rvItems: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoItems: TextView
    private lateinit var adapter: SellableItemAdapter
    private lateinit var toolbar: MaterialToolbar

    // Data and Firebase
    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var student: StudentDetailsItem? = null

    // Helpers
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormatter = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sell_item)

        // Retrieve passed data
        student = intent.getSerializableExtra(EXTRA_STUDENT) as? StudentDetailsItem
        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        // Validate necessary data
        if (organizationId == null || student == null) {
            Toast.makeText(this, "Error: Missing required student or organization data.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        loadAvailableItems()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar_sell_item)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.title = "Selling to: ${student?.studentName}"

        rvItems = findViewById(R.id.rv_sellable_items)
        progressBar = findViewById(R.id.progressBarSell)
        tvNoItems = findViewById(R.id.tv_no_items_for_sale)
    }

    private fun setupRecyclerView() {
        adapter = SellableItemAdapter(emptyList()) { item ->
            // When an item is clicked, show the confirmation dialog
            showConfirmSaleDialog(item)
        }
        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = adapter
    }

    private fun loadAvailableItems() {
        progressBar.visibility = View.VISIBLE
        tvNoItems.visibility = View.GONE
        rvItems.visibility = View.GONE

        db.collection("organizations").document(organizationId!!)
            .collection("inventoryItems")
            .whereGreaterThan("stockQuantity", 0) // Only fetch items that are in stock
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                val items = documents.toObjects<InventoryItem>()
                if (items.isEmpty()) {
                    tvNoItems.visibility = View.VISIBLE
                } else {
                    rvItems.visibility = View.VISIBLE
                    adapter.updateItems(items)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading items: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showConfirmSaleDialog(item: InventoryItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_sale, null)
        val tvItemName: TextView = dialogView.findViewById(R.id.tv_dialog_item_name)
        val tvItemPrice: TextView = dialogView.findViewById(R.id.tv_dialog_item_price)
        val etAmountPaid: TextInputEditText = dialogView.findViewById(R.id.et_amount_paid)

        tvItemName.text = "Item: ${item.itemName}"
        tvItemPrice.text = "Price: ${currencyFormatter.format(item.sellingPrice)}"
        etAmountPaid.setText(item.sellingPrice.toString()) // Pre-fill with the full price

        AlertDialog.Builder(this)
            .setTitle("Confirm Sale")
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val amountPaidStr = etAmountPaid.text.toString()
                val amountPaid = amountPaidStr.toDoubleOrNull()
                if (amountPaid == null || amountPaid < 0) {
                    Toast.makeText(this, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                processSaleTransaction(item, amountPaid)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processSaleTransaction(item: InventoryItem, amountPaid: Double) {
        val loadingDialog = StatusDialogFragment.newInstance(true, "Processing Sale...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "processingSale")

        val itemRef = db.collection("organizations").document(organizationId!!)
            .collection("inventoryItems").document(item.id)
        val salesRef = db.collection("organizations").document(organizationId!!)
            .collection("sales").document() // Create a new document reference for the sale

        // Use a Firestore transaction to ensure atomic operations (stock decrement and sale creation)
        Firebase.firestore.runTransaction { transaction ->
            val itemSnapshot = transaction.get(itemRef)
            val currentStock = itemSnapshot.getLong("stockQuantity")?.toInt() ?: 0

            // Fail the transaction if the item just went out of stock
            if (currentStock <= 0) {
                throw Exception("Item is out of stock.")
            }

            // 1. Decrement the stock quantity
            transaction.update(itemRef, "stockQuantity", currentStock - 1)

            // 2. Create the sale record object
            val saleRecord = SaleRecord(
                id = salesRef.id,
                studentId = student!!.id,
                studentName = student!!.studentName,
                studentRegNo = student!!.regNo,
                parentName = student!!.parentName,
                itemId = item.id,
                itemName = item.itemName,
                itemImageUrl = item.imageUrl,
                quantitySold = 1,
                pricePerItem = item.sellingPrice,
                totalAmount = item.sellingPrice,
                amountPaid = amountPaid,
                amountDue = item.sellingPrice - amountPaid,
                saleDate = Date()
            )

            // 3. Set the new sale record in the 'sales' collection
            transaction.set(salesRef, saleRecord)

            // Return the created sale record to the success listener
            saleRecord
        }.addOnSuccessListener { saleRecord ->
            loadingDialog.dismiss()
            // If the transaction is successful, generate and share the receipt
            generateAndShareReceipt(saleRecord)
        }.addOnFailureListener { e ->
            loadingDialog.dismiss()
            Toast.makeText(this, "Sale failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateAndShareReceipt(saleRecord: SaleRecord) {
        val loadingDialog = StatusDialogFragment.newInstance(true, "Generating Receipt...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "generatingReceipt")

        lifecycleScope.launch {
            // 1. Fetch required images in the background
            val orgLogoBitmap = LogoProvider.getActiveLogo(this@SellItemActivity)
            val itemBitmap = fetchBitmapFromUrl(saleRecord.itemImageUrl)

            // 2. Prepare the data payload for our reusable generator
            val receiptData = ReceiptGenerator.ReceiptData(
                title = "Sales Receipt",
                iconResId = R.drawable.ic_shopping_bag, // Make sure you have this icon
                details = listOf(
                    "Student Name" to saleRecord.studentName,
                    "Registration ID" to (saleRecord.studentRegNo ?: "N/A"),
                    "Parent Name" to (saleRecord.parentName ?: "N/A"),
                    "Item Sold" to saleRecord.itemName,
                    "Date of Sale" to dateFormatter.format(saleRecord.saleDate!!)
                ),
                summary = listOf(
                    "Total Amount" to currencyFormatter.format(saleRecord.totalAmount),
                    "Amount Paid" to currencyFormatter.format(saleRecord.amountPaid),
                    "Amount Due" to currencyFormatter.format(saleRecord.amountDue)
                ),
                watermarkBitmap = orgLogoBitmap,
                featuredItemBitmap = itemBitmap,
                studentNameForFilename = saleRecord.studentName
            )

            // 3. Generate the receipt image using the data
            val receiptUri = ReceiptGenerator.generate(this@SellItemActivity, receiptData)
            loadingDialog.dismiss()

            if (receiptUri != null) {
                shareReceiptToWhatsApp(receiptUri)
            } else {
                Toast.makeText(this@SellItemActivity, "Failed to generate receipt.", Toast.LENGTH_LONG).show()
                finish() // Finish activity even if receipt fails
            }
        }
    }

    private suspend fun fetchBitmapFromUrl(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        // Run on IO dispatcher for network operations
        return withContext(Dispatchers.IO) {
            try {
                Glide.with(this@SellItemActivity)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get()
            } catch (e: Exception) {
                Log.e("SellItemActivity", "Failed to fetch item bitmap", e)
                null
            }
        }
    }

    private fun shareReceiptToWhatsApp(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                // This targets WhatsApp specifically
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            // Finish this activity after successfully launching WhatsApp
            finish()
        } catch (e: ActivityNotFoundException) {
            // This block runs if WhatsApp is not installed
            Toast.makeText(this, "WhatsApp not installed. Opening share options.", Toast.LENGTH_LONG).show()
            val genericIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(genericIntent, "Share Receipt Via"))
            finish()
        }
    }
}