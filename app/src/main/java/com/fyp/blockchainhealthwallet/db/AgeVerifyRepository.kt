package com.fyp.blockchainhealthwallet.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for age proof operations.
 * Provides a clean separation between Room DAOs and the rest of the app.
 */
class AgeVerifyRepository(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val proofDao = database.proofDao()

    /**
     * Get the most recent verified age proof for this device.
     */
    suspend fun getMostRecentAgeProof(): ProofRecord? {
        return withContext(Dispatchers.IO) {
            proofDao.getMostRecentProof("AGE_PASSPORT")
        }
    }

    /**
     * Save a newly verified age proof to the local database.
     */
    suspend fun saveAgeProof(proof: ProofRecord) {
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
}
