package com.fyp.blockchainhealthwallet.blockchain

import android.util.Log
import java.security.MessageDigest

/**
 * MerkleTreeHelper provides Merkle tree functionality for partial health record sharing.
 * 
 * PURPOSE:
 * Allows users to prove that a specific attribute (e.g., HKID first 4 digits) belongs to
 * a legitimate health record WITHOUT revealing other attributes.
 * 
 * HOW IT WORKS:
 * 1. Build a Merkle tree from health record attributes
 * 2. Generate a Merkle proof for a specific attribute
 * 3. Send only the attribute + proof to recipient
 * 4. Recipient verifies the attribute is authentic using the root hash stored on blockchain
 */
object MerkleTreeHelper {
    private const val TAG = "MerkleTreeHelper"
    private const val HASH_ALGORITHM = "SHA-256"
    
    /**
     * Represents a node in the Merkle tree
     */
    data class MerkleNode(
        val hash: String,
        val left: MerkleNode? = null,
        val right: MerkleNode? = null,
        val isLeaf: Boolean = false
    )
    
    /**
     * Merkle proof: path from leaf to root
     * Contains sibling hashes needed to reconstruct the root
     */
    data class MerkleProof(
        val attributeName: String,
        val attributeValue: String,
        val leafHash: String,
        val siblings: List<String>, // Sibling hashes to reconstruct root
        val indices: List<Boolean>  // true = right sibling, false = left sibling
    )
    
    /**
     * Health record with Merkle tree properties
     */
    data class HealthRecordWithMerkle(
        val id: String,
        val attributes: Map<String, String>, // attribute name -> value
        val merkleRoot: String,
        val merkleTree: MerkleNode
    )
    
    /**
     * Hash a string value using SHA-256
     */
    private fun hashValue(value: String): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val hashBytes = digest.digest(value.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Hash two sibling hashes to create parent hash
     */
    private fun hashPair(left: String, right: String): String {
        return hashValue(left + right)
    }
    
    /**
     * Build Merkle tree from attributes
     * 
     * Example:
     *   attributes = {"name" -> "John", "hkid" -> "1234567890", "blood" -> "O+"}
     *   Creates tree structure and returns root hash
     */
    fun buildMerkleTree(attributes: Map<String, String>): Pair<String, MerkleNode> {
        if (attributes.isEmpty()) {
            throw IllegalArgumentException("Attributes cannot be empty")
        }
        
        // Step 1: Create leaf nodes (hash each attribute)
        var nodes = attributes.entries.sortedBy { it.key }.map { (key, value) ->
            val leafHash = hashValue("$key:$value")
            Log.d(TAG, "Leaf hash for $key: $leafHash")
            MerkleNode(hash = leafHash, isLeaf = true)
        }
        
        // Step 2: Build tree bottom-up
        while (nodes.size > 1) {
            val newLevel = mutableListOf<MerkleNode>()
            
            // Pair up nodes and hash them
            for (i in nodes.indices step 2) {
                val left = nodes[i]
                val right = if (i + 1 < nodes.size) nodes[i + 1] else nodes[i]
                
                val parentHash = hashPair(left.hash, right.hash)
                Log.d(TAG, "Parent hash: ${left.hash.take(8)}... + ${right.hash.take(8)}... = ${parentHash.take(8)}...")
                
                val parent = MerkleNode(
                    hash = parentHash,
                    left = left,
                    right = right,
                    isLeaf = false
                )
                newLevel.add(parent)
            }
            
            nodes = newLevel
        }
        
        val root = nodes[0]
        Log.d(TAG, "Merkle Root: ${root.hash}")
        return Pair(root.hash, root)
    }
    
    /**
     * Generate a Merkle proof for a specific attribute
     * 
     * The proof allows verification that this attribute is part of the original record
     * without revealing other attributes
     */
    fun generateProof(
        attributeName: String,
        attributeValue: String,
        merkleTree: MerkleNode
    ): MerkleProof {
        val targetHash = hashValue("$attributeName:$attributeValue")
        val siblings = mutableListOf<String>()
        val indices = mutableListOf<Boolean>()
        
        // Traverse tree and collect sibling hashes
        fun traverse(node: MerkleNode): Boolean {
            if (node.isLeaf && node.hash == targetHash) {
                return true
            }
            
            if (node.left != null && node.right != null) {
                // Try left subtree
                if (traverse(node.left)) {
                    // Found in left, add right sibling
                    siblings.add(node.right.hash)
                    indices.add(true) // right sibling
                    return true
                }
                
                // Try right subtree
                if (traverse(node.right)) {
                    // Found in right, add left sibling
                    siblings.add(node.left.hash)
                    indices.add(false) // left sibling
                    return true
                }
            }
            
            return false
        }
        
        val found = traverse(merkleTree)
        if (!found) {
            throw IllegalArgumentException("Attribute not found in Merkle tree")
        }
        
        Log.d(TAG, "Generated proof for $attributeName with ${siblings.size} sibling hashes")
        
        return MerkleProof(
            attributeName = attributeName,
            attributeValue = attributeValue,
            leafHash = targetHash,
            siblings = siblings,
            indices = indices
        )
    }
    
    /**
     * Verify a Merkle proof against a known root hash
     * 
     * Returns true if the attribute is authentic and part of the record
     */
    fun verifyProof(proof: MerkleProof, knownRootHash: String): Boolean {
        var currentHash = proof.leafHash
        
        // Reconstruct the path from leaf to root
        for (i in proof.siblings.indices) {
            val sibling = proof.siblings[i]
            val isRightSibling = proof.indices[i]
            
            currentHash = if (isRightSibling) {
                // Current is left, sibling is right
                hashPair(currentHash, sibling)
            } else {
                // Current is right, sibling is left
                hashPair(sibling, currentHash)
            }
            
            Log.d(TAG, "Reconstructed hash: ${currentHash.take(8)}...")
        }
        
        val verified = currentHash == knownRootHash
        Log.d(TAG, "Proof verification: $verified")
        Log.d(TAG, "Reconstructed root: $currentHash")
        Log.d(TAG, "Known root:        $knownRootHash")
        
        return verified
    }
    
    /**
     * Convenient method: Create health record with Merkle tree
     */
    fun createHealthRecordWithMerkle(
        recordId: String,
        attributes: Map<String, String>
    ): HealthRecordWithMerkle {
        val (rootHash, tree) = buildMerkleTree(attributes)
        return HealthRecordWithMerkle(
            id = recordId,
            attributes = attributes,
            merkleRoot = rootHash,
            merkleTree = tree
        )
    }
    
    /**
     * Get root hash of a record
     */
    fun getMerkleRoot(record: HealthRecordWithMerkle): String {
        return record.merkleRoot
    }
}
