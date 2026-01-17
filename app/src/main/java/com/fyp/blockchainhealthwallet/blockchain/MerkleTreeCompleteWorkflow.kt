package com.fyp.blockchainhealthwallet.blockchain

import android.content.Context
import android.util.Log
import com.fyp.blockchainhealthwallet.model.VaccinationRecord

/**
 * Complete workflow guide showing how all Merkle classes work together
 */
object MerkleTreeCompleteWorkflow {
    private const val TAG = "MerkleWorkflow"
    
    /**
     * COMPLETE WORKFLOW: Create, Store, Share, Verify
     */
    fun completeExample(context: Context) {
        Log.d(TAG, "=== COMPLETE MERKLE TREE WORKFLOW ===\n")
        
        // Initialize managers
        val storageManager = MerkleStorageManager(context)
        val userAddress = "0xUser123"
        
        // ============================================================
        // STEP 1: CREATE HEALTH RECORD WITH MERKLE TREE
        // ============================================================
        Log.d(TAG, "STEP 1: Create health record with Merkle tree")
        
        val vaccination = VaccinationRecord(
            id = "vax_001",
            date = "2025-12-20",
            vaccineName = "COVID-19",
            vaccineNameEn = "COVID-19",
            vaccineFullName = "Pfizer-BioNTech",
            manufacturer = "Pfizer",
            country = "USA",
            provider = "Hospital A",
            location = "Downtown",
            batchNumber = "BATCH123"
        )
        
        val merkleRecord = FlexibleMerkleManager.createMerkleRecord(
            data = vaccination,
            recordType = "VaccinationRecord",
            recordId = "vax_001"
        )
        
        Log.d(TAG, "✓ Record created with:")
        Log.d(TAG, "  Root: ${merkleRecord.merkleRoot.take(16)}...")
        Log.d(TAG, "  Attributes: ${merkleRecord.attributes.size}")
        Log.d(TAG, "")
        
        // ============================================================
        // STEP 2: STORE LOCALLY
        // ============================================================
        Log.d(TAG, "STEP 2: Store Merkle root locally")
        
        storageManager.saveMerkleRecord(
            recordId = merkleRecord.recordId,
            recordType = merkleRecord.recordType,
            ownerAddress = userAddress,
            merkleRoot = merkleRecord.merkleRoot,
            ipfsHash = "QmXxxx..."  // After uploading to IPFS
        )
        
        Log.d(TAG, "✓ Stored in local SharedPreferences")
        Log.d(TAG, "")
        
        // ============================================================
        // STEP 3: UPLOAD TO BLOCKCHAIN (async in real app)
        // ============================================================
        Log.d(TAG, "STEP 3: Upload root to blockchain")
        Log.d(TAG, "✓ Blockchain tx: 0x123456...")
        
        storageManager.updateBlockchainTxHash("vax_001", "0x123456...")
        Log.d(TAG, "")
        
        // ============================================================
        // STEP 4: CREATE PARTIAL SHARE
        // ============================================================
        Log.d(TAG, "STEP 4: Create partial share")
        
        val recipientAddress = "0xDoctor456"
        val partialShare = FlexibleMerkleManager.createPartialShare(
            record = merkleRecord,
            attributeToShare = "vaccineName",
            recipientAddress = recipientAddress
        )
        
        Log.d(TAG, "Share created:")
        Log.d(TAG, PartialShareManager.getSharePreview(partialShare))
        Log.d(TAG, "")
        
        // ============================================================
        // STEP 5: SEND SHARE TO RECIPIENT
        // ============================================================
        Log.d(TAG, "STEP 5: Send share to recipient")
        
        val shareJson = PartialShareManager.serializeShare(partialShare)
        Log.d(TAG, "✓ Serialized and sent (${shareJson.length} bytes)")
        Log.d(TAG, "")
        
        // ============================================================
        // STEP 6: RECIPIENT RECEIVES AND VERIFIES
        // ============================================================
        Log.d(TAG, "STEP 6: Recipient receives and verifies")
        
        val receivedShare = PartialShareManager.deserializeShare(shareJson)
        
        // Get blockchain root (in real app, fetch from blockchain)
        val blockchainRoot = merkleRecord.merkleRoot
        
        val isValid = PartialShareManager.verifyReceivedShare(
            shareData = receivedShare,
            blockchainMerkleRoot = blockchainRoot
        )
        
        if (isValid) {
            Log.d(TAG, "✅ VERIFIED!")
            Log.d(TAG, "  Attribute: ${receivedShare.sharedAttributeName}")
            Log.d(TAG, "  Value: ${receivedShare.sharedAttributeValue}")
            Log.d(TAG, "  Proof steps: ${receivedShare.proof.siblings.size}")
        }
        Log.d(TAG, "")
        
        // ============================================================
        // STEP 7: RETRIEVE STORED RECORDS
        // ============================================================
        Log.d(TAG, "STEP 7: Retrieve stored records")
        
        // Get by ID
        val storedRecord = storageManager.getRecordById("vax_001")
        Log.d(TAG, "✓ Retrieved by ID: ${storedRecord?.recordType}")
        
        // Get by owner
        val userRecords = storageManager.getRecordsByOwner(userAddress)
        Log.d(TAG, "✓ User has ${userRecords.size} records")
        
        // Get by type
        val vaccinationRecords = storageManager.getRecordsByType("VaccinationRecord")
        Log.d(TAG, "✓ Found ${vaccinationRecords.size} vaccination records")
        Log.d(TAG, "")
        
        // ============================================================
        // STEP 8: VIEW STATISTICS
        // ============================================================
        Log.d(TAG, "STEP 8: Storage statistics")
        
        val stats = storageManager.getStatistics()
        Log.d(TAG, "Total records: ${stats["totalRecords"]}")
        Log.d(TAG, "Types: ${stats["types"]}")
        Log.d(TAG, "IPFS linked: ${stats["ipfsLinked"]}")
        Log.d(TAG, "Blockchain confirmed: ${stats["blockchainConfirmed"]}")
    }
    
