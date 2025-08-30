// src/main/java/com/example/madarsa_attendance/models/AppUser.kt
package com.example.madarsa_attendance.models // Note the 'models' subpackage

data class AppUser(
    val organizationId: String? = null,
    val role: String? = null,
    val email: String? = null,
    val name: String? = null,
    val mobile: String? = null,
    val organizationName: String? = null // <-- ADD THIS LINE

)