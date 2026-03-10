package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.fyp.blockchainhealthwallet.zkp.VaccineCodes
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VaccinePassportActivity
 *
 * Standalone "Vaccine Passport" screen. The user:
 *   1. Selects a vaccine type from a dropdown.
 *   2. Checks whether their wallet address has a verified on-chain ZK proof for that vaccine.
 *   3. If verified: sees a visual Vaccine Passport card with a shareable QR code.
 *   4. If not yet verified: sees a guided setup flow directing them to their vaccination records.
 *   5. Can also verify any arbitrary wallet address.
 *
 * This screen is read-only with respect to proofs — proof generation is handled in
 * VaccineVerifyActivity, which is launched from the specific vaccination record view.
 */
class VaccinePassportActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VaccinePassportActivity"
        private const val QR_SCAN_REQUEST = 2001
    }

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var tvWalletStatus: TextView
    private lateinit var spinnerVaccine: Spinner
    private lateinit var btnCheckProofStatus: MaterialButton

    private lateinit var cardStatus: CardView
    private lateinit var tvProofStatus: TextView
    private lateinit var tvStatusDetail: TextView

    private lateinit var cardPassport: CardView
    private lateinit var tvPassportVaccineName: TextView
    private lateinit var tvPassportAddress: TextView
    private lateinit var tvPassportVerifiedDate: TextView
    private lateinit var ivPassportQR: ImageView
    private lateinit var btnShowFullQR: MaterialButton

    private lateinit var cardSetupRequired: CardView
    private lateinit var tvSetupGuide: TextView
    private lateinit var btnGoToRecords: MaterialButton

    private lateinit var etVerifyAddress: TextInputEditText
    private lateinit var btnVerifyAddress: MaterialButton
    private lateinit var btnScanVaccineQR: MaterialButton
    private lateinit var cardVerifyResult: CardView
    private lateinit var tvVerifyResultAddress: TextView
    private lateinit var tvVerifyResult: TextView

    // ── State ─────────────────────────────────────────────────────────────────
    private var selectedVaccineCode: Int = VaccineCodes.COVID_19
    private var selectedVaccineName: String = ""
    private var passportQrBitmap: Bitmap? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vaccine_passport)

        BlockchainService.initialize(this)

        bindViews()
        setupSpinner()
        setupClickListeners()
        updateWalletStatusBanner()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Binding
    // ─────────────────────────────────────────────────────────────────────────

    private fun bindViews() {
        tvWalletStatus          = findViewById(R.id.tvWalletStatus)
        spinnerVaccine          = findViewById(R.id.spinnerVaccine)
        btnCheckProofStatus     = findViewById(R.id.btnCheckProofStatus)

        cardStatus              = findViewById(R.id.cardStatus)
        tvProofStatus           = findViewById(R.id.tvProofStatus)
        tvStatusDetail          = findViewById(R.id.tvStatusDetail)

        cardPassport            = findViewById(R.id.cardPassport)
        tvPassportVaccineName   = findViewById(R.id.tvPassportVaccineName)
        tvPassportAddress       = findViewById(R.id.tvPassportAddress)
        tvPassportVerifiedDate  = findViewById(R.id.tvPassportVerifiedDate)
        ivPassportQR            = findViewById(R.id.ivPassportQR)
        btnShowFullQR           = findViewById(R.id.btnShowFullQR)

        cardSetupRequired       = findViewById(R.id.cardSetupRequired)
        tvSetupGuide            = findViewById(R.id.tvSetupGuide)
        btnGoToRecords          = findViewById(R.id.btnGoToRecords)

        etVerifyAddress         = findViewById(R.id.etVerifyAddress)
        btnVerifyAddress        = findViewById(R.id.btnVerifyAddress)
        btnScanVaccineQR        = findViewById(R.id.btnScanVaccineQR)
        cardVerifyResult        = findViewById(R.id.cardVerifyResult)
        tvVerifyResultAddress   = findViewById(R.id.tvVerifyResultAddress)
        tvVerifyResult          = findViewById(R.id.tvVerifyResult)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            VaccineCodes.spinnerItems
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerVaccine.adapter = adapter

        spinnerVaccine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedVaccineName = VaccineCodes.spinnerItems[position]
                selectedVaccineCode = position + 1          // codes are 1-indexed
                // Hide prior results when user changes vaccine
                cardStatus.visibility = View.GONE
                cardPassport.visibility = View.GONE
                cardSetupRequired.visibility = View.GONE
                cardVerifyResult.visibility = View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupClickListeners() {
        btnCheckProofStatus.setOnClickListener { checkMyProofStatus() }
        btnShowFullQR.setOnClickListener { sharePassportQR() }
        btnGoToRecords.setOnClickListener {
            // Navigate back to main screen; user can open their vaccination records there
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
        btnVerifyAddress.setOnClickListener { verifyOtherAddress() }
        btnScanVaccineQR.setOnClickListener { openQRScanner() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Check own proof status
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkMyProofStatus() {
        val address = WalletManager.getAddress() ?: run {
            Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
            return
        }

        btnCheckProofStatus.isEnabled = false
        btnCheckProofStatus.text = "Checking..."

        cardStatus.visibility = View.VISIBLE
        tvProofStatus.text = "Checking on-chain status…"
        tvStatusDetail.visibility = View.GONE
        cardPassport.visibility = View.GONE
        cardSetupRequired.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val verified = withContext(Dispatchers.IO) {
                    BlockchainService.checkVaccinationStatus(address, selectedVaccineCode)
                }

                if (verified) {
                    onProofVerified(address)
                } else {
                    onProofNotFound()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Status check failed", e)
                tvProofStatus.text = "❌ Check Failed"
                tvStatusDetail.text = e.message ?: "Unknown error"
                tvStatusDetail.visibility = View.VISIBLE
                cardPassport.visibility = View.GONE
                cardSetupRequired.visibility = View.GONE
            } finally {
                btnCheckProofStatus.isEnabled = true
                btnCheckProofStatus.text = "Check My Proof Status"
            }
        }
    }

    private fun onProofVerified(address: String) {
        tvProofStatus.text = "✅ Proof Verified On-Chain"
        tvStatusDetail.text = "Your ZK proof for $selectedVaccineName is verified on the blockchain."
        tvStatusDetail.setTextColor(getColor(R.color.success))
        tvStatusDetail.visibility = View.VISIBLE

        // Populate passport card
        tvPassportVaccineName.text = selectedVaccineName
        tvPassportAddress.text = WalletManager.getFormattedAddress() ?: address.abbreviate()
        tvPassportVerifiedDate.text = "On-chain ✓"

        // Generate compact QR for the passport card
        val qrJson = buildPassportJson(address, selectedVaccineCode, selectedVaccineName, true)
        passportQrBitmap = generateQrBitmap(qrJson, size = 200)
        if (passportQrBitmap != null) {
            ivPassportQR.setImageBitmap(passportQrBitmap)
        }

        cardPassport.visibility = View.VISIBLE
        cardSetupRequired.visibility = View.GONE
    }

    private fun onProofNotFound() {
        tvProofStatus.text = "⚠️ No Verified Proof Found"
        tvStatusDetail.text = "No on-chain ZK proof exists for $selectedVaccineName on your wallet."
        tvStatusDetail.setTextColor(getColor(android.R.color.holo_orange_dark))
        tvStatusDetail.visibility = View.VISIBLE

        tvSetupGuide.text = "To prove your $selectedVaccineName vaccination:\n" +
                "Open the vaccination record for this vaccine and tap \"Prove Vaccination (ZKP)\"."

        cardPassport.visibility = View.GONE
        cardSetupRequired.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Verify another address
    // ─────────────────────────────────────────────────────────────────────────

    private fun verifyOtherAddress() {
        val address = etVerifyAddress.text?.toString()?.trim() ?: ""
        if (address.isEmpty() || !address.startsWith("0x") || address.length < 10) {
            Toast.makeText(this, "Enter a valid wallet address (0x...)", Toast.LENGTH_SHORT).show()
            return
        }

        btnVerifyAddress.isEnabled = false
        btnVerifyAddress.text = "Checking..."
        cardVerifyResult.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val verified = withContext(Dispatchers.IO) {
                    BlockchainService.checkVaccinationStatus(address, selectedVaccineCode)
                }

                cardVerifyResult.visibility = View.VISIBLE
                tvVerifyResultAddress.text = "Address: ${address.abbreviate()}"
                if (verified) {
                    tvVerifyResult.text = "✅ Verified — $selectedVaccineName proof found"
                    tvVerifyResult.setTextColor(getColor(R.color.success))
                } else {
                    tvVerifyResult.text = "❌ Not verified — no proof for $selectedVaccineName"
                    tvVerifyResult.setTextColor(getColor(android.R.color.holo_red_dark))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Verify address failed", e)
                cardVerifyResult.visibility = View.VISIBLE
                tvVerifyResultAddress.text = "Address: ${address.abbreviate()}"
                tvVerifyResult.text = "Error: ${e.message ?: "check failed"}"
                tvVerifyResult.setTextColor(getColor(android.R.color.holo_orange_dark))
            } finally {
                btnVerifyAddress.isEnabled = true
                btnVerifyAddress.text = "Check Vaccination Status"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QR Code
    // ─────────────────────────────────────────────────────────────────────────

    private fun sharePassportQR() {
        val address = WalletManager.getAddress() ?: return
        val qrJson = buildPassportJson(address, selectedVaccineCode, selectedVaccineName, true)
        val fullBitmap = generateQrBitmap(qrJson, size = 512) ?: run {
            Toast.makeText(this, "Could not generate QR code", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cachePath = File(cacheDir, "qr_codes")
            cachePath.mkdirs()
            val file = File(cachePath, "vaccine_passport_${selectedVaccineCode}.png")
            FileOutputStream(file).use { out ->
                fullBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val contentUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Vaccine Passport — $selectedVaccineName\nWallet: ${address.abbreviate()}\nZK Verified on-chain ✓"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Vaccine Passport"))

        } catch (e: Exception) {
            Log.e(TAG, "QR share failed", e)
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────
    // QR Scanner
    // ─────────────────────────────────────────────────────────────────────────

    private fun openQRScanner() {
        try {
            val intent = Intent(
                this,
                Class.forName("com.fyp.blockchainhealthwallet.ui.partialshare.AddressQRScannerActivity")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, QR_SCAN_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "QR scanner not available", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == QR_SCAN_REQUEST && resultCode == RESULT_OK && data != null) {
            val address = data.getStringExtra("ADDRESS")
                ?: data.getStringExtra("SCANNED_ADDRESS")
                ?: return

            // Fill in the address field
            etVerifyAddress.setText(address)

            // If the scanned QR had vaccine info, switch the spinner to that vaccine
            val vaccineCode = data.getIntExtra("VACCINE_CODE", -1)
            val vaccineName = data.getStringExtra("VACCINE_NAME") ?: ""
            if (vaccineCode > 0 && vaccineCode <= VaccineCodes.spinnerItems.size) {
                spinnerVaccine.setSelection(vaccineCode - 1)  // spinner is 0-indexed, codes are 1-indexed
                selectedVaccineCode = vaccineCode
                selectedVaccineName = vaccineName.ifEmpty { VaccineCodes.spinnerItems[vaccineCode - 1] }
            }

            // Auto-trigger verification with the scanned address
            verifyOtherAddress()
        }
    }

    private fun updateWalletStatusBanner() {
        val addr = WalletManager.getFormattedAddress()
        tvWalletStatus.text = if (addr != null) addr else "Not Connected"
    }

    private fun buildPassportJson(
        address: String,
        vaccineCode: Int,
        vaccineName: String,
        verified: Boolean
    ): String {
        return JSONObject().apply {
            put("type", "VACCINE_PASSPORT")
            put("address", address)
            put("vaccineCode", vaccineCode)
            put("vaccineName", vaccineName)
            put("verified", verified)
            put("checkedAt", System.currentTimeMillis())
            put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        }.toString()
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap? {
        return try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
            for (x in 0 until bitMatrix.width) {
                for (y in 0 until bitMatrix.height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "QR generation failed", e)
            null
        }
    }

    private fun String.abbreviate(): String = if (length > 10) "${take(6)}...${takeLast(4)}" else this
}
