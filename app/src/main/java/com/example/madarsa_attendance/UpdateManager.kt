package com.example.madarsa_attendance

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.BuildConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

// A simple data class to hold the update information
data class UpdateInfo(
    val versionName: String,
    val updateNotes: String,
    val updateUrl: String
)

object UpdateManager {

    private const val TAG = "UpdateManager"

    // Initializes and fetches the latest configuration from Firebase
    fun checkForUpdate(
        context: Context,
        onUpdateAvailable: (UpdateInfo) -> Unit,
        onNoUpdate: () -> Unit = {}
    ) {
        val remoteConfig = Firebase.remoteConfig

        // Set developer mode for rapid testing during development
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set default values in case the app can't fetch from the server
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Config params updated: $updated")

                    // Get the latest values from Remote Config
                    val latestVersionCode = remoteConfig.getLong("latest_version_code")
                    val latestVersionName = remoteConfig.getString("latest_version_name")
                    val updateUrl = remoteConfig.getString("update_url")
                    val updateNotes = remoteConfig.getString("update_notes")

                    // Get the app's current version code
                    val currentVersionCode = getCurrentVersionCode(context)

                    Log.d(TAG, "Current Version: $currentVersionCode, Latest Version: $latestVersionCode")

                    // Compare versions
                    if (latestVersionCode > currentVersionCode) {
                        // If an update is available, trigger the callback
                        val updateInfo = UpdateInfo(latestVersionName, updateNotes, updateUrl)
                        onUpdateAvailable(updateInfo)
                    } else {
                        onNoUpdate()
                    }
                } else {
                    Log.w(TAG, "Remote Config fetch failed.")
                    onNoUpdate()
                }
            }
    }

    // Helper function to get the current app version code
    @Suppress("DEPRECATION")
    private fun getCurrentVersionCode(context: Context): Long {
        return try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            -1L // Return an error code if something goes wrong
        }
    }
}