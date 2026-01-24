package com.fyp.blockchainhealthwallet

import java.math.BigInteger

data class Medication(
    val id: BigInteger? = null,
    val name: String,
    val dosage: String,
    val frequency: String,
    val route: String = "",
    val isActive: Boolean,
    val startDate: Long,  // timestamp in milliseconds
    val endDate: Long? = null,  // optional, null means ongoing
    val purpose: String = "",
    val prescribingDoctor: String = "",
    val pharmacy: String = "",
    val notes: String = "",
    val createdAt: Long? = null
)
