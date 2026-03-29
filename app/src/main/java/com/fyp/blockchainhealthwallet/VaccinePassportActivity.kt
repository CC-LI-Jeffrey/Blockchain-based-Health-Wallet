package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.db.ProofRecord
import com.fyp.blockchainhealthwallet.db.VaccineProofRepository
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
 * VaccinePassportActivity (Option A - Hybrid)
 *
 * Standalone "Vaccine Passport" screen. The user:
 *   1. Selects a vaccine type from a dropdown.
 *   2. Checks whether they have a locally verified ZK proof for that vaccine.
 *   3. If verified locally: sees a visual Vaccine Passport card with a shareable QR code.
 *   4. If not yet verified: sees a guided setup flow directing them to their vaccination records.
 *   5. Can optionally register the commitment on-chain (blockchain anchor).
 *   6. Can also verify any arbitrary user's vaccine QR code locally.
 *
 * This screen is read-only with respect to proofs — proof generation/verification is handled in
 * VaccineVerifyActivity or when editing vaccination records.
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
    private lateinit var btnClearVaccineProofs: MaterialButton

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

    
    
    private lateinit var btnScanVaccineQR: MaterialButton
    private lateinit var cardVerifyResult: CardView
    private lateinit var tvVerifyResultAddress: TextView
    private lateinit var tvVerifyResult: TextView

    // ── State ─────────────────────────────────────────────────────────────────
    private var selectedVaccineCode: Int = VaccineCodes.COVID_19
    private var selectedVaccineName: String = ""
    private var passportQrBitmap: Bitmap? = null
    private lateinit var repository: VaccineProofRepository

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vaccine_passport)

        BlockchainService.initialize(this)
        repository = VaccineProofRepository(this)

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
        btnClearVaccineProofs   = findViewById(R.id.btnClearVaccineProofs)

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
        btnGoToRecords = findViewById(R.id.btnGoToRecords)
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
        btnClearVaccineProofs.setOnClickListener { onClearVaccineProofsClicked() }
        btnShowFullQR.setOnClickListener {
            val qrBitmap = passportQrBitmap
            if (qrBitmap != null) {
                showQrFullscreenDialog(qrBitmap, "vaccine_passport_${selectedVaccineCode}") {
                    sharePassportQR()
                }
            } else {
                Toast.makeText(this, "No QR code available yet", Toast.LENGTH_SHORT).show()
            }
        }
        ivPassportQR.setOnClickListener {
            val qrBitmap = passportQrBitmap
            if (qrBitmap != null) {
                showQrFullscreenDialog(qrBitmap, "vaccine_passport_${selectedVaccineCode}") {
                    sharePassportQR()
                }
            } else {
                Toast.makeText(this, "No QR code available yet", Toast.LENGTH_SHORT).show()
            }
        }
        btnGoToRecords.setOnClickListener {
            // Navigate back to main screen; user can open their vaccination records there
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
        btnScanVaccineQR.setOnClickListener { openQRScanner() }
    }

    private fun onClearVaccineProofsClicked() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear Local Vaccine Proofs")
            .setMessage("This removes all locally stored vaccine proofs from this device.")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.deleteAllVaccineProofs()
                    }
                    cardStatus.visibility = View.GONE
                    cardPassport.visibility = View.GONE
                    cardSetupRequired.visibility = View.GONE
                    cardVerifyResult.visibility = View.GONE
                    Toast.makeText(this@VaccinePassportActivity, "Local vaccine proofs cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Check own proof status (LOCAL VERIFICATION HISTORY)
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkMyProofStatus() {
        val address = WalletManager.getAddress() ?: run {
            Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
            return
        }

        btnCheckProofStatus.isEnabled = false
        btnCheckProofStatus.text = "Checking..."

        cardStatus.visibility = View.VISIBLE
        tvProofStatus.text = "Checking local status…"
        tvStatusDetail.visibility = View.GONE
        cardPassport.visibility = View.GONE
        cardSetupRequired.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Check database for most recent verified vaccine proof
                val mostRecent = withContext(Dispatchers.IO) {
                    repository.getMostRecentVaccineProof()
                }

                if (mostRecent != null && mostRecent.isVerified) {
                    onProofVerified(address, mostRecent)
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

    private fun onProofVerified(address: String, proof: ProofRecord) {
        tvProofStatus.text = "✅ Proof Verified Locally"
        tvStatusDetail.text = "Your ZK proof for $selectedVaccineName is verified locally on this device."
        tvStatusDetail.setTextColor(getColor(R.color.success))
        tvStatusDetail.visibility = View.VISIBLE

        // Populate passport card
        tvPassportVaccineName.text = selectedVaccineName
        tvPassportAddress.text = WalletManager.getFormattedAddress() ?: address.abbreviate()
        
        // Show verification timestamp
        if (proof.verifiedAt > 0) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            tvPassportVerifiedDate.text = "Verified: " + dateFormat.format(Date(proof.verifiedAt))
        } else {
            tvPassportVerifiedDate.text = "Locally verified ✓"
        }

        // Generate compact QR for the passport card (includes proof data)
        val qrJson = buildVaccinePassportJson(address, selectedVaccineCode, selectedVaccineName, proof)
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
    // Verify another address (via QR code scanning)
    // ─────────────────────────────────────────────────────────────────────────

    private fun sharePassportQR() {
        val address = WalletManager.getAddress() ?: return
        
        lifecycleScope.launch {
            try {
                // Get most recent verified proof
                val proof = withContext(Dispatchers.IO) {
                    repository.getMostRecentVaccineProof()
                }
                
                if (proof == null || !proof.isVerified) {
                    Toast.makeText(this@VaccinePassportActivity, "No verified vaccine proof found", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val qrJson = buildVaccinePassportJson(address, selectedVaccineCode, selectedVaccineName, proof)
                val fullBitmap = generateQrBitmap(qrJson, size = 512) ?: run {
                    Toast.makeText(this@VaccinePassportActivity, "Could not generate QR code", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    val cachePath = File(cacheDir, "qr_codes")
                    cachePath.mkdirs()
                    val file = File(cachePath, "vaccine_passport_${selectedVaccineCode}.png")
                    FileOutputStream(file).use { out ->
                        fullBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    val contentUri = FileProvider.getUriForFile(
                        this@VaccinePassportActivity,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                            "Vaccine Passport — $selectedVaccineName\nWallet: ${address.abbreviate()}\nZK Verified Locally ✓"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Vaccine Passport"))

                } catch (e: Exception) {
                    Log.e(TAG, "QR share failed", e)
                    Toast.makeText(this@VaccinePassportActivity, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing QR", e)
                Toast.makeText(this@VaccinePassportActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
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
            val scanResult = data.getStringExtra("SCAN_RESULT") ?: return
            
            try {
                val json = JSONObject(scanResult)
                val type = json.optString("type")
                
                if (type == "VACCINE_PASSPORT") {
                    val address = json.optString("address", "Unknown")
                    val vName = json.optString("vaccineName", "Unknown Vaccine")
                    val vCode = json.optInt("vaccineCode", -1)
                    val isVerified = json.optBoolean("verified", false)
                    
                    cardVerifyResult.visibility = View.VISIBLE
                    tvVerifyResultAddress.text = "Wallet: ${address.take(6)}...${address.takeLast(4)}"
                    
                    if (isVerified) {
                        tvVerifyResult.text = "Checking blockchain anchor..."
                        tvVerifyResult.setTextColor(getColor(android.R.color.holo_orange_dark))
                        
                        lifecycleScope.launch {
                            try {
                                val isAnchored = withContext(Dispatchers.IO) {
                                    BlockchainService.checkVaccinationStatus(address, vCode)
                                }
                                if (isAnchored) {
                                    tvVerifyResult.text = "Proof Verified & Anchored\nVaccine: $vName\nBlockchain Confirmed."
                                    tvVerifyResult.setTextColor(getColor(R.color.success))
                                } else {
                                    tvVerifyResult.text = "Offline Valid, but no blockchain anchor found.\nVaccine: $vName"
                                    tvVerifyResult.setTextColor(Color.RED)
                                }
                            } catch (e: Exception) {
                                tvVerifyResult.text = "Offline Verified\nCould not reach blockchain to check anchor."
                                tvVerifyResult.setTextColor(getColor(android.R.color.holo_orange_dark))
                            }
                        }
                    } else {
                        tvVerifyResult.text = "Unverified Proof\nVaccine: $vName"
                        tvVerifyResult.setTextColor(Color.RED)
                    }
                } else {
                    Toast.makeText(this, "Not a valid Vaccine Passport QR", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateWalletStatusBanner() {
        val addr = WalletManager.getFormattedAddress()
        tvWalletStatus.text = if (addr != null) addr else "Not Connected"
    }

    private fun buildVaccinePassportJson(
        address: String,
        vaccineCode: Int,
        vaccineName: String,
        proof: ProofRecord
    ): String {
        return JSONObject().apply {
            put("type", "VACCINE_PASSPORT")
            put("address", address)
            put("vaccineCode", vaccineCode)
            put("vaccineName", vaccineName)
            put("verified", proof.isVerified)
            put("proofTimestamp", proof.verifiedAt)
            put("checkedAt", System.currentTimeMillis())
            put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
            put("qrVersion", 2)  // Compact format
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

    private fun showQrFullscreenDialog(
        bitmap: Bitmap,
        filePrefix: String,
        onShare: (() -> Unit)? = null
    ) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0x88000000.toInt())
        }

        val saveButton = Button(this).apply {
            text = "Save Image"
            setOnClickListener {
                val saved = saveQrToGallery(bitmap, filePrefix)
                if (saved) {
                    Toast.makeText(this@VaccinePassportActivity, "QR saved to gallery", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@VaccinePassportActivity, "Failed to save QR image", Toast.LENGTH_SHORT).show()
                }
            }
        }
        controls.addView(saveButton)

        if (onShare != null) {
            val shareButton = Button(this).apply {
                text = "Share"
                setOnClickListener { onShare.invoke() }
            }
            controls.addView(shareButton)
        }

        val closeButton = Button(this).apply {
            text = "Close"
            setOnClickListener { dialog.dismiss() }
        }
        controls.addView(closeButton)

        root.addView(
            image,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        dialog.setContentView(root)
        dialog.show()
    }

    private fun saveQrToGallery(bitmap: Bitmap, filePrefix: String): Boolean {
        return try {
            val filename = "${filePrefix}_${System.currentTimeMillis()}.png"
            val resolver = contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BlockchainHealthWallet")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false

            val wrote = resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            wrote
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save QR image", e)
            false
        }
    }

    private fun String.abbreviate(): String = if (length > 10) "${take(6)}...${takeLast(4)}" else this
}
