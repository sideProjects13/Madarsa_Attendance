package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.madarsa_attendance.models.Organization // Make sure to import your new data class
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AdminProfileActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "AdminProfileActivity"
        private const val UNSIGNED_UPLOAD_PRESET = "BIBI_AYESHA_MASJID"
        private const val DEFAULT_ABSENCE_THRESHOLD = 3 // Default value
    }

    private lateinit var etOrgName: TextInputEditText
    private lateinit var etOrgAddress: TextInputEditText
    private lateinit var ivLogo: ImageView
    private lateinit var btnSaveProfile: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var contentLayout: LinearLayout

    // --- 1. NEW VIEW VARIABLE for the threshold ---
    private lateinit var etAbsenceThreshold: TextInputEditText
    // --- END OF NEW VARIABLE ---

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null

    private var newLogoUri: Uri? = null
    private var existingLogoUrl: String? = null

    private val imagePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    newLogoUri = uri
                    Glide.with(this).load(uri).circleCrop().into(ivLogo)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_profile)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupListeners()
        loadOrganizationData()
    }

    private fun initializeViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_admin_profile)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        etOrgName = findViewById(R.id.et_org_name)
        etOrgAddress = findViewById(R.id.et_org_address)
        ivLogo = findViewById(R.id.iv_logo)
        btnSaveProfile = findViewById(R.id.btn_save_profile)
        progressBar = findViewById(R.id.progressBarProfile)
        contentLayout = findViewById(R.id.content_layout)

        // --- 2. INITIALIZE THE NEW VIEW using its ID from the XML ---
        etAbsenceThreshold = findViewById(R.id.et_absence_threshold)
        // --- END OF INITIALIZATION ---
    }

    private fun setupListeners() {
        ivLogo.setOnClickListener { pickImage() }
        btnSaveProfile.setOnClickListener { saveChanges() }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun loadOrganizationData() {
        progressBar.visibility = View.VISIBLE
        contentLayout.visibility = View.GONE

        db.collection("organizations").document(organizationId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val org = document.toObject(Organization::class.java)
                    etOrgName.setText(org?.organizationName)
                    etOrgAddress.setText(org?.address)

                    // --- 3. LOAD THE THRESHOLD VALUE, with a default if it's not set yet ---
                    val threshold = org?.highAbsenceThreshold ?: DEFAULT_ABSENCE_THRESHOLD
                    etAbsenceThreshold.setText(threshold.toString())
                    // --- END OF LOADING THRESHOLD ---

                    existingLogoUrl = org?.logoUrl
                    loadImage(existingLogoUrl, ivLogo)
                }
                progressBar.visibility = View.GONE
                contentLayout.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                contentLayout.visibility = View.VISIBLE
                Toast.makeText(this, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadImage(url: String?, imageView: ImageView) {
        if (!url.isNullOrEmpty()) {
            Glide.with(this).load(url).circleCrop().placeholder(R.drawable.ic_upload_file).into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_upload_file)
        }
    }

    private fun saveChanges() {
        val orgName = etOrgName.text.toString().trim()
        val orgAddress = etOrgAddress.text.toString().trim()

        // --- 4. GET AND VALIDATE THE THRESHOLD VALUE ---
        val thresholdText = etAbsenceThreshold.text.toString().trim()
        val absenceThreshold = thresholdText.toIntOrNull()

        if (orgName.isEmpty() || orgAddress.isEmpty()) {
            Toast.makeText(this, "Organization Name and Address are required.", Toast.LENGTH_SHORT).show()
            return
        }

        if (absenceThreshold == null || absenceThreshold < 1) {
            Toast.makeText(this, "Please enter a valid number for the threshold (1 or greater).", Toast.LENGTH_SHORT).show()
            return
        }
        // --- END OF VALIDATION ---

        val loadingDialog = StatusDialogFragment.newInstance(true, "Saving...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "loading")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                var finalLogoUrl = existingLogoUrl
                if (newLogoUri != null) {
                    finalLogoUrl = withContext(Dispatchers.IO) { uploadImage(newLogoUri!!) }
                }

                val updates = hashMapOf<String, Any>(
                    "organizationName" to orgName,
                    "address" to orgAddress,
                    "logoUrl" to (finalLogoUrl ?: ""),
                    // --- 5. ADD THE THRESHOLD TO THE FIRESTORE UPDATE MAP ---
                    "highAbsenceThreshold" to absenceThreshold
                    // --- END OF ADDITION ---
                )

                db.collection("organizations").document(organizationId!!).update(updates).await()

                FirebaseAuthManager.saveLoginSession(
                    this@AdminProfileActivity, "admin", organizationId!!,
                    orgName, finalLogoUrl, orgAddress
                )

                // You might want to create a provider for org settings too if you need this value elsewhere
                // LogoProvider.clearCache()

                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    loadingDialog.dismiss()
                    StatusDialogFragment.newInstance(true, "Profile Updated Successfully!").show(supportFragmentManager, "successDialog")
                }

            } catch (e: Exception) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    loadingDialog.dismiss()
                    StatusDialogFragment.newInstance(false, "Failed to save changes.").show(supportFragmentManager, "failureDialog")
                }
                Log.e(TAG, "Error saving profile", e)
            }
        }
    }

    private suspend fun uploadImage(uri: Uri): String? {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            MediaManager.get().upload(uri)
                .unsigned(UNSIGNED_UPLOAD_PRESET)
                .option("folder", "org_logos")
                .callback(object : UploadCallback {
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        if (continuation.isActive) continuation.resume(url, null)
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        if (continuation.isActive) continuation.resume(null, null)
                    }
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                }).dispatch()
        }
    }
}