package com.example.madarsa_attendance.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.FieldValue

data class Organization(
    val organizationName: String? = null,
    val adminEmail: String? = null,
    val adminName: String? = null,
    val adminMobile: String? = null,

    @get:Exclude var createdAt: FieldValue? = null,

    val address: String? = null,
    val logoUrl: String? = null, // Only one logo URL is needed now
    val highAbsenceThreshold: Int? = null

)