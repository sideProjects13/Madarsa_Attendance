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
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditTeacherActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "EditTeacherActivity"
        private const val UNSIGNED_UPLOAD_PRESET = "BIBI_AYESHA_MASJID"
    }

    // UI
    private lateinit var etTeacherName: TextInputEditText
    private lateinit var etTeacherMobile: TextInputEditText
    private lateinit var etTeacherEmail: TextInputEditText
    private lateinit var ivProfileImage: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var btnSaveChanges: Button
    private lateinit var btnResetPassword: Button
    private lateinit var progressBar: ProgressBar

    // --- NEW Views for Class Timing ---
    private lateinit var etStartTime: TextInputEditText
    private lateinit var etEndTime: TextInputEditText
    // --- END of NEW Views ---

    // Firebase & Data
    private lateinit var db: FirebaseFirestore
    private var teacherDocId: String? = null
    private var currentOrganizationId: String? = null
    private var imageUri: Uri? = null
    private var existingImageUrl: String? = null
    private var teacherLoginEmail: String? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                imageUri = it
                Glide.with(this).load(imageUri).circleCrop().into(ivProfileImage)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_teacher)

        db = FirebaseFirestore.getInstance()
        teacherDocId = intent.getStringExtra("TEACHER_ID")
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)

        if (teacherDocId == null || currentOrganizationId == null) {
            Toast.makeText(this, "Required data missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        initializeViews()
        setupListeners()
        loadTeacherDetails()
    }

    private fun initializeViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.edit_teacher_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        ivProfileImage = findViewById(R.id.ivTeacherProfileImageEdit)
        btnSelectImage = findViewById(R.id.btnSelectImageEditTeacher)
        etTeacherName = findViewById(R.id.etTeacherNameEdit)
        etTeacherMobile = findViewById(R.id.etTeacherMobileEdit)
        etTeacherEmail = findViewById(R.id.etTeacherEmailEdit)
        btnSaveChanges = findViewById(R.id.btnSaveChangesTeacher)
        btnResetPassword = findViewById(R.id.btnResetPassword)
        progressBar = findViewById(R.id.progressBarEditTeacher)

        // --- NEW: Initialize time fields ---
        etStartTime = findViewById(R.id.etStartTimeEdit)
        etEndTime = findViewById(R.id.etEndTimeEdit)
        // --- END of NEW Initialization ---
    }

    private fun setupListeners() {
        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }
        btnSaveChanges.setOnClickListener { validateAndSaveChanges() }
        btnResetPassword.setOnClickListener { sendPasswordReset() }

        // --- NEW: Time Picker Listeners ---
        etStartTime.setOnClickListener { showTimePicker(isStartTime = true) }
        etEndTime.setOnClickListener { showTimePicker(isStartTime = false) }
        // --- END of NEW Listeners ---
    }

    private fun loadTeacherDetails() {
        progressBar.visibility = View.VISIBLE
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers").document(teacherDocId!!)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                if (document.exists()) {
                    val teacher = document.toObject(Teacher::class.java)
                    etTeacherName.setText(teacher?.teacherName)
                    etTeacherMobile.setText(teacher?.mobileNumber)
                    etTeacherEmail.setText(teacher?.email)
                    teacherLoginEmail = teacher?.email
                    existingImageUrl = teacher?.profileImageUrl
                    if (!existingImageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(existingImageUrl).circleCrop().placeholder(R.drawable.molana).into(ivProfileImage)
                    }

                    // --- NEW: Load and display existing times ---
                    teacher?.startTime?.let { formatAndSetTime(it, etStartTime) }
                    teacher?.endTime?.let { formatAndSetTime(it, etEndTime) }
                    // --- END of NEW ---

                } else {
                    Toast.makeText(this, "Details not found.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading details: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun validateAndSaveChanges() {
        val name = etTeacherName.text.toString().trim()
        val mobile = etTeacherMobile.text.toString().trim()
        if (name.isEmpty()) {
            etTeacherName.error = "Name cannot be empty"
            return
        }
        if (mobile.isNotEmpty() && mobile.length != 10) {
            etTeacherMobile.error = "Enter a valid 10-digit number"
            return
        }
        // --- NEW: Validation for time fields ---
        if (etStartTime.text.toString().isBlank() || etEndTime.text.toString().isBlank()) {
            Toast.makeText(this, "Please select both start and end times", Toast.LENGTH_SHORT).show()
            return
        }
        // --- END of NEW ---

        setInputsEnabled(false)

        if (imageUri != null) {
            uploadImageAndUpdate(name, mobile)
        } else {
            updateTeacherInFirestore(name, mobile, existingImageUrl)
        }
    }

    private fun uploadImageAndUpdate(name: String, mobile: String) {
        MediaManager.get().upload(imageUri).unsigned(UNSIGNED_UPLOAD_PRESET).callback(object : UploadCallback {
            override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                val newImageUrl = resultData?.get("secure_url") as? String
                updateTeacherInFirestore(name, mobile, newImageUrl)
            }
            override fun onError(requestId: String?, error: ErrorInfo?) {
                handleFailure(Exception(error?.description ?: "Image upload failed"))
            }
            override fun onStart(requestId: String?) {}
            override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
            override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
        }).dispatch()
    }

    private fun updateTeacherInFirestore(name: String, mobile: String, imageUrl: String?) {
        // --- NEW: Get time values from the tag (24-hour format) ---
        val startTime = etStartTime.tag as? String ?: ""
        val endTime = etEndTime.tag as? String ?: ""
        // --- END of NEW ---

        val teacherData = mapOf(
            "teacherName" to name,
            "mobileNumber" to mobile,
            "profileImageUrl" to (imageUrl ?: existingImageUrl ?: ""),
            // --- NEW: Add time fields to the update map ---
            "startTime" to startTime,
            "endTime" to endTime
            // --- END of NEW ---
        )
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers").document(teacherDocId!!)
            .update(teacherData)
            .addOnSuccessListener {
                StatusDialogFragment.newInstance(true, "Details Updated!", true)
                    .show(supportFragmentManager, "successDialog")
                setResult(Activity.RESULT_OK)
            }
            .addOnFailureListener { e -> handleFailure(e, "Failed to update details.") }
    }

    private fun sendPasswordReset() {
        if (teacherLoginEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Teacher email is not available.", Toast.LENGTH_SHORT).show()
            return
        }
        Firebase.auth.sendPasswordResetEmail(teacherLoginEmail!!)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    StatusDialogFragment.newInstance(true, "Password reset email sent!")
                        .show(supportFragmentManager, "successDialog")
                } else {
                    StatusDialogFragment.newInstance(false, "Failed to send email.")
                        .show(supportFragmentManager, "failureDialog")
                }
            }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
        btnSaveChanges.isEnabled = enabled
        btnSelectImage.isEnabled = enabled
        etTeacherName.isEnabled = enabled
        etTeacherMobile.isEnabled = enabled
        btnResetPassword.isEnabled = enabled
        // --- NEW: Enable/disable time fields ---
        etStartTime.isEnabled = enabled
        etEndTime.isEnabled = enabled
        // --- END of NEW ---
    }

    private fun handleFailure(e: Exception, customMessage: String? = null) {
        setInputsEnabled(true)
        val msg = customMessage ?: "An error occurred"
        StatusDialogFragment.newInstance(false, msg).show(supportFragmentManager, "failureDialog")
        Log.e(TAG, "$msg: Full error", e)
    }

    // --- NEW: Function to show the time picker dialog (reusable) ---
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

            val formattedTimeForDb = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

            val displayFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val calendarForDisplay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val displayTime = displayFormat.format(calendarForDisplay.time)

            if (isStartTime) {
                etStartTime.setText(displayTime)
                etStartTime.tag = formattedTimeForDb
            } else {
                etEndTime.setText(displayTime)
                etEndTime.tag = formattedTimeForDb
            }
        }
        timePicker.show(supportFragmentManager, if (isStartTime) "editStartTimePicker" else "editEndTimePicker")
    }

    // --- NEW: Helper to format and display time when loading data ---
    private fun formatAndSetTime(time24h: String, editText: TextInputEditText) {
        try {
            val parts = time24h.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val displayFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val displayTime = displayFormat.format(calendar.time)

            editText.setText(displayTime)
            editText.tag = time24h // Keep the original 24h format in the tag
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse time: $time24h", e)
            editText.setText(time24h) // Fallback to raw string
            editText.tag = time24h
        }
    }
}