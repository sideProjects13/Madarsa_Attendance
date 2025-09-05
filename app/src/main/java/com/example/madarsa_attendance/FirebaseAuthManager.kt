package com.example.madarsa_attendance

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FirebaseAuthManager {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_ORG_ID = "organization_id"
    private const val KEY_ORG_NAME = "organization_name"
    private const val KEY_ROLE = "user_role"
    private const val KEY_ORG_LOGO_URL = "organization_logo_url"
    private const val KEY_ORG_ADDRESS = "organization_address"
    private const val TAG = "FirebaseAuthManager" // Added for logging

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    fun isLoggedInAndOrgSelected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return auth.currentUser != null && prefs.contains(KEY_ORG_ID)
    }

    fun saveLoginSession(
        context: Context,
        role: String,
        orgId: String,
        orgName: String,
        activeLogoUrl: String?,
        address: String?
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString(KEY_ROLE, role)
            putString(KEY_ORG_ID, orgId)
            putString(KEY_ORG_NAME, orgName)
            putString(KEY_ORG_LOGO_URL, activeLogoUrl)
            putString(KEY_ORG_ADDRESS, address)
            commit()
        }

        // --- NEW: After saving the session, update the FCM token in Firestore ---
        // This is still useful for other potential notifications, even if not for this feature.
        updateFCMToken()
        // --- END OF NEW LOGIC ---
    }

    // --- NEW FUNCTION: Gets the latest FCM token and saves it to the user's document ---
    private fun updateFCMToken() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot update FCM token, user is not logged in.")
            return
        }

        // Use a coroutine to get the token asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // This call gets the unique registration token for this app instance.
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d(TAG, "FCM Token retrieved: $token")

                // We need the user's document to save the token.
                // It's assumed to be in the top-level 'users' collection with the user's UID as the document ID.
                val userDocRef = FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)

                // Update the 'fcmToken' field in the user's document.
                userDocRef.update("fcmToken", token).await()

                Log.d(TAG, "FCM Token successfully updated in Firestore for user ${currentUser.uid}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating FCM token in Firestore", e)
            }
        }
    }
    // --- END OF NEW FUNCTION ---


    fun logout(context: Context) {
        // --- NEW: Before logging out, clear the FCM token from their record ---
        // This prevents sending notifications to a logged-out device.
        auth.currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("fcmToken", FieldValue.delete()) // Deletes the field
                .addOnSuccessListener { Log.d(TAG, "FCM Token cleared on logout.") }
                .addOnFailureListener { e -> Log.w(TAG, "Failed to clear FCM token on logout.", e) }
        }
        // --- END OF NEW LOGIC ---

        auth.signOut()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }

    fun getOrganizationId(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ORG_ID, null)

    fun getOrganizationName(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORG_NAME, "My Madarsa")

    fun getOrganizationLogoUrl(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORG_LOGO_URL, null)

    fun getOrganizationAddress(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORG_ADDRESS, "Address not set")

    fun getUserRole(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ROLE, null)
}