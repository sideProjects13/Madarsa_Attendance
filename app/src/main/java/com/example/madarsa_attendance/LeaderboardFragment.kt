package com.example.madarsa_attendance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class LeaderboardFragment : Fragment() {

    // Use the viewModels delegate to get the ViewModel instance
    private val viewModel: LeaderboardViewModel by viewModels()

    private lateinit var spinnerYear: Spinner
    private lateinit var recyclerViewLeaderboard: RecyclerView
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoData: TextView

    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var initialSpinnerSetupDone = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        spinnerYear = view.findViewById(R.id.spinnerYearLeaderboard)
        recyclerViewLeaderboard = view.findViewById(R.id.recyclerViewLeaderboard)
        progressBar = view.findViewById(R.id.progressBarLeaderboard)
        tvNoData = view.findViewById(R.id.tvNoDataLeaderboard)

        // Setup UI components
        setupRecyclerView()
        setupSpinner()
        observeViewModel()
    }

    private fun setupSpinner() {
        if (!isAdded) return
        initialSpinnerSetupDone = false

        val spinnerTextColor = ContextCompat.getColor(requireContext(), R.color.mono_palette_black)
        val currentYearValue = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYearValue - 5..currentYearValue).map { it.toString() }.reversed()
        val yearAdapter = ColorableSpinnerAdapter(requireContext(), years, spinnerTextColor)
        spinnerYear.adapter = yearAdapter

        // Set initial selection without triggering the listener
        spinnerYear.setSelection(years.indexOf(selectedYear.toString()), false)

        spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Prevent loading on initial setup
                if (!initialSpinnerSetupDone) return

                val year = years[position].toInt()
                if (selectedYear != year) {
                    selectedYear = year
                    // Tell the ViewModel to load data for the new year
                    viewModel.loadLeaderboardForYear(selectedYear)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // After the spinner is laid out, mark setup as done and load initial data
        spinnerYear.post {
            initialSpinnerSetupDone = true
            // Load initial data only if it hasn't been loaded before
            if (viewModel.leaderboardData.value.isNullOrEmpty()) {
                viewModel.loadLeaderboardForYear(selectedYear)
            }
        }
    }

    private fun setupRecyclerView() {
        leaderboardAdapter = LeaderboardAdapter(emptyList()) // Start with an empty list
        recyclerViewLeaderboard.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewLeaderboard.adapter = leaderboardAdapter
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.leaderboardData.observe(viewLifecycleOwner) { data ->
            leaderboardAdapter.updateData(data)
            // Show/hide recycler view based on data
            recyclerViewLeaderboard.visibility = if (data.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage != null) {
                tvNoData.text = errorMessage
                tvNoData.visibility = View.VISIBLE
                recyclerViewLeaderboard.visibility = View.GONE
            } else {
                // Hide error message if there's no error (and data isn't empty)
                if (viewModel.leaderboardData.value?.isNotEmpty() == true) {
                    tvNoData.visibility = View.GONE
                }
            }
        }
    }
}