package com.fyp.blockchainhealthwallet

import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.fyp.blockchainhealthwallet.network.ProgressRequestBody
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*

/**
 * AddVaccinationActivity - Upload vaccination records to blockchain with encryption
 * 
 * ENCRYPTION FLOW:
 * 1. Generate random AES-256 key for this vaccination record
 * 2. Encrypt vaccination data (JSON) with random key
 * 3. Upload encrypted data to IPFS → get dataIpfsHash
 * 4. Encrypt certificate file (if selected) with same random key
 * 5. Upload encrypted certificate to IPFS → get certificateIpfsHash
 * 6. Encrypt random key with user's wallet-derived key
 * 7. Call BlockchainService.addVaccination(dataHash, certHash, date, encryptedKey)
 * 8. User signs transaction with wallet
 * 
 * This ensures:
 * - Each vaccination has unique random key (not reusable across records)
 * - Data encrypted before leaving device
 * - Backend/IPFS cannot read data
 * - Only user can decrypt with their wallet
 */
class AddVaccinationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AddVaccinationActivity"
    }

    private lateinit var etVaccineName: TextInputEditText
    private lateinit var etVaccineNameEn: TextInputEditText
    private lateinit var etVaccineFullName: TextInputEditText
    private lateinit var etManufacturer: TextInputEditText
    private lateinit var etCountry: TextInputEditText
    private lateinit var etProvider: TextInputEditText
    private lateinit var etLocation: TextInputEditText
    private lateinit var etBatchNumber: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var btnAttachCertificate: MaterialButton
    private lateinit var tvAttachedCertificate: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedCertificateUri: Uri? = null
    private var uploadedCertificateIpfsHash: String? = null
    private var uploadedDataIpfsHash: String? = null
    private var randomAESKey: javax.crypto.SecretKey? = null  // Random key for this record
    private var encryptedKeyForBlockchain: String? = null  // Encrypted random key for blockchain storage
    private var progressDialog: ProgressDialog? = null

    // Certificate file picker launcher
    private val certificatePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedCertificate(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_vaccination)

        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        // Initialize BlockchainService
        BlockchainService.initialize(this)

        // Check wallet connection
        if (!BlockchainService.isWalletConnected()) {
            showWalletNotConnectedDialog()
            return
        }

        setupToolbar()
        setupViews()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        etVaccineName = findViewById(R.id.etVaccineName)
        etVaccineNameEn = findViewById(R.id.etVaccineNameEn)
        etVaccineFullName = findViewById(R.id.etVaccineFullName)
        etManufacturer = findViewById(R.id.etManufacturer)
        etCountry = findViewById(R.id.etCountry)
        etProvider = findViewById(R.id.etProvider)
        etLocation = findViewById(R.id.etLocation)
        etBatchNumber = findViewById(R.id.etBatchNumber)
        etDate = findViewById(R.id.etDate)
        btnAttachCertificate = findViewById(R.id.btnAttachCertificate)
        tvAttachedCertificate = findViewById(R.id.tvAttachedCertificate)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // Set today's date as default
        updateDateDisplay()

        // Date picker
        etDate.setOnClickListener {
            showDatePicker()
        }

        // Attach certificate
        btnAttachCertificate.setOnClickListener {
            // Only accept PDF and image files (like medical reports)
            certificatePickerLauncher.launch("*/*")
        }

        // Save button
        btnSave.setOnClickListener {
            saveVaccination()
        }

        // Cancel button
        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun showDatePicker() {
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                updateDateDisplay()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        etDate.setText(dateFormat.format(selectedDate.time))
    }

    private fun handleSelectedCertificate(uri: Uri) {
        selectedCertificateUri = uri
        val fileName = getFileName(uri)
        
        // Validate file type (only allow medical document formats)
        if (!isValidCertificateFile(uri, fileName)) {
            Toast.makeText(
                this,
                "Please select a valid file (PDF, JPG, PNG only)",
                Toast.LENGTH_LONG
            ).show()
            selectedCertificateUri = null
            return
        }
        
        tvAttachedCertificate.text = fileName
        tvAttachedCertificate.visibility = View.VISIBLE

        // Encrypt and upload immediately
        encryptAndUploadCertificate(uri, fileName)
    }
    
    /**
     * Validate file type - only accept medical document formats
     * Checks both extension and magic bytes to prevent incompatible files
     */
    private fun isValidCertificateFile(uri: Uri, fileName: String): Boolean {
        // Check file extension first
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val allowedExtensions = listOf("pdf", "jpg", "jpeg", "png")
        
        if (extension !in allowedExtensions) {
            return false
        }
        
        // Verify file type by magic bytes
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val header = ByteArray(10)
                inputStream.read(header)
                
                // Check magic bytes
                return when {
                    // PDF: %PDF
                    header[0] == 0x25.toByte() && header[1] == 0x50.toByte() -> true
                    // JPEG: FF D8
                    header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> true
                    // PNG: 89 50 4E 47
                    header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && 
                    header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> true
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating file type", e)
            return false
        }
        
        return false
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "certificate"
    }

    private fun encryptAndUploadCertificate(uri: Uri, fileName: String) {
        lifecycleScope.launch {
            try {
                showProgressDialog("Encrypting certificate...")

                // Create temporary file from URI
                val tempFile = createTempFileFromUri(uri, fileName)

                // Generate random AES key if not already generated
                if (randomAESKey == null) {
                    randomAESKey = EncryptionHelper.generateAESKey()
                }

                // Encrypt certificate with random key
                updateProgressDialog("Encrypting certificate with random key...")
                val encryptedCertFile = withContext(Dispatchers.IO) {
                    val outputFile = File(cacheDir, "cert_enc_${System.currentTimeMillis()}.bin")
                    EncryptionHelper.encryptFile(tempFile, outputFile, randomAESKey!!)
                    outputFile
                }

                // Clean up original temp file
                tempFile.delete()

                // Upload encrypted certificate to IPFS
                uploadEncryptedCertificateToIPFS(encryptedCertFile)

            } catch (e: Exception) {
                dismissProgressDialog()
                e.printStackTrace()
                Toast.makeText(
                    this@AddVaccinationActivity,
                    "Encryption error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                // Clear certificate selection
                selectedCertificateUri = null
                tvAttachedCertificate.visibility = View.GONE
            }
        }
    }

    private suspend fun uploadEncryptedCertificateToIPFS(encryptedFile: File) {
        try {
            updateProgressDialog("Uploading encrypted certificate to IPFS...")

            val requestFile = ProgressRequestBody(
                encryptedFile,
                "application/octet-stream".toMediaTypeOrNull()
            ) { progress ->
                runOnUiThread {
                    updateProgressDialog("Uploading certificate: $progress%")
                }
            }

            val filePart = MultipartBody.Part.createFormData(
                "file",
                encryptedFile.name,
                requestFile
            )

            val response = withContext(Dispatchers.IO) {
                ApiClient.api.uploadToIPFS(filePart)
            }

            // Clean up encrypted file
            encryptedFile.delete()

            if (response.isSuccessful && response.body()?.success == true) {
                uploadedCertificateIpfsHash = response.body()!!.ipfsHash
                Log.d(TAG, "✅ Certificate uploaded to IPFS: $uploadedCertificateIpfsHash")
                dismissProgressDialog()
                Toast.makeText(
                    this@AddVaccinationActivity,
                    "Certificate encrypted and uploaded successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                dismissProgressDialog()
                Toast.makeText(
                    this@AddVaccinationActivity,
                    "Failed to upload certificate to IPFS",
                    Toast.LENGTH_SHORT
                ).show()

                // Clear certificate selection
                selectedCertificateUri = null
                uploadedCertificateIpfsHash = null
                tvAttachedCertificate.visibility = View.GONE
            }
        } catch (e: Exception) {
            dismissProgressDialog()
            e.printStackTrace()
            Toast.makeText(
                this@AddVaccinationActivity,
                "Upload error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()

            // Clear certificate selection
            selectedCertificateUri = null
            uploadedCertificateIpfsHash = null
            tvAttachedCertificate.visibility = View.GONE
        }
    }

    private fun createTempFileFromUri(uri: Uri, fileName: String): File {
        val tempFile = File(cacheDir, "temp_$fileName")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun saveVaccination() {
        // Validate inputs
        val vaccineName = etVaccineName.text.toString().trim()
        val vaccineNameEn = etVaccineNameEn.text.toString().trim()
        val vaccineFullName = etVaccineFullName.text.toString().trim()
        val manufacturer = etManufacturer.text.toString().trim()
        val country = etCountry.text.toString().trim()
        val provider = etProvider.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val batchNumber = etBatchNumber.text.toString().trim()

        if (vaccineName.isEmpty()) {
            etVaccineName.error = "Vaccine name is required"
            etVaccineName.requestFocus()
            return
        }

        if (manufacturer.isEmpty()) {
            etManufacturer.error = "Manufacturer is required"
            etManufacturer.requestFocus()
            return
        }

        // Check if personal info is set
        lifecycleScope.launch {
            try {
                val userAddress = WalletManager.getAddress()
                if (userAddress == null) {
                    showWalletNotConnectedDialog()
                    return@launch
                }
                
                val hasPersonalInfo = withContext(Dispatchers.IO) {
                    BlockchainService.hasPersonalInfo(userAddress)
                }

                if (!hasPersonalInfo) {
                    showPersonalInfoRequiredDialog()
                    return@launch
                }

                // Proceed with upload
                uploadVaccination(
                    vaccineName,
                    vaccineNameEn,
                    vaccineFullName,
                    manufacturer,
                    country,
                    provider,
                    location,
                    batchNumber
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error checking personal info", e)
                Toast.makeText(
                    this@AddVaccinationActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun uploadVaccination(
        vaccineName: String,
        vaccineNameEn: String,
        vaccineFullName: String,
        manufacturer: String,
        country: String,
        provider: String,
        location: String,
        batchNumber: String
    ) {
        lifecycleScope.launch {
            try {
                showProgressDialog("Preparing vaccination data...")

                // 1. Generate random AES key if not already generated (from certificate upload)
                if (randomAESKey == null) {
                    randomAESKey = EncryptionHelper.generateAESKey()
                    Log.d(TAG, "Generated random AES key for vaccination")
                }

                // 2. Encrypt the random key with user's wallet-derived key
                updateProgressDialog("Encrypting key with wallet...")
                encryptedKeyForBlockchain = withContext(Dispatchers.IO) {
                    EncryptionHelper.encryptKeyForBlockchain(randomAESKey!!)
                }
                Log.d(TAG, "Encrypted key for blockchain: ${encryptedKeyForBlockchain?.take(50)}...")

                // 3. Create vaccination metadata JSON
                val jsonString = createVaccinationMetadataJson(
                    vaccineName,
                    vaccineNameEn,
                    vaccineFullName,
                    manufacturer,
                    country,
                    provider,
                    location,
                    batchNumber
                )

                // 4. Encrypt metadata with the same random key
                updateProgressDialog("Encrypting vaccination data...")
                val encryptedData = withContext(Dispatchers.IO) {
                    EncryptionHelper.encryptDataWithKey(jsonString, randomAESKey!!)
                }

                // 5. Upload encrypted metadata to IPFS
                updateProgressDialog("Uploading encrypted data to IPFS...")
                val metadataIpfsHash = uploadEncryptedMetadata(encryptedData)

                if (metadataIpfsHash == null) {
                    dismissProgressDialog()
                    Toast.makeText(
                        this@AddVaccinationActivity,
                        "Failed to upload vaccination data",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                uploadedDataIpfsHash = metadataIpfsHash
                Log.d(TAG, "✅ Vaccination data uploaded to IPFS: $metadataIpfsHash")

                // 6. Save to blockchain
                saveToBlockchain(
                    metadataIpfsHash,
                    uploadedCertificateIpfsHash ?: ""
                )

            } catch (e: Exception) {
                dismissProgressDialog()
                e.printStackTrace()
                Log.e(TAG, "❌ Error uploading vaccination", e)
                Toast.makeText(
                    this@AddVaccinationActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createVaccinationMetadataJson(
        vaccineName: String,
        vaccineNameEn: String,
        vaccineFullName: String,
        manufacturer: String,
        country: String,
        provider: String,
        location: String,
        batchNumber: String
    ): String {
        val metadata = mapOf(
            "vaccineName" to vaccineName,
            "vaccineNameEn" to vaccineNameEn.ifEmpty { vaccineName },
            "vaccineFullName" to vaccineFullName.ifEmpty { vaccineName },
            "manufacturer" to manufacturer,
            "country" to country.ifEmpty { "Unknown" },
            "provider" to provider.ifEmpty { "Unknown Provider" },
            "location" to location.ifEmpty { "Unknown Location" },
            "batchNumber" to batchNumber.ifEmpty { "N/A" },
            "createdAt" to System.currentTimeMillis()
        )

        return org.json.JSONObject(metadata).toString()
    }

    private suspend fun uploadEncryptedMetadata(encryptedData: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Create encrypted file for upload (encryptedData is Base64 string)
                val encryptedFile = File(cacheDir, "vac_meta_enc_${System.currentTimeMillis()}.bin")
                encryptedFile.writeText(encryptedData)

                // Upload encrypted metadata to IPFS
                val requestFile = encryptedFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", encryptedFile.name, requestFile)

                val response = ApiClient.api.uploadToIPFS(filePart)

                // Clean up encrypted file
                encryptedFile.delete()

                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()!!.ipfsHash
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload metadata", e)
                null
            }
        }
    }

    private fun saveToBlockchain(dataIpfsHash: String, certificateIpfsHash: String) {
        lifecycleScope.launch {
            try {
                updateProgressDialog("Waiting for wallet signature...")

                // Convert date to Unix timestamp
                val vaccinationDate = BigInteger.valueOf(selectedDate.timeInMillis / 1000)

                Log.d(TAG, "Saving to blockchain:")
                Log.d(TAG, "- Data IPFS: $dataIpfsHash")
                Log.d(TAG, "- Certificate IPFS: $certificateIpfsHash")
                Log.d(TAG, "- Date: $vaccinationDate")
                Log.d(TAG, "- Encrypted Key: ${encryptedKeyForBlockchain?.take(50)}...")

                // Call blockchain service
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.addVaccination(
                        encryptedDataIpfsHash = dataIpfsHash,
                        encryptedCertificateIpfsHash = certificateIpfsHash,
                        vaccinationDate = vaccinationDate,
                        encryptedKey = encryptedKeyForBlockchain ?: ""
                    )
                }

                dismissProgressDialog()

                Log.d(TAG, "✅ Vaccination saved to blockchain!")
                Log.d(TAG, "Transaction hash: $txHash")

                // Show success dialog
                AlertDialog.Builder(this@AddVaccinationActivity)
                    .setTitle("✅ Success")
                    .setMessage("Vaccination record saved to blockchain!\n\nTransaction: ${txHash.take(20)}...")
                    .setPositiveButton("OK") { _, _ ->
                        setResult(RESULT_OK)
                        finish()
                    }
                    .setCancelable(false)
                    .show()

            } catch (e: Exception) {
                dismissProgressDialog()
                e.printStackTrace()
                Log.e(TAG, "❌ Blockchain error", e)

                AlertDialog.Builder(this@AddVaccinationActivity)
                    .setTitle("❌ Error")
                    .setMessage("Failed to save to blockchain: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showWalletNotConnectedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Wallet Not Connected")
            .setMessage("You need to connect your wallet to add vaccination records to the blockchain.")
            .setPositiveButton("Connect Wallet") { _, _ ->
                val intent = Intent(this, WalletInfoActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPersonalInfoRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Personal Info Required")
            .setMessage("You must set your personal information on the blockchain before adding vaccination records.\n\nGo to Profile and save your basic information first.")
            .setPositiveButton("Go to Profile") { _, _ ->
                val intent = Intent(this, ProfileActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showProgressDialog(message: String) {
        dismissProgressDialog()
        progressDialog = ProgressDialog(this).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }

    private fun updateProgressDialog(message: String) {
        progressDialog?.setMessage(message)
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissProgressDialog()
    }
}
