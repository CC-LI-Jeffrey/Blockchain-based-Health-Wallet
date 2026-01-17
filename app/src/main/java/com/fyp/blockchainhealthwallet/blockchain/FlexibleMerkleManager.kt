package com.fyp.blockchainhealthwallet.blockchain

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * FlexibleMerkleManager handles Merkle trees for ANY data model
 * 
 * Works with:
 * - VaccinationRecord
 * - HealthReport
 * - MedicationRecord
 * - Any other data class
 * 
 * APPROACH: Convert any data class to Map<String, String> automatically
 */
object FlexibleMerkleManager {
    private const val TAG = "FlexibleMerkleManager"
    
    /**
     * Generic record wrapper that works with any data type
     */
    data class MerkleRecord<T>(
        val recordId: String,
        val recordType: String,  // "VaccinationRecord", "HealthReport", etc
        val originalData: T,
        val attributes: Map<String, String>,
        val merkleRoot: String,
        val merkleTree: MerkleTreeHelper.MerkleNode
    )
    
    /**
     * Convert any data class to attribute map
     * Works with data classes, regular classes, and JSON
     */
    fun <T : Any> dataToAttributes(data: T): Map<String, String> {
        Log.d(TAG, "Converting ${data::class.simpleName} to attributes")
        
        val attributes = mutableMapOf<String, String>()
        
        try {
            // Try reflection first (for data classes and regular classes)
            val klass = data::class
            val properties = klass.memberProperties
            
            for (prop in properties) {
                try {
                    prop.isAccessible = true
                    val value = prop.getter.call(data)
                    
                    // Handle null values
                    val stringValue = value?.toString() ?: "null"
                    attributes[prop.name] = stringValue
                    
                    Log.d(TAG, "  ${prop.name} -> $stringValue")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read property ${prop.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reflection failed, trying Gson fallback", e)
            
            // Fallback: Convert to JSON and extract fields
            try {
                val gson = Gson()
                val json = gson.toJsonTree(data) as? JsonObject
                
                json?.entrySet()?.forEach { (key, value) ->
                    attributes[key] = value.toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gson fallback failed", e)
                throw IllegalArgumentException("Cannot convert object to attributes", e)
            }
        }
        
        Log.d(TAG, "Extracted ${attributes.size} attributes")
        return attributes
    }
    
    /**
     * Create Merkle record from any data class
     * 
     * Example:
     * val vaccination = VaccinationRecord(...)
     * val merkleRecord = FlexibleMerkleManager.createMerkleRecord(
     *     data = vaccination,
     *     recordType = "VaccinationRecord"
     * )
     */
    fun <T : Any> createMerkleRecord(
        data: T,
        recordType: String,
        recordId: String = "${recordType}_${System.currentTimeMillis()}"
    ): MerkleRecord<T> {
        Log.d(TAG, "Creating MerkleRecord for $recordType")
        
        // Step 1: Convert data class to attributes map
        val attributes = dataToAttributes(data)
        
        // Step 2: Build Merkle tree
        val (merkleRoot, merkleTree) = MerkleTreeHelper.buildMerkleTree(attributes)
        
        Log.d(TAG, "MerkleRecord created:")
        Log.d(TAG, "  Type: $recordType")
        Log.d(TAG, "  ID: $recordId")
        Log.d(TAG, "  Attributes: ${attributes.size}")
        Log.d(TAG, "  Root: ${merkleRoot.take(16)}...")
        
        return MerkleRecord(
            recordId = recordId,
            recordType = recordType,
            originalData = data,
            attributes = attributes,
            merkleRoot = merkleRoot,
            merkleTree = merkleTree
        )
    }
    
    /**
     * Create partial share from any Merkle record
     * 
     * Example:
     * val share = FlexibleMerkleManager.createPartialShare(
     *     record = merkleRecord,
     *     attributeToShare = "vaccineName",
     *     recipientAddress = "0xABC123"
     * )
     */
    fun <T : Any> createPartialShare(
        record: MerkleRecord<T>,
        attributeToShare: String,
        recipientAddress: String
    ): PartialShareManager.PartialShareData {
        Log.d(TAG, "Creating partial share from ${record.recordType}")
        
        // Verify attribute exists
        val attributeValue = record.attributes[attributeToShare]
            ?: throw IllegalArgumentException(
                "Attribute '$attributeToShare' not found in ${record.recordType}. " +
                "Available: ${record.attributes.keys.joinToString(", ")}"
            )
        
        // Generate proof
        val proof = MerkleTreeHelper.generateProof(
            attributeName = attributeToShare,
            attributeValue = attributeValue,
            merkleTree = record.merkleTree
        )
        
        Log.d(TAG, "Share created for attribute: $attributeToShare")
        
        return PartialShareManager.PartialShareData(
            recordId = record.recordId,
            recipientAddress = recipientAddress,
            sharedAttributeName = attributeToShare,
            sharedAttributeValue = attributeValue,
            merkleRoot = record.merkleRoot,
            proof = proof,
            recordType = record.recordType  // Track which type of record this is
        )
    }
    
    /**
     * Get all available attributes for a record type
     * Useful for UI: showing user which fields they can share
     */
    fun <T : Any> getAvailableAttributes(data: T): List<String> {
        return dataToAttributes(data).keys.sorted()
    }
    
    /**
     * Get attribute value from original data
     */
    fun <T : Any> getAttributeValue(record: MerkleRecord<T>, attributeName: String): String? {
        return record.attributes[attributeName]
    }
}
