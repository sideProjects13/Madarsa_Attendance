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
        const val EXTRA_IS_EXTERNAL = "EXTRA_IS_EXTERNAL"
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

    // New Flag
    private var isExternalBuyer: Boolean = false

    // Helpers
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormatter = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sell_item)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        // --- MODIFIED LOGIC TO CHECK BUYER TYPE ---
        student = intent.getSerializableExtra(EXTRA_STUDENT) as? StudentDetailsItem
        isExternalBuyer = intent.getBooleanExtra(EXTRA_IS_EXTERNAL, false)

        if (organizationId == null) {
            Toast.makeText(this, "Error: Organization data missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // If not external and no student provided, show error (Safety check for old flow)
        if (!isExternalBuyer && student == null) {
            Toast.makeText(this, "Error: Missing student data.", Toast.LENGTH_LONG).show()
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

        // Set title based on buyer type
        if (isExternalBuyer) {
            toolbar.title = "Selling to: Outside Buyer"
        } else {
            toolbar.title = "Selling to: ${student?.studentName}"
        }

        rvItems = findViewById(R.id.rv_sellable_items)
        progressBar = findViewById(R.id.progressBarSell)
        tvNoItems = findViewById(R.id.tv_no_items_for_sale)
    }

    private fun setupRecyclerView() {
        adapter = SellableItemAdapter(emptyList()) { item ->
            // --- MODIFIED LOGIC TO CHOOSE DIALOG ---
            if (isExternalBuyer) {
                showConfirmSaleDialogForExternal(item)
            } else {
                showConfirmSaleDialogForStudent(item)
            }
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
            .whereGreaterThan("stockQuantity", 0)
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

    // --- ORIGINAL DIALOG (UNCHANGED LOGIC) ---
    private fun showConfirmSaleDialogForStudent(item: InventoryItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_sale, null)
        val tvItemName: TextView = dialogView.findViewById(R.id.tv_dialog_item_name)
        val tvItemPrice: TextView = dialogView.findViewById(R.id.tv_dialog_item_price)
        val etAmountPaid: TextInputEditText = dialogView.findViewById(R.id.et_amount_paid)

        tvItemName.text = "Item: ${item.itemName}"
        tvItemPrice.text = "Price: ${currencyFormatter.format(item.sellingPrice)}"
        etAmountPaid.setText(item.sellingPrice.toString())

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
                // Use student details
                processSaleTransaction(
                    item,
                    amountPaid,
                    studentName = student!!.studentName,
                    studentRegNo = student!!.regNo,
                    parentName = student!!.parentName,
                    studentId = student!!.id,
                    parentMobile = student!!.parentMobileNumber
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- NEW DIALOG FOR EXTERNAL BUYERS ---
    private fun showConfirmSaleDialogForExternal(item: InventoryItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_sale_external, null)
        val tvItemName: TextView = dialogView.findViewById(R.id.tv_dialog_item_name_ext)
        val tvItemPrice: TextView = dialogView.findViewById(R.id.tv_dialog_item_price_ext)
        val etBuyerName: TextInputEditText = dialogView.findViewById(R.id.et_buyer_name)
        val etBuyerMobile: TextInputEditText = dialogView.findViewById(R.id.et_buyer_mobile)
        val etAmountPaid: TextInputEditText = dialogView.findViewById(R.id.et_amount_paid_ext)

        tvItemName.text = "Item: ${item.itemName}"
        tvItemPrice.text = "Price: ${currencyFormatter.format(item.sellingPrice)}"
        etAmountPaid.setText(item.sellingPrice.toString())

        AlertDialog.Builder(this)
            .setTitle("Confirm Outside Sale")
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val buyerName = etBuyerName.text.toString().trim()
                val buyerMobile = etBuyerMobile.text.toString().trim()
                val amountPaidStr = etAmountPaid.text.toString()
                val amountPaid = amountPaidStr.toDoubleOrNull()

                if (buyerName.isEmpty()) {
                    Toast.makeText(this, "Buyer Name is required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (amountPaid == null || amountPaid < 0) {
                    Toast.makeText(this, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Use custom details, mark ID as GUEST
                processSaleTransaction(
                    item,
                    amountPaid,
                    studentName = buyerName,
                    studentRegNo = "Guest",
                    parentName = "N/A",
                    studentId = "EXTERNAL_GUEST",
                    parentMobile = buyerMobile
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- MODIFIED TRANSACTION FUNCTION TO ACCEPT DETAILS ---
    private fun processSaleTransaction(
        item: InventoryItem,
        amountPaid: Double,
        studentName: String,
        studentRegNo: String?,
        parentName: String?,
        studentId: String,
        parentMobile: String?
    ) {
        val loadingDialog = StatusDialogFragment.newInstance(true, "Processing Sale...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "processingSale")

        val itemRef = db.collection("organizations").document(organizationId!!)
            .collection("inventoryItems").document(item.id)
        val salesRef = db.collection("organizations").document(organizationId!!)
            .collection("sales").document()

        Firebase.firestore.runTransaction { transaction ->
            val itemSnapshot = transaction.get(itemRef)
            val currentStock = itemSnapshot.getLong("stockQuantity")?.toInt() ?: 0

            if (currentStock <= 0) {
                throw Exception("Item is out of stock.")
            }

            transaction.update(itemRef, "stockQuantity", currentStock - 1)

            val saleRecord = SaleRecord(
                id = salesRef.id,
                studentId = studentId, // Will be actual ID or "EXTERNAL_GUEST"
                studentName = studentName,
                studentRegNo = studentRegNo,
                parentName = parentName,
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

            transaction.set(salesRef, saleRecord)

            // Return pair of record and mobile number for receipt sharing
            Pair(saleRecord, parentMobile)

        }.addOnSuccessListener { (saleRecord, mobileNumber) ->
            loadingDialog.dismiss()
            generateAndShareReceipt(saleRecord, mobileNumber)
        }.addOnFailureListener { e ->
            loadingDialog.dismiss()
            Toast.makeText(this, "Sale failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateAndShareReceipt(saleRecord: SaleRecord, mobileNumber: String?) {
        val loadingDialog = StatusDialogFragment.newInstance(true, "Generating Receipt...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "generatingReceipt")

        lifecycleScope.launch {
            val orgLogoBitmap = LogoProvider.getActiveLogo(this@SellItemActivity)
            val itemBitmap = fetchBitmapFromUrl(saleRecord.itemImageUrl)

            val receiptData = ReceiptGenerator.ReceiptData(
                title = "Sales Receipt",
                iconResId = R.drawable.ic_shopping_bag,
                details = listOf(
                    "Buyer/Student" to saleRecord.studentName,
                    "Ref/Reg ID" to (saleRecord.studentRegNo ?: "N/A"),
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

            val receiptUri = ReceiptGenerator.generate(this@SellItemActivity, receiptData)
            loadingDialog.dismiss()

            if (receiptUri != null) {
                shareReceiptToWhatsApp(receiptUri, mobileNumber)
            } else {
                Toast.makeText(this@SellItemActivity, "Failed to generate receipt.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private suspend fun fetchBitmapFromUrl(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
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

    // --- UPDATED SHARE FUNCTION TO USE SPECIFIC NUMBER IF AVAILABLE ---
    private fun shareReceiptToWhatsApp(uri: Uri, mobileNumber: String?) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)

                // If we have a mobile number, try to open chat directly
                if (!mobileNumber.isNullOrEmpty()) {
                    val cleanNumber = mobileNumber.replace(Regex("[^0-9]"), "")
                    // Assuming generic country code 91 if missing, or use as is if length > 10
                    val whatsappNumber = if (cleanNumber.length > 10) cleanNumber else "91$cleanNumber"
                    putExtra("jid", "$whatsappNumber@s.whatsapp.net")
                }

                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            finish()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "WhatsApp not installed.", Toast.LENGTH_LONG).show()
            // Fallback share
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