package com.fyp.blockchainhealthwallet.model

data class AccessLog(
    val id: String,
    val accessorName: String,
    val accessorType: String, // e.g., "Hospital", "Clinic", "Insurance Company", "Doctor"
    val accessedData: String, // e.g., "Vaccination Records", "Medical History", "Lab Results"
    val accessTime: String,
    val accessDate: String,
    val location: String,
    val purpose: String,
    val status: String, // e.g., "Approved", "Denied", "Pending"
    val ipAddress: String? = null
)
