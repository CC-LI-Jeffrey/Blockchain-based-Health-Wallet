package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.db.ProofRecord
import com.fyp.blockchainhealthwallet.db.VaccineProofRepository
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.fyp.blockchainhealthwallet.zkp.VaccineCodes
import com.fyp.blockchainhealthwallet.zkp.VaccineZkpProofResult
import com.fyp.blockchainhealthwallet.zkp.ZkpService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.SecureRandom

/**
 * VaccineVerifyActivity (Option A - Hybrid)
 *
 * Proof generation and verification workflow:
 *   1. See the vaccination proof status (local or on-chain).
 *   2. Register a Poseidon commitment one-time (optional, per vaccination record).
 *   3. Generate a ZK proof that proves vaccination without revealing the record.
 *   4. **SAVE THE PROOF LOCALLY** (primary verification for Option A).
 *   5. Optionally submit to blockchain for anchoring (on-demand).
 *   6. Redirect to VaccinePassportActivity to view the vaccine passport.
 *
 * Launched from EditVaccineRecordActivity or ViewVaccinationActivity with:
 *   EXTRA_VACCINATION_ID: Long — blockchain ID of the vaccination record
 *   EXTRA_VACCINE_NAME:   String — display name (e.g. "COVID-19 Vaccine")
 */
class VaccineVerifyActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VaccineVerifyActivity"
        const val EXTRA_VACCINATION_ID = "vaccination_id"
        const val EXTRA_VACCINE_NAME   = "vaccine_name"
    }

    // ── Views ────────────────────────────────────────────────
    private lateinit var tvVaccineName:     TextView
    private lateinit var tvVaccineCode:     TextView
    private lateinit var tvVaccinationId:   TextView
    private lateinit var tvOnChainStatus:   TextView
    private lateinit var tvOnChainDate:     TextView
    private lateinit var cardProve:         View
    private lateinit var layoutProgress:    View
    private lateinit var tvProgressStatus:  TextView
    private lateinit var btnGenerateProof:  MaterialButton
    private lateinit var layoutProofReady:  View
    private lateinit var tvProofDetails:    TextView
    private lateinit var btnSubmitProof:    MaterialButton
    private lateinit var btnClearZkpCache:  MaterialButton
    

    // ── State ────────────────────────────────────────────────
    private var vaccinationId: Long = 0L
    private var vaccineNameDisplay: String = ""
    private var vaccineCode: Int = VaccineCodes.OTHER
    private var currentProof: VaccineZkpProofResult? = null
    private lateinit var zkpService: ZkpService
    private lateinit var repository: VaccineProofRepository

    // ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vaccine_verify)

        zkpService = ZkpService(this)
        repository = VaccineProofRepository(this)
        BlockchainService.initialize(this)

        bindViews()
        loadIntentData()
        setupClickListeners()
        loadOnChainStatus()
        determineSetupState()
    }

    override fun onDestroy() {
        super.onDestroy()
        zkpService.destroy()
    }

    // ─────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────

    private fun bindViews() {
        tvVaccineName       = findViewById(R.id.tvVaccineName)
        tvVaccineCode       = findViewById(R.id.tvVaccineCode)
        tvVaccinationId     = findViewById(R.id.tvVaccinationId)
        tvOnChainStatus     = findViewById(R.id.tvOnChainStatus)
        tvOnChainDate       = findViewById(R.id.tvOnChainDate)
        cardProve           = findViewById(R.id.cardProve)
        layoutProgress      = findViewById(R.id.layoutProgress)
        tvProgressStatus    = findViewById(R.id.tvProgressStatus)
        btnGenerateProof    = findViewById(R.id.btnGenerateProof)
        layoutProofReady    = findViewById(R.id.layoutProofReady)
        tvProofDetails      = findViewById(R.id.tvProofDetails)
        btnSubmitProof      = findViewById(R.id.btnSubmitProof)
        btnClearZkpCache     = findViewById(R.id.btnClearZkpCache)
        

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun loadIntentData() {
        vaccinationId       = intent.getLongExtra(EXTRA_VACCINATION_ID, 0L)
        vaccineNameDisplay  = intent.getStringExtra(EXTRA_VACCINE_NAME) ?: ""
        vaccineCode         = VaccineCodes.fromName(vaccineNameDisplay)

        tvVaccineName.text   = vaccineNameDisplay.ifEmpty { "Unknown Vaccine" }
        tvVaccineCode.text   = "Vaccine code: $vaccineCode (${VaccineCodes.displayNames[vaccineCode]})"
        tvVaccinationId.text = "Record ID: $vaccinationId"

        Log.d(TAG, "vaccinationId=$vaccinationId  name=$vaccineNameDisplay  code=$vaccineCode")
    }

    private fun setupClickListeners() {
        btnGenerateProof.setOnClickListener { onGenerateProofClicked() }
        btnSubmitProof.setOnClickListener { onSubmitProofClicked() }
        btnClearZkpCache.setOnClickListener { onClearZkpCacheClicked() }
        
    }

    // ─────────────────────────────────────────────────────────
    // On-chain status
    // ─────────────────────────────────────────────────────────

    private fun loadOnChainStatus() {
        val address = WalletManager.getAddress() ?: run {
            tvOnChainStatus.text = "Connect your wallet to check status"
            return
        }

        tvOnChainStatus.text = "Checking..."

        lifecycleScope.launch {
            try {
                val verified = withContext(Dispatchers.IO) {
                    BlockchainService.checkVaccinationStatus(address, vaccineCode)
                }

                if (verified) {
                    tvOnChainStatus.text = "🟢 Vaccination Proven On-Chain"
                    tvOnChainStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                    tvOnChainDate.visibility = View.VISIBLE
                    tvOnChainDate.text = "Proof submitted — vaccine code $vaccineCode (${VaccineCodes.displayNames[vaccineCode]})"
                } else {
                    tvOnChainStatus.text = "🔴 Proof Not Yet Submitted"
                    tvOnChainStatus.setTextColor(getColor(android.R.color.holo_red_light))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading on-chain status", e)
                tvOnChainStatus.text = "Unable to load (contract not deployed?)"
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Determine whether setup is needed
    // ─────────────────────────────────────────────────────────

    private fun determineSetupState() {
        // One-step flow: always show proof card directly.
        cardProve.visibility = View.VISIBLE
        layoutProofReady.visibility = View.GONE
    }

    // ─────────────────────────────────────────────────────────
    // Generate Proof
    // ─────────────────────────────────────────────────────────

    private fun onGenerateProofClicked() {
        if (!WalletManager.isConnected()) {
            Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
            return
        }

        currentProof = null
        layoutProofReady.visibility = View.GONE
        btnSubmitProof.isEnabled = true
        btnSubmitProof.text = "Verify & Anchor"
        setGeneratingState(true)

        lifecycleScope.launch {
            try {
                val saltBytes = ByteArray(31)
                SecureRandom().nextBytes(saltBytes)
                val saltBigInt = BigInteger(1, saltBytes)

                val commitment = withContext(Dispatchers.Main) {
                    zkpService.computePoseidonCommitment(vaccinationId, vaccineCode, saltBigInt)
                }.toString()

                val proof = withContext(Dispatchers.Main) {
                    zkpService.generateVaccineProof(
                        vaccinationId  = vaccinationId,
                        vaccineName    = vaccineCode,
                        salt           = saltBigInt.toString(),
                        commitment     = commitment,
                        targetVaccine  = vaccineCode
                    )
                }

                currentProof = proof
                setGeneratingState(false)
                showProofReady(proof)

            } catch (e: Exception) {
                Log.e(TAG, "Vaccine proof generation failed", e)
                setGeneratingState(false)

                AlertDialog.Builder(this@VaccineVerifyActivity)
                    .setTitle("Proof Generation Failed")
                    .setMessage(e.message ?: "Unknown error — check that the circuit WASM/zkey files are in assets/zkp/")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun setGeneratingState(isGenerating: Boolean) {
        btnGenerateProof.isEnabled = !isGenerating
        layoutProgress.visibility  = if (isGenerating) View.VISIBLE else View.GONE
        if (isGenerating) tvProgressStatus.text = "Computing zero-knowledge vaccination proof (~10-30s)..."
    }

    private fun showProofReady(proof: VaccineZkpProofResult) {
        layoutProofReady.visibility = View.VISIBLE
        btnSubmitProof.isEnabled = true
        btnSubmitProof.text = "Verify & Anchor"
        tvProofDetails.text =
            "Vaccine code: ${proof.targetVaccine} (${VaccineCodes.displayNames[proof.targetVaccine]})\n" +
            "Commitment: ${proof.commitment.toString(16).take(20)}..."
    }

    // ─────────────────────────────────────────────────────────
    // Verify & Anchor (local verification + blockchain anchor)
    // ─────────────────────────────────────────────────────────

    private fun onSubmitProofClicked() {
        val proof = currentProof ?: run {
            Toast.makeText(this, "Please generate a proof first", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmitProof.isEnabled = false
        btnSubmitProof.text = "Saving locally..."

        lifecycleScope.launch {
            try {
                val address = WalletManager.getAddress() ?: run {
                    Toast.makeText(this@VaccineVerifyActivity, "Wallet not connected", Toast.LENGTH_SHORT).show()
                    btnSubmitProof.isEnabled = true
                    btnSubmitProof.text = "Verify & Anchor"
                    return@launch
                }

                // STEP 1: Save proof to local database (PRIMARY for Option A)
                tvProgressStatus.text = "Saving proof locally..."
                layoutProgress.visibility = View.VISIBLE

                withContext(Dispatchers.IO) {
                    val proofRecord = ProofRecord(
                        type = "VACCINE_PASSPORT",
                        proofHash = proof.proofHash(),
                        publicInputs = "[${proof.targetVaccine}]",
                        minValue = vaccineCode.toLong(),
                        timestamp = System.currentTimeMillis(),
                        issuerAddress = address,
                        commitment = proof.commitment.toString(),
                        isVerified = true,
                        verifiedAt = System.currentTimeMillis()
                    )
                    repository.saveVaccineProof(proofRecord)
                }

                layoutProgress.visibility = View.GONE

                Log.d(TAG, "✓ Proof saved locally for $address / code $vaccineCode")

                // STEP 2: Automatically submit to blockchain
                submitToBlockchain(proof)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save proof locally", e)
                layoutProgress.visibility = View.GONE
                btnSubmitProof.isEnabled = true
                btnSubmitProof.text = "Verify & Anchor"

                AlertDialog.Builder(this@VaccineVerifyActivity)
                    .setTitle("Save Failed")
                    .setMessage("Could not save proof: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun submitToBlockchain(proof: VaccineZkpProofResult) {
        btnSubmitProof.text = "Submitting to blockchain..."
        layoutProgress.visibility = View.VISIBLE
        tvProgressStatus.text = "Registering on-chain..."

        lifecycleScope.launch {
            try {
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.submitVaccineProof(
                        vaccineCode = proof.targetVaccine,
                        commitment = proof.commitment,
                        proofHashHex = proof.proofHash()
                    )
                }

                layoutProgress.visibility = View.GONE
                Log.d(TAG, "Vaccine proof also submitted to blockchain! tx=$txHash")

                AlertDialog.Builder(this@VaccineVerifyActivity)
                    .setTitle("✅ Complete")
                    .setMessage(
                        "Proof saved locally & anchored on-chain.\n\n" +
                        "Transaction: ${txHash.take(20)}...\n\n" +
                        "Your vaccination for ${VaccineCodes.displayNames[proof.targetVaccine]} is now proven locally with blockchain anchor."
                    )
                    .setPositiveButton("View Passport") { _, _ ->
                        navigateToPassport()
                    }
                    .setCancelable(false)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Blockchain submission failed (but proof is saved locally)", e)
                layoutProgress.visibility = View.GONE
                btnSubmitProof.isEnabled = true
                btnSubmitProof.text = "Verify & Anchor"

                AlertDialog.Builder(this@VaccineVerifyActivity)
                    .setTitle("⚠ Blockchain Submission Failed")
                    .setMessage(
                        "Proof is saved locally, but blockchain submission failed:\n\n${e.message}\n\n" +
                        "No worries — your proof still works locally. Go to your Vaccine Passport to share it."
                    )
                    .setPositiveButton("Go to Passport") { _, _ ->
                        navigateToPassport()
                    }
                    .setNegativeButton("Stay and Retry", null)
                    .show()
            }
        }
    }

    private fun navigateToPassport() {
        try {
            val intent = Intent(this, VaccinePassportActivity::class.java)
            startActivity(intent)
            finish()  // Remove VaccineVerifyActivity from stack
        } catch (e: Exception) {
            Log.e(TAG, "Could not navigate to VaccinePassportActivity", e)
            Toast.makeText(this, "Could not navigate to Vaccine Passport", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────
    // Check Another Address
    // ─────────────────────────────────────────────────────────

    

    // ─────────────────────────────────────────────────────────
    // Local cache cleanup helpers
    // ─────────────────────────────────────────────────────────

    private fun onClearZkpCacheClicked() {
        AlertDialog.Builder(this)
            .setTitle("Clear Local ZKP Cache")
            .setMessage("This removes all locally stored vaccine ZKP cache and local vaccine proofs on this device.")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.deleteAllVaccineProofs()
                    }
                    currentProof = null
                    layoutProofReady.visibility = View.GONE
                    cardProve.visibility = View.VISIBLE
                    btnGenerateProof.isEnabled = true
                    btnSubmitProof.isEnabled = true
                    btnSubmitProof.text = "Verify & Anchor"
                    Toast.makeText(this@VaccineVerifyActivity, "Local cache and vaccine proofs cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
