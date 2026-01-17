package com.fyp.blockchainhealthwallet.blockchain

import android.util.Log
import com.fyp.blockchainhealthwallet.model.VaccinationRecord

/**
 * Usage examples showing how to use FlexibleMerkleManager with different data models
 */
object FlexibleMerkleUsageExamples {
    private const val TAG = "MerkleUsageExamples"
    
    /**
     * Example 1: Sharing a VaccinationRecord
     */
    fun exampleVaccinationRecordShare() {
        Log.d(TAG, "=== Example 1: Share Vaccination Record ===\n")
        
        // Create a vaccination record
        val vaccination = VaccinationRecord(
            id = "vax_001",
            date = "2025-12-20",
            vaccineName = "COVID-19",
            vaccineNameEn = "COVID-19",
            vaccineFullName = "Pfizer-BioNTech COVID-19",
            manufacturer = "Pfizer",
            country = "USA",
            provider = "Hospital A",
            location = "Downtown",
            batchNumber = "BATCH123"
        )
        
        Log.d(TAG, "Step 1: Create Merkle record from VaccinationRecord")
        val merkleRecord = FlexibleMerkleManager.createMerkleRecord(
            data = vaccination,
            recordType = "VaccinationRecord"
        )
        
        Log.d(TAG, "\nAvailable attributes to share:")
        FlexibleMerkleManager.getAvailableAttributes(vaccination).forEach {
            Log.d(TAG, "  - $it")
        }
        
        Log.d(TAG, "\nStep 2: User wants to share only vaccine name")
        val share = FlexibleMerkleManager.createPartialShare(
            record = merkleRecord,
            attributeToShare = "vaccineName",
            recipientAddress = "0xDoctor123"
        )
        
        Log.d(TAG, "\nShare preview:")
        Log.d(TAG, PartialShareManager.getSharePreview(share))
        
        Log.d(TAG, "\nStep 3: Send to blockchain")
        Log.d(TAG, "  - Store root: ${merkleRecord.merkleRoot.take(16)}...")
        Log.d(TAG, "  - Record type: ${share.recordType}")
        Log.d(TAG, "  - Shared attribute: ${share.sharedAttributeName}")
    }
    
    /**
     * Example 2: Multiple records of different types
     */
    fun exampleMultipleRecordTypes() {
        Log.d(TAG, "=== Example 2: Multiple Record Types ===\n")
        
        // Different record types
        val vaccination = VaccinationRecord(
            id = "vax_001",
            date = "2025-12-20",
            vaccineName = "COVID-19",
            vaccineNameEn = "COVID-19",
            vaccineFullName = "Pfizer-BioNTech",
            manufacturer = "Pfizer",
            country = "USA",
            provider = "Hospital",
            location = "Downtown",
            batchNumber = "BATCH123"
        )
        
        // Another record type
        val anotherVaccination = VaccinationRecord(
            id = "vax_002",
            date = "2025-06-15",
            vaccineName = "Flu Shot",
            vaccineNameEn = "Flu Shot",
            vaccineFullName = "Seasonal Influenza",
            manufacturer = "Sanofi",
            country = "France",
            provider = "Clinic B",
            location = "Uptown",
            batchNumber = "BATCH456"
        )
        
        Log.d(TAG, "Step 1: Create Merkle records for different types")
        
        val merkle1 = FlexibleMerkleManager.createMerkleRecord(
            data = vaccination,
            recordType = "VaccinationRecord",
            recordId = "vax_001"
        )
        Log.d(TAG, "  ✓ VaccinationRecord 1: ${merkle1.merkleRoot.take(12)}...")
        
        val merkle2 = FlexibleMerkleManager.createMerkleRecord(
            data = anotherVaccination,
            recordType = "VaccinationRecord",
            recordId = "vax_002"
        )
        Log.d(TAG, "  ✓ VaccinationRecord 2: ${merkle2.merkleRoot.take(12)}...")
        
        Log.d(TAG, "\nStep 2: Create partial shares")
        val share1 = FlexibleMerkleManager.createPartialShare(
            record = merkle1,
            attributeToShare = "vaccineName",
            recipientAddress = "0xInsurance"
        )
        
        val share2 = FlexibleMerkleManager.createPartialShare(
            record = merkle2,
            attributeToShare = "manufacturer",
            recipientAddress = "0xResearch"
        )
        
        Log.d(TAG, "  Share 1: Sharing '${share1.sharedAttributeName}' from ${share1.recordType}")
        Log.d(TAG, "  Share 2: Sharing '${share2.sharedAttributeName}' from ${share2.recordType}")
    }
    
