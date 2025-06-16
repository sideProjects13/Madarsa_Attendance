package com.example.madarsa_attendance

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AddTeacherActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "AddTeacherActivity"
        private const val PERMISSION_REQUEST_CODE = 101
    }

    private lateinit var etTeacherName: TextInputEditText
    private lateinit var etTeacherMobile: TextInputEditText
    private lateinit var tilTeacherName: TextInputLayout
    private lateinit var tilTeacherMobile: TextInputLayout
    private lateinit var ivTeacherProfileImage: ImageView
    private lateinit var cardViewProfileImage: MaterialCardView
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var btnSaveTeacher: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var db: FirebaseFirestore

    private var imageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>

    // --- VV THE ONLY CHANGE NEEDED FOR THIS SPECIFIC ERROR VV ---
    private val UNSIGNED_UPLOAD_PRESET = "BIBI_AYESHA_MASJID" // <<< CORRECTED PRESET NAME
    // --- ^^ THE ONLY CHANGE NEEDED FOR THIS SPECIFIC ERROR ^^ ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_teacher)

        db = FirebaseFirestore.getInstance()

        val toolbar: MaterialToolbar = findViewById(R.id.add_teacher_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        ivTeacherProfileImage = findViewById(R.id.ivTeacherProfileImage)
        cardViewProfileImage = findViewById(R.id.cardViewProfileImage)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        etTeacherName = findViewById(R.id.etTeacherName)
        etTeacherMobile = findViewById(R.id.etTeacherMobile)
        tilTeacherName = findViewById(R.id.tilTeacherName)
        tilTeacherMobile = findViewById(R.id.tilTeacherMobile)
        btnSaveTeacher = findViewById(R.id.btnSaveTeacher)
        progressBar = findViewById(R.id.progressBarAddTeacher)

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    imageUri = uri
                    Glide.with(this)
                        .load(imageUri)
                        .circleCrop()
                        .placeholder(R.drawable.person)
                        .into(ivTeacherProfileImage)
                }
            }
        }

        val imageSelectionClickListener = View.OnClickListener { checkAndRequestPermissions() }
        btnSelectImage.setOnClickListener(imageSelectionClickListener)
        cardViewProfileImage.setOnClickListener(imageSelectionClickListener)

        btnSaveTeacher.setOnClickListener {
            saveTeacher()
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                Toast.makeText(this, "Permission denied to access gallery.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        val teacherName = etTeacherName.text.toString().trim()
        val mobileNumberRaw = etTeacherMobile.text.toString().trim()

        tilTeacherName.error = null
        tilTeacherMobile.error = null

        if (teacherName.isEmpty()) {
            tilTeacherName.error = "Teacher name cannot be empty"
            isValid = false
        }

        if (mobileNumberRaw.isEmpty()) {
            tilTeacherMobile.error = "Mobile number cannot be empty"
            isValid = false
        } else if (mobileNumberRaw.length != 10) {
            tilTeacherMobile.error = "Enter a valid 10-digit mobile number"
            isValid = false
        }
        return isValid
    }

    private fun setInputsEnabled(enabled: Boolean) {
        etTeacherName.isEnabled = enabled
        etTeacherMobile.isEnabled = enabled
        btnSelectImage.isEnabled = enabled
        cardViewProfileImage.isEnabled = enabled
        btnSaveTeacher.isEnabled = enabled
        if (enabled) {
            btnSaveTeacher.text = "Save Teacher"
            progressBar.visibility = View.GONE
        } else {
            btnSaveTeacher.text = ""
            progressBar.visibility = View.VISIBLE
        }
    }

    private fun saveTeacher() {
        if (!validateInputs()) {
            return
        }
        setInputsEnabled(false)

        val teacherName = etTeacherName.text.toString().trim()
        val mobileNumber = etTeacherMobile.text.toString().trim()

        if (imageUri != null) {
            Log.d(TAG, "Attempting to upload with preset: $UNSIGNED_UPLOAD_PRESET")
            MediaManager.get().upload(imageUri)
                .unsigned(UNSIGNED_UPLOAD_PRESET) // Uses the corrected preset name
                .option("folder", "photos") // This matches your preset's asset folder
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) { Log.d(TAG, "Cloudinary: Upload started for $requestId with preset $UNSIGNED_UPLOAD_PRESET") }
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) { /* Optional */ }
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        Log.d(TAG, "Cloudinary upload success: $resultData")
                        val imageUrl = resultData["secure_url"] as? String ?: resultData["url"] as? String
                        if (imageUrl != null) {
                            saveTeacherDataToFirestore(teacherName, mobileNumber, imageUrl)
                        } else {
                            handleSaveFailure(Exception("Cloudinary URL not found in response"), "Image upload success, but URL missing.")
                        }
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e(TAG, "Cloudinary upload error for preset $UNSIGNED_UPLOAD_PRESET: ${error.description}, Code: ${error.code}")
                        handleSaveFailure(Exception(error.description), "Image upload failed (Error: ${error.description}, Code: ${error.code})")
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        Log.w(TAG, "Cloudinary upload rescheduled for preset $UNSIGNED_UPLOAD_PRESET: ${error.description}")
                        handleSaveFailure(Exception(error.description), "Image upload rescheduled, please try again.")
                    }
                }).dispatch()
        } else {
            saveTeacherDataToFirestore(teacherName, mobileNumber, null)
        }
    }

    private fun saveTeacherDataToFirestore(name: String, mobile: String, imageUrl: String?) {
        val teacherData = hashMapOf(
            "teacherName" to name,
            "mobileNumber" to mobile,
            "createdAt" to FieldValue.serverTimestamp()
        ).apply {
            imageUrl?.let { put("profileImageUrl", it) }
        }

        db.collection("teachers")
            .add(teacherData)
            .addOnSuccessListener {
                Toast.makeText(this, "Teacher added successfully!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                handleSaveFailure(e, "Failed to save teacher data")
            }
    }

    private fun handleSaveFailure(e: Exception, message: String) {
        setInputsEnabled(true)
        Toast.makeText(this, "$message", Toast.LENGTH_LONG).show()
        Log.e(TAG, "$message: Full error: ${e.message}", e)
    }
}