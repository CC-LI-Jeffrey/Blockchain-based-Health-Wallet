package com.fyp.blockchainhealthwallet.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a stored ZK proof verification record.
 *
 * Used to track which proofs have been verified locally by this device.
 * Can be queried to show verification history (e.g., "Age 18+ verified on Mar 10, 20+ verified on Mar 14").
 */
@Entity(tableName = "proof_records")
data class ProofRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Type of proof (e.g. "AGE_PASSPORT" or "VACCINE_PASSPORT")
    val type: String,

    // SHA256 hash of the full proof JSON (unique identifier for the proof)
    val proofHash: String,

    // Public inputs as JSON string (serialized for storage)
    val publicInputs: String,

    // The main verification value
    // For age: the minAge threshold (e.g., 18, 20, 21)
    // For vaccine: the vaccine code (e.g., 1 for COVID-19)
    val minValue: Long,

    // Timestamp when this proof was created/generated (System.currentTimeMillis())
    val timestamp: Long,

    // For vaccine proofs only: the issuer's wallet address
    // For age proofs: null (self-issued)
    val issuerAddress: String = "",

    // For vaccine proofs only: the Poseidon commitment
    // For age proofs: empty string (no commitment)
    val commitment: String = "",

    // Whether the proof was successfully verified locally
    val isVerified: Boolean,

    // Timestamp when this proof was verified locally (0 if not yet verified)
    val verifiedAt: Long = 0
) {
    /**
     * Human-readable description for UI display
     */
    fun getDisplayString(): String = when (type) {
        "AGE_PASSPORT" -> "Age $minValue+ verified"
        "VACCINE_PASSPORT" -> "Vaccine code $minValue verified"
        else -> "Proof verified"
    }
}
