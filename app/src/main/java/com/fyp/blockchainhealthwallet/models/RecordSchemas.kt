package com.fyp.blockchainhealthwallet.models

/**
 * Record Type Schemas
 * Defines the ordered list of attributes for each record type
 * Order MUST remain consistent with backend for Merkle tree construction
 */
object RecordSchemas {
    
    enum class RecordType {
        PERSONAL_INFO,
        MEDICATION,
        VACCINATION,
        MEDICAL_REPORT
    }
    
    /**
     * Get schema for a record type
     */
    fun getSchema(recordType: RecordType): List<String> {
        return when (recordType) {
            RecordType.PERSONAL_INFO -> PERSONAL_INFO_SCHEMA
            RecordType.MEDICATION -> MEDICATION_SCHEMA
            RecordType.VACCINATION -> VACCINATION_SCHEMA
            RecordType.MEDICAL_REPORT -> MEDICAL_REPORT_SCHEMA
        }
    }
    
    /**
     * Get display name for attribute
     */
    fun getDisplayName(attributeName: String): String {
        return ATTRIBUTE_DISPLAY_NAMES[attributeName] ?: attributeName
            .split(Regex("(?=[A-Z])"))
            .joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
    }
    
    // ============================================
    // SCHEMAS (ORDER MUST MATCH BACKEND)
    // ============================================
    
    val PERSONAL_INFO_SCHEMA = listOf(
        "fullName",
        "dateOfBirth",
        "gender",
        "bloodType",
        "address",
        "phoneNumber",
        "email",
        "emergencyContact",
        "emergencyPhone",
        "allergies",
        "chronicConditions"
    )
    
    val MEDICATION_SCHEMA = listOf(
        "medicineName",
        "dosage",
        "prescribedBy",
        "startDate",
        "endDate",
        "frequency",
        "purpose",
        "sideEffects",
        "pharmacy",
        "prescriptionNumber",
        "refillsRemaining",
        "cost",
        "insurance",
        "notes",
        "doctorPhone"
    )
    
    val VACCINATION_SCHEMA = listOf(
        "vaccineName",
        "manufacturer",
        "lotNumber",
        "doseNumber",
        "totalDoses",
        "vaccinationDate",
        "administeredBy",
        "facilityName",
        "facilityAddress",
        "nextDoseDate",
        "reactions",
        "certificateNumber",
        "notes",
        "boosterRequired"
    )
    
    val MEDICAL_REPORT_SCHEMA = listOf(
        "reportTitle",
        "reportType",
        "reportDate",
        "facilityName",
        "doctorName",
        "doctorSpecialty",
        "chiefComplaint",
        "diagnosis",
        "treatmentPlan",
        "medications",
        "labResults",
        "imagingResults",
        "vitalSigns",
        "followUpDate",
        "referrals",
        "notes",
        "billingCode"
    )
    
    // ============================================
    // DISPLAY NAMES
    // ============================================
    
    val ATTRIBUTE_DISPLAY_NAMES = mapOf(
        // Personal Info
        "fullName" to "Full Name",
        "dateOfBirth" to "Date of Birth",
        "gender" to "Gender",
        "bloodType" to "Blood Type",
        "address" to "Address",
        "phoneNumber" to "Phone Number",
        "email" to "Email",
        "emergencyContact" to "Emergency Contact",
        "emergencyPhone" to "Emergency Phone",
        "allergies" to "Allergies",
        "chronicConditions" to "Chronic Conditions",
        
        // Medication
        "medicineName" to "Medicine Name",
        "dosage" to "Dosage",
        "prescribedBy" to "Prescribed By",
        "startDate" to "Start Date",
        "endDate" to "End Date",
        "frequency" to "Frequency",
        "purpose" to "Purpose",
        "sideEffects" to "Side Effects",
        "pharmacy" to "Pharmacy",
        "prescriptionNumber" to "Prescription Number",
        "refillsRemaining" to "Refills Remaining",
        "cost" to "Cost",
        "insurance" to "Insurance",
        "notes" to "Notes",
        "doctorPhone" to "Doctor Phone",
        
        // Vaccination
        "vaccineName" to "Vaccine Name",
        "manufacturer" to "Manufacturer",
        "lotNumber" to "Lot Number",
        "doseNumber" to "Dose Number",
        "totalDoses" to "Total Doses",
        "vaccinationDate" to "Vaccination Date",
        "administeredBy" to "Administered By",
        "facilityName" to "Facility Name",
        "facilityAddress" to "Facility Address",
        "nextDoseDate" to "Next Dose Date",
        "reactions" to "Reactions",
        "certificateNumber" to "Certificate Number",
        "boosterRequired" to "Booster Required",
        
        // Medical Report
        "reportTitle" to "Report Title",
        "reportType" to "Report Type",
        "reportDate" to "Report Date",
        "doctorName" to "Doctor Name",
        "doctorSpecialty" to "Doctor Specialty",
        "chiefComplaint" to "Chief Complaint",
        "diagnosis" to "Diagnosis",
        "treatmentPlan" to "Treatment Plan",
        "medications" to "Medications",
        "labResults" to "Lab Results",
        "imagingResults" to "Imaging Results",
        "vitalSigns" to "Vital Signs",
        "followUpDate" to "Follow Up Date",
        "referrals" to "Referrals",
        "billingCode" to "Billing Code"
    )
}
