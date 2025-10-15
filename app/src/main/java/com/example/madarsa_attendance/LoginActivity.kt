package com.example.madarsa_attendance

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.madarsa_attendance.models.AppUser
import com.example.madarsa_attendance.models.Organization
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etLoginEmail: TextInputEditText
    private lateinit var etLoginPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressBarLogin: ProgressBar
    private lateinit var tvGoToRegisterOrg: TextView
    private lateinit var tvForgotPassword: TextView

    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                allGranted = false
            }
        }

        if (allGranted) {
            Toast.makeText(this, "All permissions granted. Thank you!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions were denied. Certain features may not work correctly.", Toast.LENGTH_LONG).show()
        }
        markPermissionsAsRequested()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        if (isFirstTimeLaunch()) {
            requestAppPermissions()
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser != null) {
            val role = FirebaseAuthManager.getUserRole(this)
            when {
                role == "superadmin" -> {
                    startActivity(Intent(this, SuperAdminDashboardActivity::class.java))
                    finish()
                    return
                }
                role == "admin" && FirebaseAuthManager.getOrganizationId(this) != null -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    return
                }
                role == "teacher" && FirebaseAuthManager.getOrganizationId(this) != null -> {
                    // --- THIS IS THE FIX ---
                    // This line now correctly sends logged-in teachers to the new dashboard
                    // every time they open the app.
                    startActivity(Intent(this, TeacherHomeActivity::class.java))
                    // --- END OF FIX ---
                    finish()
                    return
                }
            }
        }

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        etLoginEmail = findViewById(R.id.etLoginEmail)
        etLoginPassword = findViewById(R.id.etLoginPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBarLogin = findViewById(R.id.progressBarLogin)
        tvGoToRegisterOrg = findViewById(R.id.tvGoToRegisterOrg)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener { loginUser() }
        tvGoToRegisterOrg.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        tvForgotPassword.setOnClickListener { showForgotPasswordDialog() }
    }

    private fun loginUser() {
        val email = etLoginEmail.text.toString().trim()
        val password = etLoginPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password are required.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user == null) {
                    logoutAndShowError("Failed to get user ID.")
                    return@addOnSuccessListener
                }
                checkUserStatusAndProceed(user)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                StatusDialogFragment.newInstance(false, "Login Failed: ${e.message}").show(supportFragmentManager, "failureDialog")
            }
    }

    private fun checkUserStatusAndProceed(user: FirebaseUser) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val appUser = document.toObject(AppUser::class.java)
                    when (appUser?.accountStatus) {
                        "active" -> {
                            when (appUser.role) {
                                "superadmin" -> handleSuperAdminLogin(user.uid)
                                "admin" -> handleAdminLogin(user.uid)
                                "teacher" -> handleTeacherLogin(user.uid)
                                else -> logoutAndShowError("Unknown user role. Please contact support.")
                            }
                        }
                        "pending" -> logoutAndShowError("Your account is pending approval by the Super Admin.")
                        "inactive" -> logoutAndShowError("Your account has been deactivated. Please contact support.")
                        else -> logoutAndShowError("Account status is unknown. Please contact support.")
                    }
                } else {
                    logoutAndShowError("User details not found in database.")
                }
            }
            .addOnFailureListener {
                logoutAndShowError("Failed to verify user role.")
            }
    }

    private fun handleSuperAdminLogin(userId: String) {
        FirebaseAuthManager.saveLoginSession(
            context = this,
            role = "superadmin",
            orgId = "",
            orgName = "Super Admin",
            activeLogoUrl = null,
            address = null
        )
        navigateToActivity(SuperAdminDashboardActivity::class.java)
    }

    private fun handleAdminLogin(userId: String) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val orgId = userDoc.getString("organizationId")
                if (orgId != null) {
                    db.collection("organizations").document(orgId).get()
                        .addOnSuccessListener { orgDoc ->
                            if (orgDoc.exists()) {
                                val org = orgDoc.toObject(Organization::class.java)
                                FirebaseAuthManager.saveLoginSession(this, "admin", orgId, org?.organizationName ?: "My Madarsa", org?.logoUrl, org?.address)
                                navigateToActivity(MainActivity::class.java)
                            } else {
                                logoutAndShowError("Organization data not found.")
                            }
                        }
                        .addOnFailureListener { logoutAndShowError("Failed to fetch organization details.") }
                } else {
                    logoutAndShowError("Admin data is incomplete.")
                }
            }
            .addOnFailureListener { logoutAndShowError("Failed to verify admin status.") }
    }

    private fun handleTeacherLogin(userId: String) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val orgId = userDoc.getString("organizationId")
                if (orgId != null) {
                    db.collection("organizations").document(orgId).get()
                        .addOnSuccessListener { orgDoc ->
                            if (orgDoc.exists()) {
                                val org = orgDoc.toObject(Organization::class.java)
                                FirebaseAuthManager.saveLoginSession(this, "teacher", orgId, org?.organizationName ?: "My Madarsa", org?.logoUrl, org?.address)
                                navigateToActivity(TeacherHomeActivity::class.java)
                            } else {
                                logoutAndShowError("Organization data for teacher not found.")
                            }
                        }
                        .addOnFailureListener { logoutAndShowError("Failed to fetch organization details for teacher.") }
                } else {
                    logoutAndShowError("Teacher data is incomplete (missing organization).")
                }
            }
            .addOnFailureListener {
                logoutAndShowError("Failed to verify teacher status.")
            }
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            setLoading(false)
            startActivity(Intent(this, activityClass))
            finish()
        }
    }

    private fun logoutAndShowError(message: String) {
        auth.signOut()
        setLoading(false)
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            StatusDialogFragment.newInstance(false, message).show(supportFragmentManager, "failureDialog")
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBarLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !isLoading
        etLoginEmail.isEnabled = !isLoading
        etLoginPassword.isEnabled = !isLoading
        tvGoToRegisterOrg.isEnabled = !isLoading
        tvForgotPassword.isEnabled = !isLoading
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
        builder.setTitle("Reset Password")
        builder.setMessage("Enter your email address to receive a password reset link.")
        val input = TextInputEditText(this)
        input.hint = "Email"
        builder.setView(input)
        builder.setPositiveButton("Send Reset Link") { dialog, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty()) {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Password reset email sent.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Failed to send reset email.", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsNotGranted.isNotEmpty()) {
            multiplePermissionsLauncher.launch(permissionsNotGranted)
        } else {
            markPermissionsAsRequested()
        }
    }

    private fun isFirstTimeLaunch(): Boolean {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("isFirstTime", true)
    }

    private fun markPermissionsAsRequested() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("isFirstTime", false)
            apply()
        }
    }
}