    /**
     * Example 3: Verification workflow
     */
    fun exampleVerificationWorkflow() {
        Log.d(TAG, "=== Example 3: Verification Workflow ===\n")
        
        // Create original record
        val vaccination = VaccinationRecord(
            id = "vax_001",
            date = "2025-12-20",
            vaccineName = "COVID-19",
            vaccineNameEn = "COVID-19",
            vaccineFullName = "Pfizer",
            manufacturer = "Pfizer",
            country = "USA",
            provider = "Hospital",
            location = "Downtown",
            batchNumber = "BATCH123"
        )
        
        Log.d(TAG, "Step 1: Sender creates and shares")
        val merkleRecord = FlexibleMerkleManager.createMerkleRecord(
            data = vaccination,
            recordType = "VaccinationRecord"
        )
        val senderRoot = merkleRecord.merkleRoot
        Log.d(TAG, "  Sender's root (to be stored on blockchain): $senderRoot")
        
        val share = FlexibleMerkleManager.createPartialShare(
            record = merkleRecord,
            attributeToShare = "vaccineName",
            recipientAddress = "0xRecipient"
        )
        Log.d(TAG, "  Share data sent to recipient")
        
        Log.d(TAG, "\nStep 2: Recipient receives and verifies")
        Log.d(TAG, "  Recipient fetches root from blockchain: $senderRoot")
        
        val isValid = PartialShareManager.verifyReceivedShare(
            shareData = share,
            blockchainMerkleRoot = senderRoot
        )
        
        if (isValid) {
            Log.d(TAG, "  ✅ VERIFIED! Vaccine '${share.sharedAttributeValue}' is authentic")
            Log.d(TAG, "  This came from a real ${share.recordType}")
        } else {
            Log.d(TAG, "  ❌ INVALID! This share has been tampered with")
        }
    }
    
    /**
     * Example 4: Using in Activities
     */
    fun exampleActivityIntegration() {
        Log.d(TAG, "=== Example 4: Activity Integration ===\n")
        
        Log.d(TAG, """
            // In ShareRecordActivity.kt
            
            fun shareRecord(record: Any, recordType: String, recipientAddress: String) {
                try {
                    // 1. Create Merkle record
                    val merkleRecord = FlexibleMerkleManager.createMerkleRecord(
                        data = record,
                        recordType = recordType
                    )
                    
                    // 2. Show available attributes to user
                    val attributes = FlexibleMerkleManager.getAvailableAttributes(record)
                    showAttributeSelectionDialog(attributes) { selected ->
                        
                        // 3. Create partial share
                        val share = FlexibleMerkleManager.createPartialShare(
                            record = merkleRecord,
                            attributeToShare = selected,
                            recipientAddress = recipientAddress
                        )
                        
                        // 4. Store root on blockchain
                        blockchainService.storeRecordRoot(
                            recordId = merkleRecord.recordId,
                            merkleRoot = merkleRecord.merkleRoot,
                            recordType = merkleRecord.recordType
                        )
                        
                        // 5. Send share to recipient
                        apiService.sendPartialShare(share)
                    }
                } catch (e: Exception) {
                    showError(e.message)
                }
            }
        """.trimIndent())
    }
}
