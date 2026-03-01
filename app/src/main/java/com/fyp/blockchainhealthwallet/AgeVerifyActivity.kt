package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.fyp.blockchainhealthwallet.zkp.ZkpProofResult
import com.fyp.blockchainhealthwallet.zkp.ZkpService
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AgeVerifyActivity
 *
 * Allows the user to:
 *   1. See their current on-chain adult verification status.
 *   2. Generate a ZK proof (birthYear stays private, never leaves the device).
 *   3. Submit the proof to the AgeVerifyExtension smart contract.
 *   4. Check any other wallet address for adult verification status.
 */
class AgeVerifyActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AgeVerifyActivity"
        private const val QR_SCAN_REQUEST = 1001
    }

    // Views
    private lateinit var tvMyStatus: TextView
    private lateinit var tvVerifiedDate: TextView
    private lateinit var btnVerifyMyAge: MaterialButton
    private lateinit var cardGenerateProof: View
    private lateinit var etBirthYear: TextInputEditText
    private lateinit var btnGenerateProof: MaterialButton
    private lateinit var layoutProgress: View
    private lateinit var tvProgressStatus: TextView
    private lateinit var cardProofDetails: View
    private lateinit var tvDetailYear: TextView
    private lateinit var btnSubmitProof: MaterialButton
    private lateinit var etCheckAddress: TextInputEditText
    private lateinit var btnScanAddress: MaterialButton
    private lateinit var btnCheckStatus: MaterialButton
    private lateinit var cardCheckResult: View
    private lateinit var tvCheckedAddress: TextView
    private lateinit var tvCheckResult: TextView

    // State
    private var currentProof: ZkpProofResult? = null
    private lateinit var zkpService: ZkpService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_age_verify)

        zkpService = ZkpService(this)

        bindViews()
        setupClickListeners()
        loadMyStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        zkpService.destroy()
    }

    // ─────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────

    private fun bindViews() {
        tvMyStatus         = findViewById(R.id.tvMyStatus)
        tvVerifiedDate     = findViewById(R.id.tvVerifiedDate)
        btnVerifyMyAge     = findViewById(R.id.btnVerifyMyAge)
        cardGenerateProof  = findViewById(R.id.cardGenerateProof)
        etBirthYear        = findViewById(R.id.etBirthYear)
        btnGenerateProof   = findViewById(R.id.btnGenerateProof)
        layoutProgress     = findViewById(R.id.layoutProgress)
        tvProgressStatus   = findViewById(R.id.tvProgressStatus)
        cardProofDetails   = findViewById(R.id.cardProofDetails)
        tvDetailYear       = findViewById(R.id.tvDetailYear)
        btnSubmitProof     = findViewById(R.id.btnSubmitProof)
        etCheckAddress     = findViewById(R.id.etCheckAddress)
        btnScanAddress     = findViewById(R.id.btnScanAddress)
        btnCheckStatus     = findViewById(R.id.btnCheckStatus)
        cardCheckResult    = findViewById(R.id.cardCheckResult)
        tvCheckedAddress   = findViewById(R.id.tvCheckedAddress)
        tvCheckResult      = findViewById(R.id.tvCheckResult)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        btnVerifyMyAge.setOnClickListener {
            if (!WalletManager.isConnected()) {
                Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            cardGenerateProof.visibility = View.VISIBLE
            cardProofDetails.visibility = View.GONE
            currentProof = null
            etBirthYear.requestFocus()
        }

        btnGenerateProof.setOnClickListener { onGenerateProofClicked() }

        btnSubmitProof.setOnClickListener { onSubmitProofClicked() }

        btnScanAddress.setOnClickListener {
            try {
                val intent = Intent(this,
                    Class.forName("com.fyp.blockchainhealthwallet.ui.partialshare.AddressQRScannerActivity"))
                startActivityForResult(intent, QR_SCAN_REQUEST)
            } catch (e: Exception) {
                Toast.makeText(this, "QR scanner not available", Toast.LENGTH_SHORT).show()
            }
        }

        btnCheckStatus.setOnClickListener { onCheckStatusClicked() }
    }

    // ─────────────────────────────────────────────
    // My Status
    // ─────────────────────────────────────────────

    private fun loadMyStatus() {
        val address = WalletManager.getAddress()
        if (address == null) {
            tvMyStatus.text = "Connect your wallet to check status"
            btnVerifyMyAge.isEnabled = false
            return
        }

        tvMyStatus.text = "Checking..."

        lifecycleScope.launch {
            try {
                val isVerified = withContext(Dispatchers.IO) {
                    BlockchainService.checkAdultStatus(address)
                }

                if (isVerified) {
                    val timestamp = withContext(Dispatchers.IO) {
                        BlockchainService.getVerificationTimestamp(address)
                    }

                    tvMyStatus.text = "🟢 Age Verified"
                    tvMyStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                    btnVerifyMyAge.isEnabled = false
                    btnVerifyMyAge.text = "Already Verified"

                    if (timestamp > java.math.BigInteger.ZERO) {
                        val date = Date(timestamp.toLong() * 1000)
                        tvVerifiedDate.text = "Verified on: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)}"
                        tvVerifiedDate.visibility = View.VISIBLE
                    }
                } else {
                    tvMyStatus.text = "🔴 Not Verified"
                    btnVerifyMyAge.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading status", e)
                tvMyStatus.text = "Unable to load status"
                btnVerifyMyAge.isEnabled = true
            }
        }
    }

    // ─────────────────────────────────────────────
    // Generate Proof
    // ─────────────────────────────────────────────

    private fun onGenerateProofClicked() {
        val birthYearStr = etBirthYear.text?.toString()?.trim()
        if (birthYearStr.isNullOrEmpty()) {
            etBirthYear.error = "Enter your birth year"
            return
        }

        val birthYear = birthYearStr.toIntOrNull()
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

        if (birthYear == null || birthYear < 1900 || birthYear >= currentYear) {
            etBirthYear.error = "Enter a valid birth year"
            return
        }

        if (currentYear - birthYear < 18) {
            Toast.makeText(this, "You must be 18 or older", Toast.LENGTH_SHORT).show()
            return
        }

        setGeneratingState(true)

        lifecycleScope.launch {
            try {
                val proof = withContext(Dispatchers.Main) {
                    // ZkpService requires Main thread (WebView)
                    zkpService.generateAgeProof(birthYear)
                }

                currentProof = proof
                showProofReady(proof)

            } catch (e: Exception) {
                Log.e(TAG, "Proof generation failed", e)
                Toast.makeText(
                    this@AgeVerifyActivity,
                    "Proof generation failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                setGeneratingState(false)
            }
        }
    }

    private fun setGeneratingState(isGenerating: Boolean) {
        btnGenerateProof.isEnabled = !isGenerating
        etBirthYear.isEnabled = !isGenerating
        layoutProgress.visibility = if (isGenerating) View.VISIBLE else View.GONE

        if (isGenerating) {
            tvProgressStatus.text = "Computing zero-knowledge proof..."
        }
    }

    private fun showProofReady(proof: ZkpProofResult) {
        setGeneratingState(false)

        tvDetailYear.text = proof.currentYear.toString()
        cardProofDetails.visibility = View.VISIBLE

        Toast.makeText(this, "Proof generated successfully", Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────
    // Submit Proof
    // ─────────────────────────────────────────────

    private fun onSubmitProofClicked() {
        val proof = currentProof ?: run {
            Toast.makeText(this, "Generate a proof first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!WalletManager.isConnected()) {
            Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmitProof.isEnabled = false
        btnSubmitProof.text = "Submitting..."

        lifecycleScope.launch {
            try {
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.submitAgeProof(
                        proofA       = proof.toA(),
                        proofB       = proof.toB(),
                        proofC       = proof.toC(),
                        publicInputs = proof.toPublicInputs()
                    )
                }

                Log.d(TAG, "Age proof submitted: $txHash")

                Toast.makeText(
                    this@AgeVerifyActivity,
                    "Submitted! Tx: ${txHash.take(18)}...",
                    Toast.LENGTH_LONG
                ).show()

                // Refresh status after a brief delay for the tx to confirm
                btnSubmitProof.text = "Submitted ✓"
                cardGenerateProof.visibility = View.GONE
                cardProofDetails.visibility = View.GONE

                // Reload status
                loadMyStatus()

            } catch (e: Exception) {
                Log.e(TAG, "Proof submission failed", e)
                Toast.makeText(
                    this@AgeVerifyActivity,
                    "Submission failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                btnSubmitProof.isEnabled = true
                btnSubmitProof.text = "Submit to Blockchain"
            }
        }
    }

    // ─────────────────────────────────────────────
    // Check Another User
    // ─────────────────────────────────────────────

    private fun onCheckStatusClicked() {
        val address = etCheckAddress.text?.toString()?.trim()
        if (address.isNullOrEmpty() || !address.startsWith("0x") || address.length != 42) {
            etCheckAddress.error = "Enter a valid wallet address (0x...)"
            return
        }

        btnCheckStatus.isEnabled = false
        btnCheckStatus.text = "Checking..."
        cardCheckResult.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val isVerified = withContext(Dispatchers.IO) {
                    BlockchainService.checkAdultStatus(address)
                }

                val truncated = "${address.take(10)}...${address.takeLast(6)}"
                tvCheckedAddress.text = truncated

                if (isVerified) {
                    tvCheckResult.text = "🟢 Age Verified"
                    tvCheckResult.setTextColor(getColor(android.R.color.holo_green_dark))
                } else {
                    tvCheckResult.text = "🔴 Not Verified"
                    tvCheckResult.setTextColor(getColor(android.R.color.holo_red_dark))
                }

                cardCheckResult.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e(TAG, "Error checking status", e)
                Toast.makeText(this@AgeVerifyActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnCheckStatus.isEnabled = true
                btnCheckStatus.text = "Check Status"
            }
        }
    }

    // ─────────────────────────────────────────────
    // QR Scan result
    // ─────────────────────────────────────────────

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == QR_SCAN_REQUEST && resultCode == RESULT_OK) {
            val scannedAddress = data?.getStringExtra("SCANNED_ADDRESS")
                ?: data?.getStringExtra("address")
                ?: return
            etCheckAddress.setText(scannedAddress)
        }
    }
}
