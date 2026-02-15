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
        "firstName",
        "lastName",
        "email",
        "hkid",
        "dateOfBirth",
        "gender",
        "bloodType",
        "phone",
        "address",
        "emergencyContactName",
        "emergencyContactRelationship",
        "emergencyContactPhone"
    )
    
    val MEDICATION_SCHEMA = listOf(
        "medicineName",
        "dosage",
        "frequency",
        "route",
        "startDate",
        "endDate",
        "purpose",
        "prescribedBy",
        "pharmacy",
        "notes"
    )
    
    val VACCINATION_SCHEMA = listOf(
        "date",
        "vaccineName",
        "vaccineNameEn",
        "vaccineFullName",
        "manufacturer",
        "country",
        "provider",
        "location",
        "batchNumber"
    )
    
    val MEDICAL_REPORT_SCHEMA = listOf(
        "title",
        "reportType",
        "reportTypeDisplay",
        "date",
        "doctorName",
        "hospital",
        "description"
    )
    
    // ============================================
    // DISPLAY NAMES
    // ============================================
    
    val ATTRIBUTE_DISPLAY_NAMES = mapOf(
        // Personal Info
        "firstName" to "First Name",
        "lastName" to "Last Name",
        "email" to "Email",
        "hkid" to "HKID Number",
        "dateOfBirth" to "Date of Birth",
        "gender" to "Gender",
        "bloodType" to "Blood Type",
        "phone" to "Phone",
        "address" to "Address",
        "emergencyContactName" to "Emergency Contact Name",
        "emergencyContactRelationship" to "Relationship",
        "emergencyContactPhone" to "Emergency Contact Phone",
        
        // Medication
        "medicineName" to "Medicine Name",
        "dosage" to "Dosage",
        "frequency" to "Frequency",
        "route" to "Route",
        "startDate" to "Start Date",
        "endDate" to "End Date",
        "purpose" to "Purpose",
        "prescribedBy" to "Prescribed By",
        "pharmacy" to "Pharmacy",
        "notes" to "Notes",
        
        // Vaccination
        "date" to "Date",
        "vaccineName" to "Vaccine Name",
        "vaccineNameEn" to "Vaccine Name (EN)",
        "vaccineFullName" to "Vaccine Full Name",
        "manufacturer" to "Manufacturer",
        "country" to "Country",
        "provider" to "Provider",
        "location" to "Location",
        "batchNumber" to "Batch Number",
        
        // Medical Report
        "title" to "Title",
        "reportType" to "Report Type",
        "reportTypeDisplay" to "Report Type Display",
        "date" to "Date",
        "doctorName" to "Doctor Name",
        "hospital" to "Hospital",
        "description" to "Description"
    )
}
