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
import com.cloudinary.android.MediaManager // Assuming you might want to use Cloudinary for edit too
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
// Firebase Storage is not used here if Cloudinary is used for AddTeacherActivity.
// If you want EditTeacherActivity to also use Cloudinary for image updates, keep MediaManager.
// If EditTeacherActivity should use Firebase Storage for image updates, use FirebaseStorage.

class EditTeacherActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "EditTeacherActivity"
        // If using Cloudinary for image edits too:
        private const val UNSIGNED_UPLOAD_PRESET_EDIT = "BIBI_AYESHA_MASJID" // Or a different preset if needed for edits
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var ivProfileImage: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var etTeacherName: TextInputEditText
    private lateinit var etTeacherMobile: TextInputEditText
    private lateinit var btnSaveChanges: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var db: FirebaseFirestore
    // private lateinit var storage: FirebaseStorage // Only if using Firebase Storage for image updates
    private var teacherId: String? = null
    private var imageUri: Uri? = null
    private var existingImageUrl: String? = null // This will be Cloudinary URL if AddTeacher used Cloudinary

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
        setContentView(R.layout.activity_edit_teacher) // Ensure this layout is styled like AddTeacher

        db = FirebaseFirestore.getInstance()
        // storage = FirebaseStorage.getInstance() // Only if using Firebase Storage

        teacherId = intent.getStringExtra("TEACHER_ID")

        if (teacherId == null) {
            Toast.makeText(this, "Teacher ID missing. Cannot edit.", Toast.LENGTH_LONG).show()
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
            // Your permission check and openGallery logic from AddTeacherActivity should be here too
            // For brevity, assuming it's similar and just launching picker for now
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(galleryIntent)
        }

        btnSaveChanges.setOnClickListener {
            validateAndSaveChanges()
        }
    }

    private fun loadTeacherDetails() {
        progressBar.visibility = View.VISIBLE
        db.collection("teachers").document(teacherId!!)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                if (document != null && document.exists()) {
                    etTeacherName.setText(document.getString("teacherName"))

                    // Fetch "mobileNumber" as stored by AddTeacherActivity
                    // It should already be the 10-digit number without +91
                    val mobileFromDb = document.getString("mobileNumber")
                    etTeacherMobile.setText(mobileFromDb ?: "") // Set empty if null

                    existingImageUrl = document.getString("profileImageUrl") // This is the Cloudinary URL
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
        val mobile = etTeacherMobile.text.toString().trim() // This is the 10-digit number

        if (name.isEmpty()) {
            etTeacherName.error = "Teacher name is required"
            etTeacherName.requestFocus()
            return
        }
        if (mobile.isNotEmpty() && mobile.length != 10) { // Check only if not empty
            etTeacherMobile.error = "Enter a valid 10-digit mobile number"
            etTeacherMobile.requestFocus()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSaveChanges.isEnabled = false

        if (imageUri != null) { // New image selected, upload it
            // If AddTeacherActivity uses Cloudinary, EditTeacherActivity should also use Cloudinary for consistency
            // Or, if you switched AddTeacherActivity to Firebase Storage, use that here too.
            // Assuming Cloudinary for now, similar to your AddTeacherActivity:
            Log.d(TAG, "New image selected. Uploading via Cloudinary...")
            MediaManager.get().upload(imageUri)
                .unsigned(UNSIGNED_UPLOAD_PRESET_EDIT) // Use the same or a similar preset
                .option("folder", "photos")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        val newImageUrl = resultData?.get("secure_url") as? String ?: resultData?.get("url") as? String
                        if (newImageUrl != null) {
                            Log.d(TAG, "Cloudinary - New image uploaded: $newImageUrl")
                            // TODO: Optionally delete the old image from Cloudinary if `existingImageUrl` is different and not null
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
        } else { // No new image, just update details with existing image URL
            updateTeacherInFirestore(name, mobile, existingImageUrl)
        }
    }

    private fun updateTeacherInFirestore(name: String, mobileDigitsOnly: String, newImageUrl: String?) {
        // Mobile is stored as 10 digits, consistent with AddTeacherActivity
        val teacherData = hashMapOf<String, Any?>(
            "teacherName" to name,
            "mobileNumber" to if (mobileDigitsOnly.isNotEmpty()) mobileDigitsOnly else null, // Store raw 10 digits or null
            "profileImageUrl" to newImageUrl, // This could be new Cloudinary URL or existing one
            "updatedAt" to FieldValue.serverTimestamp() // Good practice to add an update timestamp
        )

        db.collection("teachers").document(teacherId!!)
            .set(teacherData, SetOptions.merge()) // merge to update only these fields
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