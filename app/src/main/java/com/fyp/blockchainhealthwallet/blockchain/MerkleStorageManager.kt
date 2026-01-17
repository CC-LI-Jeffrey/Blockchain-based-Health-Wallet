package com.fyp.blockchainhealthwallet.blockchain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson

/**
 * MerkleStorageManager handles persistent storage of Merkle records
 * 
 * Responsibilities:
 * 1. Local storage (SharedPreferences) for quick access
 * 2. IPFS hash management
 * 3. Retrieval by recordId, type, owner
 * 4. Sync with blockchain
 */
class MerkleStorageManager(context: Context) {
    companion object {
        private const val TAG = "MerkleStorageManager"
        private const val PREFS_NAME = "merkle_records"
        private const val KEY_PREFIX = "merkle_record_"
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val gson = Gson()
    
    /**
     * Data class for local storage
     */
    data class StoredMerkleRecord(
        val recordId: String,
        val recordType: String,
        val ownerAddress: String,
        val merkleRoot: String,
        val ipfsHash: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val blockchainTxHash: String? = null
    )
    
    /**
     * Save Merkle record to local storage
     */
    fun saveMerkleRecord(
        recordId: String,
        recordType: String,
        ownerAddress: String,
        merkleRoot: String,
        ipfsHash: String? = null
    ): Boolean {
        return try {
            val stored = StoredMerkleRecord(
                recordId = recordId,
                recordType = recordType,
                ownerAddress = ownerAddress,
                merkleRoot = merkleRoot,
                ipfsHash = ipfsHash,
                timestamp = System.currentTimeMillis()
            )
            
            val key = "$KEY_PREFIX$recordId"
            val json = gson.toJson(stored)
            
            sharedPreferences.edit().putString(key, json).apply()
            
            Log.d(TAG, "✓ Saved record: $recordId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save record: $recordId", e)
            false
        }
    }
    
    /**
     * Get record by recordId (fastest lookup)
     */
    fun getRecordById(recordId: String): StoredMerkleRecord? {
        return try {
            val key = "$KEY_PREFIX$recordId"
            val json = sharedPreferences.getString(key, null) ?: return null
            
            val record = gson.fromJson(json, StoredMerkleRecord::class.java)
            Log.d(TAG, "✓ Retrieved record: $recordId")
            record
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve record: $recordId", e)
            null
        }
    }
    
    /**
     * Get all records owned by a user
     */
    fun getRecordsByOwner(ownerAddress: String): List<StoredMerkleRecord> {
        return try {
            val records = mutableListOf<StoredMerkleRecord>()
            
            sharedPreferences.all.forEach { (key, value) ->
                if (key.startsWith(KEY_PREFIX) && value is String) {
                    try {
                        val record = gson.fromJson(value, StoredMerkleRecord::class.java)
                        if (record.ownerAddress == ownerAddress) {
                            records.add(record)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse record: $key", e)
                    }
                }
            }
            
            Log.d(TAG, "✓ Retrieved ${records.size} records for owner")
            records.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve records by owner", e)
            emptyList()
        }
    }
    
    /**
     * Get all records of a specific type
     */
    fun getRecordsByType(recordType: String): List<StoredMerkleRecord> {
        return try {
            val records = mutableListOf<StoredMerkleRecord>()
            
            sharedPreferences.all.forEach { (key, value) ->
                if (key.startsWith(KEY_PREFIX) && value is String) {
                    try {
                        val record = gson.fromJson(value, StoredMerkleRecord::class.java)
                        if (record.recordType == recordType) {
                            records.add(record)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse record: $key", e)
                    }
                }
            }
            
            Log.d(TAG, "✓ Retrieved ${records.size} records of type: $recordType")
            records.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve records by type", e)
            emptyList()
        }
    }
    
    /**
     * Get all records (owned by user)
     */
    fun getAllRecords(ownerAddress: String): List<StoredMerkleRecord> {
        return getRecordsByOwner(ownerAddress)
    }
    
    /**
     * Get Merkle root for verification
     */
    fun getMerkleRoot(recordId: String): String? {
        return getRecordById(recordId)?.merkleRoot
    }
    
    /**
     * Update blockchain transaction hash after confirmation
     */
    fun updateBlockchainTxHash(recordId: String, txHash: String): Boolean {
        return try {
            val record = getRecordById(recordId) ?: return false
            
            val updated = record.copy(blockchainTxHash = txHash)
            val key = "$KEY_PREFIX$recordId"
            val json = gson.toJson(updated)
            
            sharedPreferences.edit().putString(key, json).apply()
            
            Log.d(TAG, "✓ Updated blockchain tx for: $recordId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update blockchain tx", e)
            false
        }
    }
    
    /**
     * Delete record
     */
    fun deleteRecord(recordId: String): Boolean {
        return try {
            val key = "$KEY_PREFIX$recordId"
            sharedPreferences.edit().remove(key).apply()
            
            Log.d(TAG, "✓ Deleted record: $recordId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete record", e)
            false
        }
    }
    
    /**
     * Clear all records (use with caution!)
     */
    fun clearAllRecords(): Boolean {
        return try {
            sharedPreferences.edit().clear().apply()
            Log.d(TAG, "✓ Cleared all records")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear records", e)
            false
        }
    }
    
    /**
     * Check if record exists
     */
    fun recordExists(recordId: String): Boolean {
        return getRecordById(recordId) != null
    }
    
    /**
     * Get statistics
     */
    fun getStatistics(): Map<String, Any> {
        return try {
            val allRecords = sharedPreferences.all
                .filter { it.key.startsWith(KEY_PREFIX) && it.value is String }
                .mapNotNull { (_, value) ->
                    try {
                        gson.fromJson(value as String, StoredMerkleRecord::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
            
            mapOf(
                "totalRecords" to allRecords.size,
                "types" to allRecords.groupingBy { it.recordType }.eachCount(),
                "owners" to allRecords.groupingBy { it.ownerAddress }.eachCount(),
                "ipfsLinked" to allRecords.count { it.ipfsHash != null },
                "blockchainConfirmed" to allRecords.count { it.blockchainTxHash != null }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get statistics", e)
            emptyMap()
        }
    }
}
