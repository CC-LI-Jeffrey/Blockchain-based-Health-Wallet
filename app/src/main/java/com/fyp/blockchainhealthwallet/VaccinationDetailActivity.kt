package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VaccinationDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vaccination_detail)

        setupUI()
        loadRecordDetails()
    }

    private fun setupUI() {
        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Setup view certificate button (will detect and open PDF or image)
        findViewById<androidx.cardview.widget.CardView>(R.id.btnViewRelatedRecords).setOnClickListener {
            val certificateHash = intent.getStringExtra("CERTIFICATE_HASH")
            val encryptedKey = intent.getStringExtra("ENCRYPTED_KEY")
            
            if (!certificateHash.isNullOrEmpty()) {
                downloadAndViewCertificate(certificateHash, encryptedKey ?: "")
            } else {
                Toast.makeText(this, "No certificate attached", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadRecordDetails() {
        // Get data from intent
        val date = intent.getStringExtra("DATE") ?: ""
        val vaccineName = intent.getStringExtra("VACCINE_NAME") ?: ""
        val vaccineNameEn = intent.getStringExtra("VACCINE_NAME_EN") ?: ""
        val vaccineFullName = intent.getStringExtra("VACCINE_FULL_NAME") ?: ""
        val manufacturer = intent.getStringExtra("MANUFACTURER") ?: ""
        val country = intent.getStringExtra("COUNTRY") ?: ""
        val provider = intent.getStringExtra("PROVIDER") ?: ""
        val location = intent.getStringExtra("LOCATION") ?: ""
        val batchNumber = intent.getStringExtra("BATCH_NUMBER") ?: ""

        // Set data to views
        findViewById<TextView>(R.id.tvDate).text = date
        findViewById<TextView>(R.id.tvVaccineName).text = vaccineName
        findViewById<TextView>(R.id.tvVaccineNameEn).text = vaccineNameEn
        findViewById<TextView>(R.id.tvVaccineFullName).text = vaccineFullName
        findViewById<TextView>(R.id.tvManufacturer).text = manufacturer
        findViewById<TextView>(R.id.tvCountry).text = country
        findViewById<TextView>(R.id.tvProvider).text = provider
        findViewById<TextView>(R.id.tvLocation).text = location
        findViewById<TextView>(R.id.tvBatchNumber).text = batchNumber
    }

    /**
     * Download, decrypt, and view certificate (PDF or Image)
     * Same logic as ViewReportActivity
     */
    private fun downloadAndViewCertificate(ipfsHash: String, encryptedKey: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@VaccinationDetailActivity, "Loading certificate...", Toast.LENGTH_SHORT).show()
                
                // Download encrypted file from IPFS
                val encryptedFile = withContext(Dispatchers.IO) {
                    val response = ApiClient.api.getFromIPFS(ipfsHash)
                    if (!response.isSuccessful || response.body() == null) {
                        throw Exception("Failed to download file from IPFS")
                    }
                    
                    // Save encrypted file temporarily
                    val tempFile = File(cacheDir, "encrypted_cert_${System.currentTimeMillis()}.enc")
                    val fileBytes = response.body()!!.bytes()
                    tempFile.writeBytes(fileBytes)
                    tempFile
                }
                
                // Decrypt file
                val decryptedFile = withContext(Dispatchers.IO) {
                    val outputFile = File(cacheDir, "certificate_${System.currentTimeMillis()}.dat")
                    
                    if (encryptedKey.isNotEmpty()) {
                        EncryptionHelper.decryptDownloadedFile(
                            encryptedFile,
                            encryptedKey,
                            outputFile
                        )
                        outputFile
                    } else {
                        throw Exception("No encryption key available")
                    }
                }
                
                // Clean up encrypted file
                encryptedFile.delete()
                
                // Detect file type and open appropriate viewer
                decryptedFile?.let { file ->
                    val header = file.inputStream().use { it.readBytes().take(10).toByteArray() }
                    Log.d("VaccinationDetail", "File header: ${header.joinToString(" ") { "%02x".format(it) }}")
                    
                    val (fileType, extension) = when {
                        header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() -> Pair("PDF", "pdf")
                        header.size >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> Pair("Image", "jpg")
                        header.size >= 2 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() -> Pair("Image", "png")
                        else -> Pair("Unknown", "dat")
                    }
                    
                    // Rename file with correct extension
                    val correctFile = if (file.extension != extension) {
                        val renamed = File(file.parent, "certificate_${System.currentTimeMillis()}.$extension")
                        file.copyTo(renamed, overwrite = true)
                        file.delete()
                        renamed
                    } else {
                        file
                    }
                    
                    // Open appropriate viewer
                    when (fileType) {
                        "PDF" -> {
                            val intent = Intent(this@VaccinationDetailActivity, PdfViewerActivity::class.java)
                            intent.putExtra("PDF_PATH", correctFile.absolutePath)
                            startActivity(intent)
                        }
                        "Image" -> {
                            val intent = Intent(this@VaccinationDetailActivity, ImageViewerActivity::class.java)
                            intent.putExtra("IMAGE_PATH", correctFile.absolutePath)
                            startActivity(intent)
                        }
                        else -> {
                            Toast.makeText(
                                this@VaccinationDetailActivity,
                                "Unsupported file type",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } ?: run {
                    Toast.makeText(
                        this@VaccinationDetailActivity,
                        "Failed to decrypt certificate",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: Exception) {
                Log.e("VaccinationDetail", "Error loading certificate", e)
                Toast.makeText(
                    this@VaccinationDetailActivity,
                    "Error loading certificate: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
