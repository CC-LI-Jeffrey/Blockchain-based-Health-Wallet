package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewVaccinationActivity - Displays full vaccination details
 * Similar pattern to ViewMedicationActivity and ViewReportActivity
 */
class ViewVaccinationActivity : AppCompatActivity() {

    private lateinit var tvVaccineName: TextView
    private lateinit var tvVaccineNameEn: TextView
    private lateinit var tvVaccineFullName: TextView
    private lateinit var tvManufacturer: TextView
    private lateinit var tvCountry: TextView
    private lateinit var tvProvider: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvBatchNumber: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvCreatedAt: TextView
    private lateinit var cardCertificate: CardView
    private lateinit var tvCertificateStatus: TextView
    private lateinit var ivCertificatePreview: ImageView
    private lateinit var btnViewCertificate: MaterialButton
    private lateinit var btnShareVaccination: MaterialButton
    private lateinit var btnDeleteVaccination: MaterialButton

    private var vaccinationId: String? = null
    private var certificateIpfsHash: String? = null
    private var encryptedKey: String? = null
    private var decryptedCertFile: File? = null

    companion object {
        private const val TAG = "ViewVaccinationActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_vaccination)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        BlockchainService.initialize(this)

        setupToolbar()
        setupViews()
        loadDataFromIntent()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up decrypted certificate file when leaving
        decryptedCertFile?.delete()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        tvVaccineName = findViewById(R.id.tvVaccineName)
        tvVaccineNameEn = findViewById(R.id.tvVaccineNameEn)
        tvVaccineFullName = findViewById(R.id.tvVaccineFullName)
        tvManufacturer = findViewById(R.id.tvManufacturer)
        tvCountry = findViewById(R.id.tvCountry)
        tvProvider = findViewById(R.id.tvProvider)
        tvLocation = findViewById(R.id.tvLocation)
        tvBatchNumber = findViewById(R.id.tvBatchNumber)
        tvDate = findViewById(R.id.tvDate)
        tvCreatedAt = findViewById(R.id.tvCreatedAt)
        cardCertificate = findViewById(R.id.cardCertificate)
        tvCertificateStatus = findViewById(R.id.tvCertificateStatus)
        ivCertificatePreview = findViewById(R.id.ivCertificatePreview)
        btnViewCertificate = findViewById(R.id.btnViewCertificate)
        btnShareVaccination = findViewById(R.id.btnShareVaccination)
        btnDeleteVaccination = findViewById(R.id.btnDeleteVaccination)

        btnViewCertificate.setOnClickListener {
            if (certificateIpfsHash != null) {
                viewCertificate()
            } else {
                Toast.makeText(this, "No certificate available", Toast.LENGTH_SHORT).show()
            }
        }

        btnShareVaccination.setOnClickListener {
            // Vaccination sharing can follow MedicationShareHelper pattern
            Toast.makeText(this, "Vaccination sharing - implementation pending", Toast.LENGTH_SHORT).show()
        }

        btnDeleteVaccination.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun loadDataFromIntent() {
        // Get data from intent
        vaccinationId = intent.getStringExtra("RECORD_ID")
        val date = intent.getStringExtra("DATE") ?: ""
        val vaccineName = intent.getStringExtra("VACCINE_NAME") ?: ""
        val vaccineNameEn = intent.getStringExtra("VACCINE_NAME_EN") ?: ""
        val vaccineFullName = intent.getStringExtra("VACCINE_FULL_NAME") ?: ""
        val manufacturer = intent.getStringExtra("MANUFACTURER") ?: ""
        val country = intent.getStringExtra("COUNTRY") ?: ""
        val provider = intent.getStringExtra("PROVIDER") ?: ""
        val location = intent.getStringExtra("LOCATION") ?: ""
        val batchNumber = intent.getStringExtra("BATCH_NUMBER") ?: ""
        certificateIpfsHash = intent.getStringExtra("CERTIFICATE_HASH")
        encryptedKey = intent.getStringExtra("ENCRYPTED_KEY")

        // Set data to views
        tvVaccineName.text = vaccineName
        tvVaccineNameEn.text = vaccineNameEn
        tvVaccineFullName.text = vaccineFullName
        tvManufacturer.text = manufacturer
        tvCountry.text = country
        tvProvider.text = provider
        tvLocation.text = location
        tvBatchNumber.text = batchNumber
        tvDate.text = date

        // Format created at date if available
        val createdAtTimestamp = intent.getLongExtra("CREATED_AT", 0L)
        if (createdAtTimestamp > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            tvCreatedAt.text = "Added on ${dateFormat.format(Date(createdAtTimestamp * 1000))}"
        } else {
            tvCreatedAt.visibility = View.GONE
        }

        // Show certificate section if available
        if (certificateIpfsHash != null && certificateIpfsHash!!.isNotEmpty()) {
            cardCertificate.visibility = View.VISIBLE
            tvCertificateStatus.text = "Certificate available - Click to view"
            // Auto-load certificate preview
            loadCertificatePreview()
        } else {
            cardCertificate.visibility = View.GONE
        }
    }

    private fun loadCertificatePreview() {
        if (certificateIpfsHash == null || encryptedKey == null) return

        lifecycleScope.launch {
            try {
                tvCertificateStatus.text = "Loading certificate preview..."

                val certFile = withContext(Dispatchers.IO) {
                    downloadAndDecryptCertificate(certificateIpfsHash!!, encryptedKey!!)
                }

                if (certFile != null) {
                    decryptedCertFile = certFile
                    
                    // Try to show preview if it's an image
                    val extension = certFile.extension.lowercase()
                    if (extension in listOf("jpg", "jpeg", "png", "gif", "bmp")) {
                        val bitmap = BitmapFactory.decodeFile(certFile.absolutePath)
                        if (bitmap != null) {
                            ivCertificatePreview.setImageBitmap(bitmap)
                            ivCertificatePreview.visibility = View.VISIBLE
                            tvCertificateStatus.text = "Certificate loaded (Image)"
                        } else {
                            tvCertificateStatus.text = "Certificate ready to view"
                        }
                    } else if (extension == "pdf") {
                        tvCertificateStatus.text = "Certificate loaded (PDF)"
                    } else {
                        tvCertificateStatus.text = "Certificate loaded"
                    }
                } else {
                    tvCertificateStatus.text = "Failed to load certificate"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading certificate preview", e)
                tvCertificateStatus.text = "Certificate available - Click to view"
            }
        }
    }

    private suspend fun downloadAndDecryptCertificate(ipfsHash: String, encKey: String): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading certificate from IPFS: $ipfsHash")

            // 1. Download encrypted certificate from IPFS
            val response = ApiClient.api.getFromIPFS(ipfsHash)
            if (!response.isSuccessful || response.body() == null) {
                Log.e(TAG, "Failed to download certificate")
                return@withContext null
            }

            // Save encrypted file temporarily
            val tempEncryptedFile = File(cacheDir, "encrypted_cert_$ipfsHash")
            val encryptedBytes = response.body()!!.bytes()
            tempEncryptedFile.writeBytes(encryptedBytes)
            Log.d(TAG, "Downloaded ${encryptedBytes.size} bytes")

            // 2. Decrypt certificate using the encrypted key from blockchain
            val outputFile = File(cacheDir, "vaccination_cert_${System.currentTimeMillis()}.tmp")
            
            if (encKey.isNotEmpty()) {
                EncryptionHelper.decryptDownloadedFile(
                    tempEncryptedFile,
                    encKey,
                    outputFile
                )
            } else {
                Log.e(TAG, "No encryption key provided")
                tempEncryptedFile.delete()
                return@withContext null
            }

            // Delete encrypted temp file
            tempEncryptedFile.delete()

            // 3. Detect file type and rename with proper extension
            val fileBytes = outputFile.readBytes()
            val fileType = detectFileType(fileBytes)
            val finalFile = if (fileType != null) {
                val renamedFile = File(outputFile.parent, "vaccination_cert_${System.currentTimeMillis()}.$fileType")
                outputFile.copyTo(renamedFile, overwrite = true)
                outputFile.delete()
                renamedFile
            } else {
                outputFile
            }

            Log.d(TAG, "Certificate saved: ${finalFile.absolutePath}")
            finalFile

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading/decrypting certificate", e)
            null
        }
    }

