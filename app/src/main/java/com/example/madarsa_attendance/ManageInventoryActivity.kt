package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects

class ManageInventoryActivity : AppCompatActivity(), InventoryAdapter.OnItemActionClickListener {

    private lateinit var rvInventory: RecyclerView
    private lateinit var fabAddItem: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoItems: TextView
    private lateinit var adapter: InventoryAdapter

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null

    private val itemActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Item was added or edited successfully, refresh the list
            loadInventoryItems()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_inventory)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        loadInventoryItems()

        fabAddItem.setOnClickListener {
            val intent = Intent(this, AddEditInventoryItemActivity::class.java)
            itemActivityResultLauncher.launch(intent)
        }
    }

    private fun initializeViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_manage_inventory)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        rvInventory = findViewById(R.id.rv_inventory_items)
        fabAddItem = findViewById(R.id.fab_add_item)
        progressBar = findViewById(R.id.progressBarInventory)
        tvNoItems = findViewById(R.id.tv_no_items)
    }

    private fun setupRecyclerView() {
        adapter = InventoryAdapter(emptyList(), this)
        rvInventory.layoutManager = LinearLayoutManager(this)
        rvInventory.adapter = adapter
    }

    private fun loadInventoryItems() {
        progressBar.visibility = View.VISIBLE
        tvNoItems.visibility = View.GONE
        rvInventory.visibility = View.GONE

        db.collection("organizations").document(organizationId!!)
            .collection("inventoryItems")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                val items = documents.toObjects<InventoryItem>()
                if (items.isEmpty()) {
                    tvNoItems.visibility = View.VISIBLE
                    rvInventory.visibility = View.GONE
                } else {
                    tvNoItems.visibility = View.GONE
                    rvInventory.visibility = View.VISIBLE
                    adapter.updateItems(items)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading items: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onEditClick(item: InventoryItem) {
        val intent = Intent(this, AddEditInventoryItemActivity::class.java).apply {
            putExtra(AddEditInventoryItemActivity.EXTRA_ITEM, item)
        }
        itemActivityResultLauncher.launch(intent)
    }

    override fun onDeleteClick(item: InventoryItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete '${item.itemName}'? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteItemFromFirestore(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteItemFromFirestore(item: InventoryItem) {
        val loadingDialog = StatusDialogFragment.newInstance(true, "Deleting...")
        loadingDialog.show(supportFragmentManager, "deleting")

        db.collection("organizations").document(organizationId!!)
            .collection("inventoryItems").document(item.id)
            .delete()
            .addOnSuccessListener {
                loadingDialog.dismiss()
                Toast.makeText(this, "'${item.itemName}' deleted.", Toast.LENGTH_SHORT).show()
                loadInventoryItems() // Refresh the list
            }
            .addOnFailureListener { e ->
                loadingDialog.dismiss()
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}