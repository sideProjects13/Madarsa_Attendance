package com.example.madarsa_attendance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class LeaderboardFragment : Fragment() {

    private val viewModel: LeaderboardViewModel by viewModels()

    // CHANGED: From Spinner to AutoCompleteTextView
    private lateinit var spinnerYear: AutoCompleteTextView
    private lateinit var recyclerViewLeaderboard: RecyclerView
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoData: TextView

    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)

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

        val currentYearValue = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYearValue - 5..currentYearValue).map { it.toString() }.reversed()
        val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, years)
        spinnerYear.setAdapter(yearAdapter)

        // Set initial text without triggering listener or showing dropdown
        spinnerYear.setText(selectedYear.toString(), false)

        // CHANGED: Use setOnItemClickListener for AutoCompleteTextView
        spinnerYear.setOnItemClickListener { _, _, position, _ ->
            val year = years[position].toInt()
            if (selectedYear != year) {
                selectedYear = year
                viewModel.loadLeaderboardForYear(selectedYear)
            }
        }

        // Load initial data only if it hasn't been loaded before
        if (viewModel.leaderboardData.value.isNullOrEmpty()) {
            viewModel.loadLeaderboardForYear(selectedYear)
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
            recyclerViewLeaderboard.visibility = if (data.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage != null) {
                tvNoData.text = errorMessage
                tvNoData.visibility = View.VISIBLE
                recyclerViewLeaderboard.visibility = View.GONE
            } else {
                if (viewModel.leaderboardData.value?.isNotEmpty() == true) {
                    tvNoData.visibility = View.GONE
                }
            }
        }
    }
}