package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class EditTeacherActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "EditTeacherActivity"
        private const val UNSIGNED_UPLOAD_PRESET_EDIT = "BIBI_AYESHA_MASJID"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var ivProfileImage: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var etTeacherName: TextInputEditText
    private lateinit var etTeacherMobile: TextInputEditText
    private lateinit var btnSaveChanges: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var db: FirebaseFirestore
    private var teacherId: String? = null
    private var currentOrganizationId: String? = null // NEW: Organization ID

    private var imageUri: Uri? = null
    private var existingImageUrl: String? = null

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
        teacherId = intent.getStringExtra("TEACHER_ID")
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this) // NEW: Get organization ID

        if (teacherId == null) {
            Toast.makeText(this, "Teacher ID missing. Cannot edit.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (currentOrganizationId == null) { // NEW: Check organization ID
            Toast.makeText(this, "Organization information missing. Please log in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }


        toolbar = findViewById(R.id.edit_teacher_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        ivProfileImage = findViewById(R.id.ivTeacherProfileImageEdit)
        btnSelectImage = findViewById(R.id.btnSelectImageEditTeacher)
        etTeacherName = findViewById(R.id.etTeacherNameEdit)
        etTeacherMobile = findViewById(R.id.etTeacherMobileEdit)
        btnSaveChanges = findViewById(R.id.btnSaveChangesTeacher)
        progressBar = findViewById(R.id.progressBarEditTeacher)

        loadTeacherDetails()

        btnSelectImage.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(galleryIntent)
        }

        btnSaveChanges.setOnClickListener {
            validateAndSaveChanges()
        }
    }

    private fun loadTeacherDetails() {
        if (teacherId == null || currentOrganizationId == null) { // NEW: Check organization ID
            Toast.makeText(this, "Teacher or Organization ID missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        progressBar.visibility = View.VISIBLE
        // NEW: Scope query to the organization
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers").document(teacherId!!)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                if (document != null && document.exists()) {
                    etTeacherName.setText(document.getString("teacherName"))

                    val mobileFromDb = document.getString("mobileNumber")
                    etTeacherMobile.setText(mobileFromDb ?: "")

                    existingImageUrl = document.getString("profileImageUrl")
                    if (!existingImageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(existingImageUrl)
                            .circleCrop()
                            .placeholder(R.drawable.molana)
                            .error(R.drawable.molana)
                            .into(ivProfileImage)
                    } else {
                        ivProfileImage.setImageResource(R.drawable.molana)
                    }
                } else {
                    Toast.makeText(this, "Teacher details not found.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading details: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun validateAndSaveChanges() {
        val name = etTeacherName.text.toString().trim()
        val mobile = etTeacherMobile.text.toString().trim()

        if (name.isEmpty()) {
            etTeacherName.error = "Teacher name is required"
            etTeacherName.requestFocus()
            return
        }
        if (mobile.isNotEmpty() && mobile.length != 10) {
            etTeacherMobile.error = "Enter a valid 10-digit mobile number"
            etTeacherMobile.requestFocus()
            return
        }
        // NEW: Basic check for organization ID before proceeding
        if (currentOrganizationId == null) {
            Toast.makeText(this, "Cannot save changes: Organization ID missing.", Toast.LENGTH_SHORT).show()
            return
        }


        progressBar.visibility = View.VISIBLE
        btnSaveChanges.isEnabled = false

        if (imageUri != null) {
            Log.d(TAG, "New image selected. Uploading via Cloudinary...")
            MediaManager.get().upload(imageUri)
                .unsigned(UNSIGNED_UPLOAD_PRESET_EDIT)
                .option("folder", "photos")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        val newImageUrl = resultData?.get("secure_url") as? String ?: resultData?.get("url") as? String
                        if (newImageUrl != null) {
                            Log.d(TAG, "Cloudinary - New image uploaded: $newImageUrl")
                            updateTeacherInFirestore(name, mobile, newImageUrl)
                        } else {
                            handleFailure(Exception("Cloudinary URL missing after upload"))
                        }
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        Log.e(TAG, "Cloudinary upload error: ${error?.description}")
                        handleFailure(Exception("Image upload failed: ${error?.description}"))
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        } else {
            updateTeacherInFirestore(name, mobile, existingImageUrl)
        }
    }

    private fun updateTeacherInFirestore(name: String, mobileDigitsOnly: String, newImageUrl: String?) {
        if (teacherId == null || currentOrganizationId == null) { // NEW: Check organization ID
            Toast.makeText(this, "Cannot update: Teacher or Organization ID missing.", Toast.LENGTH_SHORT).show()
            handleFailure(Exception("Missing IDs."))
            return
        }

        val teacherData = hashMapOf<String, Any?>(
            "teacherName" to name,
            "mobileNumber" to if (mobileDigitsOnly.isNotEmpty()) mobileDigitsOnly else null,
            "profileImageUrl" to newImageUrl,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // NEW: Scope update to the organization
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers").document(teacherId!!)
            .set(teacherData, SetOptions.merge())
            .addOnSuccessListener {
                handleSuccess("Teacher details updated successfully.")
            }
            .addOnFailureListener { e ->
                handleFailure(Exception("Error updating teacher details: ${e.message}"))
            }
    }

    private fun handleSuccess(message: String) {
        progressBar.visibility = View.GONE
        btnSaveChanges.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun handleFailure(e: Exception, customMessage: String? = null) {
        progressBar.visibility = View.GONE
        btnSaveChanges.isEnabled = true
        val msg = customMessage ?: "An error occurred: ${e.message}"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        Log.e(TAG, msg, e)
    }
}