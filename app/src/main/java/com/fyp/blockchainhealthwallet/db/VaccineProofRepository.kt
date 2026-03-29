package com.fyp.blockchainhealthwallet.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for vaccine proof operations.
 * Provides a clean separation between Room DAOs and the rest of the app.
 */
class VaccineProofRepository(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val proofDao = database.proofDao()

    /**
     * Get the most recent verified vaccine proof for this device.
     */
    suspend fun getMostRecentVaccineProof(): ProofRecord? {
        return withContext(Dispatchers.IO) {
            proofDao.getMostRecentProof("VACCINE_PASSPORT")
        }
    }

    /**
     * Get the most recent verified vaccine proof for a specific vaccine code.
     */
    suspend fun getMostRecentVaccineProofByCode(vaccineCode: Int): ProofRecord? {
        return withContext(Dispatchers.IO) {
            proofDao.getMostRecentProofByTypeAndMinValue("VACCINE_PASSPORT", vaccineCode.toLong())
        }
    }

    /**
     * Get all verified vaccine proofs.
     */
    suspend fun getAllVaccineProofs(): List<ProofRecord> {
        return withContext(Dispatchers.IO) {
            proofDao.getProofsByType("VACCINE_PASSPORT")
        }
    }

    /**
     * Save a newly verified vaccine proof to the local database.
     */
    suspend fun saveVaccineProof(proof: ProofRecord) {
        return withContext(Dispatchers.IO) {
            proofDao.insertProof(proof)
        }
    }

    /**
     * Check if a proof has been verified.
     */
    suspend fun isProofVerified(proofHash: String): Boolean {
        return withContext(Dispatchers.IO) {
            proofDao.isProofVerified(proofHash) > 0
        }
    }

    /**
     * Get count of verified proofs.
     */
    suspend fun getVerifiedProofCount(): Int {
        return withContext(Dispatchers.IO) {
            proofDao.countVerifiedProofs()
        }
    }

    /**
     * Get vaccine proof by hash.
     */
    suspend fun getProofByHash(proofHash: String): ProofRecord? {
        return withContext(Dispatchers.IO) {
            proofDao.getProofByHash(proofHash)
        }
    }

    /**
     * Delete old vaccine proofs to save storage.
     */
    suspend fun deleteOldProofs(olderThan: Long) {
        return withContext(Dispatchers.IO) {
            proofDao.deleteOldProofs(olderThan)
        }
    }

    /**
     * Delete all locally stored vaccine proofs.
     */
    suspend fun deleteAllVaccineProofs() {
        return withContext(Dispatchers.IO) {
            proofDao.deleteProofsByType("VACCINE_PASSPORT")
        }
    }
}
