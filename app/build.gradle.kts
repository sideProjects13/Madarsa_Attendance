plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services") // Apply the google-services plugin
}

android {
    namespace = "com.example.madarsa_attendance"
    compileSdk = 35 // Keep this as libraries require it

    defaultConfig {
        applicationId = "com.example.madarsa_attendance"
        minSdk = 29
        targetSdk = 35 // <<< UPDATED to match compileSdk or requirements
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true // You have this enabled
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10" // <<< OFTEN NEEDS TO MATCH KOTLIN VERSION (e.g., for Kotlin 1.9.22, this is usually 1.5.10 or 1.5.11)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0") // Or latest stable (e.g., 1.13.x)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3") // Updated to a recent stable version
    implementation("androidx.activity:activity-compose:1.9.0") // Updated to a recent stable version
    implementation(platform("androidx.compose:compose-bom:2024.06.00")) // <<< USE LATEST COMPOSE BOM
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3") // This will pull in an appropriate version from the BOM

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.1")) // <<< ADDED/UPDATED FIREBASE BOM (use latest)
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.firebase:firebase-firestore-ktx") // Version managed by BoM
    implementation("com.google.firebase:firebase-analytics-ktx") // Also managed by BoM (if you added it before)


    implementation("androidx.appcompat:appcompat:1.7.0") // Or latest stable (e.g., 1.6.1 is very common)
    implementation("com.google.android.material:material:1.12.0") // This version should support Material 3 dialog themes
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Updated to common stable
    implementation("androidx.core:core-splashscreen:1.0.1") // This is fine
    implementation("androidx.activity:activity-ktx:1.8.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Check latest version
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2") //
    implementation("com.cloudinary:cloudinary-android:2.4.0") // Check for the latest version
    // You'll also need an HTTP client, OkHttp is common:
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.activity:activity:1.10.1") // Or your

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5") // Updated
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1") // Updated
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00")) // Match BOM
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest") // Check latest version
}