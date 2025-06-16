package com.example.madarsa_attendance

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore // Import Firestore

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
        const val PREFS_NAME = "MadarsaTeacherPrefs"
        const val KEY_LOGGED_IN_UID = "loggedInTeacherUID"
    }

    private lateinit var etEmailOrMobile: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilEmailOrMobile: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvGoToRegister: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: FirebaseFirestore // Add Firestore instance


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        firebaseAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance() // Initialize Firestore
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (firebaseAuth.currentUser != null && sharedPreferences.getString(KEY_LOGGED_IN_UID, null) == firebaseAuth.currentUser?.uid) {
            Log.d(TAG, "User already logged in: ${firebaseAuth.currentUser?.uid}. Navigating to home.")
            // When navigating from here, TeacherOptionsActivity will rely on its internal Auth check and Firestore fetch
            navigateToHome(firebaseAuth.currentUser!!.uid, firebaseAuth.currentUser!!.displayName)
            return
        }

        // ... (rest of your onCreate) ...
        val toolbar: MaterialToolbar = findViewById(R.id.login_toolbar)
        setSupportActionBar(toolbar)

        etEmailOrMobile = findViewById(R.id.etLoginEmailOrMobile)
        etPassword = findViewById(R.id.etLoginPassword)
        tilEmailOrMobile = findViewById(R.id.tilLoginEmailOrMobile)
        tilPassword = findViewById(R.id.tilLoginPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)
        progressBar = findViewById(R.id.progressBarLogin)

        btnLogin.setOnClickListener {
            loginUser()
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // ... (validateInputs, setInputsEnabled remain the same) ...
    private fun validateInputs(): Boolean {
        tilEmailOrMobile.error = null
        tilPassword.error = null
        var isValid = true
        val emailOrMobile = etEmailOrMobile.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (emailOrMobile.isEmpty()) {
            tilEmailOrMobile.error = "Email cannot be empty"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(emailOrMobile).matches()) {
            tilEmailOrMobile.error = "Enter a valid email address"
            isValid = false
        }

        if (password.isEmpty()) {
            tilPassword.error = "Password cannot be empty"
            isValid = false
        }
        return isValid
    }

    private fun setInputsEnabled(enabled: Boolean) {
        etEmailOrMobile.isEnabled = enabled
        etPassword.isEnabled = enabled
        btnLogin.isEnabled = enabled
        tvGoToRegister.isEnabled = enabled
        progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
        btnLogin.text = if (enabled) "Login" else ""
    }


    private fun loginUser() {
        if (!validateInputs()) {
            return
        }
        setInputsEnabled(false)

        val email = etEmailOrMobile.text.toString().trim()
        val password = etPassword.text.toString().trim()

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    val user = firebaseAuth.currentUser
                    user?.let { firebaseUser ->
                        sharedPreferences.edit().putString(KEY_LOGGED_IN_UID, firebaseUser.uid).apply()
                        // Pass UID and attempt to pass displayName. TeacherOptionsActivity will verify/fetch.
                        navigateToHome(firebaseUser.uid, firebaseUser.displayName)
                    } ?: run {
                        setInputsEnabled(true)
                        Toast.makeText(baseContext, "Login failed: User data unavailable.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    setInputsEnabled(true)
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    var errorMessage = "Authentication failed."
                    try {
                        throw task.exception!!
                    } catch (e: FirebaseAuthInvalidUserException) {
                        errorMessage = "No account found with this email."
                        tilEmailOrMobile.error = errorMessage
                    } catch (e: FirebaseAuthInvalidCredentialsException) {
                        errorMessage = "Incorrect password."
                        tilPassword.error = errorMessage
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "An unknown error occurred."
                    }
                    Toast.makeText(baseContext, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    // Modified navigateToHome to always try to pass ID and Name
    private fun navigateToHome(teacherId: String?, teacherName: String?) {
        val intent = Intent(this, TeacherOptionsActivity::class.java).apply {
            // TeacherOptionsActivity will primarily rely on FirebaseAuth.currentUser
            // but passing these can speed up initial display if available.
            // TeacherOptionsActivity's onCreate has logic to fetch if these are null.
            if (teacherId != null) putExtra("TEACHER_ID", teacherId)
            if (teacherName != null) putExtra("TEACHER_NAME", teacherName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}