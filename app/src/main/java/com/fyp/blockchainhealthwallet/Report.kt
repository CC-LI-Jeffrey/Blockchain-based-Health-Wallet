package com.fyp.blockchainhealthwallet

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Report(
    val id: String,
    val title: String,
    val reportType: ReportType,
    val date: String,
    val doctorName: String,
    val hospital: String,
    val description: String,
    val filePath: String? = null,
    val ipfsHash: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

enum class ReportType(val displayName: String) {
    LAB_RESULT("Lab Result"),
    DOCTOR_NOTE("Doctor's Note"),
    PRESCRIPTION("Prescription"),
    IMAGING("Imaging/Scan"),
    PATHOLOGY("Pathology Report"),
    CONSULTATION("Consultation Note"),
    DISCHARGE_SUMMARY("Discharge Summary"),
    OTHER("Other")
}
