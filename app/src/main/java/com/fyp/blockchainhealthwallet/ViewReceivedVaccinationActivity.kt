package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.RSAHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ViewReceivedVaccinationActivity : AppCompatActivity() {

    private val TAG = "ViewReceivedVaccination"
    
    private lateinit var tvShareInfo: TextView
    private lateinit var tvVaccineName: TextView
    private lateinit var tvVaccineNameEn: TextView
    private lateinit var tvVaccineFullName: TextView
    private lateinit var tvVaccinationDate: TextView
    private lateinit var tvManufacturer: TextView
    private lateinit var tvCountry: TextView
    private lateinit var tvBatchNumber: TextView
    private lateinit var tvProvider: TextView
    private lateinit var tvLocation: TextView
    private lateinit var cardNotes: CardView
    private lateinit var tvNotes: TextView
    private lateinit var cardAttachedCertificate: CardView
    private lateinit var tvCertificatePath: TextView
    private lateinit var ivCertificatePreview: ImageView
    
    private var decryptedCertFile: File? = null
    private var aesKey: javax.crypto.SecretKey? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_received_vaccination)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        setupToolbar()
        setupViews()
        loadVaccinationData()
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
        supportActionBar?.title = "Vaccination Details"
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        tvShareInfo = findViewById(R.id.tvShareInfo)
        tvVaccineName = findViewById(R.id.tvVaccineName)
        tvVaccineNameEn = findViewById(R.id.tvVaccineNameEn)
        tvVaccineFullName = findViewById(R.id.tvVaccineFullName)
        tvVaccinationDate = findViewById(R.id.tvVaccinationDate)
        tvManufacturer = findViewById(R.id.tvManufacturer)
        tvCountry = findViewById(R.id.tvCountry)
        tvBatchNumber = findViewById(R.id.tvBatchNumber)
        tvProvider = findViewById(R.id.tvProvider)
        tvLocation = findViewById(R.id.tvLocation)
        cardNotes = findViewById(R.id.cardNotes)
        tvNotes = findViewById(R.id.tvNotes)
        cardAttachedCertificate = findViewById(R.id.cardAttachedCertificate)
        tvCertificatePath = findViewById(R.id.tvCertificatePath)
        ivCertificatePreview = findViewById(R.id.ivCertificatePreview)
    }

    private fun loadVaccinationData() {
        val shareId = intent.getStringExtra("SHARE_ID") ?: ""
        val vaccineName = intent.getStringExtra("VACCINE_NAME") ?: "N/A"
        val vaccineNameEn = intent.getStringExtra("VACCINE_NAME_EN") ?: ""
        val vaccineFullName = intent.getStringExtra("VACCINE_FULL_NAME") ?: ""
        val manufacturer = intent.getStringExtra("MANUFACTURER") ?: "N/A"
        val country = intent.getStringExtra("COUNTRY") ?: "N/A"
        val batchNumber = intent.getStringExtra("BATCH_NUMBER") ?: "N/A"
        val provider = intent.getStringExtra("PROVIDER") ?: "N/A"
        val location = intent.getStringExtra("LOCATION") ?: "N/A"
        val notes = intent.getStringExtra("NOTES") ?: ""
        val vaccinationDateMs = intent.getLongExtra("VACCINATION_DATE", 0L)
        val hasCertificate = intent.getBooleanExtra("HAS_CERTIFICATE", false)
        val certificateIpfsHash = intent.getStringExtra("CERTIFICATE_IPFS_HASH") ?: ""
        val encryptedRecordKey = intent.getStringExtra("ENCRYPTED_RECORD_KEY") ?: ""

        // Set share info
        tvShareInfo.text = "Share ID: $shareId"

        // Set vaccine information
        tvVaccineName.text = vaccineName
        
        // Show English name if different from main name
        if (vaccineNameEn.isNotEmpty() && vaccineNameEn != vaccineName) {
            tvVaccineNameEn.visibility = View.VISIBLE
            tvVaccineNameEn.text = vaccineNameEn
        } else {
            tvVaccineNameEn.visibility = View.GONE
        }

        // Show full name if available
        if (vaccineFullName.isNotEmpty()) {
            tvVaccineFullName.text = vaccineFullName
        } else {
            tvVaccineFullName.visibility = View.GONE
        }

        // Format and set vaccination date
        if (vaccinationDateMs > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvVaccinationDate.text = dateFormat.format(Date(vaccinationDateMs))
        } else {
            tvVaccinationDate.text = "N/A"
        }

        // Set manufacturer, country, and batch number
        tvManufacturer.text = manufacturer
        tvCountry.text = country
        tvBatchNumber.text = batchNumber

        // Set provider information
        tvProvider.text = provider
        tvLocation.text = location

        // Show notes card only if notes exist
        if (notes.isNotEmpty() && notes != "N/A") {
            cardNotes.visibility = View.VISIBLE
            tvNotes.text = notes
        } else {
            cardNotes.visibility = View.GONE
        }
        
        // Show attached certificate if available
        if (hasCertificate && certificateIpfsHash.isNotEmpty() && encryptedRecordKey.isNotEmpty()) {
            cardAttachedCertificate.visibility = View.VISIBLE
            tvCertificatePath.text = "Downloading certificate..."
            
            // Decrypt the AES key first, then download and decrypt certificate
            decryptKeyAndCertificate(encryptedRecordKey, certificateIpfsHash)
        } else {
            cardAttachedCertificate.visibility = View.GONE
        }
    }
    
    /**
     * Decrypt the RSA-encrypted AES key, then download and decrypt the certificate
     */
    private fun decryptKeyAndCertificate(encryptedRecordKey: String, certificateIpfsHash: String) {
        lifecycleScope.launch {
            try {
                tvCertificatePath.text = "Decrypting access key..."
                
                // Step 1: Decrypt the AES key using our RSA private key
                aesKey = withContext(Dispatchers.IO) {
                    RSAHelper.decryptKeyWithPrivateKey(encryptedRecordKey)
                }
                
                Log.d(TAG, "Successfully decrypted AES key")
                
                // Step 2: Download and decrypt the certificate
                downloadAndDecryptCertificate(certificateIpfsHash)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error decrypting key or certificate", e)
                tvCertificatePath.text = "Error: ${e.message}"
            }
        }
    }
    
    /**
     * Download encrypted certificate from IPFS and decrypt it
     */
    private fun downloadAndDecryptCertificate(ipfsHash: String) {
        lifecycleScope.launch {
            try {
                tvCertificatePath.text = "Downloading certificate..."
                
                // 1. Download encrypted certificate from IPFS
                val encryptedFile = withContext(Dispatchers.IO) {
                    val response = ApiClient.api.getFromIPFS(ipfsHash)
                    if (!response.isSuccessful || response.body() == null) {
                        throw Exception("Failed to download certificate from IPFS")
                    }

                    val tempFile = File(cacheDir, "cert_${System.currentTimeMillis()}.enc")
                    val fileBytes = response.body()!!.bytes()
                    tempFile.writeBytes(fileBytes)
                    tempFile
                }
                
                Log.d(TAG, "Downloaded certificate from IPFS")
                
                // 2. Decrypt certificate
                tvCertificatePath.text = "Decrypting certificate..."
                
                val decryptedFile = withContext(Dispatchers.IO) {
                    val outputFile = File(cacheDir, "cert_${System.currentTimeMillis()}.dat")
                    
                    // Decrypt using AES/CBC
                    val encryptedBytes = encryptedFile.readBytes()
                    val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
                    val iv = encryptedBytes.copyOfRange(0, 16)
                    val encryptedData = encryptedBytes.copyOfRange(16, encryptedBytes.size)
                    
                    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, aesKey, javax.crypto.spec.IvParameterSpec(iv))
                    val decryptedBytes = cipher.doFinal(encryptedData)
                    
                    outputFile.writeBytes(decryptedBytes)
                    outputFile
                }
                
                // Clean up encrypted file
                encryptedFile.delete()
                
                // 3. Detect file type and update UI
                val header = decryptedFile.inputStream().use { it.readBytes().take(10).toByteArray() }
                Log.d(TAG, "Certificate header: ${header.joinToString(" ") { "%02x".format(it) }}")
                
                val (fileType, extension) = when {
                    header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() -> Pair("PDF", "pdf")
                    header.size >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> Pair("Image", "jpg")
                    header.size >= 2 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() -> Pair("Image", "png")
                    else -> Pair("Unknown", "dat")
                }
                
                // Rename file with correct extension
                val correctFile = if (decryptedFile.extension != extension) {
                    val renamed = File(decryptedFile.parent, "cert_${System.currentTimeMillis()}.$extension")
                    decryptedFile.copyTo(renamed, overwrite = true)
                    decryptedFile.delete()
                    renamed
                } else {
                    decryptedFile
                }
                
                decryptedCertFile = correctFile
                
                // Update UI
                tvCertificatePath.text = "vaccination_certificate.$extension"
                
                // Show preview if it's an image
                if (fileType == "Image") {
                    val bitmap = BitmapFactory.decodeFile(correctFile.absolutePath)
                    if (bitmap != null) {
                        ivCertificatePreview.setImageBitmap(bitmap)
                        ivCertificatePreview.visibility = View.VISIBLE
                    }
                }
                
                // Make certificate clickable to open
                cardAttachedCertificate.setOnClickListener {
                    viewCertificate()
                }
                
                Log.d(TAG, "✅ Certificate loaded successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading/decrypting certificate", e)
                tvCertificatePath.text = "Error loading certificate: ${e.message}"
            }
        }
    }
    
    /**
     * Open certificate in appropriate viewer
     */
    private fun viewCertificate() {
        if (decryptedCertFile == null) {
            return
        }

        val file = decryptedCertFile!!
        val extension = file.extension.lowercase()

        try {
            when (extension) {
                "pdf" -> {
                    val intent = Intent(this, PdfViewerActivity::class.java)
                    intent.putExtra("PDF_PATH", file.absolutePath)
                    intent.putExtra("TITLE", "Vaccination Certificate")
                    startActivity(intent)
                }
                in listOf("jpg", "jpeg", "png", "gif", "bmp") -> {
                    val intent = Intent(this, ImageViewerActivity::class.java)
                    intent.putExtra("IMAGE_PATH", file.absolutePath)
                    intent.putExtra("TITLE", "Vaccination Certificate")
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening certificate", e)
        }
    }
}
