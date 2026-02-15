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
     * Verify a Merkle proof with detailed logging
     * Shows step-by-step hash computation for verification
     */
    fun verifyProofWithDetailedLogging(
        attributeName: String,
        attributeValue: String,
        proof: List<ProofNode>,
        expectedRoot: String,
        tag: String = "MerkleVerify"
    ): Boolean {
        android.util.Log.d(tag, "╔═══════════════════════════════════════════════════════════════")
        android.util.Log.d(tag, "║ DETAILED MERKLE PROOF VERIFICATION")
        android.util.Log.d(tag, "╠═══════════════════════════════════════════════════════════════")
        android.util.Log.d(tag, "║ Attribute: $attributeName")
        android.util.Log.d(tag, "║ Value: $attributeValue")
        android.util.Log.d(tag, "╠═══════════════════════════════════════════════════════════════")
        android.util.Log.d(tag, "║ MERKLE TREE CONCEPT:")
        android.util.Log.d(tag, "║ • We only need the 'sibling nodes' along the path from leaf→root")
        android.util.Log.d(tag, "║ • Other nodes exist but aren't included (that's the efficiency!)")
        android.util.Log.d(tag, "║ • This allows verification with only log₂(n) hashes")
        android.util.Log.d(tag, "╚═══════════════════════════════════════════════════════════════")
        
        // Step 1: Hash the leaf
        val leafData = "$attributeName:$attributeValue"
        var currentHash = hashLeaf(attributeName, attributeValue)
        
        android.util.Log.d(tag, "")
        android.util.Log.d(tag, "[STEP 0] Compute Leaf Hash")
        android.util.Log.d(tag, "  Input: \"$leafData\"")
        android.util.Log.d(tag, "  Leaf Hash: $currentHash")
        
        // Step 2: Climb the tree level by level
        android.util.Log.d(tag, "")
        android.util.Log.d(tag, "[CLIMBING TREE] Proof path has ${proof.size} levels")
        android.util.Log.d(tag, "NOTE: Only showing nodes relevant to THIS attribute's proof path")
        android.util.Log.d(tag, "      Other nodes exist but aren't needed for verification")
        
        for ((index, proofNode) in proof.withIndex()) {
            val levelNum = index + 1
            val oldHash = currentHash
            
            android.util.Log.d(tag, "")
            android.util.Log.d(tag, "┌─────────────────────────────────────────────────────────────┐")
            android.util.Log.d(tag, "│ LEVEL $levelNum                                                  │")
            android.util.Log.d(tag, "└─────────────────────────────────────────────────────────────┘")
            android.util.Log.d(tag, "  ▸ Current Node (being verified):")
            android.util.Log.d(tag, "    $oldHash")
            android.util.Log.d(tag, "")
            android.util.Log.d(tag, "  ▸ Sibling Node (from proof, position: ${proofNode.position}):")
            android.util.Log.d(tag, "    ${proofNode.hash}")
            android.util.Log.d(tag, "")
            android.util.Log.d(tag, "  ▸ Hashing Strategy:")
            
            val concatenated = if (proofNode.position == "left") {
                // Sibling is on left: sibling + current
                android.util.Log.d(tag, "    Hash(Sibling + Current) because sibling is on LEFT")
                android.util.Log.d(tag, "    ├─ Left:  ${proofNode.hash.take(20)}...")
                android.util.Log.d(tag, "    └─ Right: ${oldHash.take(20)}...")
                proofNode.hash + currentHash
            } else {
                // Sibling is on right: current + sibling
                android.util.Log.d(tag, "    Hash(Current + Sibling) because sibling is on RIGHT")
                android.util.Log.d(tag, "    ├─ Left:  ${oldHash.take(20)}...")
                android.util.Log.d(tag, "    └─ Right: ${proofNode.hash.take(20)}...")
                currentHash + proofNode.hash
            }
            
            currentHash = sha256(concatenated)
            android.util.Log.d(tag, "")
            android.util.Log.d(tag, "  ✓ Parent Node Computed:")
            android.util.Log.d(tag, "    $currentHash")
            android.util.Log.d(tag, "    (This becomes 'Current Node' for next level)")
        }
        
        // Step 3: Compare with expected root
        android.util.Log.d(tag, "")
        android.util.Log.d(tag, "╔═══════════════════════════════════════════════════════════════")
        android.util.Log.d(tag, "║ VERIFICATION RESULT")
        android.util.Log.d(tag, "╠═══════════════════════════════════════════════════════════════")
        android.util.Log.d(tag, "║ Computed Root: $currentHash")
        android.util.Log.d(tag, "║ Expected Root: $expectedRoot")
        
        val isValid = currentHash == expectedRoot
        
        if (isValid) {
            android.util.Log.d(tag, "║ Status: ✓ VALID - Hashes match!")
        } else {
            android.util.Log.d(tag, "║ Status: ✗ INVALID - Hashes do NOT match!")
        }
        android.util.Log.d(tag, "╚═══════════════════════════════════════════════════════════════")
        android.util.Log.d(tag, "")
        
        return isValid
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
