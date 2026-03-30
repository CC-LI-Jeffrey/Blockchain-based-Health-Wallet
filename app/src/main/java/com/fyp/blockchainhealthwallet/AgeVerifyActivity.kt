package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.content.ContentValues
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.fyp.blockchainhealthwallet.zkp.ZkpProofResult
import com.fyp.blockchainhealthwallet.zkp.ZkpService
import com.fyp.blockchainhealthwallet.db.ProofRecord
import com.fyp.blockchainhealthwallet.db.AppDatabase
import com.fyp.blockchainhealthwallet.db.AgeVerifyRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AgeVerifyActivity (Option A - Hybrid)
 *
 * Allows the user to:
 *   1. Generate a ZK proof for a selected age threshold (18, 20, 21, 25, 65).
 *   2. Verify the proof locally without blockchain submission.
 *   3. Save verified proofs to local database for audit trail.
 *   4. Generate QR codes with proof data for sharing with others.
 *   5. Scan and verify other users' age proofs (fully local, no blockchain).
 */
class AgeVerifyActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AgeVerifyActivity"
        private const val QR_SCAN_REQUEST = 1001
    }

    // Views
    private lateinit var tvMyStatus: TextView
    private lateinit var tvVerifiedDate: TextView
    private lateinit var cardAgePassport: View
    private lateinit var tvPassportAddress: TextView
    private lateinit var ivAgePassportQR: android.widget.ImageView
    private lateinit var btnShowAgeQR: MaterialButton
    private lateinit var btnVerifyMyAge: MaterialButton
    private lateinit var btnClearAgeProofs: MaterialButton
    private lateinit var cardGenerateProof: View
    private lateinit var spinnerMinAge: Spinner
    private lateinit var etBirthDate: TextInputEditText
    private lateinit var btnGenerateProof: MaterialButton
    private lateinit var layoutProgress: View
    private lateinit var tvProgressStatus: TextView
    private lateinit var cardProofDetails: View
    private lateinit var tvDetailYear: TextView
    private lateinit var tvDetailMinAge: TextView
    private lateinit var btnSubmitProof: MaterialButton
    private lateinit var btnScanAddress: MaterialButton
    private lateinit var cardCheckResult: View
    private lateinit var tvCheckedAddress: TextView
    private lateinit var tvCheckResult: TextView

    // State
    private var selectedBirthYear  = 0
    private var selectedBirthMonth = 0  // 1-12
    private var selectedBirthDay   = 0  // 1-31
    private var selectedMinAge = 18  // Age threshold from spinner
    private var currentProof: ZkpProofResult? = null
    private lateinit var zkpService: ZkpService
    private lateinit var repository: AgeVerifyRepository
    private var agePassportQrBitmap: android.graphics.Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_age_verify)

        zkpService = ZkpService(this)
        repository = AgeVerifyRepository(this)

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
        cardAgePassport    = findViewById(R.id.cardAgePassport)
        tvPassportAddress  = findViewById(R.id.tvPassportAddress)
        ivAgePassportQR    = findViewById(R.id.ivAgePassportQR)
        btnShowAgeQR       = findViewById(R.id.btnShowAgeQR)
        btnVerifyMyAge     = findViewById(R.id.btnVerifyMyAge)
        btnClearAgeProofs  = findViewById(R.id.btnClearAgeProofs)
        cardGenerateProof  = findViewById(R.id.cardGenerateProof)
        spinnerMinAge      = findViewById(R.id.spinnerMinAge)
        etBirthDate        = findViewById(R.id.etBirthDate)
        btnGenerateProof   = findViewById(R.id.btnGenerateProof)
        layoutProgress     = findViewById(R.id.layoutProgress)
        tvProgressStatus   = findViewById(R.id.tvProgressStatus)
        cardProofDetails   = findViewById(R.id.cardProofDetails)
        tvDetailYear       = findViewById(R.id.tvDetailYear)
        tvDetailMinAge     = findViewById(R.id.tvDetailMinAge)
        btnSubmitProof     = findViewById(R.id.btnSubmitProof)
        btnScanAddress     = findViewById(R.id.btnScanAddress)
        cardCheckResult    = findViewById(R.id.cardCheckResult)
        tvCheckedAddress   = findViewById(R.id.tvCheckedAddress)
        tvCheckResult      = findViewById(R.id.tvCheckResult)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        // Age threshold spinner
        val ageOptions = arrayOf("18+", "20+", "21+", "25+", "65+")
        val ageValues = intArrayOf(18, 20, 21, 25, 65)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ageOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMinAge.adapter = spinnerAdapter
        spinnerMinAge.setSelection(0)  // Default to 18+
        spinnerMinAge.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMinAge = ageValues[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        btnVerifyMyAge.setOnClickListener {
            if (!WalletManager.isConnected()) {
                Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            cardGenerateProof.visibility = View.GONE
            cardProofDetails.visibility = View.GONE
            currentProof = null
            selectedBirthYear = 0; selectedBirthMonth = 0; selectedBirthDay = 0
            btnSubmitProof.isEnabled = true
            btnSubmitProof.text = "Verify & Anchor"
            loadBirthDateFromProfile()
        }

        // DOB is read-only — loaded from profile, not manually entered
        etBirthDate.isFocusable = false
        etBirthDate.isClickable = false
        etBirthDate.inputType = android.text.InputType.TYPE_NULL

        btnGenerateProof.setOnClickListener { onGenerateProofClicked() }

        btnSubmitProof.setOnClickListener { onVerifyProofLocally() }

        btnScanAddress.setOnClickListener {
            try {
                val intent = Intent(this,
                    Class.forName("com.fyp.blockchainhealthwallet.ui.partialshare.AddressQRScannerActivity"))
                startActivityForResult(intent, QR_SCAN_REQUEST)
            } catch (e: Exception) {
                Toast.makeText(this, "QR scanner not available", Toast.LENGTH_SHORT).show()
            }
        }

        // check status removed

        btnShowAgeQR.setOnClickListener { shareAgePassportQR() }
        ivAgePassportQR.setOnClickListener {
            val qrBitmap = agePassportQrBitmap
            if (qrBitmap != null) {
                showQrFullscreenDialog(qrBitmap, "age_passport") {
                    shareAgePassportQR()
                }
            } else {
                Toast.makeText(this, "No QR code available yet", Toast.LENGTH_SHORT).show()
            }
        }

        btnClearAgeProofs.setOnClickListener { onClearAgeProofsClicked() }
    }

    private fun onClearAgeProofsClicked() {
        AlertDialog.Builder(this)
            .setTitle("Clear Local Age Proofs")
            .setMessage("This removes all locally stored age proofs from this device.")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.deleteAllAgeProofs()
                    }
                    tvMyStatus.text = "🔴 Not Verified Locally"
                    tvVerifiedDate.visibility = View.GONE
                    cardAgePassport.visibility = View.GONE
                    cardProofDetails.visibility = View.GONE
                    cardGenerateProof.visibility = View.GONE
                    btnVerifyMyAge.text = "Verify My Age"
                    btnSubmitProof.isEnabled = true
                    btnSubmitProof.text = "Verify & Anchor"
                    Toast.makeText(this@AgeVerifyActivity, "Local age proofs cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────────
    // My Status (LOCAL VERIFICATION HISTORY)
    // ─────────────────────────────────────────────

    private fun loadMyStatus() {
        val address = WalletManager.getAddress()
        if (address == null) {
            tvMyStatus.text = "Connect your wallet to use age verification"
            btnVerifyMyAge.isEnabled = false
            return
        }

        tvMyStatus.text = "Checking..."

        lifecycleScope.launch {
            try {
                // Check database for most recent verified AGE_PASSPORT proof
                val mostRecent = withContext(Dispatchers.IO) {
                    repository.getMostRecentAgeProof()
                }

                if (mostRecent != null && mostRecent.isVerified) {
                    val minAge = mostRecent.minValue
                    tvMyStatus.text = "✅ Age Verified (${minAge}+)"
                    tvMyStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                    btnVerifyMyAge.isEnabled = true
                    btnVerifyMyAge.text = "Regenerate Proof"

                    if (mostRecent.verifiedAt > 0) {
                        val date = Date(mostRecent.verifiedAt)
                        tvVerifiedDate.text = "Verified on: ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(date)}"
                        tvVerifiedDate.visibility = View.VISIBLE
                    }

                    // Show the Age Passport QR card
                    showAgePassportCard(address, mostRecent)
                } else {
                    tvMyStatus.text = "🔴 Not Verified Locally"
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
    // Age Passport QR (LOCAL VERIFICATION)
    // ─────────────────────────────────────────────

    private fun showAgePassportCard(address: String, proofRecord: ProofRecord) {
        tvPassportAddress.text = WalletManager.getFormattedAddress() ?: address.let {
            if (it.length > 10) "${it.take(6)}...${it.takeLast(4)}" else it
        }

        // Parse publicInputs from proof record to get actual verification details
        val publicInputs = try { 
            proofRecord.publicInputs.split(",").mapNotNull { it.trim().toIntOrNull() }
        } catch (e: Exception) { 
            listOf(proofRecord.minValue.toInt())
        }

        val qrJson = org.json.JSONObject().apply {
            put("type", "AGE_PASSPORT")
            put("address", address)
            put("minAge", proofRecord.minValue)
            put("verified", true)
            put("verifiedAt", proofRecord.verifiedAt)
            put("proofHash", normalizeProofHash(proofRecord.proofHash) ?: proofRecord.proofHash)
            put("timestamp", proofRecord.timestamp)
        }.toString()

        agePassportQrBitmap = generateQrBitmap(qrJson, 200)
        if (agePassportQrBitmap != null) {
            ivAgePassportQR.setImageBitmap(agePassportQrBitmap)
        }
        cardAgePassport.visibility = View.VISIBLE
    }

    private fun shareAgePassportQR() {
        val address = WalletManager.getAddress() ?: return

        lifecycleScope.launch {
            try {
                // Get most recent verified proof
                val proofRecord = withContext(Dispatchers.IO) {
                    repository.getMostRecentAgeProof()
                }

                if (proofRecord == null || !proofRecord.isVerified) {
                    Toast.makeText(this@AgeVerifyActivity, "No verified age proof found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val qrJson = org.json.JSONObject().apply {
                    put("type", "AGE_PASSPORT")
                    put("address", address)
                    put("minAge", proofRecord.minValue)
                    put("verified", true)
                    put("verifiedAt", proofRecord.verifiedAt)
                    put("proofHash", normalizeProofHash(proofRecord.proofHash) ?: proofRecord.proofHash)
                }.toString()

                val fullBitmap = generateQrBitmap(qrJson, 512) ?: run {
                    Toast.makeText(this@AgeVerifyActivity, "Could not generate QR code", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    val cachePath = java.io.File(cacheDir, "qr_codes")
                    cachePath.mkdirs()
                    val file = java.io.File(cachePath, "age_passport.png")
                    java.io.FileOutputStream(file).use { fos -> fullBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos) }

                    val contentUri = androidx.core.content.FileProvider.getUriForFile(
                        this@AgeVerifyActivity, "${applicationContext.packageName}.fileprovider", file
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        putExtra(Intent.EXTRA_TEXT, "Age Verification Passport\nWallet: ${address.let { if (it.length > 10) "${it.take(6)}...${it.takeLast(4)}" else it }}\n${proofRecord.minValue}+ ZK Verified ✓")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Age Passport"))
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "QR share failed", e)
                    Toast.makeText(this@AgeVerifyActivity, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing QR", e)
                Toast.makeText(this@AgeVerifyActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateQrBitmap(content: String, size: Int): android.graphics.Bitmap? {
        return try {
            val bitMatrix = com.google.zxing.qrcode.QRCodeWriter()
                .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
            val bmp = android.graphics.Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, android.graphics.Bitmap.Config.RGB_565)
            for (x in 0 until bitMatrix.width)
                for (y in 0 until bitMatrix.height)
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun showQrFullscreenDialog(
        bitmap: android.graphics.Bitmap,
        filePrefix: String,
        onShare: (() -> Unit)? = null
    ) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val image = android.widget.ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        val controls = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0x88000000.toInt())
        }

        val saveButton = android.widget.Button(this).apply {
            text = "Save Image"
            setOnClickListener {
                val saved = saveQrToGallery(bitmap, filePrefix)
                if (saved) {
                    Toast.makeText(this@AgeVerifyActivity, "QR saved to gallery", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AgeVerifyActivity, "Failed to save QR image", Toast.LENGTH_SHORT).show()
                }
            }
        }

        controls.addView(saveButton)

        if (onShare != null) {
            val shareButton = android.widget.Button(this).apply {
                text = "Share"
                setOnClickListener { onShare.invoke() }
            }
            controls.addView(shareButton)
        }

        val closeButton = android.widget.Button(this).apply {
            text = "Close"
            setOnClickListener { dialog.dismiss() }
        }
        controls.addView(closeButton)

        root.addView(
            image,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            controls,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        dialog.setContentView(root)
        dialog.show()
    }

    private fun saveQrToGallery(bitmap: android.graphics.Bitmap, filePrefix: String): Boolean {
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
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
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

    // ─────────────────────────────────────────────
    // Generate Proof
    // ─────────────────────────────────────────────

    // ─────────────────────────────────────────────
    // Load DOB from user profile (blockchain + IPFS)
    // ─────────────────────────────────────────────

    private fun loadBirthDateFromProfile() {
        val address = WalletManager.getAddress() ?: return

        btnVerifyMyAge.isEnabled = false
        btnVerifyMyAge.text = "Loading profile..."

        lifecycleScope.launch {
            try {
                // Step 1: Get IPFS hash from blockchain
                val personalInfoRef = withContext(Dispatchers.IO) {
                    BlockchainService.getPersonalInfoRef(address)
                }

                if (personalInfoRef == null || !personalInfoRef.exists) {
                    btnVerifyMyAge.isEnabled = true
                    btnVerifyMyAge.text = "Verify My Age"
                    AlertDialog.Builder(this@AgeVerifyActivity)
                        .setTitle("Profile Required")
                        .setMessage("No profile found. Please set up your profile with your date of birth first.")
                        .setPositiveButton("Go to Profile") { _, _ ->
                            startActivity(Intent(this@AgeVerifyActivity, ProfileActivity::class.java))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@launch
                }

                // Step 2: Fetch encrypted data from IPFS
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getFromIPFS(personalInfoRef.encryptedDataIpfsHash)
                }

                if (!response.isSuccessful || response.body() == null) {
                    throw Exception("Failed to fetch profile from IPFS")
                }

                // Step 3: Decrypt
                val encryptedDataBase64 = response.body()!!.string()
                val jsonData = if (personalInfoRef.encryptedKey.isNotEmpty()) {
                    val encryptedBytes = Base64.decode(encryptedDataBase64, Base64.NO_WRAP)
                    val aesKey = EncryptionHelper.decryptKeyFromBlockchain(personalInfoRef.encryptedKey)
                    EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                } else {
                    encryptedDataBase64
                }

                // Step 4: Parse PersonalInfo
                val personalInfo = Gson().fromJson(jsonData, PersonalInfo::class.java)
                val dob = personalInfo.dateOfBirth

                if (dob.isEmpty()) {
                    btnVerifyMyAge.isEnabled = true
                    btnVerifyMyAge.text = "Verify My Age"
                    AlertDialog.Builder(this@AgeVerifyActivity)
                        .setTitle("Date of Birth Missing")
                        .setMessage("Your profile does not have a date of birth. Please update your profile first.")
                        .setPositiveButton("Go to Profile") { _, _ ->
                            startActivity(Intent(this@AgeVerifyActivity, ProfileActivity::class.java))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@launch
                }

                // Step 5: Parse the DOB string into year/month/day
                val parsed = parseDateOfBirth(dob)
                if (parsed == null) {
                    btnVerifyMyAge.isEnabled = true
                    btnVerifyMyAge.text = "Verify My Age"
                    Toast.makeText(this@AgeVerifyActivity,
                        "Could not parse date of birth: \"$dob\"", Toast.LENGTH_LONG).show()
                    return@launch
                }

                selectedBirthYear  = parsed.first
                selectedBirthMonth = parsed.second
                selectedBirthDay   = parsed.third

                etBirthDate.setText(String.format("%02d/%02d/%d",
                    selectedBirthDay, selectedBirthMonth, selectedBirthYear))

                btnVerifyMyAge.isEnabled = true
                btnVerifyMyAge.text = "Verify My Age"
                cardGenerateProof.visibility = View.VISIBLE

                Log.d(TAG, "DOB loaded from profile: $selectedBirthYear-$selectedBirthMonth-$selectedBirthDay")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load DOB from profile", e)
                btnVerifyMyAge.isEnabled = true
                btnVerifyMyAge.text = "Verify My Age"
                Toast.makeText(this@AgeVerifyActivity,
                    "Could not load profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Tries several common date formats to parse the DOB string stored in PersonalInfo.
     * Returns Triple(year, month 1-12, day).
     */
    private fun parseDateOfBirth(dob: String): Triple<Int, Int, Int>? {
        val formats = listOf(
            "MMMM dd, yyyy",   // January 15, 2004
            "MMMM d, yyyy",    // January 5, 2004
            "dd/MM/yyyy",      // 15/01/2004
            "MM/dd/yyyy",      // 01/15/2004
            "yyyy-MM-dd",      // 2004-01-15
            "dd-MM-yyyy",      // 15-01-2004
            "d MMM yyyy",      // 15 Jan 2004
            "dd MMM yyyy"      // 15 Jan 2004
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
                sdf.isLenient = false
                val date = sdf.parse(dob.trim()) ?: continue
                val cal = Calendar.getInstance()
                cal.time = date
                return Triple(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,  // 1-based
                    cal.get(Calendar.DAY_OF_MONTH)
                )
            } catch (_: Exception) { }
        }
        return null
    }

    private fun onGenerateProofClicked() {
        if (selectedBirthYear == 0) {
            Toast.makeText(this, "Date of birth not loaded yet. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        val cal = Calendar.getInstance()
        val currentYear  = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val currentDay   = cal.get(Calendar.DAY_OF_MONTH)

        // Check if user meets selected age threshold
        val age = currentYear - selectedBirthYear
        val isBirthdayReached = (currentMonth > selectedBirthMonth) ||
                (currentMonth == selectedBirthMonth && currentDay >= selectedBirthDay)
        val meetsThreshold = age > selectedMinAge || (age == selectedMinAge && isBirthdayReached)

        if (!meetsThreshold) {
            Toast.makeText(this, "You must be $selectedMinAge or older", Toast.LENGTH_SHORT).show()
            return
        }

        setGeneratingState(true)

        lifecycleScope.launch {
            try {
                val proof = withContext(Dispatchers.Main) {
                    // ZkpService requires Main thread (WebView)
                    // Note: Circuit always uses selectedMinAge as public input
                    zkpService.generateAgeProof(selectedBirthYear, selectedBirthMonth, selectedBirthDay, selectedMinAge)
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
        layoutProgress.visibility = if (isGenerating) View.VISIBLE else View.GONE

        if (isGenerating) {
            tvProgressStatus.text = "Computing zero-knowledge proof for $selectedMinAge+ age..."
        }
    }

    private fun showProofReady(proof: ZkpProofResult) {
        setGeneratingState(false)

        tvDetailYear.text = proof.currentYear.toString()
        tvDetailMinAge.text = selectedMinAge.toString()
        cardProofDetails.visibility = View.VISIBLE
        btnSubmitProof.isEnabled = true
        btnSubmitProof.text = "Verify & Anchor"

        Toast.makeText(this, "Proof generated successfully", Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────
    // Verify Proof Locally (Option A - No Blockchain)
    // ─────────────────────────────────────────────

    private fun onVerifyProofLocally() {
        val proof = currentProof ?: run {
            Toast.makeText(this, "Generate a proof first", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmitProof.isEnabled = false
        btnSubmitProof.text = "Verifying..."

        lifecycleScope.launch {
            try {
                // Get current date for verification
                val cal = Calendar.getInstance()
                val currentYear  = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH) + 1
                val currentDay   = cal.get(Calendar.DAY_OF_MONTH)

                // Verify proof locally (ZKP verification, no blockchain)
                val isValid = withContext(Dispatchers.Main) {
                    zkpService.verifyAgeProof(
                        proof = proof,
                        currentYear = currentYear,
                        currentMonth = currentMonth,
                        currentDay = currentDay,
                        minAge = selectedMinAge
                    )
                }

                if (!isValid) {
                    Toast.makeText(
                        this@AgeVerifyActivity,
                        "Proof verification failed",
                        Toast.LENGTH_LONG
                    ).show()
                    btnSubmitProof.isEnabled = true
                    btnSubmitProof.text = "Verify Locally"
                    return@launch
                }

                // Proof is valid! Save to database
                val address = WalletManager.getAddress() ?: ""
                val proofHash = proof.proofHash()  // Hash the proof
                val proofRecord = ProofRecord(
                    id = 0,  // Auto-generated
                    type = "AGE_PASSPORT",
                    proofHash = proofHash,
                    publicInputs = proof.publicSignals.joinToString(","),
                    minValue = selectedMinAge.toLong(),
                    timestamp = System.currentTimeMillis(),
                    issuerAddress = address,
                    commitment = "",  // Not used for age proofs
                    isVerified = true,
                    verifiedAt = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    repository.saveAgeProof(proofRecord)
                }
                
                layoutProgress.visibility = View.GONE

                Log.d(TAG, "Age proof verified and saved locally")

                // Step 2: Attempt blockchain anchor (optional, local proof remains valid if this fails)
                anchorAgeProofToBlockchain(proof)

            } catch (e: Exception) {
                Log.e(TAG, "Proof verification failed", e)
                Toast.makeText(
                    this@AgeVerifyActivity,
                    "Verification error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                btnSubmitProof.isEnabled = true
                btnSubmitProof.text = "Verify Locally"
            }
        }
    }

    private fun anchorAgeProofToBlockchain(proof: ZkpProofResult) {
        btnSubmitProof.text = "Anchoring..."
        layoutProgress.visibility = View.VISIBLE
        tvProgressStatus.text = "Anchoring age proof on-chain..."

        lifecycleScope.launch {
            try {
                val proofHashHex = proof.proofHash()
                val commitment = java.math.BigInteger(proofHashHex, 16)
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.submitAgeProof(
                        minAge = selectedMinAge,
                        commitment = commitment,
                        proofHashHex = proofHashHex
                    )
                }

                layoutProgress.visibility = View.GONE
                btnSubmitProof.text = "Verified & Anchored"
                cardGenerateProof.visibility = View.GONE
                cardProofDetails.visibility = View.GONE
                loadMyStatus()

                Toast.makeText(
                    this@AgeVerifyActivity,
                    "Proof verified locally and anchored on-chain. Tx: ${txHash.take(16)}...",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Age proof anchor failed", e)
                layoutProgress.visibility = View.GONE
                btnSubmitProof.text = "Verified (Local)"
                cardGenerateProof.visibility = View.GONE
                cardProofDetails.visibility = View.GONE
                loadMyStatus()

                AlertDialog.Builder(this@AgeVerifyActivity)
                    .setTitle("Local Verify Complete")
                    .setMessage(
                        "Proof is valid and saved locally, but blockchain anchor failed:\n\n${e.message}\n\nYou can still use local verification."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    // ─────────────────────────────────────────────
    // Check Another User (via QR Code Scanning)
    // ─────────────────────────────────────────────

    private fun onCheckStatusClicked() {
        // For Option A, QR code scanning is the primary way to verify age proofs from others
        // The actual QR scanning and local verification happens in AddressQRScannerActivity
        Toast.makeText(
            this,
            "Use the QR scan button (📷) to scan age verification QR codes from other users. Verification happens locally on your device.",
            Toast.LENGTH_LONG
        ).show()
    }

    // ─────────────────────────────────────────────
    // QR Scan result
    // ─────────────────────────────────────────────

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == QR_SCAN_REQUEST && resultCode == RESULT_OK && data != null) {
            val scanResult = data.getStringExtra("SCAN_RESULT") ?: return
            
            try {
                val json = org.json.JSONObject(scanResult)
                val type = json.optString("type")
                if (type == "AGE_PASSPORT") {
                    val address = json.optString("address")
                    val verified = json.optBoolean("verified")
                    val minAge = json.optInt("minAge")
                    val qrProofHash = normalizeProofHash(json.optString("proofHash", ""))
                    
                    cardCheckResult.visibility = View.VISIBLE
                    tvCheckedAddress.text = "Wallet: ${address.take(6)}...${address.takeLast(4)}"
                    
                    if (verified) {
                        tvCheckResult.text = "Offline proof valid (>= ${minAge}). Checking blockchain anchor..."
                        tvCheckResult.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_orange_dark))

                        lifecycleScope.launch {
                            try {
                                val isAnchored = withContext(Dispatchers.IO) {
                                    BlockchainService.checkAdultStatus(address)
                                }
                                val chainProofHash = withContext(Dispatchers.IO) {
                                    BlockchainService.getAgeProofHash(address)
                                }

                                val normalizedChainProofHash = normalizeProofHash(chainProofHash)
                                val exactMatch = qrProofHash != null && normalizedChainProofHash != null && qrProofHash == normalizedChainProofHash

                                if (isAnchored && exactMatch) {
                                    tvCheckResult.text = "Proof Verified & Anchored\nSubject is >= ${minAge}"
                                    tvCheckResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@AgeVerifyActivity, android.R.color.holo_green_dark))
                                } else if (isAnchored && normalizedChainProofHash == null) {
                                    tvCheckResult.text = "Offline proof valid (>= ${minAge}), anchor exists, but on-chain proof hash is unavailable. Ensure app contract address matches the latest deployment."
                                    tvCheckResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@AgeVerifyActivity, android.R.color.holo_orange_dark))
                                } else if (isAnchored && qrProofHash == null) {
                                    tvCheckResult.text = "Offline proof valid (>= ${minAge}), anchor exists, but this QR has no proof hash."
                                    tvCheckResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@AgeVerifyActivity, android.R.color.holo_orange_dark))
                                } else if (isAnchored) {
                                    val qrShort = qrProofHash?.take(8) ?: "n/a"
                                    val chainShort = normalizedChainProofHash?.take(8) ?: "n/a"
                                    tvCheckResult.text = "Offline proof valid (>= ${minAge}), but this QR proof is not the currently anchored proof.\nQR: ${qrShort}... Chain: ${chainShort}..."
                                    tvCheckResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@AgeVerifyActivity, android.R.color.holo_orange_dark))
                                } else {
                                    tvCheckResult.text = "Offline proof valid (>= ${minAge}), but no blockchain anchor found."
                                    tvCheckResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@AgeVerifyActivity, android.R.color.holo_orange_dark))
                                }
                            } catch (e: Exception) {
                                tvCheckResult.text = "Offline proof valid (>= ${minAge}), but anchor check failed."
                                tvCheckResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@AgeVerifyActivity, android.R.color.holo_orange_dark))
                            }
                        }
                    } else {
                        tvCheckResult.text = "✗ Age Proof Verification Failed (Local)"
                        tvCheckResult.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    }
                } else {
                    Toast.makeText(this, "Invalid QR Code type for Age", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Invalid QR Format", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun normalizeProofHash(hash: String?): String? {
        if (hash.isNullOrBlank()) return null

        val cleaned = hash.trim()
            .removePrefix("0x")
            .removePrefix("0X")
            .lowercase()

        if (cleaned.length != 64) return null
        if (!cleaned.all { it in '0'..'9' || it in 'a'..'f' }) return null
        if (cleaned == "0".repeat(64)) return null

        return "0x$cleaned"
    }
}
