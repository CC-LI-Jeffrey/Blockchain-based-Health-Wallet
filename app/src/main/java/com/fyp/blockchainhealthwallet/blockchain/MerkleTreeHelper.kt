package com.fyp.blockchainhealthwallet.blockchain

import com.fyp.blockchainhealthwallet.models.RecordSchemas
import java.security.MessageDigest

/**
 * Merkle Tree Helper for Partial Data Sharing
 * Enables selective attribute disclosure with cryptographic proofs
 */
class MerkleTreeHelper {
    
    data class MerkleTree(
        val root: String,
        val tree: List<List<String>>,
        val leaves: List<String>,
        val schema: List<String>
    )
    
    data class ProofNode(
        val hash: String,
        val position: String  // "left" or "right"
    )
    
    /**
     * Build Merkle tree from record attributes
     */
    fun buildMerkleTree(
        recordType: RecordSchemas.RecordType,
        attributes: Map<String, String>
    ): MerkleTree {
        val schema = RecordSchemas.getSchema(recordType)
        
        // Create leaves in schema order
        val leaves = schema.map { attrName ->
            val value = attributes[attrName] ?: ""
            hashLeaf(attrName, value)
        }
        
        // Build tree bottom-up
        val tree = constructTree(leaves)
        
        return MerkleTree(
            root = tree.last()[0],
            tree = tree,
            leaves = leaves,
            schema = schema
        )
    }
    
    /**
     * Hash a single leaf node
     */
    fun hashLeaf(attributeName: String, attributeValue: String): String {
        val data = "$attributeName:$attributeValue"
        return sha256(data)
    }
    
    /**
     * Construct Merkle tree from leaves
     */
    private fun constructTree(leaves: List<String>): List<List<String>> {
        if (leaves.isEmpty()) {
            throw IllegalArgumentException("Cannot build tree from empty leaves")
        }
        
        val tree = mutableListOf(leaves)
        var currentLayer = leaves
        
        while (currentLayer.size > 1) {
            val nextLayer = mutableListOf<String>()
            
            var i = 0
            while (i < currentLayer.size) {
                if (i + 1 < currentLayer.size) {
                    // Pair exists
                    val combined = currentLayer[i] + currentLayer[i + 1]
                    val hash = sha256(combined)
                    nextLayer.add(hash)
                } else {
                    // Odd node, duplicate it
                    val combined = currentLayer[i] + currentLayer[i]
                    val hash = sha256(combined)
                    nextLayer.add(hash)
                }
                i += 2
            }
            
            tree.add(nextLayer)
            currentLayer = nextLayer
        }
        
        return tree
    }
    
    /**
     * Generate Merkle proof for a specific attribute
     */
    fun generateProof(
        merkleTree: MerkleTree,
        attributeName: String,
        attributeValue: String
    ): List<ProofNode> {
        // Find index of attribute in schema
        val leafIndex = merkleTree.schema.indexOf(attributeName)
        if (leafIndex == -1) {
            throw IllegalArgumentException("Attribute $attributeName not found in schema")
        }
        
        val proof = mutableListOf<ProofNode>()
        var currentIndex = leafIndex
        
        // Traverse from leaf to root, collecting sibling hashes
        for (level in 0 until merkleTree.tree.size - 1) {
            val layer = merkleTree.tree[level]
            val isRightNode = currentIndex % 2 == 1
            
            if (isRightNode) {
                // Sibling is to the left
                proof.add(ProofNode(
                    hash = layer[currentIndex - 1],
                    position = "left"
                ))
            } else {
                // Sibling is to the right
                if (currentIndex + 1 < layer.size) {
                    proof.add(ProofNode(
                        hash = layer[currentIndex + 1],
                        position = "right"
                    ))
                } else {
                    // No sibling, duplicate self
                    proof.add(ProofNode(
                        hash = layer[currentIndex],
                        position = "right"
                    ))
                }
            }
            
            currentIndex = currentIndex / 2
        }
        
        return proof
    }
    
    /**
     * Verify a Merkle proof
     */
    fun verifyProof(
        attributeName: String,
        attributeValue: String,
        proof: List<ProofNode>,
        expectedRoot: String
    ): Boolean {
        // Start with leaf hash
        var currentHash = hashLeaf(attributeName, attributeValue)
        
        // Climb the tree using proof
        for (proofNode in proof) {
            currentHash = if (proofNode.position == "left") {
                // Sibling is on left
                sha256(proofNode.hash + currentHash)
            } else {
                // Sibling is on right
                sha256(currentHash + proofNode.hash)
            }
        }
        
        return currentHash == expectedRoot
    }
    
    /**
     * Generate proofs for multiple attributes
     */
    fun generateProofs(
        merkleTree: MerkleTree,
        attributes: Map<String, String>
    ): Map<String, List<ProofNode>> {
        val proofs = mutableMapOf<String, List<ProofNode>>()
        
        for ((attrName, attrValue) in attributes) {
            proofs[attrName] = generateProof(merkleTree, attrName, attrValue)
        }
        
        return proofs
    }
    
    /**
     * Verify multiple proofs
     */
    fun verifyProofs(
        attributes: Map<String, String>,
        proofs: Map<String, List<ProofNode>>,
        expectedRoot: String
    ): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        
        for ((attrName, attrValue) in attributes) {
            val proof = proofs[attrName]
            if (proof != null) {
                results[attrName] = verifyProof(attrName, attrValue, proof, expectedRoot)
            } else {
                results[attrName] = false
            }
        }
        
        return results
    }
    
    /**
     * SHA-256 hashing
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    companion object {
        /**
         * Calculate Merkle root from blockchain (bytes32)
         */
        fun bytes32ToHex(bytes32: ByteArray): String {
            return bytes32.joinToString("") { "%02x".format(it) }
        }
        
        /**
         * Convert hex string to bytes32 for blockchain
         */
        fun hexToBytes32(hex: String): ByteArray {
            val cleanHex = hex.removePrefix("0x")
            return cleanHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    }
}
