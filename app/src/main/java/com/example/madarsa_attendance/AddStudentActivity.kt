package com.example.madarsa_attendance

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.google.firebase.firestore.Query
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddStudentActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "AddStudentActivity"
        private const val PERMISSION_REQUEST_CODE_STORAGE = 103
        private const val PERMISSION_REQUEST_CODE_CAMERA = 104
    }

    // UI Components
    private lateinit var spinnerTeachers: Spinner
    private lateinit var tvLabelSelectTeacher: TextView
    private lateinit var etStudentName: TextInputEditText
    private lateinit var tilStudentName: TextInputLayout
    private lateinit var etParentName: TextInputEditText
    private lateinit var tilParentName: TextInputLayout
    private lateinit var etParentMobileNumber: TextInputEditText
    private lateinit var tilParentMobileNumber: TextInputLayout
    private lateinit var ivStudentProfileImage: ImageView
    private lateinit var cardViewProfileImage: MaterialCardView
    private lateinit var btnSelectImageStudent: MaterialButton
    private lateinit var btnSaveStudent: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var etRegNo: TextInputEditText
    private lateinit var tilRegNo: TextInputLayout
    private lateinit var rgGender: RadioGroup
    private lateinit var etBirthDate: TextInputEditText
    private lateinit var tilBirthDate: TextInputLayout
    private lateinit var etAdmissionDate: TextInputEditText
    private lateinit var tilAdmissionDate: TextInputLayout
    private lateinit var etMonthlyFee: TextInputEditText
    private lateinit var tilMonthlyFee: TextInputLayout
    private lateinit var etAlternateMobile: TextInputEditText
    private lateinit var tilAlternateMobile: TextInputLayout
    private lateinit var etAddress: TextInputEditText
    private lateinit var tilAddress: TextInputLayout

    // Backend and data
    private lateinit var db: FirebaseFirestore
    private var teacherList = mutableListOf<TeacherSpinnerItem>()
    private var selectedTeacher: TeacherSpinnerItem? = null
    private var preselectedTeacherId: String? = null
    private var preselectedTeacherName: String? = null
    private var imageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private var cameraImageUri: Uri? = null
    // Cloudinary Preset
    private val UNSIGNED_UPLOAD_PRESET_STUDENT = "BIBI_AYESHA_MASJID"
    private var currentOrganizationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_student)

        db = FirebaseFirestore.getInstance()
        preselectedTeacherId = intent.getStringExtra("PRESELECTED_TEACHER_ID")
        preselectedTeacherName = intent.getStringExtra("PRESELECTED_TEACHER_NAME")
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(this)

        if (currentOrganizationId == null) {
            Toast.makeText(this, "Organization data missing. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val toolbar: MaterialToolbar = findViewById(R.id.add_student_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        spinnerTeachers = findViewById(R.id.spinnerTeachers)
        tvLabelSelectTeacher = findViewById(R.id.textViewLabelSelectTeacher)
        etStudentName = findViewById(R.id.etStudentName)
        tilStudentName = findViewById(R.id.tilStudentName)
        etParentName = findViewById(R.id.etParentName)
        tilParentName = findViewById(R.id.tilParentName)
        etParentMobileNumber = findViewById(R.id.etParentMobileNumber)
        tilParentMobileNumber = findViewById(R.id.tilParentMobileNumber)
        ivStudentProfileImage = findViewById(R.id.ivStudentProfileImage)
        cardViewProfileImage = findViewById(R.id.cardViewProfileImageStudent)
        btnSelectImageStudent = findViewById(R.id.btnSelectImageStudent)
        btnSaveStudent = findViewById(R.id.btnSaveStudent)
        progressBar = findViewById(R.id.progressBarAddStudent)
        etRegNo = findViewById(R.id.etRegNo)
        tilRegNo = findViewById(R.id.tilRegNo)
        rgGender = findViewById(R.id.rgGender)
        etBirthDate = findViewById(R.id.etBirthDate)
        tilBirthDate = findViewById(R.id.tilBirthDate)
        etAdmissionDate = findViewById(R.id.etAdmissionDate)
        tilAdmissionDate = findViewById(R.id.tilAdmissionDate)
        etMonthlyFee = findViewById(R.id.etMonthlyFee)
        tilMonthlyFee = findViewById(R.id.tilMonthlyFee)
        etAlternateMobile = findViewById(R.id.etAlternateMobile)
        tilAlternateMobile = findViewById(R.id.tilAlternateMobile)
        etAddress = findViewById(R.id.etAddress)
        tilAddress = findViewById(R.id.tilAddress)
    }

    private fun setupListeners() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    imageUri = uri
                    Glide.with(this).load(uri).circleCrop().placeholder(R.drawable.student).into(ivStudentProfileImage)
                }
            }
        }

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                cameraImageUri?.let { uri ->
                    imageUri = uri
                    Glide.with(this).load(uri).circleCrop().placeholder(R.drawable.student).into(ivStudentProfileImage)
                }
            }
        }

        val imageSelectionClickListener = View.OnClickListener { showImageSourceDialog() }
        btnSelectImageStudent.setOnClickListener(imageSelectionClickListener)
        cardViewProfileImage.setOnClickListener(imageSelectionClickListener)

        etBirthDate.addTextChangedListener(DateTextWatcher(etBirthDate))
        etAdmissionDate.addTextChangedListener(DateTextWatcher(etAdmissionDate))

        tilBirthDate.setEndIconOnClickListener {
            showCustomDatePickerDialog(etBirthDate)
        }
        tilAdmissionDate.setEndIconOnClickListener {
            showCustomDatePickerDialog(etAdmissionDate)
        }

        if (preselectedTeacherId != null && preselectedTeacherName != null) {
            spinnerTeachers.visibility = View.GONE
            tvLabelSelectTeacher.visibility = View.GONE
            selectedTeacher = TeacherSpinnerItem(preselectedTeacherId!!, preselectedTeacherName!!, null)
        } else {
            loadTeachersIntoSpinner()
        }

        fetchNextRegistrationNumber()
        btnSaveStudent.setOnClickListener { saveStudent() }
    }

    private fun fetchNextRegistrationNumber() {
        if (currentOrganizationId == null) {
            etRegNo.isEnabled = false
            return
        }
        tilRegNo.helperText = "Suggesting next number..."
        etRegNo.isEnabled = false
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students")
            .orderBy("regNo", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                var nextRegNo = 101
                if (!documents.isEmpty) {
                    val lastRegNoStr = documents.documents[0].getString("regNo")
                    val lastRegNo = lastRegNoStr?.toIntOrNull()
                    if (lastRegNo != null) nextRegNo = lastRegNo + 1
                }
                etRegNo.setText(nextRegNo.toString())
                tilRegNo.helperText = "Next available number is suggested"
                etRegNo.isEnabled = true
            }
            .addOnFailureListener {
                tilRegNo.helperText = "Enter registration number manually"
                etRegNo.isEnabled = true
            }
    }

    private fun setInputsEnabled(enabled: Boolean, showProgressForSpinner: Boolean = false) {
        progressBar.visibility = if (!enabled && !showProgressForSpinner) View.VISIBLE else View.GONE
        btnSaveStudent.isEnabled = enabled
        spinnerTeachers.isEnabled = enabled
        etStudentName.isEnabled = enabled
        etParentName.isEnabled = enabled
        etParentMobileNumber.isEnabled = enabled
        etRegNo.isEnabled = enabled
        rgGender.isEnabled = enabled
        etBirthDate.isEnabled = enabled
        etAdmissionDate.isEnabled = enabled
        etMonthlyFee.isEnabled = enabled
        etAlternateMobile.isEnabled = enabled
        etAddress.isEnabled = enabled
        btnSelectImageStudent.isEnabled = enabled
        cardViewProfileImage.isClickable = enabled
    }

    private fun validateStudentInputs(): Boolean {
        var isValid = true
        tilStudentName.error = null; tilParentName.error = null; tilParentMobileNumber.error = null
        tilRegNo.error = null; tilMonthlyFee.error = null; tilAlternateMobile.error = null
        tilBirthDate.error = null; tilAdmissionDate.error = null

        if (selectedTeacher == null && preselectedTeacherId == null) {
            Toast.makeText(this, "Please select a teacher.", Toast.LENGTH_SHORT).show(); isValid = false
        }
        if (etStudentName.text.toString().trim().isEmpty()) { tilStudentName.error = "Required"; isValid = false }
        if (etParentName.text.toString().trim().isEmpty()) { tilParentName.error = "Required"; isValid = false }
        if (etParentMobileNumber.text.toString().trim().isEmpty()) { tilParentMobileNumber.error = "Required"; isValid = false }
        else if (!isValidIndianMobileNumber(etParentMobileNumber.text.toString().trim())) { tilParentMobileNumber.error = "Invalid"; isValid = false }

        val alternateMobile = etAlternateMobile.text.toString().trim()
        if (alternateMobile.isNotEmpty() && !isValidIndianMobileNumber(alternateMobile)) { tilAlternateMobile.error = "Invalid"; isValid = false }

        if (etRegNo.text.toString().trim().isEmpty()) { tilRegNo.error = "Required"; isValid = false }
        if (rgGender.checkedRadioButtonId == -1) { Toast.makeText(this, "Select Gender", Toast.LENGTH_SHORT).show(); isValid = false }
        if (etMonthlyFee.text.toString().trim().isEmpty()) { tilMonthlyFee.error = "Required"; isValid = false }

        val birthDateStr = etBirthDate.text.toString().trim()
        if (birthDateStr.isNotEmpty() && !isValidDateFormat(birthDateStr)) { tilBirthDate.error = "Invalid Date"; isValid = false }
        else if (isDateInFuture(birthDateStr)) { tilBirthDate.error = "Future Date"; isValid = false }

        val admissionDateStr = etAdmissionDate.text.toString().trim()
        if (admissionDateStr.isNotEmpty() && !isValidDateFormat(admissionDateStr)) { tilAdmissionDate.error = "Invalid Date"; isValid = false }
        else if (isDateInFuture(admissionDateStr)) { tilAdmissionDate.error = "Future Date"; isValid = false }

        return isValid
    }

    private fun saveStudent() {
        if (!validateStudentInputs()) return
        if (currentOrganizationId == null) {
            handleSaveFailure(Exception("Organization ID is null"), "Cannot save: Org ID is missing.")
            return
        }

        setInputsEnabled(false)

        val studentName = etStudentName.text.toString().trim()
        val parentNameStr = etParentName.text.toString().trim()
        val parentMobileStr = etParentMobileNumber.text.toString().trim()
        val monthlyFee = etMonthlyFee.text.toString().trim().toDoubleOrNull() ?: 0.0
        val regNo = etRegNo.text.toString().trim()
        val alternateMobile = etAlternateMobile.text.toString().trim().ifEmpty { null }
        val address = etAddress.text.toString().trim().ifEmpty { null }
        val finalTeacher = selectedTeacher ?: TeacherSpinnerItem(preselectedTeacherId!!, preselectedTeacherName!!, null)

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").whereEqualTo("regNo", regNo).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    uploadImageAndSaveStudent(
                        studentName, parentNameStr, parentMobileStr, monthlyFee,
                        regNo, finalTeacher,
                        findViewById<RadioButton>(rgGender.checkedRadioButtonId).text.toString(),
                        etBirthDate.text.toString().trim().ifEmpty { null },
                        etAdmissionDate.text.toString().trim().ifEmpty { null },
                        alternateMobile, address
                    )
                } else {
                    handleSaveFailure(Exception("Reg No already exists."), "This registration number already exists.")
                }
            }
            .addOnFailureListener { e -> handleSaveFailure(e, "Error checking registration number.") }
    }

    private fun uploadImageAndSaveStudent(
        studentName: String, parentName: String, parentMobile: String, monthlyFee: Double,
        regNo: String, teacher: TeacherSpinnerItem, gender: String, birthDate: String?, admissionDate: String?,
        alternateMobile: String?, address: String?
    ) {
        if (imageUri != null) {
            lifecycleScope.launch {
                try {
                    val imageFile = uriToFile(imageUri!!)
                    val compressedImageFile = Compressor.compress(this@AddStudentActivity, imageFile) {
                        quality(80)
                        size(100 * 1024)
                    }

                    MediaManager.get().upload(compressedImageFile.path)
                        .unsigned(UNSIGNED_UPLOAD_PRESET_STUDENT)
                        .option("folder", "student_profiles")
                        .callback(object : UploadCallback {
                            override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                                val imageUrl = resultData?.get("secure_url") as? String
                                saveStudentDataToFirestore(
                                    studentName, parentName, parentMobile, monthlyFee,
                                    regNo, teacher, gender, birthDate, admissionDate,
                                    alternateMobile, address, imageUrl
                                )
                            }
                            override fun onError(requestId: String?, error: ErrorInfo?) {
                                Log.e(TAG, "Image Upload Failed: ${error?.description}")
                                saveStudentDataToFirestore(studentName, parentName, parentMobile, monthlyFee, regNo, teacher, gender, birthDate, admissionDate, alternateMobile, address, null)
                            }
                            override fun onStart(requestId: String?) {}
                            override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                            override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                        }).dispatch()
                } catch (e: Exception) {
                    handleSaveFailure(e, "Failed to process image.")
                }
            }
        } else {
            saveStudentDataToFirestore(studentName, parentName, parentMobile, monthlyFee, regNo, teacher, gender, birthDate, admissionDate, alternateMobile, address, null)
        }
    }

    private fun saveStudentDataToFirestore(
        studentName: String, parentName: String, parentMobile: String,
        monthlyFee: Double, regNo: String,
        teacher: TeacherSpinnerItem, gender: String, birthDate: String?, admissionDate: String?,
        alternateMobile: String?, address: String?,
        profileImageUrl: String?
    ) {
        if (currentOrganizationId == null) return

        val studentData = hashMapOf(
            "studentName" to studentName,
            "parentName" to parentName,
            "parentMobileNumber" to parentMobile,
            "teacherId" to teacher.id,
            "teacherName" to teacher.name,
            "profileImageUrl" to (profileImageUrl ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "regNo" to regNo,
            "gender" to gender,
            "birthDate" to birthDate,
            "admissionDate" to admissionDate,
            "monthlyFee" to monthlyFee,
            "isActive" to true,
            "alternateMobileNumber" to alternateMobile,
            "address" to address
        )

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("students").add(studentData).addOnSuccessListener { documentRef ->

                // --- NEW PDF & WHATSAPP LOGIC ---
                // 1. Create Student Object
                val newStudent = StudentDetailsItem(
                    id = documentRef.id,
                    studentName = studentName,
                    parentName = parentName,
                    parentMobileNumber = parentMobile,
                    teacherId = teacher.id,
                    teacherName = teacher.name,
                    profileImageUrl = profileImageUrl,
                    regNo = regNo,
                    gender = gender,
                    birthDate = birthDate,
                    admissionDate = admissionDate,
                    monthlyFee = monthlyFee,
                    alternateMobileNumber = alternateMobile,
                    address = address,
                    isActive = true
                )

                // 2. Generate PDF and Share
                lifecycleScope.launch {
                    val generator = AdmissionFormGenerator(this@AddStudentActivity)
                    val pdfUri = generator.generateAdmissionForm(newStudent)

                    if (pdfUri != null) {
                        StatusDialogFragment.newInstance(true, "Student Added! Opening WhatsApp...").show(supportFragmentManager, "successDialog")
                        delay(1000)
                        sharePdfToWhatsApp(pdfUri, parentMobile)
                    } else {
                        StatusDialogFragment.newInstance(true, "Student Added (PDF Failed)").show(supportFragmentManager, "successDialog")
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }.addOnFailureListener { e -> handleSaveFailure(e, "Failed to save student data.") }
    }

    private fun sharePdfToWhatsApp(pdfUri: Uri, mobileNumber: String?) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)

                if (!mobileNumber.isNullOrEmpty()) {
                    val cleanNumber = mobileNumber.replace(Regex("[^0-9]"), "")
                    val formattedNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
                    putExtra("jid", "$formattedNumber@s.whatsapp.net")
                }

                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "WhatsApp not installed. PDF saved to Downloads.", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening WhatsApp.", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun handleSaveFailure(e: Exception, message: String) {
        setInputsEnabled(true)
        StatusDialogFragment.newInstance(isSuccess = false, message = message).show(supportFragmentManager, "failureDialog")
        Log.e(TAG, "$message: Full error: ${e.message}", e)
    }

    // --- Helper Functions ---
    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)!!
        val tempFile = File.createTempFile("prefix", ".extension", cacheDir)
        tempFile.deleteOnExit()
        tempFile.outputStream().use { fileOut -> inputStream.copyTo(fileOut) }
        inputStream.close()
        return tempFile
    }

    private fun isValidDateFormat(dateStr: String): Boolean {
        if (dateStr.isEmpty()) return true
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        sdf.isLenient = false
        return try { sdf.parse(dateStr); true } catch (e: Exception) { false }
    }

    private fun isDateInFuture(dateStr: String): Boolean {
        if (!isValidDateFormat(dateStr)) return false
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        return try {
            val enteredDate = sdf.parse(dateStr) ?: return false
            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
            enteredDate.after(today)
        } catch (e: ParseException) { false }
    }

    private fun showCustomDatePickerDialog(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = android.app.DatePickerDialog(this, { _, year, month, day ->
            val formattedDate = String.format(Locale.getDefault(), "%02d-%02d-%04d", day, month + 1, year)
            editText.setText(formattedDate)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        datePickerDialog.show()
    }

    private fun isValidIndianMobileNumber(mobile: String) = mobile.length == 10 && mobile.all { it.isDigit() }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this).setTitle("Select Image Source").setItems(options) { _, which ->
            when (which) { 0 -> checkCameraPermissions(); 1 -> checkStoragePermissions() }
        }.show()
    }
    private fun checkStoragePermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE_STORAGE) else openGallery()
    }
    private fun checkCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE_CAMERA) else openCamera()
    }
    private fun openGallery() { imagePickerLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)) }
    private fun openCamera() {
        try {
            val photoFile = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
            cameraImageUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", photoFile)
            takePictureLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri) })
        } catch (e: IOException) { Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show() }
    }
    private fun loadTeachersIntoSpinner() {
        if (currentOrganizationId == null) return
        db.collection("organizations").document(currentOrganizationId!!).collection("teachers").get().addOnSuccessListener {
            teacherList.clear(); teacherList.add(TeacherSpinnerItem("", "Select Teacher", null))
            it.forEach { doc -> teacherList.add(TeacherSpinnerItem(doc.id, doc.getString("teacherName")?:"", null)) }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, teacherList.map { item -> item.name })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTeachers.adapter = adapter
            spinnerTeachers.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { selectedTeacher = if(pos > 0) teacherList[pos] else null }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) { PERMISSION_REQUEST_CODE_STORAGE -> openGallery(); PERMISSION_REQUEST_CODE_CAMERA -> openCamera() }
        }
    }
}