package com.example.madarsa_attendance

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FeesDashboardActivity : AppCompatActivity() {

    private val viewModel: FeesDashboardViewModel by viewModels()

    private lateinit var spinnerMonth: Spinner
    private lateinit var spinnerYear: Spinner
    private lateinit var tvFeesMonth: TextView
    private lateinit var tvFeesYear: TextView
    private lateinit var progressBar: ProgressBar

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_fees_dashboard)

        initializeViews()
        setupToolbar()
        setupSpinners()
        setupObservers()
    }

    private fun initializeViews() {
        spinnerMonth = findViewById(R.id.spinner_month)
        spinnerYear = findViewById(R.id.spinner_year)
        tvFeesMonth = findViewById(R.id.tv_fees_this_month)
        tvFeesYear = findViewById(R.id.tv_fees_this_year)
        progressBar = findViewById(R.id.progress_bar_fees)
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val appBarLayout: AppBarLayout = findViewById(R.id.app_bar_layout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
    }

    private fun setupSpinners() {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        // Month Spinner
        val months = SimpleDateFormat("MMMM", Locale.getDefault()).let { sdf ->
            (0..11).map {
                val cal = Calendar.getInstance().apply { set(Calendar.MONTH, it) }
                sdf.format(cal.time)
            }
        }
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)
        spinnerMonth.adapter = monthAdapter
        spinnerMonth.setSelection(currentMonth)

        // Year Spinner
        val years = (currentYear - 5..currentYear + 1).map { it.toString() }.reversed()
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        spinnerYear.adapter = yearAdapter
        spinnerYear.setSelection(years.indexOf(currentYear.toString()))

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadDataForSelectedPeriod()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerMonth.onItemSelectedListener = listener
        spinnerYear.onItemSelectedListener = listener
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        viewModel.monthlyTotal.observe(this) { total ->
            tvFeesMonth.text = currencyFormatter.format(total)
        }
        viewModel.yearlyTotal.observe(this) { total ->
            tvFeesYear.text = currencyFormatter.format(total)
        }
    }

    private fun loadDataForSelectedPeriod() {
        val selectedMonth = spinnerMonth.selectedItemPosition // 0-11
        val selectedYear = spinnerYear.selectedItem.toString().toInt()
        viewModel.loadFeeDataForPeriod(selectedYear, selectedMonth)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}