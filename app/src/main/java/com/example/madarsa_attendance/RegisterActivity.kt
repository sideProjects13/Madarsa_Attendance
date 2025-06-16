package com.example.madarsa_attendance

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "RegisterActivity"
        private const val PERMISSION_REQUEST_CODE = 102
    }

    private lateinit var etTeacherName: TextInputEditText
    private lateinit var etTeacherEmail: TextInputEditText
    private lateinit var etTeacherMobile: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText

    private lateinit var tilTeacherName: TextInputLayout
    private lateinit var tilTeacherEmail: TextInputLayout
    private lateinit var tilTeacherMobile: TextInputLayout
    private lateinit var tilPasswordRegister: TextInputLayout
    private lateinit var tilConfirmPasswordRegister: TextInputLayout

    private lateinit var ivTeacherProfileImage: ImageView
    private lateinit var cardViewProfileImage: MaterialCardView
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var btnRegisterTeacher: MaterialButton
    private lateinit var tvGoToLogin: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var db: FirebaseFirestore
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences

    private var imageUri: Uri? = null
    // private var uploadedImageUrl: String? = null // Not needed as instance var if passed directly
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>

    private val UNSIGNED_UPLOAD_PRESET = "BIBI_AYESHA_MASJID"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        db = FirebaseFirestore.getInstance()
        firebaseAuth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, Context.MODE_PRIVATE)

        val toolbar: MaterialToolbar = findViewById(R.id.register_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        ivTeacherProfileImage = findViewById(R.id.ivTeacherProfileImageRegister)
        cardViewProfileImage = findViewById(R.id.cardViewProfileImageRegister)
        btnSelectImage = findViewById(R.id.btnSelectImageRegister)
        etTeacherName = findViewById(R.id.etTeacherNameRegister)
        etTeacherEmail = findViewById(R.id.etTeacherEmailRegister)
        etTeacherMobile = findViewById(R.id.etTeacherMobileRegister)
        etPassword = findViewById(R.id.etPasswordRegister)
        etConfirmPassword = findViewById(R.id.etConfirmPasswordRegister)

        tilTeacherName = findViewById(R.id.tilTeacherNameRegister)
        tilTeacherEmail = findViewById(R.id.tilTeacherEmailRegister)
        tilTeacherMobile = findViewById(R.id.tilTeacherMobileRegister)
        tilPasswordRegister = findViewById(R.id.tilPasswordRegister)
        tilConfirmPasswordRegister = findViewById(R.id.tilConfirmPasswordRegister)

        btnRegisterTeacher = findViewById(R.id.btnRegisterTeacher)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)
        progressBar = findViewById(R.id.progressBarRegister)

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    imageUri = uri
                    Glide.with(this).load(imageUri).circleCrop().placeholder(R.drawable.person).into(ivTeacherProfileImage)
                }
            }
        }

        val imageSelectionClickListener = View.OnClickListener { checkAndRequestPermissions() }
        btnSelectImage.setOnClickListener(imageSelectionClickListener)
        cardViewProfileImage.setOnClickListener(imageSelectionClickListener)

        btnRegisterTeacher.setOnClickListener {
            registerUser()
        }
        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
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
        val name = etTeacherName.text.toString().trim()
        val email = etTeacherEmail.text.toString().trim()
        val mobile = etTeacherMobile.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        tilTeacherName.error = null
        tilTeacherEmail.error = null
        tilTeacherMobile.error = null
        tilPasswordRegister.error = null
        tilConfirmPasswordRegister.error = null

        if (name.isEmpty()) {
            tilTeacherName.error = "Full name is required"; isValid = false
        }
        if (email.isEmpty()) {
            tilTeacherEmail.error = "Email is required"; isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilTeacherEmail.error = "Enter a valid email address"; isValid = false
        }
        if (mobile.isEmpty()) {
            tilTeacherMobile.error = "Mobile number is required"; isValid = false
        } else if (mobile.length != 10) {
            tilTeacherMobile.error = "Enter a 10-digit mobile number"; isValid = false
        }
        if (password.isEmpty()) {
            tilPasswordRegister.error = "Password is required"; isValid = false
        } else if (password.length < 6) {
            tilPasswordRegister.error = "Password must be at least 6 characters"; isValid = false
        }
        if (confirmPassword.isEmpty()) {
            tilConfirmPasswordRegister.error = "Confirm password is required"; isValid = false
        } else if (password != confirmPassword) {
            tilConfirmPasswordRegister.error = "Passwords do not match"; isValid = false
        }
        return isValid
    }

    private fun setInputsEnabled(enabled: Boolean) {
        etTeacherName.isEnabled = enabled
        etTeacherEmail.isEnabled = enabled
        etTeacherMobile.isEnabled = enabled
        etPassword.isEnabled = enabled
        etConfirmPassword.isEnabled = enabled
        btnSelectImage.isEnabled = enabled
        cardViewProfileImage.isEnabled = enabled
        btnRegisterTeacher.isEnabled = enabled
        tvGoToLogin.isEnabled = enabled
        progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
        btnRegisterTeacher.text = if (enabled) "Register Teacher" else ""
    }

    private fun registerUser() {
        if (!validateInputs()) {
            return
        }
        setInputsEnabled(false)

        val email = etTeacherEmail.text.toString().trim()
        val password = etPassword.text.toString()

        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")
                    val firebaseUser = firebaseAuth.currentUser
                    firebaseUser?.let {
                        val teacherNameForProfile = etTeacherName.text.toString().trim()
                        // Update Firebase Auth profile display name
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(teacherNameForProfile)
                            .build()
                        it.updateProfile(profileUpdates).addOnCompleteListener { profileUpdateTask ->
                            if (profileUpdateTask.isSuccessful) {
                                Log.d(TAG, "User profile updated in Auth.")
                            } else {
                                Log.w(TAG, "Failed to update user profile in Auth.", profileUpdateTask.exception)
                            }
                            // Proceed to image upload/Firestore save regardless of profile update success
                            if (imageUri != null) {
                                uploadProfileImageAndSaveDetails(it.uid, teacherNameForProfile)
                            } else {
                                saveUserDetailsToFirestore(it.uid, teacherNameForProfile, null)
                            }
                        }
                    } ?: handleRegistrationFailure("Firebase user is null after creation.") // Should not happen
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    var errorMessage = "Registration failed."
                    try {
                        throw task.exception!!
                    } catch (e: FirebaseAuthUserCollisionException) {
                        errorMessage = "This email address is already in use."
                        tilTeacherEmail.error = errorMessage
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "An unknown error occurred."
                    }
                    handleRegistrationFailure(errorMessage)
                }
            }
    }

    private fun uploadProfileImageAndSaveDetails(userId: String, teacherName: String) {
        Log.d(TAG, "Uploading image with preset: $UNSIGNED_UPLOAD_PRESET")
        MediaManager.get().upload(imageUri)
            .unsigned(UNSIGNED_UPLOAD_PRESET)
            .option("folder", "teacher_profiles")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val imageUrl = resultData["secure_url"] as? String ?: resultData["url"] as? String
                    if (imageUrl != null) {
                        saveUserDetailsToFirestore(userId, teacherName, imageUrl)
                    } else {
                        // Save details without image URL if Cloudinary URL is missing, but log it
                        Log.e(TAG, "Cloudinary URL missing after successful upload. Saving without image URL.")
                        saveUserDetailsToFirestore(userId, teacherName, null)
                        // Optionally inform user that image might not have saved correctly
                        Toast.makeText(this@RegisterActivity, "Registration successful, but profile image link was not retrieved.", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    Log.e(TAG, "Cloudinary upload error: ${error.description}")
                    // Decide if you want to proceed with registration without an image or fail completely
                    // For now, proceeding without image:
                    Toast.makeText(this@RegisterActivity, "Image upload failed. Registering without profile image.", Toast.LENGTH_LONG).show()
                    saveUserDetailsToFirestore(userId, teacherName, null)
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    Log.w(TAG, "Cloudinary upload rescheduled: ${error.description}")
                    Toast.makeText(this@RegisterActivity, "Image upload rescheduled. Registering without profile image.", Toast.LENGTH_LONG).show()
                    saveUserDetailsToFirestore(userId, teacherName, null) // Proceed without image
                }
            }).dispatch()
    }

    private fun saveUserDetailsToFirestore(userId: String, name: String, imageUrl: String?) {
        val email = etTeacherEmail.text.toString().trim()
        val mobile = etTeacherMobile.text.toString().trim()

        val teacherData = hashMapOf(
            "uid" to userId,
            "teacherName" to name,
            "email" to email,
            "mobileNumber" to mobile,
            "profileImageUrl" to (imageUrl ?: ""),
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("teachers").document(userId)
            .set(teacherData)
            .addOnSuccessListener {
                Log.d(TAG, "Teacher details saved to Firestore for UID: $userId")
                Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()
                sharedPreferences.edit().putString(LoginActivity.KEY_LOGGED_IN_UID, userId).apply()
                // Pass teacherId and teacherName to navigateToHome
                navigateToHome(userId, name) // <<< PASS THE DATA
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving teacher details to Firestore", e)
                firebaseAuth.currentUser?.delete()?.addOnCompleteListener { deleteTask ->
                    if(deleteTask.isSuccessful){
                        Log.d(TAG, "Firebase Auth user deleted due to Firestore save failure.")
                    } else {
                        Log.e(TAG, "Failed to delete Firebase Auth user after Firestore save failure.", deleteTask.exception)
                    }
                }
                handleRegistrationFailure("Failed to save teacher details: ${e.message}")
            }
    }

    private fun handleRegistrationFailure(message: String) {
        setInputsEnabled(true)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, "Registration Failure: $message")
    }

    // Modified navigateToHome to accept parameters
    private fun navigateToHome(teacherId: String, teacherName: String) {
        val intent = Intent(this, TeacherOptionsActivity::class.java).apply {
            putExtra("TEACHER_ID", teacherId)
            putExtra("TEACHER_NAME", teacherName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}