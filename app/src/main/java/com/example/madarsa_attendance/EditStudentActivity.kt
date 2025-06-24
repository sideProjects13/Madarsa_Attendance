package com.example.madarsa_attendance

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
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
import android.widget.RadioButton
import android.widget.RadioGroup
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditStudentActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "EditStudentActivity"
        private const val PERMISSION_REQUEST_CODE_EDIT_STUDENT = 104
        private const val UNSIGNED_UPLOAD_PRESET_STUDENT_EDIT = "BIBI_AYESHA_MASJID"
    }

    // Existing UI
    private lateinit var toolbar: MaterialToolbar
    private lateinit var ivProfileImage: ImageView
    private lateinit var cardViewProfileImage: MaterialCardView
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var etStudentName: TextInputEditText
    private lateinit var tilStudentName: TextInputLayout
    private lateinit var etParentName: TextInputEditText
    private lateinit var tilParentName: TextInputLayout
    private lateinit var etParentMobile: TextInputEditText
    private lateinit var tilParentMobile: TextInputLayout
    private lateinit var tvCurrentTeacher: TextView
    private lateinit var btnSaveChanges: MaterialButton
    private lateinit var progressBar: ProgressBar

    // New UI
    private lateinit var etRegNo: TextInputEditText
    private lateinit var rgGender: RadioGroup
    private lateinit var etBirthDate: TextInputEditText
    private lateinit var etAdmissionDate: TextInputEditText

    // Backend
    private lateinit var db: FirebaseFirestore
    private var studentId: String? = null
    private var currentTeacherNameFromIntent: String? = null
    private var imageUri: Uri? = null // This will hold the URI of a NEW image if selected
    private var existingProfileImageUrl: String? = null // This holds the student's current image URL
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_student)

        db = FirebaseFirestore.getInstance()
        studentId = intent.getStringExtra("STUDENT_ID")
        currentTeacherNameFromIntent = intent.getStringExtra("TEACHER_NAME")

        if (studentId == null) {
            Toast.makeText(this, "Student ID not found.", Toast.LENGTH_LONG).show(); finish(); return
        }

        initializeViews()
        setupListeners()
        loadStudentDetails()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.edit_student_toolbar)
        ivProfileImage = findViewById(R.id.ivStudentProfileImageEdit)
        cardViewProfileImage = findViewById(R.id.cardViewProfileImageStudentEdit)
        btnSelectImage = findViewById(R.id.btnSelectImageStudentEdit)
        etStudentName = findViewById(R.id.etStudentNameEdit)
        tilStudentName = findViewById(R.id.tilStudentNameEdit)
        etParentName = findViewById(R.id.etParentNameEdit)
        tilParentName = findViewById(R.id.tilParentNameEdit)
        etParentMobile = findViewById(R.id.etParentMobileEdit)
        tilParentMobile = findViewById(R.id.tilParentMobileEdit)
        tvCurrentTeacher = findViewById(R.id.tvCurrentTeacherEdit)
        btnSaveChanges = findViewById(R.id.btnSaveChangesStudent)
        progressBar = findViewById(R.id.progressBarEditStudent)

        // New Views
        etRegNo = findViewById(R.id.etRegNoEdit)
        rgGender = findViewById(R.id.rgGenderEdit)
        etBirthDate = findViewById(R.id.etBirthDateEdit)
        etAdmissionDate = findViewById(R.id.etAdmissionDateEdit)
    }

    private fun setupListeners() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    imageUri = uri
                    Glide.with(this).load(uri).circleCrop().into(ivProfileImage)
                }
            }
        }
        val imageSelectionClickListener = View.OnClickListener { checkAndRequestPermissions() }
        btnSelectImage.setOnClickListener(imageSelectionClickListener)
        cardViewProfileImage.setOnClickListener(imageSelectionClickListener)

        etBirthDate.setOnClickListener { showDatePickerDialog(etBirthDate) }
        etAdmissionDate.setOnClickListener { showDatePickerDialog(etAdmissionDate) }

        btnSaveChanges.setOnClickListener { validateAndUpdateStudentDetails() }
    }

    private fun showDatePickerDialog(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            editText.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadStudentDetails() {
        setInputsEnabled(false, isInitialLoad = true)
        db.collection("students").document(studentId!!).get().addOnSuccessListener { document ->
            setInputsEnabled(true)
            if (document != null && document.exists()) {
                val student = document.toObject(StudentDetailsItem::class.java)
                if (student != null) {
                    etStudentName.setText(student.studentName)
                    etParentName.setText(student.parentName)
                    etParentMobile.setText(student.parentMobileNumber)
                    etRegNo.setText(student.regNo)
                    etBirthDate.setText(student.birthDate)
                    etAdmissionDate.setText(student.admissionDate)

                    when (student.gender) {
                        "Male" -> rgGender.check(R.id.rbMaleEdit)
                        "Female" -> rgGender.check(R.id.rbFemaleEdit)
                    }

                    existingProfileImageUrl = student.profileImageUrl
                    // Load existing image if available
                    if (!existingProfileImageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(existingProfileImageUrl).circleCrop().placeholder(R.drawable.student).into(ivProfileImage)
                    } else {
                        ivProfileImage.setImageResource(R.drawable.student)
                    }
                    tvCurrentTeacher.text = student.teacherName ?: currentTeacherNameFromIntent ?: "N/A"
                }
            } else {
                Toast.makeText(this, "Student details not found.", Toast.LENGTH_SHORT).show(); finish()
            }
        }.addOnFailureListener { e ->
            setInputsEnabled(true)
            Toast.makeText(this, "Error loading details: ${e.message}", Toast.LENGTH_LONG).show(); finish()
        }
    }

    private fun validateAndUpdateStudentDetails() {
        // Simple validation, can be expanded
        if (etStudentName.text.toString().trim().isEmpty()) {
            tilStudentName.error = "Student name cannot be empty"
            return
        } else {
            tilStudentName.error = null
        }

        setInputsEnabled(false)
        if (imageUri != null) {
            // A new image was selected, so upload it first
            uploadImageAndUpdateStudent()
        } else {
            // No new image selected, update Firestore with existing image URL
            updateStudentInFirestore(existingProfileImageUrl)
        }
    }

    private fun uploadImageAndUpdateStudent() {
        MediaManager.get().upload(imageUri).unsigned(UNSIGNED_UPLOAD_PRESET_STUDENT_EDIT)
            .option("folder", "student_profiles").callback(object : UploadCallback {
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    // Image uploaded successfully, get the new URL
                    val newImageUrl = resultData?.get("secure_url") as? String
                    updateStudentInFirestore(newImageUrl)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    Toast.makeText(this@EditStudentActivity, "Image upload failed. Updating details without image change.", Toast.LENGTH_LONG).show()
                    // If upload fails, proceed with the update using the old image URL
                    updateStudentInFirestore(existingProfileImageUrl)
                }
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    // --- FUNCTION CORRECTED ---
    private fun updateStudentInFirestore(imageUrl: String?) {
        val selectedGenderId = rgGender.checkedRadioButtonId
        val gender = if (selectedGenderId != -1) findViewById<RadioButton>(selectedGenderId).text.toString() else null

        // Use a mutable map to build the update data.
        // This allows us to conditionally add the image URL.
        val studentUpdates = mutableMapOf<String, Any?>(
            "studentName" to etStudentName.text.toString().trim(),
            "parentName" to etParentName.text.toString().trim(),
            "parentMobileNumber" to etParentMobile.text.toString().trim(),
            "regNo" to etRegNo.text.toString().trim(),
            "gender" to gender,
            "birthDate" to etBirthDate.text.toString().trim().ifEmpty { null },
            "admissionDate" to etAdmissionDate.text.toString().trim().ifEmpty { null },
            "lastUpdatedAt" to FieldValue.serverTimestamp()
        )

        // **THE FIX**: Only add the profileImageUrl to the map if it's not null.
        // This prevents overwriting an existing URL with null or an empty string.
        if (imageUrl != null) {
            studentUpdates["profileImageUrl"] = imageUrl
        }

        db.collection("students").document(studentId!!)
            .set(studentUpdates, SetOptions.merge()) // Use merge to only update the specified fields
            .addOnSuccessListener {
                Toast.makeText(this, "Details updated successfully.", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }.addOnFailureListener { e ->
                setInputsEnabled(true)
                Toast.makeText(this, "Error updating details: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setInputsEnabled(enabled: Boolean, isInitialLoad: Boolean = false) {
        if (isInitialLoad) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
        }
        btnSaveChanges.isEnabled = enabled
        btnSelectImage.isEnabled = enabled
        etStudentName.isEnabled = enabled
        etParentName.isEnabled = enabled
        etParentMobile.isEnabled = enabled
        etRegNo.isEnabled = enabled
        rgGender.isEnabled = enabled
        etBirthDate.isEnabled = enabled
        etAdmissionDate.isEnabled = enabled
    }

    private fun checkAndRequestPermissions() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE_EDIT_STUDENT)
        } else {
            openGallery()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE_EDIT_STUDENT && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            Toast.makeText(this, "Permission to access gallery denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }
}