    private fun detectFileType(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        
        return when {
            // PDF signature
            bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && 
            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte() -> "pdf"
            
            // PNG signature
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && 
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
            
            // JPEG signature
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            
            else -> null
        }
    }

    private fun viewCertificate() {
        if (decryptedCertFile == null) {
            Toast.makeText(this, "Certificate not loaded yet. Please wait...", Toast.LENGTH_SHORT).show()
            loadCertificatePreview()
            return
        }

        val file = decryptedCertFile!!
        val extension = file.extension.lowercase()

        try {
            when (extension) {
                "pdf" -> {
                    val intent = Intent(this, PdfViewerActivity::class.java)
                    intent.putExtra("PDF_FILE_PATH", file.absolutePath)
                    startActivity(intent)
                }
                in listOf("jpg", "jpeg", "png", "gif", "bmp") -> {
                    val intent = Intent(this, ImageViewerActivity::class.java)
                    intent.putExtra("IMAGE_FILE_PATH", file.absolutePath)
                    startActivity(intent)
                }
                else -> {
                    Toast.makeText(this, "Unsupported file type: $extension", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening certificate", e)
            Toast.makeText(this, "Error opening certificate: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Vaccination")
            .setMessage("Are you sure you want to delete this vaccination record?\n\nNote: This will mark it as deleted but data remains on blockchain. Shared records remain accessible to recipients.")
            .setPositiveButton("Delete") { _, _ ->
                performDelete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDelete() {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Deleting vaccination...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val vaccId = BigInteger(vaccinationId)
                val txHash = BlockchainService.deleteVaccination(vaccId)

                progressDialog.dismiss()

                if (txHash.startsWith("pending_")) {
                    androidx.appcompat.app.AlertDialog.Builder(this@ViewVaccinationActivity)
                        .setTitle("⏳ Waiting for Approval")
                        .setMessage("Delete request sent!\n\n📱 Open your wallet app to approve.")
                        .setPositiveButton("OK") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    androidx.appcompat.app.AlertDialog.Builder(this@ViewVaccinationActivity)
                        .setTitle("✅ Deleted")
                        .setMessage("Vaccination deleted successfully.\n\nTransaction: ${txHash.take(10)}...")
                        .setPositiveButton("OK") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }

            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error deleting vaccination", e)

                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true ->
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true ->
                        "Insufficient funds for gas fees"
                    e.message?.contains("Already deleted", ignoreCase = true) == true ->
                        "This vaccination has already been deleted"
                    else -> "Delete failed: ${e.message}"
                }

                androidx.appcompat.app.AlertDialog.Builder(this@ViewVaccinationActivity)
                    .setTitle("Delete Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
