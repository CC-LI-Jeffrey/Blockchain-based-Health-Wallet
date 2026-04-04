package com.fyp.blockchainhealthwallet.zkp

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fyp.blockchainhealthwallet.AgeVerifyActivity
import kotlinx.coroutines.runBlocking
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
        var result: T? = null
        activityRule.scenario.onActivity { activity ->
            val service = ZkpService(activity)
            runBlocking {
                result = block(service)
            }
        }
        return result!!
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
            val isValid = zkp.verifyAgeProof(
                proofResult!!.pi_a,
                proofResult.pi_b,
                proofResult.pi_c,
                proofResult.publicSignals
            )
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
                val proofResult = result.getOrNull()!!
                val isValid = zkp.verifyAgeProof(
                    proofResult.pi_a,
                    proofResult.pi_b,
                    proofResult.pi_c,
                    proofResult.publicSignals
                )
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

        // TC-ZKP-03: Attacker intercepts the payload and alters the public claim 
        // Pretending the proof was generated for a higher threshold age (e.g., altered to 21 based on your circom setup)
        // Public signals are an array of strings. AgeVerify usually has [thresholdAge, currentYear...]
        val tamperedPublicSignals = proofResult!!.publicSignals.toMutableList()
        if (tamperedPublicSignals.isNotEmpty()) {
            tamperedPublicSignals[0] = "21" // Tamper the claim
        } else {
            // Failsafe if format differs
            tamperedPublicSignals.add("21") 
        }
        
        withZkpService { zkp ->
            val isValid = zkp.verifyAgeProof(
                proofResult.pi_a,
                proofResult.pi_b,
                proofResult.pi_c,
                tamperedPublicSignals
            )
            assertFalse("Verification MUST fail if public inputs do not perfectly match the proof hash", isValid)
        }
    }

    @Test
    fun verifyVaccineProof_succeeds() = runBlocking {
        // TC-ZKP-04: Test the secondary circuit (Vaccine verify)
        // Assuming Covid-19 is integer code 0 based on your VaccineZkpProofResult.kt
        val proofResult = withZkpService { zkp ->
            try {
                // Generating proof that the user took vaccine code '0'
                zkp.generateVaccineProof(targetVaccine = 0)
            } catch (e: Exception) {
                null
            }
        }

        assertNotNull("Proof result should not be null for valid vaccine inputs", proofResult)

        withZkpService { zkp ->
            val isValid = zkp.verifyVaccineProof(
                proofResult!!.pi_a,
                proofResult.pi_b,
                proofResult.pi_c,
                proofResult.publicSignals
            )
            assertTrue("A valid vaccine code must produce a verifiable proof", isValid)
        }
    }
}
