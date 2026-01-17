package com.fyp.blockchainhealthwallet.blockchain

import android.util.Log
import com.google.gson.Gson

/**
 * PartialShareManager handles the logic for sharing individual attributes
 * of a health record using Merkle tree proofs.
 * 
 * WORKFLOW:
 * 1. User creates a health record (with Merkle tree)
 * 2. Merkle root is stored on blockchain
 * 3. User selects an attribute to share (e.g., HKID first 4 digits)
 * 4. System generates Merkle proof for that attribute
 * 5. Share with recipient: attribute + proof
 * 6. Recipient verifies using root hash from blockchain
 */
object PartialShareManager {
    private const val TAG = "PartialShareManager"
    
    /**
     * Represents a partial share that can be sent to another user
     */
    data class PartialShareData(
        val recordId: String,
        val recipientAddress: String,
        val sharedAttributeName: String,
        val sharedAttributeValue: String,
        val merkleRoot: String,
        val proof: MerkleTreeHelper.MerkleProof,
        val recordType: String = "HealthRecord",  // e.g., "VaccinationRecord", "HealthReport"
        val timestamp: Long = System.currentTimeMillis(),
        val blockchainTxHash: String? = null // Will be set after blockchain confirmation
    )
    
    /**
     * Create a partial share for a specific attribute
     * 
     * @param record Health record with Merkle tree
     * @param attributeToShare Name of the attribute to share (e.g., "hkid")
     * @param recipientAddress Address of the recipient
     * @return PartialShareData ready to send
     */
    fun createPartialShare(
        record: MerkleTreeHelper.HealthRecordWithMerkle,
        attributeToShare: String,
        recipientAddress: String
    ): PartialShareData {
        Log.d(TAG, "Creating partial share for attribute: $attributeToShare")
        
        // Verify attribute exists
        val attributeValue = record.attributes[attributeToShare]
            ?: throw IllegalArgumentException("Attribute '$attributeToShare' not found in record")
        
        // Generate proof for this attribute
        val proof = MerkleTreeHelper.generateProof(
            attributeName = attributeToShare,
            attributeValue = attributeValue,
            merkleTree = record.merkleTree
        )
        
        Log.d(TAG, "Proof generated with ${proof.siblings.size} siblings")
        
        return PartialShareData(
            recordId = record.id,
            recipientAddress = recipientAddress,
            sharedAttributeName = attributeToShare,
            sharedAttributeValue = attributeValue,
            merkleRoot = record.merkleRoot,
            proof = proof
        )
    }
    
    /**
     * Verify a received partial share
     * 
     * @param shareData The partial share data received
     * @param blockchainMerkleRoot The Merkle root stored on blockchain (verification reference)
     * @return true if the share is authentic and unmodified
     */
    fun verifyReceivedShare(
        shareData: PartialShareData,
        blockchainMerkleRoot: String
    ): Boolean {
        Log.d(TAG, "Verifying partial share for attribute: ${shareData.sharedAttributeName}")
        
        // Verify the proof
        val isValid = MerkleTreeHelper.verifyProof(
            proof = shareData.proof,
            knownRootHash = blockchainMerkleRoot
        )
        
        if (isValid) {
            Log.d(TAG, "✓ Share verification successful!")
            Log.d(TAG, "  Attribute: ${shareData.sharedAttributeName}")
            Log.d(TAG, "  Value: ${shareData.sharedAttributeValue}")
        } else {
            Log.d(TAG, "✗ Share verification FAILED - proof doesn't match blockchain root")
        }
        
        return isValid
    }
    
    /**
     * Serialize partial share to JSON (for sending over network)
     */
    fun serializeShare(shareData: PartialShareData): String {
        return Gson().toJson(shareData)
    }
    
    /**
     * Deserialize partial share from JSON (for receiving over network)
     */
    fun deserializeShare(json: String): PartialShareData {
        return Gson().fromJson(json, PartialShareData::class.java)
    }
    
    /**
     * Get what will be revealed in a partial share
     * (Useful for UI preview)
     */
    fun getSharePreview(shareData: PartialShareData): String {
        return buildString {
            appendLine("You will share:")
            appendLine("  Attribute: ${shareData.sharedAttributeName}")
            appendLine("  Value: ${shareData.sharedAttributeValue}")
            appendLine("  With: ${shareData.recipientAddress.take(10)}...")
            appendLine("\nVerification:")
            appendLine("  Merkle Root: ${shareData.merkleRoot.take(16)}...")
            appendLine("  Proof Steps: ${shareData.proof.siblings.size}")
        }
    }
}
