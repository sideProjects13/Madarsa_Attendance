package com.example.madarsa_attendance

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

object FirebaseAuthManager {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_ORG_ID = "organization_id"
    private const val KEY_ORG_NAME = "organization_name"
    private const val KEY_ROLE = "user_role"
    private const val KEY_ORG_LOGO_URL = "organization_logo_url"
    private const val KEY_ORG_ADDRESS = "organization_address"

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
            // --- THE FIX ---
            // commit() is synchronous and guarantees the data is saved before the code continues.
            commit()
        }
    }

    fun logout(context: Context) {
        auth.signOut()
        // It's good practice to use commit() here as well for consistency.
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