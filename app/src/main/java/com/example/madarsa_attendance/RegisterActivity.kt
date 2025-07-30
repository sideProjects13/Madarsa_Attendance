package com.example.madarsa_attendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.madarsa_attendance.models.AppUser
import com.example.madarsa_attendance.models.Organization

// Data class to represent an Organization

// Data class to link Firebase Auth UID to Organization ID and Role


class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etOrganizationNameRegister: TextInputEditText // NEW
    private lateinit var etAdminNameRegister: TextInputEditText // Renamed
    private lateinit var etAdminEmailRegister: TextInputEditText // Renamed
    private lateinit var etAdminMobileRegister: TextInputEditText // Renamed
    private lateinit var etPasswordRegister: TextInputEditText
    private lateinit var etConfirmPasswordRegister: TextInputEditText
    private lateinit var btnRegisterOrganization: MaterialButton // Renamed
    private lateinit var progressBarRegister: ProgressBar
    private lateinit var tvGoToLogin: TextView

    // Not directly used in the current organization registration flow, but keep if you want organization logo selection
    // private lateinit var ivTeacherProfileImageRegister: ImageView
    // private lateinit var btnSelectImageRegister: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupToolbar()
        initViews()
        setupListeners()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.register_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun initViews() {
        // Image selection views commented out as per XML for simplicity for Organization registration
        // ivTeacherProfileImageRegister = findViewById(R.id.ivTeacherProfileImageRegister)
        // btnSelectImageRegister = findViewById(R.id.btnSelectImageRegister)

        etOrganizationNameRegister = findViewById(R.id.etOrganizationNameRegister) // NEW
        etAdminNameRegister = findViewById(R.id.etAdminNameRegister) // Renamed from etTeacherNameRegister
        etAdminEmailRegister = findViewById(R.id.etAdminEmailRegister) // Renamed from etTeacherEmailRegister
        etAdminMobileRegister = findViewById(R.id.etAdminMobileRegister) // Renamed from etTeacherMobileRegister
        etPasswordRegister = findViewById(R.id.etPasswordRegister)
        etConfirmPasswordRegister = findViewById(R.id.etConfirmPasswordRegister)
        btnRegisterOrganization = findViewById(R.id.btnRegisterOrganization) // Renamed
        progressBarRegister = findViewById(R.id.progressBarRegister)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)
    }

    private fun setupListeners() {
        // btnSelectImageRegister.setOnClickListener { /* Implement image selection logic */ }

        btnRegisterOrganization.setOnClickListener {
            registerOrganizationAndAdmin()
        }

        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerOrganizationAndAdmin() {
        val orgName = etOrganizationNameRegister.text.toString().trim()
        val adminName = etAdminNameRegister.text.toString().trim()
        val adminEmail = etAdminEmailRegister.text.toString().trim()
        val adminMobile = etAdminMobileRegister.text.toString().trim() // Optional
        val password = etPasswordRegister.text.toString().trim()
        val confirmPassword = etConfirmPasswordRegister.text.toString().trim()

        if (orgName.isEmpty()) {
            etOrganizationNameRegister.error = "Organization name is required"
            etOrganizationNameRegister.requestFocus()
            return
        }
        if (adminName.isEmpty()) {
            etAdminNameRegister.error = "Admin name is required"
            etAdminNameRegister.requestFocus()
            return
        }
        if (adminEmail.isEmpty()) {
            etAdminEmailRegister.error = "Email is required"
            etAdminEmailRegister.requestFocus()
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(adminEmail).matches()) {
            etAdminEmailRegister.error = "Invalid email format"
            etAdminEmailRegister.requestFocus()
            return
        }
        if (password.length < 6) {
            etPasswordRegister.error = "Password must be at least 6 characters"
            etPasswordRegister.requestFocus()
            return
        }
        if (password != confirmPassword) {
            etConfirmPasswordRegister.error = "Passwords do not match"
            etConfirmPasswordRegister.requestFocus()
            return
        }

        setLoading(true)

        // 1. Register user with Firebase Authentication
        auth.createUserWithEmailAndPassword(adminEmail, password)
            .addOnCompleteListener(this) { authTask ->
                if (authTask.isSuccessful) {
                    val firebaseUser = authTask.result?.user
                    firebaseUser?.let { user ->
                        // 2. Create organization document in Firestore
                        val organization = Organization(
                            organizationName = orgName,
                            adminEmail = adminEmail,
                            adminName = adminName,
                            adminMobile = adminMobile.ifEmpty { null }, // Save as null if empty
                            createdAt = com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )

                        db.collection("organizations")
                            .add(organization) // Let Firestore auto-generate the ID
                            .addOnSuccessListener { orgDocumentRef ->
                                val organizationId = orgDocumentRef.id

                                // 3. Create user mapping document in top-level 'users' collection
                                val appUser = AppUser(
                                    organizationId = organizationId,
                                    role = "admin",
                                    email = adminEmail,
                                    name = adminName,
                                    mobile = adminMobile.ifEmpty { null }
                                )
                                db.collection("users").document(user.uid)
                                    .set(appUser)
                                    .addOnSuccessListener {
                                        setLoading(false)
                                        // Save organization ID and Name to SharedPreferences immediately for seamless login
                                        saveOrganizationId(organizationId)
                                        saveOrganizationName(orgName)

                                        Toast.makeText(this, "Organization & Admin Registered Successfully!", Toast.LENGTH_LONG).show()
                                        // Optionally, sign in the user directly or redirect to login
                                        startActivity(Intent(this, MainActivity::class.java)) // Go to main activity
                                        finish()

                                    }
                                    .addOnFailureListener { e ->
                                        setLoading(false)
                                        // If user mapping fails, consider deleting Firebase Auth user and organization doc
                                        user.delete()
                                        orgDocumentRef.delete()
                                        Toast.makeText(this, "Failed to create user data: ${e.message}. Please try again.", Toast.LENGTH_LONG).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                setLoading(false)
                                // If organization creation fails, delete the Firebase Auth user
                                user.delete()
                                Toast.makeText(this, "Failed to create organization: ${e.message}. Please try again.", Toast.LENGTH_LONG).show()
                            }
                    }
                } else {
                    setLoading(false)
                    Toast.makeText(this, "Registration failed: ${authTask.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBarRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnRegisterOrganization.isEnabled = !isLoading
        etOrganizationNameRegister.isEnabled = !isLoading
        etAdminNameRegister.isEnabled = !isLoading
        etAdminEmailRegister.isEnabled = !isLoading
        etAdminMobileRegister.isEnabled = !isLoading
        etPasswordRegister.isEnabled = !isLoading
        etConfirmPasswordRegister.isEnabled = !isLoading
        tvGoToLogin.isEnabled = !isLoading
    }

    // Helper functions for SharedPreferences (will be moved to a central place later)
    private fun saveOrganizationId(organizationId: String) {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("organization_id", organizationId)
            apply()
        }
    }

    private fun saveOrganizationName(organizationName: String) {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("organization_name", organizationName)
            apply()
        }
    }
}