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
    private var imageUri: Uri? = null
    private var existingProfileImageUrl: String? = null
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
                    if (!existingProfileImageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(existingProfileImageUrl).circleCrop().into(ivProfileImage)
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
        // Validation logic here (similar to AddStudentActivity)
        setInputsEnabled(false)
        if (imageUri != null) {
            uploadImageAndUpdateStudent()
        } else {
            updateStudentInFirestore(existingProfileImageUrl)
        }
    }

    private fun uploadImageAndUpdateStudent() {
        MediaManager.get().upload(imageUri).unsigned(UNSIGNED_UPLOAD_PRESET_STUDENT_EDIT)
            .option("folder", "student_profiles").callback(object : UploadCallback {
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    updateStudentInFirestore(resultData?.get("secure_url") as? String)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    Toast.makeText(this@EditStudentActivity, "Image upload failed. Updating details without image change.", Toast.LENGTH_LONG).show()
                    updateStudentInFirestore(existingProfileImageUrl)
                }
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun updateStudentInFirestore(imageUrl: String?) {
        val selectedGenderId = rgGender.checkedRadioButtonId
        val gender = if (selectedGenderId != -1) findViewById<RadioButton>(selectedGenderId).text.toString() else null

        val studentUpdates = mapOf(
            "studentName" to etStudentName.text.toString().trim(),
            "parentName" to etParentName.text.toString().trim(),
            "parentMobileNumber" to etParentMobile.text.toString().trim(),
            "profileImageUrl" to (imageUrl ?: ""),
            "regNo" to etRegNo.text.toString().trim(),
            "gender" to gender,
            "birthDate" to etBirthDate.text.toString().trim().ifEmpty { null },
            "admissionDate" to etAdmissionDate.text.toString().trim().ifEmpty { null },
            "lastUpdatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("students").document(studentId!!)
            .set(studentUpdates, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Details updated.", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }.addOnFailureListener { e ->
                setInputsEnabled(true)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setInputsEnabled(enabled: Boolean, isInitialLoad: Boolean = false) {
        // ... (this function can be copied from the previous response)
    }

    private fun checkAndRequestPermissions() {
        // ... (this function can be copied from the previous response)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        // ... (this function can be copied from the previous response)
    }

    private fun openGallery() {
        // ... (this function can be copied from the previous response)
    }
}