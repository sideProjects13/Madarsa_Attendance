package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.ktx.app
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTeacherActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "AddTeacherActivity"
        private const val PERMISSION_REQUEST_CODE = 101
    }

    // Existing Views
    private lateinit var etTeacherName: TextInputEditText
    private lateinit var etTeacherMobile: TextInputEditText
    private lateinit var etTeacherEmail: TextInputEditText
    private lateinit var etTeacherPassword: TextInputEditText
    private lateinit var etTeacherConfirmPassword: TextInputEditText
    private lateinit var tilTeacherName: TextInputLayout
    private lateinit var tilTeacherMobile: TextInputLayout
    private lateinit var tilTeacherEmail: TextInputLayout
    private lateinit var tilTeacherPassword: TextInputLayout
    private lateinit var tilTeacherConfirmPassword: TextInputLayout
    private lateinit var ivTeacherProfileImage: ImageView
    private lateinit var cardViewProfileImage: MaterialCardView
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var btnSaveTeacher: MaterialButton
    private lateinit var progressBar: ProgressBar

    // --- NEW Views for Class Timing ---
    private lateinit var etStartTime: TextInputEditText
    private lateinit var etEndTime: TextInputEditText
    private lateinit var tilStartTime: TextInputLayout
    private lateinit var tilEndTime: TextInputLayout
    // --- END of NEW Views ---

    // Existing Properties
    private lateinit var db: FirebaseFirestore
    private lateinit var mainAuth: FirebaseAuth
    private lateinit var secondaryAuth: FirebaseAuth
    private var currentOrganizationId: String? = null
    private var imageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private val UNSIGNED_UPLOAD_PRESET = "BIBI_AYESHA_MASJID"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_teacher)

        db = FirebaseFirestore.getInstance()
        mainAuth = FirebaseAuth.getInstance()

        if (FirebaseApp.getApps(this).none { it.name == "secondary" }) {
            val options = FirebaseApp.getInstance().options
            Firebase.initialize(this, options, "secondary")
        }
        secondaryAuth = FirebaseAuth.getInstance(Firebase.app("secondary"))

        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)

        if (currentOrganizationId == null) {
            Toast.makeText(this, "Organization data missing. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.add_teacher_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        ivTeacherProfileImage = findViewById(R.id.ivTeacherProfileImage)
        cardViewProfileImage = findViewById(R.id.cardViewProfileImage)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnSaveTeacher = findViewById(R.id.btnSaveTeacher)
        progressBar = findViewById(R.id.progressBarAddTeacher)
        etTeacherName = findViewById(R.id.etTeacherName)
        etTeacherMobile = findViewById(R.id.etTeacherMobile)
        etTeacherEmail = findViewById(R.id.etTeacherEmail)
        etTeacherPassword = findViewById(R.id.etTeacherPassword)
        etTeacherConfirmPassword = findViewById(R.id.etTeacherConfirmPassword)
        tilTeacherName = findViewById(R.id.tilTeacherName)
        tilTeacherMobile = findViewById(R.id.tilTeacherMobile)
        tilTeacherEmail = findViewById(R.id.tilTeacherEmail)
        tilTeacherPassword = findViewById(R.id.tilTeacherPassword)
        tilTeacherConfirmPassword = findViewById(R.id.tilTeacherConfirmPassword)

        // --- NEW: Initialize time fields ---
        etStartTime = findViewById(R.id.etStartTime)
        etEndTime = findViewById(R.id.etEndTime)
        tilStartTime = findViewById(R.id.tilStartTime)
        tilEndTime = findViewById(R.id.tilEndTime)
        // --- END of NEW Initialization ---
    }

    private fun setupListeners() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    imageUri = uri
                    Glide.with(this).load(uri).circleCrop().into(ivTeacherProfileImage)
                }
            }
        }
        val imageClickListener = View.OnClickListener { checkAndRequestPermissions() }
        btnSelectImage.setOnClickListener(imageClickListener)
        cardViewProfileImage.setOnClickListener(imageClickListener)
        btnSaveTeacher.setOnClickListener { saveTeacherFlow() }

        // --- NEW: Time Picker Listeners ---
        etStartTime.setOnClickListener { showTimePicker(isStartTime = true) }
        etEndTime.setOnClickListener { showTimePicker(isStartTime = false) }
        // --- END of NEW Listeners ---
    }

    private fun validateInputs(): Boolean {
        tilTeacherName.error = null
        tilTeacherMobile.error = null
        tilTeacherEmail.error = null
        tilTeacherPassword.error = null
        tilTeacherConfirmPassword.error = null
        // --- NEW: Clear time field errors ---
        tilStartTime.error = null
        tilEndTime.error = null
        // --- END of NEW ---
        var isValid = true

        if (etTeacherName.text.toString().trim().isEmpty()) {
            tilTeacherName.error = "Class/Teacher name cannot be empty"
            isValid = false
        }
        if (etTeacherMobile.text.toString().trim().length != 10) {
            tilTeacherMobile.error = "Enter a valid 10-digit mobile number"
            isValid = false
        }
        val email = etTeacherEmail.text.toString().trim()
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilTeacherEmail.error = "Enter a valid email address"
            isValid = false
        }
        val password = etTeacherPassword.text.toString().trim()
        if (password.length < 6) {
            tilTeacherPassword.error = "Password must be at least 6 characters"
            isValid = false
        }
        if (etTeacherConfirmPassword.text.toString().trim() != password) {
            tilTeacherConfirmPassword.error = "Passwords do not match"
            isValid = false
        }
        // --- NEW: Add validation for time fields ---
        if (etStartTime.text.toString().isBlank()) {
            tilStartTime.error = "Required"
            isValid = false
        }
        if (etEndTime.text.toString().isBlank()) {
            tilEndTime.error = "Required"
            isValid = false
        }
        // --- END of NEW Validation ---
        return isValid
    }

    private fun saveTeacherFlow() {
        if (!validateInputs()) return
        setInputsEnabled(false)

        val teacherName = etTeacherName.text.toString().trim()
        val mobileNumber = etTeacherMobile.text.toString().trim()
        val email = etTeacherEmail.text.toString().trim()
        val password = etTeacherPassword.text.toString().trim()
        // --- NEW: Get time values from the tag (24-hour format) ---
        val startTime = etStartTime.tag as? String ?: ""
        val endTime = etEndTime.tag as? String ?: ""
        // --- END of NEW ---

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val teacherUid = getOrCreateAuthUser(email, password, teacherName)

                val imageUrl = imageUri?.let { withContext(Dispatchers.IO) { uploadImage(it) } }

                val teacherData = hashMapOf(
                    "teacherName" to teacherName,
                    "mobileNumber" to mobileNumber,
                    "email" to email,
                    "uid" to teacherUid,
                    "profileImageUrl" to (imageUrl ?: ""),
                    "createdAt" to FieldValue.serverTimestamp(),
                    // --- NEW: Add time fields to the data map ---
                    "startTime" to startTime,
                    "endTime" to endTime
                    // --- END of NEW ---
                )
                db.collection("organizations").document(currentOrganizationId!!)
                    .collection("teachers").add(teacherData).await()

                StatusDialogFragment.newInstance(true, "Class Added Successfully!", true)
                    .show(supportFragmentManager, "successDialog")
                setResult(Activity.RESULT_OK)

            } catch (e: Exception) {
                Log.e(TAG, "Error during teacher/class creation", e)
                handleSaveFailure(e, e.message ?: "An unknown error occurred.")
            }
        }
    }

    // --- NEW: Function to show the time picker dialog ---
    private fun showTimePicker(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(currentHour)
            .setMinute(currentMinute)
            .setTitleText(if (isStartTime) "Select Start Time" else "Select End Time")
            .build()

        timePicker.addOnPositiveButtonClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute

            // Format to HH:mm (24-hour) for saving to Firestore
            val formattedTimeForDb = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

            // Format for display (12-hour with AM/PM)
            val displayFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val calendarForDisplay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val displayTime = displayFormat.format(calendarForDisplay.time)

            if (isStartTime) {
                etStartTime.setText(displayTime)
                etStartTime.tag = formattedTimeForDb // Store 24-hour format in tag for saving
            } else {
                etEndTime.setText(displayTime)
                etEndTime.tag = formattedTimeForDb // Store 24-hour format in tag for saving
            }
        }
        timePicker.show(supportFragmentManager, if (isStartTime) "startTimePicker" else "endTimePicker")
    }
    // --- END of NEW Function ---

    private suspend fun getOrCreateAuthUser(email: String, password: String, teacherName: String): String {
        try {
            val authResult = secondaryAuth.createUserWithEmailAndPassword(email, password).await()
            val newUid = authResult.user?.uid ?: throw Exception("Failed to get UID for new Auth account.")

            val userRecord = hashMapOf(
                "role" to "teacher",
                "organizationId" to currentOrganizationId,
                "email" to email,
                "name" to teacherName
            )
            db.collection("users").document(newUid).set(userRecord).await()

            secondaryAuth.signOut()
            mainAuth.currentUser?.reload()?.await()

            return newUid
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.d(TAG, "Auth user with email $email already exists. Fetching UID.")

            val userQuery = db.collection("users").whereEqualTo("email", email).limit(1).get().await()
            if (userQuery.isEmpty) {
                throw Exception("User login exists, but profile is missing. Please contact support.")
            }
            return userQuery.documents[0].id
        }
    }

    private suspend fun uploadImage(uri: Uri): String? {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            MediaManager.get().upload(uri)
                .unsigned(UNSIGNED_UPLOAD_PRESET)
                .option("folder", "photos")
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

    private fun handleSaveFailure(e: Exception, message: String) {
        setInputsEnabled(true)
        StatusDialogFragment.newInstance(false, message).show(supportFragmentManager, "failureDialog")
    }

    private fun setInputsEnabled(enabled: Boolean) {
        etTeacherName.isEnabled = enabled
        etTeacherMobile.isEnabled = enabled
        etTeacherEmail.isEnabled = enabled
        etTeacherPassword.isEnabled = enabled
        etTeacherConfirmPassword.isEnabled = enabled
        btnSelectImage.isEnabled = enabled
        cardViewProfileImage.isEnabled = enabled
        btnSaveTeacher.isEnabled = enabled
        // --- NEW: Enable/disable time fields ---
        etStartTime.isEnabled = enabled
        etEndTime.isEnabled = enabled
        // --- END of NEW ---
        progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
    }

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }
}