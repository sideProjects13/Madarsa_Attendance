package com.example.madarsa_attendance

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
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
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditStudentActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "EditStudentActivity"
        private const val PERMISSION_REQUEST_CODE_STORAGE = 104
        private const val PERMISSION_REQUEST_CODE_CAMERA = 105
        private const val UNSIGNED_UPLOAD_PRESET_STUDENT_EDIT = "BIBI_AYESHA_MASJID"
    }

    // UI
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
    private lateinit var etRegNo: TextInputEditText
    private lateinit var rgGender: RadioGroup
    private lateinit var etBirthDate: TextInputEditText
    private lateinit var tilBirthDate: TextInputLayout
    private lateinit var etAdmissionDate: TextInputEditText
    private lateinit var tilAdmissionDate: TextInputLayout
    private lateinit var etAlternateMobile: TextInputEditText
    private lateinit var tilAlternateMobile: TextInputLayout
    private lateinit var etAddress: TextInputEditText
    private lateinit var tilAddress: TextInputLayout
    // --- START: ADDED UI COMPONENTS FOR FEE ---
    private lateinit var etMonthlyFee: TextInputEditText
    private lateinit var tilMonthlyFee: TextInputLayout
    // --- END: ADDED UI COMPONENTS FOR FEE ---


    // Backend
    private lateinit var db: FirebaseFirestore
    private var studentId: String? = null
    private var currentTeacherNameFromIntent: String? = null
    private var currentOrganizationId: String? = null
    private var imageUri: Uri? = null
    private var existingProfileImageUrl: String? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private var cameraImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_student)

        db = FirebaseFirestore.getInstance()
        studentId = intent.getStringExtra("STUDENT_ID")
        currentTeacherNameFromIntent = intent.getStringExtra("TEACHER_NAME")
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)

        if (studentId == null || currentOrganizationId == null) {
            Toast.makeText(this, "Required data not found.", Toast.LENGTH_LONG).show(); finish(); return
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
        etRegNo = findViewById(R.id.etRegNoEdit)
        rgGender = findViewById(R.id.rgGenderEdit)
        etBirthDate = findViewById(R.id.etBirthDateEdit)
        tilBirthDate = findViewById(R.id.tilBirthDateEdit)
        etAdmissionDate = findViewById(R.id.etAdmissionDateEdit)
        tilAdmissionDate = findViewById(R.id.tilAdmissionDateEdit)
        etAlternateMobile = findViewById(R.id.etAlternateMobileEdit)
        tilAlternateMobile = findViewById(R.id.tilAlternateMobileEdit)
        etAddress = findViewById(R.id.etAddressEdit)
        tilAddress = findViewById(R.id.tilAddressEdit)
        // --- START: INITIALIZE FEE VIEWS ---
        etMonthlyFee = findViewById(R.id.etMonthlyFeeEdit)
        tilMonthlyFee = findViewById(R.id.tilMonthlyFeeEdit)
        // --- END: INITIALIZE FEE VIEWS ---
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

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                cameraImageUri?.let { uri ->
                    imageUri = uri
                    Glide.with(this).load(uri).circleCrop().placeholder(R.drawable.student).into(ivProfileImage)
                }
            }
        }

        val imageSelectionClickListener = View.OnClickListener { showImageSourceDialog() }
        btnSelectImage.setOnClickListener(imageSelectionClickListener)
        cardViewProfileImage.setOnClickListener(imageSelectionClickListener)

        etBirthDate.addTextChangedListener(DateTextWatcher(etBirthDate))
        etAdmissionDate.addTextChangedListener(DateTextWatcher(etAdmissionDate))

        tilBirthDate.setEndIconOnClickListener {
            showCustomDatePickerDialog(etBirthDate)
        }
        tilAdmissionDate.setEndIconOnClickListener {
            showCustomDatePickerDialog(etAdmissionDate)
        }

        btnSaveChanges.setOnClickListener { validateAndUpdateStudentDetails() }
    }

    private fun loadStudentDetails() {
        if (studentId == null || currentOrganizationId == null) return
        setInputsEnabled(false, isInitialLoad = true)

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").document(studentId!!).get().addOnSuccessListener { document ->
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
                        etAlternateMobile.setText(student.alternateMobileNumber)
                        etAddress.setText(student.address)

                        // --- START: LOAD EXISTING MONTHLY FEE ---
                        student.monthlyFee?.let { fee ->
                            etMonthlyFee.setText(String.format(Locale.US, "%.0f", fee))
                        }
                        // --- END: LOAD EXISTING MONTHLY FEE ---

                        when (student.gender) {
                            "Male" -> rgGender.check(R.id.rbMaleEdit)
                            "Female" -> rgGender.check(R.id.rbFemaleEdit)
                        }

                        existingProfileImageUrl = student.profileImageUrl
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
        tilStudentName.error = null
        tilAlternateMobile.error = null
        tilBirthDate.error = null
        tilAdmissionDate.error = null
        tilMonthlyFee.error = null

        var isValid = true

        if (etStudentName.text.toString().trim().isEmpty()) {
            tilStudentName.error = "Student name cannot be empty"
            isValid = false
        }
        val alternateMobile = etAlternateMobile.text.toString().trim()
        if (alternateMobile.isNotEmpty() && (alternateMobile.length != 10 || !alternateMobile.all { it.isDigit() })) {
            tilAlternateMobile.error = "Enter a valid 10-digit number"
            isValid = false
        }

        // --- START: ADDED FEE VALIDATION LOGIC ---
        val monthlyFeeText = etMonthlyFee.text.toString().trim()
        if (monthlyFeeText.isEmpty()) {
            tilMonthlyFee.error = "Monthly fee cannot be empty"
            isValid = false
        } else {
            val feeValue = monthlyFeeText.toDoubleOrNull()
            if (feeValue == null || feeValue <= 0) {
                tilMonthlyFee.error = "Enter a valid positive fee amount"
                isValid = false
            }
        }
        // --- END: ADDED FEE VALIDATION LOGIC ---

        val birthDateStr = etBirthDate.text.toString().trim()
        if (birthDateStr.isNotEmpty()) {
            if (!isValidDateFormat(birthDateStr)) {
                tilBirthDate.error = "Invalid format or date"; isValid = false
            } else if (isDateInFuture(birthDateStr)) {
                tilBirthDate.error = "Date cannot be in the future"; isValid = false
            }
        }
        val admissionDateStr = etAdmissionDate.text.toString().trim()
        if (admissionDateStr.isNotEmpty()) {
            if (!isValidDateFormat(admissionDateStr)) {
                tilAdmissionDate.error = "Invalid format or date"; isValid = false
            } else if (isDateInFuture(admissionDateStr)) {
                tilAdmissionDate.error = "Date cannot be in the future"; isValid = false
            }
        }

        if (!isValid) return

        if (currentOrganizationId == null) {
            handleFailure("Cannot save: Organization ID is missing.")
            return
        }

        setInputsEnabled(false)
        if (imageUri != null) {
            uploadImageAndUpdateStudent()
        } else {
            updateStudentInFirestore(existingProfileImageUrl)
        }
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)!!
        val tempFile = File.createTempFile("prefix", ".extension", cacheDir)
        tempFile.deleteOnExit()
        tempFile.outputStream().use { fileOut ->
            inputStream.copyTo(fileOut)
        }
        inputStream.close()
        return tempFile
    }

    // --- REVERTED TO CLOUDINARY LOGIC ---
    private fun uploadImageAndUpdateStudent() {
        if (imageUri == null) return

        lifecycleScope.launch {
            try {
                val imageFile = uriToFile(imageUri!!)
                val compressedImageFile = Compressor.compress(this@EditStudentActivity, imageFile) {
                    quality(80)
                    size(100 * 1024)
                }

                // Cloudinary Upload
                MediaManager.get().upload(compressedImageFile.path)
                    .unsigned(UNSIGNED_UPLOAD_PRESET_STUDENT_EDIT)
                    .option("folder", "student_profiles")
                    .callback(object : UploadCallback {
                        override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                            val newImageUrl = resultData?.get("secure_url") as? String
                            updateStudentInFirestore(newImageUrl)
                        }

                        override fun onError(requestId: String?, error: ErrorInfo?) {
                            Log.e(TAG, "Image upload failed, updating details without image change. Error: ${error?.description}")
                            updateStudentInFirestore(existingProfileImageUrl)
                        }
                        override fun onStart(requestId: String?) {}
                        override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                        override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                    }).dispatch()

            } catch (e: Exception) {
                Log.e(TAG, "Image compression or file handling failed", e)
                handleFailure("Failed to process image.")
            }
        }
    }

    private fun updateStudentInFirestore(imageUrl: String?) {
        if (studentId == null || currentOrganizationId == null) return

        val selectedGenderId = rgGender.checkedRadioButtonId
        val gender = if (selectedGenderId != -1) findViewById<RadioButton>(selectedGenderId).text.toString() else null

        val studentUpdates = mutableMapOf<String, Any?>(
            "studentName" to etStudentName.text.toString().trim(),
            "parentName" to etParentName.text.toString().trim(),
            "parentMobileNumber" to etParentMobile.text.toString().trim(),
            "regNo" to etRegNo.text.toString().trim(),
            "gender" to gender,
            "birthDate" to etBirthDate.text.toString().trim().ifEmpty { null },
            "admissionDate" to etAdmissionDate.text.toString().trim().ifEmpty { null },
            "lastUpdatedAt" to FieldValue.serverTimestamp(),
            "alternateMobileNumber" to etAlternateMobile.text.toString().trim().ifEmpty { null },
            "address" to etAddress.text.toString().trim().ifEmpty { null },
            "monthlyFee" to (etMonthlyFee.text.toString().trim().toDoubleOrNull() ?: 0.0)
        )

        imageUrl?.let { studentUpdates["profileImageUrl"] = it }

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").document(studentId!!)
            .set(studentUpdates, SetOptions.merge())
            .addOnSuccessListener {
                StatusDialogFragment.newInstance(
                    isSuccess = true,
                    message = "Details Updated Successfully!",
                    finishActivityOnDismiss = true
                ).show(supportFragmentManager, "successDialog")
                setResult(Activity.RESULT_OK)
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error updating student details", e)
                handleFailure("Failed to update details.")
            }
    }

    private fun handleFailure(message: String) {
        setInputsEnabled(true)
        StatusDialogFragment.newInstance(
            isSuccess = false,
            message = message
        ).show(supportFragmentManager, "failureDialog")
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
        etAlternateMobile.isEnabled = enabled
        etAddress.isEnabled = enabled
        cardViewProfileImage.isClickable = enabled
        etMonthlyFee.isEnabled = enabled
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissions()
                    1 -> checkStoragePermissions()
                }
            }
            .show()
    }

    private fun checkStoragePermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE_STORAGE)
        } else {
            openGallery()
        }
    }

    private fun checkCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE_CAMERA)
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(this, "Storage permission denied.", Toast.LENGTH_SHORT).show()
                }
            }
            PERMISSION_REQUEST_CODE_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        var photoFile: File? = null
        try {
            photoFile = createImageFile()
        } catch (ex: IOException) {
            Log.e(TAG, "IOException while creating image file", ex)
            Toast.makeText(this, "Error creating image file.", Toast.LENGTH_SHORT).show()
            return
        }
        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                it
            )
            cameraImageUri = photoURI
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            takePictureLauncher.launch(intent)
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun isValidDateFormat(dateStr: String): Boolean {
        if (dateStr.isEmpty()) return true
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        sdf.isLenient = false
        return try {
            sdf.parse(dateStr)
            dateStr.length == 10
        } catch (e: ParseException) {
            false
        }
    }

    private fun isDateInFuture(dateStr: String): Boolean {
        if (!isValidDateFormat(dateStr)) return false
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        return try {
            val enteredDate = sdf.parse(dateStr) ?: return false
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            enteredDate.after(today)
        } catch (e: ParseException) {
            false
        }
    }

    private fun showCustomDatePickerDialog(editText: TextInputEditText) {
        val dialogView = View.inflate(this, R.layout.dialog_custom_date_picker, null)
        val dayPicker = dialogView.findViewById<NumberPicker>(R.id.dayPicker)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.monthPicker)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)

        val existingDateStr = editText.text.toString()
        if (isValidDateFormat(existingDateStr)) {
            try {
                val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val date = sdf.parse(existingDateStr)
                if (date != null) {
                    calendar.time = date
                }
            } catch (e: Exception) { }
        }

        yearPicker.minValue = 1950
        yearPicker.maxValue = currentYear
        yearPicker.value = calendar.get(Calendar.YEAR)

        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.displayedValues = months
        monthPicker.value = calendar.get(Calendar.MONTH) + 1

        dayPicker.minValue = 1
        dayPicker.maxValue = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        dayPicker.value = calendar.get(Calendar.DAY_OF_MONTH)

        val onValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
            val tempCalendar = Calendar.getInstance()
            tempCalendar.set(Calendar.YEAR, yearPicker.value)
            tempCalendar.set(Calendar.MONTH, monthPicker.value - 1)
            val maxDay = tempCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            if (dayPicker.value > maxDay) {
                dayPicker.value = maxDay
            }
            dayPicker.maxValue = maxDay
        }
        yearPicker.setOnValueChangedListener(onValueChangeListener)
        monthPicker.setOnValueChangedListener(onValueChangeListener)

        AlertDialog.Builder(this)
            .setTitle("Select Date")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedYear = yearPicker.value
                val selectedMonth = monthPicker.value
                val selectedDay = dayPicker.value
                val formattedDate = String.format(Locale.getDefault(), "%02d-%02d-%04d", selectedDay, selectedMonth, selectedYear)
                editText.setText(formattedDate)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }
}