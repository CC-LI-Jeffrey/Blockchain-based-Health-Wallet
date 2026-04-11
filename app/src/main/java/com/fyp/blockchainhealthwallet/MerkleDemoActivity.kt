package com.fyp.blockchainhealthwallet

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fyp.blockchainhealthwallet.blockchain.MerkleTreeHelper
import com.fyp.blockchainhealthwallet.models.RecordSchemas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MerkleDemoActivity : AppCompatActivity() {

    private lateinit var tvLogOutput: TextView
    private lateinit var svLogOutput: ScrollView

    private val merkleHelper = MerkleTreeHelper()
    
    // Mock Record (matching merkleSecurity.test.js)
    private val fullRecord = mapOf(
        "medicineName" to "Aspirin",
        "dosage" to "100mg",
        "frequency" to "Once daily",
        "route" to "Oral",
        "startDate" to "2026-01-01",
        "endDate" to "2026-12-31",
        "purpose" to "Pain relief",
        "prescribedBy" to "Dr. Smith",
        "pharmacy" to "Local Pharmacy",
        "notes" to "After meal"
    )

    private val selectedAttributes = mapOf(
        "medicineName" to fullRecord["medicineName"]!!,
        "dosage" to fullRecord["dosage"]!!,
        "frequency" to fullRecord["frequency"]!!
    )

    private val recordType = RecordSchemas.RecordType.MEDICATION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_merkle_demo)
        supportActionBar?.title = "Live Merkle Security Tests"

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
        runOnUiThread {
            tvLogOutput.append("$msg\n")
            svLogOutput.post { svLogOutput.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    // 1. valid proof verification
    private fun runTest1() {
        CoroutineScope(Dispatchers.Default).launch {
            log("\n=============================")
            log("[START] valid proof verification")
            log("[INFO] Target: Build full tree, extract subset, and verify valid path")
            
            try {
                log("> Building Merkle Tree from ${fullRecord.size} records...")
                val tree = merkleHelper.buildMerkleTree(recordType, fullRecord)
                log("   Tree Root: ${tree.root}")
                delay(300)
                
                log("> Generating Proofs for selected subset (medicineName, dosage, frequency)...")
                val proofs = mutableMapOf<String, List<MerkleTreeHelper.ProofNode>>()
                for ((attr, value) in selectedAttributes) {
                    proofs[attr] = merkleHelper.generateProof(tree, attr, value)
                }
                
                log("> Starting Verification Simulation...")
                delay(300)
                
                var allValid = true
                for ((attr, value) in selectedAttributes) {
                    log("\n--- Verifying '$attr' ---")
                    
                    // Call the step-by-step verifier for the UI!
                    val result = merkleHelper.verifyProofWithSteps(attr, value, proofs[attr]!!, tree.root)
                    
                    for (step in result.steps) {
                        log("   [${step.stepTitle}]: ${step.detail}")
                        log("    -> ${step.hash}")
                    }
                    
                    if (result.isValid) {
                        log("   [✅] '$attr' PASSED")
                    } else {
                        log("   [❌] '$attr' FAILED")
                        allValid = false
                    }
                }
                
                if (allValid) {
                    log("\n[SUCCESS] Test Case 1: valid proof verification passed.")
                }
            } catch (e: Exception) {
                log("[ERROR] Exception: ${e.message}")
            }
        }
    }

    // 2. tampered merkle root detection
    private fun runTest2() {
        CoroutineScope(Dispatchers.Default).launch {
            log("\n=============================")
            log("[START] tampered merkle root detection")
            log("[INFO] Target: Fake Blockchain Anchor (Compromised source)")
            
            try {
                val tree = merkleHelper.buildMerkleTree(recordType, fullRecord)
                val proofDosage = merkleHelper.generateProof(tree, "dosage", selectedAttributes["dosage"]!!)
                
                val tamperedRoot = tree.root.dropLast(1) + (if (tree.root.endsWith('0')) "1" else "0")
                log("> Original Root: ${tree.root.take(20)}...")
                log("> Tampered Root: ${tamperedRoot.take(20)}...")
                
                delay(500)
                log("> Verifying 'dosage' against Tampered Root...")
                val result = merkleHelper.verifyProofWithSteps("dosage", selectedAttributes["dosage"]!!, proofDosage, tamperedRoot)
                
                log("   Computed Root: ${result.computedRoot.take(20)}...")
                log("   Expected Root: ${tamperedRoot.take(20)}...")
                
                if (!result.isValid) {
                    log("[✅ SUCCESS] Tampered root correctly rejected.")
                } else {
                    log("[❌ FAIL] Tampered root was accepted! Cryptography broke!")
                }
            } catch (e: Exception) {
                log("[ERROR] Exception: ${e.message}")
            }
        }
    }

    // 3. invalid proof rejection (wrong attribute/value pairing)
    private fun runTest3() {
        CoroutineScope(Dispatchers.Default).launch {
            log("\n=============================")
            log("[START] invalid proof rejection (wrong pair)")
            log("[INFO] Target: Supply proof of 'frequency' to verify 'dosage'")
            
            try {
                val tree = merkleHelper.buildMerkleTree(recordType, fullRecord)
                log("> Generating proof for 'frequency'...")
                val proofFreq = merkleHelper.generateProof(tree, "frequency", selectedAttributes["frequency"]!!)
                
                delay(500)
                log("> Attacker provides proof path of 'frequency' alongside value of 'dosage'...")
                
                val result = merkleHelper.verifyProof("dosage", selectedAttributes["dosage"]!!, proofFreq, tree.root)
                
                if (!result) {
                    log("[✅ SUCCESS] Mismatched proof path rejected!")
                    log("   The leaf index bindings locked the path to the correct attribute.")
                } else {
                    log("[❌ FAIL] Mismatched path accepted!")
                }
            } catch (e: Exception) {
                log("[ERROR] Exception: ${e.message}")
            }
        }
    }

    // 4. proof tampering attempts are rejected
    private fun runTest4() {
        CoroutineScope(Dispatchers.Default).launch {
            log("\n=============================")
            log("[START] proof tampering attempts are rejected")
            log("[INFO] Target: Intercept sibling hash in transit (MITM) and alter 1 bit")
            
            try {
                val tree = merkleHelper.buildMerkleTree(recordType, fullRecord)
                var proofMedName = merkleHelper.generateProof(tree, "medicineName", selectedAttributes["medicineName"]!!)
                
                val targetHash = proofMedName[0].hash
                log("> Valid Sibling Hash: ${targetHash.take(15)}...")
                
                // Tamper first sibling
                val tamperedHash = targetHash.dropLast(1) + (if (targetHash.endsWith('0')) "1" else "0")
                log("> MITM Attack -> ${tamperedHash.take(15)}...")
                
                val tamperedProof = proofMedName.toMutableList()
                tamperedProof[0] = MerkleTreeHelper.ProofNode(tamperedHash, tamperedProof[0].position)
                
                delay(500)
                log("> Checking tampered structural integrity...")
                val result = merkleHelper.verifyProof("medicineName", selectedAttributes["medicineName"]!!, tamperedProof, tree.root)
                
                if (!result) {
                    log("[✅ SUCCESS] MITM Attack Prevented!")
                    log("   Final computed root cascaded into a completely different hash.")
                } else {
                    log("[❌ FAIL] MITM Attack successful!")
                }
            } catch (e: Exception) {
                log("[ERROR] Exception: ${e.message}")
            }
        }
    }
}
