package com.example.madarsa_attendance

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.textfield.TextInputEditText

class SuperAdminDashboardActivity : AppCompatActivity() {

    private val viewModel: SuperAdminViewModel by viewModels()
    private lateinit var adapter: OrganizationStatAdapter

    private lateinit var tvTotalOrgs: TextView
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalTeachers: TextView
    private lateinit var rvOrgStats: RecyclerView
    private lateinit var etAnnouncement: TextInputEditText
    private lateinit var btnSend: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_super_admin_dashboard)

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupObservers()

        btnSend.setOnClickListener {
            val message = etAnnouncement.text.toString().trim()
            viewModel.sendAnnouncement(message)
        }
    }

    private fun initializeViews() {
        tvTotalOrgs = findViewById(R.id.tv_total_orgs)
        tvTotalStudents = findViewById(R.id.tv_total_students)
        tvTotalTeachers = findViewById(R.id.tv_total_teachers)
        rvOrgStats = findViewById(R.id.rv_org_stats)
        etAnnouncement = findViewById(R.id.et_announcement)
        btnSend = findViewById(R.id.btn_send_announcement)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val appBarLayout: AppBarLayout = findViewById(R.id.app_bar_layout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = OrganizationStatAdapter(emptyList())
        rvOrgStats.layoutManager = LinearLayoutManager(this)
        rvOrgStats.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) {
            progressBar.visibility = if (it) View.VISIBLE else View.GONE
        }
        viewModel.totalOrgs.observe(this) { tvTotalOrgs.text = it.toString() }
        viewModel.totalStudents.observe(this) { tvTotalStudents.text = it.toString() }
        viewModel.totalTeachers.observe(this) { tvTotalTeachers.text = it.toString() }
        viewModel.orgStatsList.observe(this) { adapter.updateData(it) }

        viewModel.operationStatus.observe(this) { event ->
            event.getContentIfNotHandled()?.let { (isSuccess, message) ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                if (isSuccess) {
                    etAnnouncement.text?.clear()
                }
            }
        }
    }

    // --- REPLACED with the new menu logic ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.super_admin_dashboard_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_manage_users -> {
                startActivity(Intent(this, ManageUsersActivity::class.java))
                true
            }
            R.id.action_logout -> {
                FirebaseAuthManager.logout(this)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    // --- END OF REPLACEMENT ---
}