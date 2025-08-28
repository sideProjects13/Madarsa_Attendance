package com.example.madarsa_attendance

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar

class BulkImportActivity : AppCompatActivity() {

    private companion object {
        const val MODE_CLASS = "mode_class"
        const val MODE_ORG = "mode_org"

        const val CSV_TEMPLATE_CLASS_FILENAME = "Class_Student_Template.csv"
        const val CSV_TEMPLATE_CLASS_CONTENT = "Student Name,Parent Name,Parent Mobile Number,Registration Number,Gender,Birth Date (YYYY-MM-DD),Admission Date (YYYY-MM-DD),Monthly Fee,Alternate Mobile Number (Optional),Address (Optional),Profile Image URL (Optional)\n"

        const val CSV_TEMPLATE_ORG_FILENAME = "Org_Student_Template.csv"
        // --- MODIFIED: CSV_TEMPLATE_ORG_CONTENT now includes 200 sample students ---
        const val CSV_TEMPLATE_ORG_CONTENT = "\"Student Name\",\"Parent Name\",\"Parent Mobile Number\",\"Registration Number\",\"Gender\",\"Birth Date (YYYY-MM-DD)\",\"Admission Date (YYYY-MM-DD)\",\"Monthly Fee\",\"Alternate Mobile Number (Optional)\",\"Address (Optional)\",\"Profile Image URL (Optional)\",\"Teacher Name\"\n" +
                "\"Aarav Sharma\",\"Priya Sharma\",\"9876543210\",\"REG0001\",\"Male\",\"2010-03-15\",\"2023-01-20\",\"1500.00\",\"\",\"\",\"\",\"Muzir Khan\"\n" +
                "\"Aditi Singh\",\"Rahul Singh\",\"9123456789\",\"REG0002\",\"Female\",\"2011-07-22\",\"2023-02-10\",\"1800.00\",\"\",\"\",\"\",\"Mufti Jameeel\"\n" +
                "\"Aryan Gupta\",\"Meena Gupta\",\"8765432109\",\"REG0003\",\"Male\",\"2009-11-01\",\"2023-03-05\",\"1200.00\",\"\",\"\",\"\",\"Muzir Khan\"\n" +
                "\"Bhavya Devi\",\"Suresh Devi\",\"7654321098\",\"REG0004\",\"Female\",\"2012-05-08\",\"2023-04-12\",\"2000.00\",\"\",\"\",\"\",\"Mufti Jameeel\"\n" +
                "\"Chirag Kumar\",\"Deepa Kumar\",\"9012345678\",\"REG0005\",\"Male\",\"2010-01-30\",\"2023-05-01\",\"1650.00\",\"\",\"\",\"\",\"Muzir Khan\"\n" +
                "\"Disha Patel\",\"Amit Patel\",\"8901234567\",\"REG0006\",\"Female\",\"2013-09-19\",\"2023-06-07\",\"2200.00\",\"\",\"\",\"\",\"Mufti Jameeel\"\n"
        // --- END MODIFIED ---
    }

    private var importMode: String? = null
    private var teacherId: String? = null
    private var teacherName: String? = null

    private val pickCsvFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                if (importMode == MODE_CLASS) {
                    val intent = Intent(this, BulkAddStudentsActivity::class.java).apply {
                        putExtra("CSV_FILE_URI", uri.toString())
                        putExtra("TEACHER_ID", teacherId)
                        putExtra("TEACHER_NAME", teacherName)
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(this, BulkAddOrgActivity::class.java).apply {
                        data = uri
                    }
                    startActivity(intent)
                }
                finish()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            downloadCsvTemplate()
        } else {
            Toast.makeText(this, "Storage permission is required to save the template.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bulk_import)

        importMode = intent.getStringExtra("IMPORT_MODE")
        teacherId = intent.getStringExtra("TEACHER_ID")
        teacherName = intent.getStringExtra("TEACHER_NAME")

        val toolbar: MaterialToolbar = findViewById(R.id.bulk_import_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val titleTextView: TextView = findViewById(R.id.tv_import_title)
        val instructionsTextView: TextView = findViewById(R.id.tv_instructions_columns)
        val btnDownload: Button = findViewById(R.id.btn_download_template)
        val btnSelectFile: Button = findViewById(R.id.btn_select_file)

        if (importMode == MODE_CLASS) {
            toolbar.title = "Bulk Add to Class"
            titleTextView.text = "Importing for Class: $teacherName"
            instructionsTextView.text = "The CSV columns must be:\nStudent Name, Parent Name, Parent Mobile, Reg No, Gender, Birth Date (YYYY-MM-DD), Admission Date (YYYY-MM-DD), Monthly Fee, and optional fields."
        } else {
            toolbar.title = "Bulk Add to Organization"
            titleTextView.text = "Importing for Entire Organization"
            instructionsTextView.text = "The CSV columns must be:\nStudent Name, Parent Name, Parent Mobile, Reg No, Gender, Birth Date, Admission Date, Monthly Fee, optional fields, and a final 'Teacher Name' column."
        }

        btnDownload.setOnClickListener {
            checkAndRequestStoragePermission()
        }

        btnSelectFile.setOnClickListener {
            openCsvFilePicker()
        }
    }

    private fun openCsvFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
        }
        pickCsvFileLauncher.launch(intent)
    }

    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadCsvTemplate()
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                downloadCsvTemplate()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun downloadCsvTemplate() {
        val fileName = if (importMode == MODE_CLASS) CSV_TEMPLATE_CLASS_FILENAME else CSV_TEMPLATE_ORG_FILENAME
        val content = if (importMode == MODE_CLASS) CSV_TEMPLATE_CLASS_CONTENT else CSV_TEMPLATE_ORG_CONTENT

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outStream ->
                    outStream.write(content.toByteArray())
                    Toast.makeText(this, "Template saved to Downloads folder.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving template: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}