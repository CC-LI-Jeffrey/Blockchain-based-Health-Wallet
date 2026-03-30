package com.fyp.blockchainhealthwallet.zkp

import java.math.BigInteger

/**
 * Vaccine integer codes — MUST match VaccineVerify.circom and VaccineVerifyExtension.sol
 *
 * The app maps a user-typed vaccine name to one of these codes before generating a proof.
 * The mapping is normalized (lowercased, trimmed) so minor spelling differences still match.
 */
object VaccineCodes {
    const val COVID_19     = 1
    const val INFLUENZA    = 2
    const val HEPATITIS_B  = 3
    const val HEPATITIS_A  = 4
    const val MMR          = 5
    const val VARICELLA    = 6
    const val HPV          = 7
    const val TETANUS      = 8
    const val PNEUMOCOCCAL = 9
    const val MENINGOCOCCAL = 10
    const val RABIES       = 11
    const val YELLOW_FEVER = 12
    const val TYPHOID      = 13
    const val OTHER        = 14

    val displayNames = mapOf(
        COVID_19      to "COVID-19",
        INFLUENZA     to "Influenza",
        HEPATITIS_B   to "Hepatitis B",
        HEPATITIS_A   to "Hepatitis A",
        MMR           to "MMR (Measles, Mumps, Rubella)",
        VARICELLA     to "Varicella (Chickenpox)",
        HPV           to "HPV",
        TETANUS       to "Tetanus / TdaP",
        PNEUMOCOCCAL  to "Pneumococcal",
        MENINGOCOCCAL to "Meningococcal",
        RABIES        to "Rabies",
        YELLOW_FEVER  to "Yellow Fever",
        TYPHOID       to "Typhoid",
        OTHER         to "Other"
    )

    /** All 14 vaccine names in order (for Spinner adapter, index+1 = code) */
    val spinnerItems: List<String> = (1..14).map { displayNames[it]!! }

    /**
     * Map a free-text vaccine name to its integer code.
     * Returns [OTHER] if no match found.
     */
    fun fromName(name: String): Int {
        val n = name.lowercase().trim()
        return when {
            n.contains("covid") || n.contains("corona") || n.contains("sars") -> COVID_19
            n.contains("influenza") || n.contains("flu") -> INFLUENZA
            n.contains("hepatitis b") || n.contains("hepb") || n.contains("hbv") -> HEPATITIS_B
            n.contains("hepatitis a") || n.contains("hepa") || n.contains("hav") -> HEPATITIS_A
            n.contains("mmr") || n.contains("measles") || n.contains("mumps") || n.contains("rubella") -> MMR
            n.contains("varicella") || n.contains("chickenpox") || n.contains("chicken pox") -> VARICELLA
            n.contains("hpv") || n.contains("human papilloma") -> HPV
            n.contains("tetanus") || n.contains("tdap") || n.contains("tdp") || n.contains("dtp") -> TETANUS
            n.contains("pneumo") -> PNEUMOCOCCAL
            n.contains("meningo") || n.contains("meningitis") -> MENINGOCOCCAL
            n.contains("rabies") -> RABIES
            n.contains("yellow fever") || n.contains("yellow-fever") -> YELLOW_FEVER
            n.contains("typhoid") -> TYPHOID
            else -> OTHER
        }
    }
}

/**
 * Holds a generated Groth16 ZK proof for vaccine verification.
 *
 * proofA, proofB, proofC are the elliptic curve points that make up the proof.
 * publicSignals contains:
 *   [0] = isVaccinated  (always "1")
 *   [1] = commitment    (Poseidon hash registered on-chain)
 *   [2] = targetVaccine (integer vaccine code, e.g. 1 for COVID-19)
 *
 * NOTE: vaccinationId and salt are NOT in this class — they never leave the device.
 */
data class VaccineZkpProofResult(
    val proofA: List<String>,           // 2 field elements
    val proofB: List<List<String>>,     // 2x2 field elements
    val proofC: List<String>,           // 2 field elements
    val publicSignals: List<String>     // ["1", "<commitment>", "<vaccineCode>"]
) {
    fun toA(): List<BigInteger> = proofA.map { BigInteger(it) }

    /**
     * snarkjs outputs pi_b inner pairs as [imaginary, real].
     * Solidity Groth16 verifier expects each inner pair REVERSED: [real, imaginary].
     */
    fun toB(): List<List<BigInteger>> = proofB.map { row -> row.reversed().map { BigInteger(it) } }

    fun toC(): List<BigInteger> = proofC.map { BigInteger(it) }

    fun toPublicInputs(): List<BigInteger> = publicSignals.map { BigInteger(it) }

    fun isStructureValid(): Boolean {
        return proofA.size == 2 &&
                proofB.size == 2 &&
                proofB.all { it.size == 2 } &&
                proofC.size == 2 &&
                publicSignals.size == 3 &&
                publicSignals[0] == "1"    // isVaccinated must be 1
    }

    val isVaccinated:  Boolean get() = publicSignals.getOrNull(0) == "1"
    val commitment:    BigInteger get() = BigInteger(publicSignals.getOrNull(1) ?: "0")
    val targetVaccine: Int get() = publicSignals.getOrNull(2)?.toIntOrNull() ?: 0

    /**
     * Generate a hash of this proof for database storage and verification tracking.
     * Uses SHA256 of concatenated proof points only (A, B, C).
     * publicSignals are intentionally not included in this hash.
     */
    fun proofHash(): String {
        val proofString = "${proofA.joinToString(",")},${proofB.flatten().joinToString(",")},${proofC.joinToString(",")}"
        val messageDigest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = messageDigest.digest(proofString.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
