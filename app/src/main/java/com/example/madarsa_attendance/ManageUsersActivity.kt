package com.example.madarsa_attendance

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.madarsa_attendance.models.AppUser
import com.google.android.material.appbar.MaterialToolbar

class ManageUsersActivity : AppCompatActivity(), ManageUsersAdapter.UserActionListener {

    private val viewModel: ManageUsersViewModel by viewModels()
    private lateinit var adapter: ManageUsersAdapter

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoUsers: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_users)

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupObservers()

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.fetchUsers()
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.rv_users)
        progressBar = findViewById(R.id.progressBarUsers)
        tvNoUsers = findViewById(R.id.tv_no_users)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout_users)
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_manage_users)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = ManageUsersAdapter(emptyList(), this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            if (!swipeRefreshLayout.isRefreshing) {
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        viewModel.users.observe(this) { users ->
            swipeRefreshLayout.isRefreshing = false
            adapter.updateData(users)
            tvNoUsers.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.operationStatus.observe(this) { event ->
            event.getContentIfNotHandled()?.let { (isSuccess, message) ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStatusChange(user: AppUser, newStatus: String) {
        viewModel.updateUserStatus(user, newStatus)
    }
}