package com.example.madarsa_attendance

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SalesHistoryActivity : AppCompatActivity(), SalesHistoryAdapter.OnSaleInteractionListener {

    // UI Views
    private lateinit var rvSales: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoSales: TextView
    private lateinit var tvRevenueToday: TextView
    private lateinit var tvRevenueMonth: TextView
    private lateinit var tvRevenueYear: TextView
    private lateinit var tvRevenueTotal: TextView
    private lateinit var tvDateRange: TextView
    private lateinit var btnFilterDate: Button
    private lateinit var adapter: SalesHistoryAdapter

    // Data and Firebase
    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var allSales: List<SaleRecord> = emptyList() // Cache for all sales records

    // Helpers
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_history)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        loadSalesHistory() // Load all sales initially

        btnFilterDate.setOnClickListener { showDateRangePicker() }
    }

    private fun initializeViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_sales_history)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Summary Cards
        tvRevenueToday = findViewById(R.id.tv_revenue_today)
        tvRevenueMonth = findViewById(R.id.tv_revenue_month)
        tvRevenueYear = findViewById(R.id.tv_revenue_year)
        tvRevenueTotal = findViewById(R.id.tv_revenue_total)

        // Filter and List
        tvDateRange = findViewById(R.id.tv_date_range)
        btnFilterDate = findViewById(R.id.btn_filter_date)
        rvSales = findViewById(R.id.rv_sales_history)
        progressBar = findViewById(R.id.progressBarSales)
        tvNoSales = findViewById(R.id.tv_no_sales)
    }

    private fun setupRecyclerView() {
        adapter = SalesHistoryAdapter(emptyList(), this)
        rvSales.layoutManager = LinearLayoutManager(this)
        rvSales.adapter = adapter
    }

    private fun loadSalesHistory() {
        progressBar.visibility = View.VISIBLE
        tvNoSales.visibility = View.GONE
        rvSales.visibility = View.GONE

        // Fetch all sales records from Firestore, ordered by most recent
        db.collection("organizations").document(organizationId!!)
            .collection("sales")
            .orderBy("saleDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                allSales = documents.toObjects() // Store all records in our cache

                // Initially, display all sales in the list
                adapter.updateSales(allSales)

                if (allSales.isEmpty()) {
                    tvNoSales.visibility = View.VISIBLE
                    rvSales.visibility = View.GONE
                } else {
                    tvNoSales.visibility = View.GONE
                    rvSales.visibility = View.VISIBLE
                }

                // Calculate and display summaries based on the complete list
                calculateAndDisplaySummaries()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading sales: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateAndDisplaySummaries() {
        val now = Calendar.getInstance()

        // Today's Revenue
        val todayStart = now.clone() as Calendar
        todayStart.set(Calendar.HOUR_OF_DAY, 0); todayStart.set(Calendar.MINUTE, 0); todayStart.set(Calendar.SECOND, 0)
        val todayRevenue = allSales
            .filter { it.saleDate != null && !it.saleDate.before(todayStart.time) }
            .sumOf { it.amountPaid }
        tvRevenueToday.text = currencyFormatter.format(todayRevenue)

        // This Month's Revenue
        val monthStart = now.clone() as Calendar
        monthStart.set(Calendar.DAY_OF_MONTH, 1); monthStart.set(Calendar.HOUR_OF_DAY, 0); monthStart.set(Calendar.MINUTE, 0)
        val monthRevenue = allSales
            .filter { it.saleDate != null && !it.saleDate.before(monthStart.time) }
            .sumOf { it.amountPaid }
        tvRevenueMonth.text = currencyFormatter.format(monthRevenue)

        // This Year's Revenue
        val yearStart = now.clone() as Calendar
        yearStart.set(Calendar.DAY_OF_YEAR, 1); yearStart.set(Calendar.HOUR_OF_DAY, 0); yearStart.set(Calendar.MINUTE, 0)
        val yearRevenue = allSales
            .filter { it.saleDate != null && !it.saleDate.before(yearStart.time) }
            .sumOf { it.amountPaid }
        tvRevenueYear.text = currencyFormatter.format(yearRevenue)

        // All Time Revenue
        val totalRevenue = allSales.sumOf { it.amountPaid }
        tvRevenueTotal.text = currencyFormatter.format(totalRevenue)
    }

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection: Pair<Long, Long> ->
            val startDate = Date(selection.first)
            // Adjust end date to include the whole day (until 23:59:59)
            val calendar = Calendar.getInstance().apply { time = Date(selection.second); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
            val endDate = calendar.time

            tvDateRange.text = "Showing: ${dateFormatter.format(startDate)} - ${dateFormatter.format(endDate)}"

            // Filter the cached list instead of re-querying Firestore
            val filteredSales = allSales.filter { it.saleDate != null && !it.saleDate.before(startDate) && !it.saleDate.after(endDate) }
            adapter.updateSales(filteredSales)

            if (filteredSales.isEmpty()) {
                tvNoSales.visibility = View.VISIBLE
                rvSales.visibility = View.GONE
            } else {
                tvNoSales.visibility = View.GONE
                rvSales.visibility = View.VISIBLE
            }
        }
        dateRangePicker.show(supportFragmentManager, "DATE_RANGE_PICKER")
    }

    override fun onViewReceiptClick(saleRecord: SaleRecord) {
        val loadingDialog = StatusDialogFragment.newInstance(true, "Generating Receipt...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "generatingReceipt")

        lifecycleScope.launch {
            val orgLogoBitmap = LogoProvider.getActiveLogo(this@SalesHistoryActivity)
            val itemBitmap = fetchBitmapFromUrl(saleRecord.itemImageUrl)

            val receiptData = ReceiptGenerator.ReceiptData(
                title = "Sales Receipt",
                iconResId = R.drawable.ic_shopping_bag,
                details = listOf(
                    "Student Name" to saleRecord.studentName,
                    "Registration ID" to (saleRecord.studentRegNo ?: "N/A"),
                    "Parent Name" to (saleRecord.parentName ?: "N/A"),
                    "Item Sold" to saleRecord.itemName,
                    "Date of Sale" to SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(saleRecord.saleDate!!)
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

            val receiptUri = ReceiptGenerator.generate(this@SalesHistoryActivity, receiptData)
            loadingDialog.dismiss()

            if (receiptUri != null) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(receiptUri, "image/png")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this@SalesHistoryActivity, "No app found to view images.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@SalesHistoryActivity, "Failed to generate receipt.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun fetchBitmapFromUrl(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return withContext(Dispatchers.IO) {
            try {
                Glide.with(this@SalesHistoryActivity)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get()
            } catch (e: Exception) {
                Log.e("SalesHistoryActivity", "Failed to fetch item bitmap", e)
                null
            }
        }
    }
}