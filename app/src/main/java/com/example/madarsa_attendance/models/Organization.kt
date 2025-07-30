// src/main/java/com/example/madarsa_attendance/models/Organization.kt
package com.example.madarsa_attendance.models // Note the 'models' subpackage

import com.google.firebase.firestore.FieldValue

data class Organization(
    val organizationId: String? = null,
    val organizationName: String? = null,
    val adminEmail: String? = null,
    val adminName: String? = null,
    val adminMobile: String? = null,
    val createdAt: FieldValue? = null
)