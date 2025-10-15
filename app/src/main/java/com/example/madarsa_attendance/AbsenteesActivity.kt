package com.example.madarsa_attendance

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AbsenteesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ROLE = "EXTRA_USER_ROLE"
    }

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var spinnerTeacherFilter: AutoCompleteTextView
    private lateinit var rvAbsentees: RecyclerView
    private lateinit var tvNoAbsentees: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var absenteesAdapter: DashboardStudentAdapter
    private lateinit var btnSendWhatsApp: MaterialButton
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var teacherList = mutableListOf<Teacher>()
    private var allAbsentStudents = listOf<DashboardStudentItem>()
    private lateinit var db: FirebaseFirestore

    private var pendingMessages: MutableList<String> = mutableListOf()
    private var totalMessagesToSend = 0

    private var isUserAdmin = false

    private val whatsAppMessageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        sendNextWhatsAppMessage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_absentees)

        db = FirebaseFirestore.getInstance()

        initializeViews()

        val userRole = intent.getStringExtra(EXTRA_USER_ROLE) ?: FirebaseAuthManager.getUserRole(this)
        isUserAdmin = userRole == "admin"

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        loadTeachersIntoFilter()

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }

        btnSendWhatsApp.setOnClickListener {
            confirmAndSendWhatsAppMessage()
        }
    }

    private fun initializeViews() {
        spinnerTeacherFilter = findViewById(R.id.spinner_teacher_filter)
        rvAbsentees = findViewById(R.id.rv_absentees)
        tvNoAbsentees = findViewById(R.id.tv_no_absentees)
        progressBar = findViewById(R.id.progress_bar_absentees)
        btnSendWhatsApp = findViewById(R.id.btn_send_whatsapp)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout_absentees)
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val appBarLayout: AppBarLayout = findViewById(R.id.app_bar_layout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { _, insets ->
            appBarLayout.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
    }

    private fun setupRecyclerView() {
        absenteesAdapter = DashboardStudentAdapter()
        rvAbsentees.layoutManager = LinearLayoutManager(this)
        rvAbsentees.adapter = absenteesAdapter
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            if (!swipeRefreshLayout.isRefreshing) {
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
            if (!isLoading) {
                swipeRefreshLayout.isRefreshing = false
            }
        }
        viewModel.absentStudents.observe(this) { absentees ->
            allAbsentStudents = absentees
            val selectedTeacherName = spinnerTeacherFilter.text.toString()
            val selectedTeacher = teacherList.find { it.teacherName == selectedTeacherName }
            if (selectedTeacher != null && selectedTeacher.teacherId != "ALL") {
                filterAbsentees(selectedTeacher.teacherName)
            } else {
                filterAbsentees(null)
            }
        }
    }

    // --- MODIFIED: THIS FUNCTION NOW CORRECTLY FETCHES CLASSES FOR TEACHERS ---
    private fun loadTeachersIntoFilter() {
        val organizationId = FirebaseAuthManager.getOrganizationId(this) ?: return
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        if (isUserAdmin) {
            // Admin Role: Fetch all teachers as before
            db.collection("organizations").document(organizationId)
                .collection("teachers").orderBy("teacherName").get()
                .addOnSuccessListener { documents ->
                    teacherList.clear()
                    teacherList.add(0, Teacher(teacherId = "ALL", teacherName = "All Classes"))
                    teacherList.addAll(documents.toObjects())
                    setupSpinnerAdapter()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading classes: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // --- THIS IS THE FIX FOR TEACHERS ---
            // Teacher Role: Fetch only the classes assigned to the current user's UID
            db.collection("organizations").document(organizationId)
                .collection("teachers")
                .whereEqualTo("uid", currentUserUid)
                .get()
                .addOnSuccessListener { documents ->
                    teacherList.clear()
                    if (!documents.isEmpty) {
                        teacherList.addAll(documents.toObjects())
                    }
                    setupSpinnerAdapter()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading your class: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            // --- END OF FIX ---
        }
    }

    private fun setupSpinnerAdapter() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teacherList.map { it.teacherName })
        spinnerTeacherFilter.setAdapter(adapter)

        if (teacherList.isNotEmpty()) {
            spinnerTeacherFilter.setText(teacherList[0].teacherName, false)
            filterAbsentees(if (isUserAdmin && teacherList[0].teacherId == "ALL") null else teacherList[0].teacherName)
        }

        spinnerTeacherFilter.setOnItemClickListener { _, _, position, _ ->
            val selectedTeacher = teacherList[position]
            filterAbsentees(if (selectedTeacher.teacherId == "ALL") null else selectedTeacher.teacherName)
        }
    }

    private fun filterAbsentees(teacherName: String?) {
        val filteredList = if (teacherName == null) {
            allAbsentStudents
        } else {
            allAbsentStudents.filter { it.subtitle == teacherName }
        }
        absenteesAdapter.submitList(filteredList)

        if (filteredList.isEmpty()) {
            tvNoAbsentees.visibility = View.VISIBLE
            rvAbsentees.visibility = View.GONE
            btnSendWhatsApp.visibility = View.GONE
        } else {
            tvNoAbsentees.visibility = View.GONE
            rvAbsentees.visibility = View.VISIBLE
            btnSendWhatsApp.visibility = if (isUserAdmin) View.VISIBLE else View.GONE
        }
    }

    private fun confirmAndSendWhatsAppMessage() {
        if (!isUserAdmin) {
            Toast.makeText(this, "This feature is only available for admins.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentAbsentees = absenteesAdapter.currentList
        if (currentAbsentees.isEmpty()) {
            Toast.makeText(this, "No absentees to message.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Send WhatsApp Messages")
            .setMessage("This will open WhatsApp individually for each of the ${currentAbsentees.size} absent students' parents. Continue?")
            .setPositiveButton("Continue") { _, _ ->
                fetchParentNumbersAndStartSequence(currentAbsentees)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchParentNumbersAndStartSequence(absentees: List<DashboardStudentItem>) {
        val loadingDialog = StatusDialogFragment.newInstance(true, "Fetching parent numbers...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "fetchingNumbers")

        val organizationId = FirebaseAuthManager.getOrganizationId(this) ?: return
        val studentIds = absentees.map { it.id }

        lifecycleScope.launch {
            try {
                val parentNumbers = mutableListOf<String>()
                val studentDocs = db.collection("organizations").document(organizationId)
                    .collection("students").whereIn(FieldPath.documentId(), studentIds).get().await()

                for (doc in studentDocs) {
                    val mobile = doc.getString("parentMobileNumber")
                    if (!mobile.isNullOrBlank()) {
                        parentNumbers.add(mobile)
                    }
                }

                loadingDialog.dismiss()
                if (parentNumbers.isEmpty()) {
                    Toast.makeText(this@AbsenteesActivity, "No valid parent mobile numbers found for the absentees.", Toast.LENGTH_LONG).show()
                } else {
                    pendingMessages = parentNumbers.distinct().toMutableList()
                    totalMessagesToSend = pendingMessages.size
                    sendNextWhatsAppMessage()
                }

            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(this@AbsenteesActivity, "Error fetching numbers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendNextWhatsAppMessage() {
        if (pendingMessages.isEmpty()) {
            Toast.makeText(this, "All messages sent!", Toast.LENGTH_SHORT).show()
            return
        }

        val number = pendingMessages.removeAt(0)
        val message = "Dear Parent, Your child was absent from the madrasa today. Please ensure regular attendance."
        val cleanNumber = "91${number.replace(Regex("[^0-9]"), "")}"

        val progress = totalMessagesToSend - pendingMessages.size
        Toast.makeText(this, "Sending message ${progress + 1} of $totalMessagesToSend...", Toast.LENGTH_SHORT).show()

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data =
                "https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}".toUri()
            intent.setPackage("com.whatsapp")
            whatsAppMessageLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "WhatsApp is not installed. Skipping number.", Toast.LENGTH_SHORT).show()
            sendNextWhatsAppMessage()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open WhatsApp for $number. Skipping.", Toast.LENGTH_LONG).show()
            sendNextWhatsAppMessage()
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