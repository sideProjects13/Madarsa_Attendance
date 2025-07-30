package com.example.madarsa_attendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etLoginEmail: TextInputEditText
    private lateinit var etLoginPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressBarLogin: ProgressBar
    private lateinit var tvGoToRegisterOrg: TextView
    private lateinit var tvForgotPassword: TextView // Optional

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Check if user is already logged in and has an organizationId stored
        if (auth.currentUser != null && getOrganizationId() != null) {
            // User is logged in and organization ID is available, go to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        etLoginEmail = findViewById(R.id.etLoginEmail)
        etLoginPassword = findViewById(R.id.etLoginPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBarLogin = findViewById(R.id.progressBarLogin)
        tvGoToRegisterOrg = findViewById(R.id.tvGoToRegisterOrg)
        tvForgotPassword = findViewById(R.id.tvForgotPassword) // Optional




        btnLogin.setOnClickListener {
            loginUser()
        }

        tvGoToRegisterOrg.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java)) // Redirect to organization registration
        }

        tvForgotPassword.setOnClickListener {
            // Implement forgot password logic, e.g., show a dialog to enter email
            showForgotPasswordDialog()
        }
    }

    private fun loginUser() {
        val email = etLoginEmail.text.toString().trim()
        val password = etLoginPassword.text.toString().trim()

        if (email.isEmpty()) {
            etLoginEmail.error = "Email is required"
            etLoginEmail.requestFocus()
            return
        }
        if (password.isEmpty()) {
            etLoginPassword.error = "Password is required"
            etLoginPassword.requestFocus()
            return
        }

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        // Fetch organizationId from users collection
                        db.collection("users").document(userId).get()
                            .addOnSuccessListener { document ->
                                setLoading(false)
                                val organizationId = document.getString("organizationId")
                                val role = document.getString("role") // Get the role as well

                                if (organizationId != null && role == "admin") { // Ensure it's an admin user
                                    saveOrganizationId(organizationId)
                                    Toast.makeText(this, "Login Successful! Welcome to ${getOrganizationName()}", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, MainActivity::class.java))
                                    finish()
                                } else {
                                    // User exists but no organizationId or not an admin, or role is different
                                    auth.signOut() // Sign out this user
                                    Toast.makeText(this, "Authentication failed. Invalid organization or role.", Toast.LENGTH_LONG).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                setLoading(false)
                                auth.signOut() // Sign out user if fetching orgId fails
                                Toast.makeText(this, "Error fetching organization data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        setLoading(false)
                        Toast.makeText(this, "User ID not found after login.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    setLoading(false)
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
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
        // Simple dialog to prompt for email to send reset link
        val builder = androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
        builder.setTitle("Reset Password")
        builder.setMessage("Enter your email address to receive a password reset link.")

        val input = TextInputEditText(this)
        input.hint = "Email"
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        builder.setView(input)

        builder.setPositiveButton("Send Reset Link") { dialog, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty()) {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
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

    // Helper functions for SharedPreferences (will be moved to a central place later)
    private fun saveOrganizationId(organizationId: String) {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("organization_id", organizationId)
            apply()
        }
    }

    private fun getOrganizationId(): String? {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("organization_id", null)
    }

    private fun saveOrganizationName(organizationName: String) {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("organization_name", organizationName)
            apply()
        }
    }

    private fun getOrganizationName(): String? {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("organization_name", "Your Organization") // Default name
    }
}