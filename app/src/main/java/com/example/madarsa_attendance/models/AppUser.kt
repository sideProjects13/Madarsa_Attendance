// src/main/java/com/example/madarsa_attendance/models/AppUser.kt
package com.example.madarsa_attendance.models // Note the 'models' subpackage
import com.google.firebase.firestore.DocumentId

data class AppUser(
    @DocumentId val uid: String = "",
    val organizationId: String? = null,
    val role: String? = null,
    val email: String? = null,
    val name: String? = null,
    val mobile: String? = null,
    val organizationName: String? = null, // <-- ADD THIS LINE
    var accountStatus: String = "pending" // "pending", "active", "inactive"


)