    /**
     * Example: Query records for UI display
     */
    fun exampleUIDisplay(context: Context) {
        Log.d(TAG, "\n=== EXAMPLE: UI DISPLAY ===\n")
        
        val storageManager = MerkleStorageManager(context)
        val userAddress = "0xUser123"
        
        // Show user's health records in a list
        val records = storageManager.getAllRecords(userAddress)
        
        Log.d(TAG, "Display in RecyclerView:")
        records.forEach { record ->
            Log.d(TAG, buildString {
                append("Record ID: ${record.recordId}\n")
                append("  Type: ${record.recordType}\n")
                append("  Date: ${record.timestamp}\n")
                append("  Root: ${record.merkleRoot.take(12)}...\n")
                append("  Shareable: ${record.ipfsHash != null}\n")
            })
        }
    }
    
    /**
     * Example: Share record with filtering
     */
    fun exampleShareWithFilter(context: Context) {
        Log.d(TAG, "\n=== EXAMPLE: SHARE WITH FILTERING ===\n")
        
        val storageManager = MerkleStorageManager(context)
        
        // User wants to share only vaccination records
        val vaccinationRecords = storageManager.getRecordsByType("VaccinationRecord")
        
        Log.d(TAG, "Available vaccination records to share:")
        vaccinationRecords.forEach { record ->
            Log.d(TAG, "  - ${record.recordId}: ${record.merkleRoot.take(12)}...")
        }
        
        // User selects one to share
        if (vaccinationRecords.isNotEmpty()) {
            val selected = vaccinationRecords.first()
            Log.d(TAG, "User selected: ${selected.recordId}")
            Log.d(TAG, "Ready to share with different recipients")
        }
    }
    
    /**
     * Example: Offline verification
     */
    fun exampleOfflineVerification(context: Context) {
        Log.d(TAG, "\n=== EXAMPLE: OFFLINE VERIFICATION ===\n")
        
        val storageManager = MerkleStorageManager(context)
        
        // Recipient already has the share data
        val shareData = PartialShareManager.PartialShareData(
            recordId = "vax_001",
            recipientAddress = "0xRecipient",
            sharedAttributeName = "vaccineName",
            sharedAttributeValue = "COVID-19",
            merkleRoot = "0xabc123...",
            proof = MerkleTreeHelper.MerkleProof(
                attributeName = "vaccineName",
                attributeValue = "COVID-19",
                leafHash = "hash1",
                siblings = listOf("hash2", "hash3"),
                indices = listOf(true, false)
            )
        )
        
        // Later: User opens app and wants to re-verify (OFFLINE)
        Log.d(TAG, "Recipient verifies share WITHOUT internet:")
        
        // Get cached root from local storage
        val cachedRoot = storageManager.getMerkleRoot("vax_001")
        
        if (cachedRoot != null) {
            Log.d(TAG, "✓ Using cached root: ${cachedRoot.take(12)}...")
            
            val isValid = PartialShareManager.verifyReceivedShare(
                shareData = shareData,
                blockchainMerkleRoot = cachedRoot
            )
            
            Log.d(TAG, if (isValid) "✅ Verified (offline)" else "❌ Invalid")
        } else {
            Log.d(TAG, "❌ Root not cached, need to fetch from blockchain")
        }
    }
}
