package com.example.madarsa_attendance

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.madarsa_attendance.models.AppUser
import com.example.madarsa_attendance.models.Organization
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "RegisterActivity"
        private const val PERMISSION_REQUEST_CODE = 102
        private const val UNSIGNED_UPLOAD_PRESET = "BIBI_AYESHA_MASJID"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI Components
    private lateinit var etOrganizationNameRegister: TextInputEditText
    private lateinit var etOrganizationAddress: TextInputEditText
    private lateinit var etAdminNameRegister: TextInputEditText
    private lateinit var etAdminEmailRegister: TextInputEditText
    private lateinit var etAdminMobileRegister: TextInputEditText
    private lateinit var etPasswordRegister: TextInputEditText
    private lateinit var etConfirmPasswordRegister: TextInputEditText
    private lateinit var btnRegisterOrganization: MaterialButton
    private lateinit var progressBarRegister: ProgressBar
    private lateinit var tvGoToLogin: TextView
    private lateinit var ivOrgLogo: ImageView
    private lateinit var btnSelectOrgLogo: MaterialButton

    // Data
    private var imageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.register_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        etOrganizationNameRegister = findViewById(R.id.etOrganizationNameRegister)
        etOrganizationAddress = findViewById(R.id.etOrganizationAddress)
        etAdminNameRegister = findViewById(R.id.etAdminNameRegister)
        etAdminEmailRegister = findViewById(R.id.etAdminEmailRegister)
        etAdminMobileRegister = findViewById(R.id.etAdminMobileRegister)
        etPasswordRegister = findViewById(R.id.etPasswordRegister)
        etConfirmPasswordRegister = findViewById(R.id.etConfirmPasswordRegister)
        btnRegisterOrganization = findViewById(R.id.btnRegisterOrganization)
        progressBarRegister = findViewById(R.id.progressBarRegister)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)
        ivOrgLogo = findViewById(R.id.ivOrgLogo)
        btnSelectOrgLogo = findViewById(R.id.btnSelectOrgLogo)
    }

    private fun setupListeners() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    imageUri = uri
                    Glide.with(this).load(uri).circleCrop().into(ivOrgLogo)
                }
            }
        }
        val imageClickListener = View.OnClickListener { checkAndRequestPermissions() }
        ivOrgLogo.setOnClickListener(imageClickListener)
        btnSelectOrgLogo.setOnClickListener(imageClickListener)
        btnRegisterOrganization.setOnClickListener { registerOrganizationAndAdmin() }
        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun validateInputs(): Boolean {
        if (etOrganizationNameRegister.text.toString().trim().isEmpty()) {
            etOrganizationNameRegister.error = "Organization name is required"
            return false
        }
        if (etOrganizationAddress.text.toString().trim().isEmpty()) {
            etOrganizationAddress.error = "Organization address is required"
            return false
        }
        if (etAdminNameRegister.text.toString().trim().isEmpty()) {
            etAdminNameRegister.error = "Admin name is required"
            return false
        }
        if (etAdminEmailRegister.text.toString().trim().isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(etAdminEmailRegister.text.toString().trim()).matches()) {
            etAdminEmailRegister.error = "A valid email is required"
            return false
        }
        if (etAdminMobileRegister.text.toString().trim().length != 10) {
            etAdminMobileRegister.error = "A valid 10-digit mobile number is required"
            return false
        }
        if (etPasswordRegister.text.toString().trim().length < 6) {
            etPasswordRegister.error = "Password must be at least 6 characters"
            return false
        }
        if (etConfirmPasswordRegister.text.toString().trim() != etPasswordRegister.text.toString().trim()) {
            etConfirmPasswordRegister.error = "Passwords do not match"
            return false
        }
        if (imageUri == null) {
            Toast.makeText(this, "Please select an organization logo", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun registerOrganizationAndAdmin() {
        if (!validateInputs()) return
        setLoading(true)

        val orgName = etOrganizationNameRegister.text.toString().trim()
        val orgAddress = etOrganizationAddress.text.toString().trim()
        val adminName = etAdminNameRegister.text.toString().trim()
        val adminEmail = etAdminEmailRegister.text.toString().trim()
        val adminMobile = etAdminMobileRegister.text.toString().trim()
        val password = etPasswordRegister.text.toString().trim()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Step 1: Upload Logo to Cloudinary
                val logoUrl = withContext(Dispatchers.IO) { uploadImage(imageUri!!) }
                    ?: throw Exception("Logo upload failed. Please try again.")

                // Step 2: Create Firebase Auth User
                val authResult = auth.createUserWithEmailAndPassword(adminEmail, password).await()
                val user = authResult.user ?: throw Exception("Failed to create user account.")

                // Step 3: Create Organization Document in Firestore
                val organization = Organization(
                    organizationName = orgName,
                    address = orgAddress,
                    logoUrl = logoUrl,
                    adminEmail = adminEmail,
                    adminName = adminName,
                    adminMobile = adminMobile,
                    createdAt = FieldValue.serverTimestamp()
                )
                val orgDocRef = db.collection("organizations").add(organization).await()

                // --- THIS IS THE CRITICAL CHANGE ---
                // Step 4: Create User Document in top-level 'users' collection with "pending" status
                val appUser = AppUser(
                    uid = user.uid, // Use the auth UID as the document ID
                    organizationId = orgDocRef.id,
                    role = "admin",
                    email = adminEmail,
                    name = adminName,
                    mobile = adminMobile,
                    organizationName = orgName,
                    accountStatus = "pending" // Set the initial status
                )
                db.collection("users").document(user.uid).set(appUser).await()
                // --- END OF CHANGE ---

                // --- NEW SUCCESS FLOW ---
                // Do NOT log the user in. Show a success message and send them to the login screen.
                auth.signOut() // Immediately sign out the newly created user
                setLoading(false)
                StatusDialogFragment.newInstance(true, "Registration successful! Your account is pending approval by the Super Admin.", true)
                    .show(supportFragmentManager, "successDialog")
                // The dialog's finishActivityOnDismiss will handle closing this activity.

            } catch (e: Exception) {
                setLoading(false)
                StatusDialogFragment.newInstance(false, e.message ?: "Registration Failed").show(supportFragmentManager, "failureDialog")
                Log.e(TAG, "Registration failed", e)
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
                        if (continuation.isActive) continuation.cancel(Exception(error.description))
                    }
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                }).dispatch()
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBarRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnRegisterOrganization.isEnabled = !isLoading
        etOrganizationNameRegister.isEnabled = !isLoading
        etOrganizationAddress.isEnabled = !isLoading
        etAdminNameRegister.isEnabled = !isLoading
        etAdminEmailRegister.isEnabled = !isLoading
        etAdminMobileRegister.isEnabled = !isLoading
        etPasswordRegister.isEnabled = !isLoading
        etConfirmPasswordRegister.isEnabled = !isLoading
        tvGoToLogin.isEnabled = !isLoading
        btnSelectOrgLogo.isEnabled = !isLoading
        ivOrgLogo.isEnabled = !isLoading
    }

    // This function is no longer needed here as we don't auto-login
    // private fun saveLoginSession(...) { ... }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        } else {
            openGallery()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }
}