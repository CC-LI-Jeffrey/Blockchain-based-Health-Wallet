package com.fyp.blockchainhealthwallet.models

import com.fyp.blockchainhealthwallet.blockchain.MerkleTreeHelper
import kotlinx.serialization.Serializable

/**
 * Data models for partial sharing
 */

/**
 * Partial Share Package (for QR code or IPFS)
 */
@Serializable
data class PartialSharePackage(
    val version: String = "1.0",
    val recordType: String,
    val merkleRoot: String,
    val attributes: Map<String, String>,
    val proofs: Map<String, List<ProofNodeData>>,
    val timestamp: Long,
    val expiryTime: Long,
    val recordId: String? = null,  // Optional, for blockchain method
    val ownerAddress: String? = null,
    val encryptedForReceiver: String? = null  // RSA encrypted payload
)

/**
 * Serializable proof node
 */
@Serializable
data class ProofNodeData(
    val hash: String,
    val position: String
) {
    companion object {
        fun fromProofNode(node: MerkleTreeHelper.ProofNode): ProofNodeData {
            return ProofNodeData(node.hash, node.position)
        }
        
        fun toProofNode(data: ProofNodeData): MerkleTreeHelper.ProofNode {
            return MerkleTreeHelper.ProofNode(data.hash, data.position)
        }
    }
}

/**
 * Share request from UI
 */
data class PartialShareRequest(
    val recordId: String,
    val recordType: RecordSchemas.RecordType,
    val fullRecord: Map<String, String>,
    val selectedAttributes: List<String>,
    val shareMethod: ShareMethod,
    val receiverAddress: String? = null,
    val receiverPublicKey: String? = null,
    val expiryHours: Int = 24
)

enum class ShareMethod {
    QR_CODE,        // Instant, no blockchain tx
    BLOCKCHAIN      // Persistent, with IPFS + blockchain
}

/**
 * Verification result
 */
data class VerificationResult(
    val isValid: Boolean,
    val merkleRoot: String,
    val attributeResults: Map<String, Boolean>,
    val invalidAttributes: List<String>
) {
    fun allValid(): Boolean {
        return isValid && attributeResults.values.all { it }
    }
}
