package com.fyp.blockchainhealthwallet.zkp

import java.math.BigInteger

/**
 * Holds a generated Groth16 ZK proof for age verification.
 *
 * proofA, proofB, proofC are the elliptic curve points that make up the proof.
 * publicSignals contains only:
 *   [0] = isAdult    (always "1" — proof is invalid if 0)
 *   [1] = currentYear
 *   [2] = minAge     (always "18")
 *
 * NOTE: birthYear is NOT in this class — it never leaves the device.
 */
data class ZkpProofResult(
    val proofA: List<String>,           // 2 field elements
    val proofB: List<List<String>>,     // 2x2 field elements
    val proofC: List<String>,           // 2 field elements
    val publicSignals: List<String>     // ["1", "2026", "18"]
) {
    /**
     * Convert proof to BigInteger arrays for web3j ABI encoding.
     */
    fun toA(): List<BigInteger> = proofA.map { BigInteger(it) }

    /**
     * IMPORTANT: snarkjs outputs pi_b inner pairs as [imaginary, real] (e.g. [[x2,x1],[y2,y1]]),
     * but the snarkjs-generated Solidity Groth16 verifier expects each inner pair REVERSED:
     *   _pB[0] = [pi_b[0][1], pi_b[0][0]]  (i.e. [x1, x2])
     *   _pB[1] = [pi_b[1][1], pi_b[1][0]]  (i.e. [y1, y2])
     * Failing to reverse causes "Invalid ZK proof" on-chain.
     */
    fun toB(): List<List<BigInteger>> = proofB.map { row -> row.reversed().map { BigInteger(it) } }

    fun toC(): List<BigInteger> = proofC.map { BigInteger(it) }

    fun toPublicInputs(): List<BigInteger> = publicSignals.map { BigInteger(it) }

    /**
     * Quick sanity check before submitting on-chain.
     */
    fun isStructureValid(): Boolean {
        return proofA.size == 2 &&
                proofB.size == 2 &&
                proofB.all { it.size == 2 } &&
                proofC.size == 2 &&
                publicSignals.size == 3 &&
                publicSignals[0] == "1"    // isAdult must be 1
    }

    val currentYear: Int get() = publicSignals.getOrNull(1)?.toIntOrNull() ?: 0
    val minAge: Int get() = publicSignals.getOrNull(2)?.toIntOrNull() ?: 0
}
