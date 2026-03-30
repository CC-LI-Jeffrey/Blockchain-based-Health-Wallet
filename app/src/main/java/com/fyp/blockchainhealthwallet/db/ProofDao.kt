package com.fyp.blockchainhealthwallet.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Data Access Object for ProofRecord entity.
 * All methods are blocking - call from a coroutine dispatcher.
 */
@Dao
interface ProofDao {

    /**
     * Insert a new proof record.
     */
    @Insert
    fun insertProof(proof: ProofRecord)

    /**
     * Get all proofs of a specific type, ordered by most recent first.
     */
    @Query("SELECT * FROM proof_records WHERE type = :type ORDER BY timestamp DESC")
    fun getProofsByType(type: String): List<ProofRecord>

    /**
     * Get all verified proofs, ordered by most recent first.
     */
    @Query("SELECT * FROM proof_records WHERE isVerified = 1 ORDER BY timestamp DESC")
    fun getVerifiedProofs(): List<ProofRecord>

    /**
     * Get the most recent proof of a given type.
     */
    @Query("SELECT * FROM proof_records WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    fun getMostRecentProof(type: String): ProofRecord?

    /**
     * Get the most recent proof of a given type and value bucket.
     * For vaccine proofs, minValue stores vaccineCode.
     */
    @Query("SELECT * FROM proof_records WHERE type = :type AND minValue = :minValue ORDER BY timestamp DESC LIMIT 1")
    fun getMostRecentProofByTypeAndMinValue(type: String, minValue: Long): ProofRecord?

    /**
     * Check if a specific proof (by hash) has been verified.
     */
    @Query("SELECT COUNT(*) FROM proof_records WHERE proofHash = :proofHash AND isVerified = 1")
    fun isProofVerified(proofHash: String): Int

    /**
     * Delete old proofs (older than a given timestamp).
     */
    @Query("DELETE FROM proof_records WHERE timestamp < :olderThan")
    fun deleteOldProofs(olderThan: Long)

    /**
     * Delete all proofs of a specific type.
     */
    @Query("DELETE FROM proof_records WHERE type = :type")
    fun deleteProofsByType(type: String)

    /**
     * Get total number of verified proofs.
     */
    @Query("SELECT COUNT(*) FROM proof_records WHERE isVerified = 1")
    fun countVerifiedProofs(): Int

    /**
     * Get a specific proof by hash.
     */
    @Query("SELECT * FROM proof_records WHERE proofHash = :proofHash")
    fun getProofByHash(proofHash: String): ProofRecord?

    /**
     * Update verification status and timestamp for a proof.
     */
    @Query("UPDATE proof_records SET isVerified = 1, verifiedAt = :verifiedAt WHERE id = :id")
    fun markAsVerified(id: Int, verifiedAt: Long)
}

