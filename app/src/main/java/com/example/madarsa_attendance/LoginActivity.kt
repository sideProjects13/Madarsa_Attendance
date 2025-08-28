package com.example.madarsa_attendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var toggleLoginAs: MaterialButtonToggleGroup
    private lateinit var etLoginEmail: TextInputEditText
    private lateinit var etLoginPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressBarLogin: ProgressBar
    private lateinit var tvGoToRegisterOrg: TextView
    private lateinit var tvForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser != null) {
            val role = getUserRole()
            if (role == "admin" && getOrganizationId() != null) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            } else if (role == "teacher" && getOrganizationId() != null) {
                startActivity(Intent(this, TeacherDashboardActivity::class.java))
                finish()
                return
            }
        }

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        toggleLoginAs = findViewById(R.id.toggleLoginAs)
        etLoginEmail = findViewById(R.id.etLoginEmail)
        etLoginPassword = findViewById(R.id.etLoginPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBarLogin = findViewById(R.id.progressBarLogin)
        tvGoToRegisterOrg = findViewById(R.id.tvGoToRegisterOrg)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
    }

    private fun setupListeners() {
        // Hide the toggle group as it's no longer needed for role selection
        toggleLoginAs.visibility = View.GONE

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
                    logoutAndShowError("Login failed, user not found.")
                    return@addOnSuccessListener
                }

                // Unified Login Logic: Check the /users collection for this user's role and orgId
                db.collection("users").document(user.uid).get()
                    .addOnSuccessListener { userDoc ->
                        if (!userDoc.exists()) {
                            logoutAndShowError("User record not found.")
                            return@addOnSuccessListener
                        }

                        val role = userDoc.getString("role")
                        val orgId = userDoc.getString("organizationId")
                        val orgName = userDoc.getString("organizationName") ?: "My Madarsa"

                        if (orgId == null) {
                            logoutAndShowError("User is not linked to an organization.")
                            return@addOnSuccessListener
                        }

                        saveLoginSession(role ?: "unknown", orgId, orgName)

                        // Navigate based on role
                        if (role == "admin") {
                            navigateToActivity(MainActivity::class.java)
                        } else if (role == "teacher") {
                            navigateToActivity(TeacherDashboardActivity::class.java)
                        } else {
                            logoutAndShowError("Unknown user role.")
                        }
                    }
                    .addOnFailureListener { e ->
                        logoutAndShowError("Failed to fetch user data: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                StatusDialogFragment.newInstance(false, "Login Failed: ${e.message}")
                    .show(supportFragmentManager, "failureDialog")
            }
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        StatusDialogFragment.newInstance(true, "Login Successful!", true)
            .show(supportFragmentManager, "successDialog")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, activityClass))
            finish()
        }, 1800)
    }

    private fun logoutAndShowError(message: String) {
        auth.signOut()
        setLoading(false)
        StatusDialogFragment.newInstance(false, message).show(supportFragmentManager, "failureDialog")
    }

    private fun setLoading(isLoading: Boolean) {
        progressBarLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !isLoading
        etLoginEmail.isEnabled = !isLoading
        etLoginPassword.isEnabled = !isLoading
        tvGoToRegisterOrg.isEnabled = !isLoading
        tvForgotPassword.isEnabled = !isLoading
    }

    private fun saveLoginSession(role: String, orgId: String, orgName: String) {
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().apply {
            putString("user_role", role)
            putString("organization_id", orgId)
            putString("organization_name", orgName)
            apply()
        }
    }

    private fun getUserRole(): String? = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("user_role", null)
    private fun getOrganizationId(): String? = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("organization_id", null)
    private fun getOrganizationName(): String? = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("organization_name", null)

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
}