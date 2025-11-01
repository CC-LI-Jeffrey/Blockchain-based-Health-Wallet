package com.fyp.blockchainhealthwallet.model

data class ShareRecord(
    val id: String,
    val recipientName: String,
    val recipientType: String, // e.g., "Doctor", "Hospital", "Clinic", "Insurance Company"
    val sharedData: String, // e.g., "Vaccination Records", "Medical History"
    val shareDate: String,
    val shareTime: String,
    val expiryDate: String,
    val accessLevel: String, // e.g., "View Only", "Full Access"
    val status: String, // e.g., "Active", "Expired", "Revoked"
    val recipientEmail: String? = null
)
