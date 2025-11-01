package com.fyp.blockchainhealthwallet.model

data class VaccinationRecord(
    val id: String,
    val date: String,
    val vaccineName: String,
    val vaccineNameEn: String,
    val vaccineFullName: String,
    val manufacturer: String,
    val country: String,
    val provider: String,
    val location: String,
    val batchNumber: String,
    val pdfUrl: String? = null
)
