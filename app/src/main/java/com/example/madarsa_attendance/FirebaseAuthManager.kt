    // src/main/java/com/example/madarsa_attendance/FirebaseAuthManager.kt
    package com.example.madarsa_attendance

    import android.content.Context
    import com.google.firebase.auth.FirebaseAuth

    object FirebaseAuthManager {

        private const val PREFS_FILE = "app_prefs"
        private const val KEY_ORGANIZATION_ID = "organization_id"
        private const val KEY_ORGANIZATION_NAME = "organization_name"

        private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

        /**
         * Saves the current organization ID to SharedPreferences.
         */
        fun saveOrganizationId(context: Context, organizationId: String) {
            val sharedPref = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString(KEY_ORGANIZATION_ID, organizationId)
                apply()
            }
        }

        /**
         * Retrieves the current organization ID from SharedPreferences.
         */
        fun getOrganizationId(context: Context): String? {
            val sharedPref = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            return sharedPref.getString(KEY_ORGANIZATION_ID, null)
        }

        /**
         * Saves the current organization name to SharedPreferences.
         */
        fun saveOrganizationName(context: Context, organizationName: String) {
            val sharedPref = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString(KEY_ORGANIZATION_NAME, organizationName)
                apply()
            }
        }

        /**
         * Retrieves the current organization name from SharedPreferences.
         */
        fun getOrganizationName(context: Context): String? {
            val sharedPref = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            return sharedPref.getString(KEY_ORGANIZATION_NAME, null)
        }

        /**
         * Clears stored organization data and signs out the Firebase user.
         */
        fun logout(context: Context) {
            val sharedPref = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                remove(KEY_ORGANIZATION_ID)
                remove(KEY_ORGANIZATION_NAME)
                apply()
            }
            auth.signOut()
        }

        /**
         * Checks if a user is currently logged in AND an organization ID is stored.
         */
        fun isLoggedInAndOrgSelected(context: Context): Boolean {
            return auth.currentUser != null && getOrganizationId(context) != null
        }

        /**
         * Returns the current Firebase Auth user ID.
         */
        fun getCurrentUserId(): String? {
            return auth.currentUser?.uid
        }
    }