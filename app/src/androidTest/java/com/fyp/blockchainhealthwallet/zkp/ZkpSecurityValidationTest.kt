package com.fyp.blockchainhealthwallet.zkp

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fyp.blockchainhealthwallet.AgeVerifyActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 * Validates the Zero-Knowledge Proof (ZKP) Generation and Verification Integrity.
 */
@RunWith(AndroidJUnit4::class)
class ZkpSecurityValidationTest {

    // Boot up a dummy activity because ZkpService requires an Activity context for the WebView
    @get:Rule
    val activityRule = ActivityScenarioRule(AgeVerifyActivity::class.java)

    // Helper block to safely get the ZkpService initialized with the Activity
    private suspend fun <T> withZkpService(block: suspend (ZkpService) -> T): T {
        var service: ZkpService? = null
        activityRule.scenario.onActivity { activity ->
            // Service creation might need to happen on the main thread
            service = ZkpService(activity)
        }
        
        // Execute the suspending block on the Main dispatcher so the WebView
        // can process events on the UI thread without blocking it.
        return withContext(Dispatchers.Main) {
            block(service!!)
        }
    }

    @Test
    fun verifyValidAgeProof_succeeds() = runBlocking {
        // TC-ZKP-01: generate a valid proof
        // 25-year-old checking against threshold age 18.
        val proofResult = withZkpService { zkp ->
            try {
                // E.g. born Jan 1, 2001
                zkp.generateAgeProof(2001, 1, 1, 18)
            } catch (e: Exception) {
                null
            }
        }

        assertNotNull("Proof result should not be null for valid inputs", proofResult)

        // Verify the proof
        withZkpService { zkp ->
            val p = proofResult!!
            val isValid = zkp.verifyAgeProof(p, p.currentYear, p.currentMonth, p.currentDay, 18)
            assertTrue("A valid age above threshold must produce a verifiable proof", isValid)
        }
    }

    @Test
    fun generateAgeProof_failsUnderThreshold() = runBlocking {
        // TC-ZKP-02: generating a proof where constraints are not met should fail
        // 16-year-old checking against threshold age 18. Born 2010.
        val result = runCatching {
            withZkpService { zkp ->
                zkp.generateAgeProof(2010, 1, 1, 18)
            }
        }

        // Technically, snarkjs WASM throws an error when Circom constraints fail
        if (result.isSuccess && result.getOrNull() != null) {
            withZkpService { zkp ->
                val p = result.getOrNull()!!
                val isValid = zkp.verifyAgeProof(p, p.currentYear, p.currentMonth, p.currentDay, 18)
                assertFalse("Snarkjs must not verify a proof that violates the threshold constraint", isValid)
            }
        } else {
            assertTrue("Snarkjs failed to generate witness for mathematically invalid constraints as expected", true)
        }
    }

    @Test
    fun tamperedPublicInput_proofFails() = runBlocking {
        // Generate a valid proof (Age 25, Threshold 18)
        val proofResult = withZkpService { zkp ->
            zkp.generateAgeProof(2001, 1, 1, 18)
        }
        assertNotNull("Setup failed: Proof result should not be null", proofResult)

        // TC-ZKP-03: Attacker intercepts the proof and tries to reuse it for a higher threshold (minAge = 21)
        // Since the proof is generated for minAge = 18, verifying it for minAge = 21 must fail.
        withZkpService { zkp ->
            val p = proofResult!!
            val isValid = zkp.verifyAgeProof(p, p.currentYear, p.currentMonth, p.currentDay, 21)
            assertFalse("Verification MUST fail if public inputs (minAge 21) do not match the proof (minAge 18)", isValid)
        }
    }

    @Test
    fun verifyVaccineProof_succeeds() = runBlocking {
        // TC-ZKP-04: Test the secondary circuit (Vaccine verify)
        // Valid vaccine code per snarkjs_wrapper is 1-14
        val validVaccineCode = 1
        val saltStr = "123456789012345"
        val saltBigInt = java.math.BigInteger(saltStr)
        
        var generatedCommitment = ""

        val proofResult = withZkpService { zkp ->
            // Pre-compute the correct Poseidon commitment hash that snarkjs expects
            generatedCommitment = zkp.computePoseidonCommitment(
                vaccinationId = 1L,
                vaccineCode = validVaccineCode,
                salt = saltBigInt
            ).toString()

            try {
                // Generate proof that the user took vaccine code '1'
                zkp.generateVaccineProof(
                    vaccinationId = 1L,
                    vaccineName = validVaccineCode,
                    salt = saltStr,
                    commitment = generatedCommitment,
                    targetVaccine = validVaccineCode
                )
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        assertNotNull("Proof result should not be null for valid vaccine inputs", proofResult)

        withZkpService { zkp ->
            val isValid = zkp.verifyVaccineProof(proofResult!!, generatedCommitment, targetVaccine = validVaccineCode)
            assertTrue("A valid vaccine code must produce a verifiable proof", isValid)
        }
    }
}
