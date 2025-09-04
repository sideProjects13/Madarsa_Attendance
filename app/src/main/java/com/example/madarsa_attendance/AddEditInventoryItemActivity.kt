package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.coroutines.resume

class AddEditInventoryItemActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ITEM = "EXTRA_ITEM"
        private const val UNSIGNED_UPLOAD_PRESET = "BIBI_AYESHA_MASJID" // Use your upload preset
    }

    private lateinit var etItemName: TextInputEditText
    private lateinit var etItemStock: TextInputEditText
    private lateinit var etItemPrice: TextInputEditText
    private lateinit var ivItemImage: ImageView
    private lateinit var btnSaveItem: Button
    private lateinit var toolbar: MaterialToolbar

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null

    private var editingItem: InventoryItem? = null
    private var newItemImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                newItemImageUri = uri
                Glide.with(this).load(uri).into(ivItemImage)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_inventory_item)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()

        // Check if we are editing an existing item or adding a new one
        editingItem = intent.getSerializableExtra(EXTRA_ITEM) as? InventoryItem
        if (editingItem != null) {
            populateUiForEdit()
        }

        ivItemImage.setOnClickListener { pickImage() }
        btnSaveItem.setOnClickListener { saveItem() }
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar_add_edit_item)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        etItemName = findViewById(R.id.et_item_name)
        etItemStock = findViewById(R.id.et_item_stock)
        etItemPrice = findViewById(R.id.et_item_price)
        ivItemImage = findViewById(R.id.iv_add_item_image)
        btnSaveItem = findViewById(R.id.btn_save_item)
    }

    private fun populateUiForEdit() {
        toolbar.title = "Edit Item"
        editingItem?.let {
            etItemName.setText(it.itemName)
            etItemStock.setText(it.stockQuantity.toString())
            etItemPrice.setText(it.sellingPrice.toString())
            Glide.with(this)
                .load(it.imageUrl)
                .placeholder(R.drawable.ic_upload_file)
                .into(ivItemImage)
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun saveItem() {
        val itemName = etItemName.text.toString().trim()
        val stockStr = etItemStock.text.toString().trim()
        val priceStr = etItemPrice.text.toString().trim()

        if (itemName.isEmpty() || stockStr.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show()
            return
        }

        val stock = stockStr.toIntOrNull()
        val price = priceStr.toDoubleOrNull()

        if (stock == null || price == null || stock < 0 || price < 0) {
            Toast.makeText(this, "Please enter valid numbers for stock and price.", Toast.LENGTH_SHORT).show()
            return
        }

        if (editingItem == null && newItemImageUri == null) {
            Toast.makeText(this, "Please select an image for the item.", Toast.LENGTH_SHORT).show()
            return
        }

        // Show the loading dialog BEFORE starting the background work
        val loadingDialog = StatusDialogFragment.newInstance(true, "Saving Item...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "savingItem")

        lifecycleScope.launch {
            try {
                var finalImageUrl = editingItem?.imageUrl

                if (newItemImageUri != null) {
                    finalImageUrl = uploadImageToCloudinary(newItemImageUri!!)
                    if (finalImageUrl == null) {
                        // If upload fails, throw an exception to be caught below
                        throw Exception("Image upload failed.")
                    }
                }

                val itemCollection = db.collection("organizations").document(organizationId!!)
                    .collection("inventoryItems")

                val itemToSave = if (editingItem == null) {
                    val newDocRef = itemCollection.document()
                    InventoryItem(
                        id = newDocRef.id,
                        itemName = itemName,
                        stockQuantity = stock,
                        sellingPrice = price,
                        imageUrl = finalImageUrl,
                        createdAt = Date() // Use Date() here
                    )
                } else {
                    editingItem!!.copy(
                        itemName = itemName,
                        stockQuantity = stock,
                        sellingPrice = price,
                        imageUrl = finalImageUrl
                    )
                }

                // Await the Firestore save operation
                itemCollection.document(itemToSave.id).set(itemToSave).await()

                // --- FIX IS HERE: Dialog is dismissed and Toast is shown AFTER await() succeeds ---
                loadingDialog.dismiss()
                Toast.makeText(this@AddEditInventoryItemActivity, "Item saved successfully!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()

            } catch (e: Exception) {
                // --- FIX IS HERE: Dialog is dismissed on ANY failure ---
                loadingDialog.dismiss()
                Toast.makeText(this@AddEditInventoryItemActivity, "Failed to save item: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadImageToCloudinary(uri: Uri): String? {
        return suspendCancellableCoroutine { continuation ->
            MediaManager.get().upload(uri)
                .unsigned(UNSIGNED_UPLOAD_PRESET)
                .option("folder", "inventory_items") // Organize images in Cloudinary
                .callback(object : UploadCallback {
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        if (continuation.isActive) continuation.resume(url)
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                }).dispatch()
        }
    }
}