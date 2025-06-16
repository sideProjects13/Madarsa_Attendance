// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript { // This block is for older plugin application style, but your google-services is here
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.2") // Or latest stable 4.x.x version
    }
}

plugins {
    id("com.android.application") version "8.6.0" apply false // <<< UPDATED AGP VERSION (try 8.4.0 or 8.5.0)
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false // <<< UPDATED KOTLIN (match Compose needs, 1.9.0 or 1.9.22 are good)
    // id("com.google.gms.google-services") version "4.4.2" apply false // Not needed here if in buildscript
}