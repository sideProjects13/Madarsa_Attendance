package com.example.madarsa_attendance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LogoProvider {

    private const val TAG = "LogoProvider"
    private var cachedLogo: Bitmap? = null
    private var cachedLogoUrl: String? = null

    /**
     * Fetches the currently active logo for the organization.
     * It uses an in-memory cache to avoid re-downloading the logo repeatedly.
     * The cache is invalidated if the active logo URL changes.
     */
    suspend fun getActiveLogo(context: Context): Bitmap {
        val activeLogoUrl = FirebaseAuthManager.getOrganizationLogoUrl(context)

        // If the URL is the same and we have a cached logo, return it.
        if (activeLogoUrl == cachedLogoUrl && cachedLogo != null) {
            Log.d(TAG, "Returning cached logo.")
            return cachedLogo!!
        }

        // Update the cached URL
        cachedLogoUrl = activeLogoUrl

        return withContext(Dispatchers.IO) {
            try {
                if (activeLogoUrl.isNullOrEmpty()) {
                    Log.w(TAG, "Active logo URL is empty. Falling back to default.")
                    return@withContext getDefaultLogo(context)
                }

                // Use Glide to download the image from the URL
                Log.d(TAG, "Downloading new logo from URL: $activeLogoUrl")
                val downloadedBitmap = Glide.with(context)
                    .asBitmap()
                    .load(activeLogoUrl)
                    .submit()
                    .get()

                // Cache the newly downloaded logo
                cachedLogo = downloadedBitmap
                downloadedBitmap

            } catch (e: Exception) {
                Log.e(TAG, "Failed to download or process active logo. Falling back to default.", e)
                getDefaultLogo(context)
            }
        }
    }

    private fun getDefaultLogo(context: Context): Bitmap {
        return BitmapFactory.decodeResource(context.resources, R.drawable.logo)
    }

    /**
     * Clears the in-memory cache. Call this on logout, login, or after updating the profile.
     */
    fun clearCache() {
        cachedLogo = null
        cachedLogoUrl = null
        Log.d(TAG, "Logo cache cleared.")
    }
}