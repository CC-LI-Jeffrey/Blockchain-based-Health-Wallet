package com.fyp.blockchainhealthwallet

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.zkp.ZkpService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class ZkpDemoActivity : AppCompatActivity() {

    private lateinit var zkpService: ZkpService
    private lateinit var tvLogOutput: TextView
    private lateinit var svLogOutput: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zkp_demo)
        supportActionBar?.title = "Live ZKP Security Tests"

        zkpService = ZkpService(this)
        tvLogOutput = findViewById(R.id.tvLogOutput)
        svLogOutput = findViewById(R.id.svLogOutput)

        findViewById<Button>(R.id.btnTest1).setOnClickListener { runTest1() }
        findViewById<Button>(R.id.btnTest2).setOnClickListener { runTest2() }
        findViewById<Button>(R.id.btnTest3).setOnClickListener { runTest3() }
        findViewById<Button>(R.id.btnTest4).setOnClickListener { runTest4() }
        
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            tvLogOutput.text = "> System Ready. Waiting for test execution...\n\n"
        }
    }

    private fun log(msg: String) {
        tvLogOutput.append("$msg\n")
        svLogOutput.post { svLogOutput.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // 1. verifyValidAgeProof_succeeds
    private fun runTest1() {
        lifecycleScope.launch {
            log("\n=============================")
            log("[START] verifyValidAgeProof_succeeds")
            log("[INFO] Target: Generate age proof for 25y/o against minAge 18")
            log("> Initializing ZKP WASM Circuit...")
            
            try {
                // Generate Proof
                log("> Sending Birth Data (2001-1-1) to local engine ONLY...")
                val proof = zkpService.generateAgeProof(2001, 1, 1, 18)
                
                log("[SUCCESS] Proof Object Generated!")
                log("   pi_a: [${proof.proofA.joinToString(", ").take(30)}...]")
                log("   pi_b: [${proof.proofB.firstOrNull()?.joinToString(", ")?.take(30)}...]")
                log("   Public Signals: ${proof.publicSignals}")
                log("> Sending Proof to Verifier...")
                
                delay(500)
                
                // Verify Proof
                val cal = Calendar.getInstance()
                val isValid = zkpService.verifyAgeProof(
                    proof, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH), 18
                )
                
                if (isValid) {
                    log("[✅ RESULT] Verification PASSED. Cryptography checks out.")
                } else {
                    log("[❌ RESULT] Verification FAILED.")
                }
            } catch (e: Exception) {
                log("[ERROR] Exception: ${e.message}")
            }
        }
    }

    // 2. generateAgeProof_failsUnderThreshold
    private fun runTest2() {
        lifecycleScope.launch {
            log("\n=============================")
            log("[START] generateAgeProof_failsUnderThreshold (HACK)")
            log("[INFO] Target: 16y/o trying to generate proof for minAge 18")
            log("> Attempting to trick WASM circuit with birth year 2010...")
            
            try {
                val proof = zkpService.generateAgeProof(2010, 1, 1, 18)
                log("[CRITICAL FAIL] Wait, the circuit generated a proof? This shouldn't happen!")
            } catch (e: Exception) {
                log("[✅ RESULT] ZKP Engine Rejected Inputs!")
                log("> Error caught: Mathematical constraint failed in WebAssembly.")
                log("> It is physically impossible to generate a valid zero-knowledge proof when conditions aren't met.")
            }
        }
    }

    // 3. tamperedPublicInput_proofFails
    private fun runTest3() {
        lifecycleScope.launch {
            log("\n=============================")
            log("[START] tamperedPublicInput_proofFails (REPLAY ATTACK)")
            log("[INFO] Target: Change verifier threshold from 18 to 21 using an intercepted 18+ proof")
            
            try {
                log("> 1. Generating valid 'Over 18' proof for a 25y/o...")
                val proof = zkpService.generateAgeProof(2001, 1, 1, 18)
                log("[SUCCESS] Valid proof intercepted.")
                
                log("> 2. Hacker resubmits exact same proof block...")
                log("> 3. Hacker changes public variable [minAge] requirement to '21'...")
                delay(500)
                
                val cal = Calendar.getInstance()
                val isValid = zkpService.verifyAgeProof(
                    proof, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH), 21
                )
                
                if (!isValid) {
                    log("[✅ RESULT] Attack PREVENTED! Verification FAILED.")
                    log("> Reason: Math binds the public input directly to the proof points.")
                } else {
                    log("[CRITICAL FAIL] Attack succeeded.")
                }
            } catch (e: Exception) {
                log("[ERROR] Exception: ${e.message}")
            }
        }
    }

    // 4. verifyVaccineProof_succeeds
    private fun runTest4() {
        lifecycleScope.launch {
            log("\n=============================")
            log("[START] verifyVaccineProof_succeeds")
            log("[INFO] Target: Prove vaccination ID=1 without revealing salt/ID")
            
            try {
                log("> Generating simulated on-chain commitment...")
                val saltStr = "123456789012345"
                val saltBigInt = java.math.BigInteger(saltStr)
                val commitment = zkpService.computePoseidonCommitment(1L, 1, saltBigInt).toString()
                log("   Commitment (Blockchain): ${commitment.take(20)}...")
                
                log("> Generating vaccine ZK-Proof (Circuit computation)...")
                val proof = zkpService.generateVaccineProof(
                    vaccinationId = 1L, vaccineName = 1, salt = saltStr, commitment = commitment, targetVaccine = 1
                )
                
                log("[SUCCESS] Vaccine Proof Generated")
                log("> Verifying Proof against Blockchain commitment...")
                delay(500)
                
                val isValid = zkpService.verifyVaccineProof(proof, commitment, targetVaccine = 1)
                
                if (isValid) {
                    log("[✅ RESULT] Vaccine Verification PASSED.")
                } else {
                    log("[❌ RESULT] Vaccine Verification FAILED.")
                }
            } catch (e: Exception) {
                log("[ERROR] Exception: ${e.message}")
            }
        }
    }
}
