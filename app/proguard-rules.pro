# =========================================================================
# THIS IS THE FINAL, CORRECTED FILE.
# COPY THIS ENTIRE CONTENT INTO app/proguard-rules.pro
# =========================================================================

# General rule for keeping annotations, which some libraries rely on.
-keepattributes *Annotation*

# --- Your Project's Data/Model Classes ---
# THIS IS THE MOST IMPORTANT PART FOR YOUR APP TO WORK.
# It protects all data classes in your 'com.example.madarsa_attendance' package
# from being removed or renamed by R8, which is essential for Firestore.
-keep class com.example.madarsa_attendance.** { *; }
-keepclassmembers class com.example.madarsa_attendance.** { *; }

# --- Firebase ---
# These rules prevent crashes when using Firebase Auth and Firestore.
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.firestore.** { *; }

# --- Glide (for image loading) ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$ImageType {
  **[] $VALUES;
  public *;
}

# --- OkHttp (a dependency for Cloudinary, Glide, etc.) ---
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# --- MPAndroidChart (for charts) ---
-keep class com.github.mikephil.charting.** { *; }

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.HandlerContext {}
-keepclassmembers class kotlinx.coroutines.flow.** {
    volatile <fields>;
}

# --- Facebook Shimmer (for loading effects) ---
-keep class com.facebook.shimmer.** { *; }

# The default rules for Android views, etc., are in `proguard-android-optimize.txt`
# which you already include in your build.gradle file. No need to add them here.