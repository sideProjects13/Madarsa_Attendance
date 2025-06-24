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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
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
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddStudentActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "AddStudentActivity"
        private const val PERMISSION_REQUEST_CODE_STUDENT = 103
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
    private lateinit var etAdmissionDate: TextInputEditText

    // Backend and data
    private lateinit var db: FirebaseFirestore
    private var teacherList = mutableListOf<TeacherSpinnerItem>()
    private var selectedTeacher: TeacherSpinnerItem? = null
    private var preselectedTeacherId: String? = null
    private var preselectedTeacherName: String? = null
    private var imageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private val UNSIGNED_UPLOAD_PRESET_STUDENT = "BIBI_AYESHA_MASJID"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_student)

        db = FirebaseFirestore.getInstance()

        preselectedTeacherId = intent.getStringExtra("PRESELECTED_TEACHER_ID")
        preselectedTeacherName = intent.getStringExtra("PRESELECTED_TEACHER_NAME")

        val toolbar: MaterialToolbar = findViewById(R.id.add_student_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Find views
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
        etAdmissionDate = findViewById(R.id.etAdmissionDate)

        // Setup image picker
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    imageUri = uri
                    Glide.with(this).load(uri).circleCrop().placeholder(R.drawable.student).into(ivStudentProfileImage)
                }
            }
        }
        val imageSelectionClickListener = View.OnClickListener { checkAndRequestPermissions() }
        btnSelectImageStudent.setOnClickListener(imageSelectionClickListener)
        cardViewProfileImage.setOnClickListener(imageSelectionClickListener)

        // Setup date pickers
        etBirthDate.setOnClickListener { showDatePickerDialog(etBirthDate) }
        etAdmissionDate.setOnClickListener { showDatePickerDialog(etAdmissionDate) }

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

    // <<< MODIFIED: This function now just suggests the number but leaves the field editable >>>
    private fun fetchNextRegistrationNumber() {
        tilRegNo.helperText = "Suggesting next number..."
        etRegNo.isEnabled = false // Disable temporarily while fetching

        db.collection("students")
            .orderBy("regNo", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                var nextRegNo = 101
                if (!documents.isEmpty) {
                    val lastRegNoStr = documents.documents[0].getString("regNo")
                    val lastRegNo = lastRegNoStr?.toIntOrNull()
                    if (lastRegNo != null) {
                        nextRegNo = lastRegNo + 1
                    }
                }
                etRegNo.setText(nextRegNo.toString())
                tilRegNo.helperText = "Next available number is suggested"
                etRegNo.isEnabled = true // <<< THE FIX: Re-enable the field for editing
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching last registration number", e)
                Toast.makeText(this, "Could not suggest next Reg No. Please enter manually.", Toast.LENGTH_LONG).show()
                tilRegNo.helperText = "Enter registration number manually"
                etRegNo.isEnabled = true // <<< THE FIX: Ensure it's enabled on failure
            }
    }

    // <<< REVERTED: The logic for etRegNo is now simplified here >>>
    private fun setInputsEnabled(enabled: Boolean, showProgressForSpinner: Boolean = false) {
        progressBar.visibility = if (!enabled && !showProgressForSpinner) View.VISIBLE else View.GONE
        spinnerTeachers.isEnabled = enabled
        etStudentName.isEnabled = enabled
        etParentName.isEnabled = enabled
        etParentMobileNumber.isEnabled = enabled
        etRegNo.isEnabled = enabled // <<< THE FIX: Always respects the 'enabled' parameter
        rgGender.isEnabled = enabled
        etBirthDate.isEnabled = enabled
        etAdmissionDate.isEnabled = enabled
        btnSelectImageStudent.isEnabled = enabled
        btnSaveStudent.isEnabled = enabled
        cardViewProfileImage.isClickable = enabled
    }

    // All other functions are unchanged from your original working code
    private fun validateStudentInputs(): Boolean {
        var isValid = true
        tilStudentName.error = null
        tilParentName.error = null
        tilParentMobileNumber.error = null
        tilRegNo.error = null

        if (selectedTeacher == null && preselectedTeacherId == null) {
            Toast.makeText(this, "Please select a teacher.", Toast.LENGTH_SHORT).show()
            isValid = false
        }
        if (etStudentName.text.toString().trim().isEmpty()) {
            tilStudentName.error = "Student name is required"; isValid = false
        }
        if (etParentName.text.toString().trim().isEmpty()) {
            tilParentName.error = "Parent's name is required"; isValid = false
        }
        if (etParentMobileNumber.text.toString().trim().isEmpty()) {
            tilParentMobileNumber.error = "Parent's mobile is required"; isValid = false
        } else if (!isValidIndianMobileNumber(etParentMobileNumber.text.toString().trim())) {
            tilParentMobileNumber.error = "Enter a valid 10-digit number"; isValid = false
        }
        if (etRegNo.text.toString().trim().isEmpty()) {
            tilRegNo.error = "Registration number is required"; isValid = false
        }
        if (rgGender.checkedRadioButtonId == -1) {
            Toast.makeText(this, "Please select a gender", Toast.LENGTH_SHORT).show(); isValid = false
        }
        return isValid
    }

    private fun showDatePickerDialog(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            editText.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun checkAndRequestPermissions() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE_STUDENT)
        } else {
            openGallery()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE_STUDENT && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun loadTeachersIntoSpinner() {
        setInputsEnabled(false, showProgressForSpinner = true)
        db.collection("teachers").orderBy("teacherName").get()
            .addOnSuccessListener { documents ->
                teacherList.clear()
                teacherList.add(TeacherSpinnerItem("", "Select a Teacher", null))
                for (document in documents) {
                    val teacher = TeacherSpinnerItem(
                        document.id,
                        document.getString("teacherName") ?: "N/A",
                        document.getString("profileImageUrl")
                    )
                    teacherList.add(teacher)
                }
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, teacherList.map { it.name })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerTeachers.adapter = adapter
                setInputsEnabled(true)

                spinnerTeachers.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        selectedTeacher = if (position > 0) teacherList[position] else null
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {
                        selectedTeacher = null
                    }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error loading teachers: ${exception.message}", Toast.LENGTH_LONG).show()
                setInputsEnabled(true)
            }
    }

    private fun isValidIndianMobileNumber(mobile: String) = mobile.length == 10 && mobile.all { it.isDigit() }

    private fun saveStudent() {
        if (!validateStudentInputs()) return
        setInputsEnabled(false)

        val studentName = etStudentName.text.toString().trim()
        val parentNameStr = etParentName.text.toString().trim()
        val parentMobileStr = etParentMobileNumber.text.toString().trim()
        val teacherToAssignTo = selectedTeacher

        if (teacherToAssignTo == null && preselectedTeacherId == null) {
            handleSaveFailure(Exception("Teacher not selected"), "Error: Teacher not selected.")
            return
        }

        val finalTeacher = teacherToAssignTo ?: TeacherSpinnerItem(preselectedTeacherId!!, preselectedTeacherName!!, null)

        if (imageUri != null) {
            MediaManager.get().upload(imageUri).unsigned(UNSIGNED_UPLOAD_PRESET_STUDENT)
                .option("folder", "student_profiles").callback(object : UploadCallback {
                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        val imageUrl = resultData?.get("secure_url") as? String
                        saveStudentDataToFirestore(studentName, parentNameStr, parentMobileStr, finalTeacher, imageUrl)
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        Toast.makeText(this@AddStudentActivity, "Image upload failed, saving without image.", Toast.LENGTH_LONG).show()
                        saveStudentDataToFirestore(studentName, parentNameStr, parentMobileStr, finalTeacher, null)
                    }
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        } else {
            saveStudentDataToFirestore(studentName, parentNameStr, parentMobileStr, finalTeacher, null)
        }
    }

    private fun saveStudentDataToFirestore(
        studentName: String, parentName: String, parentMobile: String,
        teacher: TeacherSpinnerItem, profileImageUrl: String?
    ) {
        val studentData = hashMapOf(
            "studentName" to studentName,
            "parentName" to parentName,
            "parentMobileNumber" to parentMobile,
            "teacherId" to teacher.id,
            "teacherName" to teacher.name,
            "profileImageUrl" to (profileImageUrl ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "regNo" to etRegNo.text.toString().trim(),
            "gender" to findViewById<RadioButton>(rgGender.checkedRadioButtonId).text.toString(),
            "birthDate" to etBirthDate.text.toString().trim().ifEmpty { null },
            "admissionDate" to etAdmissionDate.text.toString().trim().ifEmpty { null },
            "isActive" to true
        )

        db.collection("students").add(studentData).addOnSuccessListener {
            setInputsEnabled(true)
            Toast.makeText(this, "'$studentName' added!", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_OK)
            finish()
        }.addOnFailureListener { e -> handleSaveFailure(e, "Failed to save data") }
    }

    private fun handleSaveFailure(e: Exception, message: String) {
        setInputsEnabled(true)
        Toast.makeText(this, "$message: ${e.message}", Toast.LENGTH_LONG).show()
        Log.e(TAG, message, e)
    }
}