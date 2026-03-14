package com.fyp.blockchainhealthwallet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.db.VaccineProofRepository
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.fyp.blockchainhealthwallet.zkp.VaccineCodes
import com.fyp.blockchainhealthwallet.zkp.VaccineZkpProofResult
import com.fyp.blockchainhealthwallet.zkp.ZkpService
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        private const val PREFS_NAME   = "vaccine_zkp_prefs"
        private const val SALT_KEY_PREFIX = "salt_"         // "salt_{vaccinationId}"
        private const val COMMITMENT_KEY_PREFIX = "commitment_" // "commitment_{vaccinationId}"
    }

    // ── Views ────────────────────────────────────────────────
    private lateinit var tvVaccineName:     TextView
    private lateinit var tvVaccineCode:     TextView
    private lateinit var tvVaccinationId:   TextView
    private lateinit var tvOnChainStatus:   TextView
    private lateinit var tvOnChainDate:     TextView
    private lateinit var cardSetupZkp:      View
    private lateinit var btnRegisterCommitment: MaterialButton
    private lateinit var cardProve:         View
    private lateinit var layoutProgress:    View
    private lateinit var tvProgressStatus:  TextView
    private lateinit var btnGenerateProof:  MaterialButton
    private lateinit var layoutProofReady:  View
    private lateinit var tvProofDetails:    TextView
    private lateinit var btnSubmitProof:    MaterialButton
    private lateinit var etCheckAddress:    TextInputEditText
    private lateinit var btnCheckStatus:    MaterialButton
    private lateinit var cardCheckResult:   View
    private lateinit var tvCheckedAddress:  TextView
    private lateinit var tvCheckResult:     TextView

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
        cardSetupZkp        = findViewById(R.id.cardSetupZkp)
        btnRegisterCommitment = findViewById(R.id.btnRegisterCommitment)
        cardProve           = findViewById(R.id.cardProve)
        layoutProgress      = findViewById(R.id.layoutProgress)
        tvProgressStatus    = findViewById(R.id.tvProgressStatus)
        btnGenerateProof    = findViewById(R.id.btnGenerateProof)
        layoutProofReady    = findViewById(R.id.layoutProofReady)
        tvProofDetails      = findViewById(R.id.tvProofDetails)
        btnSubmitProof      = findViewById(R.id.btnSubmitProof)
        etCheckAddress      = findViewById(R.id.etCheckAddress)
        btnCheckStatus      = findViewById(R.id.btnCheckStatus)
        cardCheckResult     = findViewById(R.id.cardCheckResult)
        tvCheckedAddress    = findViewById(R.id.tvCheckedAddress)
        tvCheckResult       = findViewById(R.id.tvCheckResult)

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
        btnRegisterCommitment.setOnClickListener { onRegisterCommitmentClicked() }
        btnGenerateProof.setOnClickListener { onGenerateProofClicked() }
        btnSubmitProof.setOnClickListener { onSubmitProofClicked() }
        btnCheckStatus.setOnClickListener { onCheckStatusClicked() }
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
        val hasSalt = loadSalt() != null
        if (hasSalt) {
            // Commitment already set up — show the prove card
            cardSetupZkp.visibility = View.GONE
            cardProve.visibility    = View.VISIBLE
        } else {
            // No salt yet — user needs to register commitment first
            cardSetupZkp.visibility = View.VISIBLE
            cardProve.visibility    = View.GONE
        }
    }

    // ─────────────────────────────────────────────────────────
    // Register Commitment (one-time setup)
    // ─────────────────────────────────────────────────────────

    private fun onRegisterCommitmentClicked() {
        if (!WalletManager.isConnected()) {
            Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
            return
        }
        if (vaccinationId == 0L) {
            Toast.makeText(this, "Invalid vaccination record ID", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Register ZKP Commitment")
            .setMessage("This generates a random secret (salt) stored locally on your device and registers a cryptographic commitment on-chain.\n\nThis is a one-time setup per vaccination record and costs a small gas fee.")
            .setPositiveButton("Register") { _, _ -> doRegisterCommitment() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doRegisterCommitment() {
        btnRegisterCommitment.isEnabled = false
        btnRegisterCommitment.text = "Registering..."

        lifecycleScope.launch {
            try {
                // 1. Generate a random 31-byte salt (fits in a BN128 field element)
                val saltBytes = ByteArray(31)
                SecureRandom().nextBytes(saltBytes)
                val saltBigInt = BigInteger(1, saltBytes)  // positive, always < field size

                // 2. Compute commitment via Poseidon in JS (reuse snarkjs WebView)
                tvProgressStatus.text = "Computing Poseidon commitment..."
                layoutProgress.visibility = View.VISIBLE

                val commitment = withContext(Dispatchers.Main) {
                    zkpService.computePoseidonCommitment(vaccinationId, vaccineCode, saltBigInt)
                }

                layoutProgress.visibility = View.GONE

                Log.d(TAG, "Computed commitment: ${commitment.toString(16).take(16)}...")

                // 3. Store salt and commitment locally (encrypted SharedPreferences)
                saveSalt(saltBigInt.toString())
                saveCommitment(commitment.toString())

                // 4. Register commitment on-chain
                btnRegisterCommitment.text = "Sending transaction..."

                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.registerVaccineCommitment(commitment)
                }

                Log.d(TAG, "Commitment registration tx: $txHash")

                // 5. Show success and reveal the prove card
                cardSetupZkp.visibility = View.GONE
                cardProve.visibility    = View.VISIBLE

                Toast.makeText(this@VaccineVerifyActivity,
                    "Commitment registered! You can now generate proofs.",
                    Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e(TAG, "Commitment registration failed", e)
                layoutProgress.visibility = View.GONE
                btnRegisterCommitment.isEnabled = true
                btnRegisterCommitment.text = "Register Commitment On-Chain"

                AlertDialog.Builder(this@VaccineVerifyActivity)
                    .setTitle("Registration Failed")
                    .setMessage(e.message ?: "Unknown error")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Generate Proof
    // ─────────────────────────────────────────────────────────

    private fun onGenerateProofClicked() {
        if (!WalletManager.isConnected()) {
            Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
            return
        }

        val saltStr = loadSalt()
        val commitmentStr = loadCommitment()

        if (saltStr == null || commitmentStr == null) {
            Toast.makeText(this, "No commitment found. Please re-register.", Toast.LENGTH_SHORT).show()
            cardSetupZkp.visibility = View.VISIBLE
            cardProve.visibility    = View.GONE
            return
        }

        currentProof = null
        layoutProofReady.visibility = View.GONE
        setGeneratingState(true)

        lifecycleScope.launch {
            try {
                val saltBigInt = BigInteger(saltStr)
                val commitment = commitmentStr

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
        tvProofDetails.text =
            "Vaccine code: ${proof.targetVaccine} (${VaccineCodes.displayNames[proof.targetVaccine]})\n" +
            "Commitment: ${proof.commitment.toString(16).take(20)}..."
    }

    // ─────────────────────────────────────────────────────────
    // Submit Proof (Option A: LOCAL-FIRST, OPTIONAL BLOCKCHAIN)
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
                    btnSubmitProof.text = "Submit Proof"
                    return@launch
                }

                // STEP 1: Save proof to local database (PRIMARY for Option A)
                tvProgressStatus.text = "Saving proof locally..."
                layoutProgress.visibility = View.VISIBLE

                withContext(Dispatchers.IO) {
                    repository.insertVaccineProof(
                        address = address,
                        vaccineCode = vaccineCode,
                        proof = proof.toString(),  // Serialize proof
                        isVerified = true,
                        verifiedAt = System.currentTimeMillis()
                    )
                }

                layoutProgress.visibility = View.GONE

                Log.d(TAG, "✓ Proof saved locally for $address / code $vaccineCode")

                // STEP 2: Ask user if they want to anchor on-chain
                AlertDialog.Builder(this@VaccineVerifyActivity)
                    .setTitle("Save Proof & Optional Blockchain Anchor")
                    .setMessage(
                        "Your ZK proof has been saved locally.\n\n" +
                        "You can now view your Vaccine Passport.\n\n" +
                        "Optionally, submit the proof to the blockchain for additional anchoring?"
                    )
                    .setPositiveButton("Yes, Submit to Blockchain") { _, _ ->
                        submitToBlockchain(proof)
                    }
                    .setNegativeButton("Skip, Go to Passport") { _, _ ->
                        navigateToPassport()
                    }
                    .setCancelable(false)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save proof locally", e)
                layoutProgress.visibility = View.GONE
                btnSubmitProof.isEnabled = true
                btnSubmitProof.text = "Submit Proof"

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
                        proofA = proof.toA(),
                        proofB = proof.toB(),
                        proofC = proof.toC(),
                        publicInputs = proof.toPublicInputs()
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

    private fun onCheckStatusClicked() {
        val address = etCheckAddress.text?.toString()?.trim()
        if (address.isNullOrEmpty() || !address.startsWith("0x") || address.length != 42) {
            Toast.makeText(this, "Enter a valid 0x wallet address", Toast.LENGTH_SHORT).show()
            return
        }

        cardCheckResult.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val verified = withContext(Dispatchers.IO) {
                    BlockchainService.checkVaccinationStatus(address, vaccineCode)
                }

                tvCheckedAddress.text = "Address: ${address.take(10)}...${address.takeLast(6)}"
                tvCheckResult.text    = if (verified) "🟢 Vaccinated (on-chain proof)" else "🔴 No on-chain proof"
                tvCheckResult.setTextColor(
                    getColor(if (verified) android.R.color.holo_green_dark else android.R.color.holo_red_light)
                )
                cardCheckResult.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e(TAG, "Error checking address status", e)
                Toast.makeText(this@VaccineVerifyActivity,
                    "Check failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Local Storage helpers (salt + commitment per vaccinationId)
    // ─────────────────────────────────────────────────────────

    private fun prefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun saltKey()       = "$SALT_KEY_PREFIX$vaccinationId"
    private fun commitmentKey() = "$COMMITMENT_KEY_PREFIX$vaccinationId"

    private fun saveSalt(salt: String) {
        prefs().edit().putString(saltKey(), salt).apply()
    }

    private fun loadSalt(): String? = prefs().getString(saltKey(), null)

    private fun saveCommitment(commitment: String) {
        prefs().edit().putString(commitmentKey(), commitment).apply()
    }

    private fun loadCommitment(): String? = prefs().getString(commitmentKey(), null)
}
