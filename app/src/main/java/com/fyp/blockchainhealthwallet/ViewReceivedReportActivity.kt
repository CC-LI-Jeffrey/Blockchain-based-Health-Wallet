package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.blockchain.RSAHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ViewReceivedReportActivity : AppCompatActivity() {

    private val TAG = "ViewReceivedReport"
    
    private lateinit var tvReportTitle: TextView
    private lateinit var tvReportType: TextView
    private lateinit var tvReportDate: TextView
    private lateinit var tvDoctorName: TextView
    private lateinit var tvHospital: TextView
    private lateinit var tvDescription: TextView
    private lateinit var cardAttachedFile: CardView
    private lateinit var tvFilePath: TextView
    private lateinit var ivFilePreview: ImageView
    
    private var decryptedFile: File? = null
    private var aesKey: javax.crypto.SecretKey? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_report)

        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        setupToolbar()
        setupViews()
        loadReportData()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up decrypted file when leaving
        decryptedFile?.delete()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Received Report"
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        tvReportTitle = findViewById(R.id.tvReportTitle)
        tvReportType = findViewById(R.id.tvReportType)
        tvReportDate = findViewById(R.id.tvReportDate)
        tvDoctorName = findViewById(R.id.tvDoctorName)
        tvHospital = findViewById(R.id.tvHospital)
        tvDescription = findViewById(R.id.tvDescription)
        cardAttachedFile = findViewById(R.id.cardAttachedFile)
        tvFilePath = findViewById(R.id.tvFilePath)
        ivFilePreview = findViewById(R.id.ivFilePreview)
    }

    private fun loadReportData() {
        val title = intent.getStringExtra("TITLE") ?: "N/A"
        val type = intent.getStringExtra("TYPE") ?: "N/A"
        val date = intent.getStringExtra("DATE") ?: "N/A"
        val doctor = intent.getStringExtra("DOCTOR") ?: "N/A"
        val hospital = intent.getStringExtra("HOSPITAL") ?: "N/A"
        val description = intent.getStringExtra("DESCRIPTION") ?: "N/A"
        val hasFile = intent.getBooleanExtra("HAS_FILE", false)
        val fileIpfsHash = intent.getStringExtra("FILE_IPFS_HASH") ?: ""
        val encryptedRecordKey = intent.getStringExtra("ENCRYPTED_RECORD_KEY") ?: ""

        tvReportTitle.text = title
        tvReportType.text = type
        tvReportDate.text = date
        tvDoctorName.text = doctor
        tvHospital.text = hospital
        tvDescription.text = description

        // Show attached file if available
        if (hasFile && fileIpfsHash.isNotEmpty() && encryptedRecordKey.isNotEmpty()) {
            cardAttachedFile.visibility = View.VISIBLE
            tvFilePath.text = "Downloading file..."
            
            // Decrypt the AES key first, then download and decrypt file
            decryptKeyAndFile(encryptedRecordKey, fileIpfsHash)
        } else {
            cardAttachedFile.visibility = View.GONE
        }
    }
    
    /**
     * Decrypt the RSA-encrypted AES key, then download and decrypt the file
     */
    private fun decryptKeyAndFile(encryptedRecordKey: String, fileIpfsHash: String) {
        lifecycleScope.launch {
            try {
                tvFilePath.text = "Decrypting access key..."
                
                // Step 1: Decrypt the AES key using our RSA private key
                aesKey = withContext(Dispatchers.IO) {
                    RSAHelper.decryptKeyWithPrivateKey(encryptedRecordKey)
                }
                
                Log.d(TAG, "Successfully decrypted AES key")
                
                // Step 2: Download and decrypt the file
                downloadAndDecryptFile(fileIpfsHash)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error decrypting key or file", e)
                tvFilePath.text = "Error: ${e.message}"
                Toast.makeText(this@ViewReceivedReportActivity, 
                    "Failed to decrypt file: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Download encrypted file from IPFS and decrypt it
     */
    private fun downloadAndDecryptFile(ipfsHash: String) {
        lifecycleScope.launch {
            try {
                tvFilePath.text = "Downloading file..."
                
                // 1. Download encrypted file from IPFS
                val encryptedFile = withContext(Dispatchers.IO) {
                    val response = ApiClient.api.getFromIPFS(ipfsHash)
                    if (!response.isSuccessful || response.body() == null) {
                        throw Exception("Failed to download file from IPFS")
                    }
                    
                    // Save encrypted file temporarily
                    val tempEncryptedFile = File(cacheDir, "encrypted_$ipfsHash")
                    val fileBytes = response.body()!!.bytes()
                    tempEncryptedFile.writeBytes(fileBytes)
                    
                    Log.d(TAG, "Downloaded encrypted file: ${fileBytes.size} bytes")
                    tempEncryptedFile
                }
                
                tvFilePath.text = "Decrypting file..."
                
                // 2. Decrypt file using the AES key
                decryptedFile = withContext(Dispatchers.IO) {
                    if (aesKey == null) {
                        throw Exception("AES key not available")
                    }
                    
                    val outputFile = File(cacheDir, "received_report_${System.currentTimeMillis()}.pdf")
                    
                    // Decrypt the file using the AES key
                    EncryptionHelper.decryptFile(
                        encryptedFile,
                        outputFile,
                        aesKey!!
                    )
                    
                    // Delete encrypted temp file
                    encryptedFile.delete()
                    
                    Log.d(TAG, "File decrypted successfully: ${outputFile.absolutePath}")
                    outputFile
                }
                
                // 3. Display file info and preview
                tvFilePath.text = decryptedFile?.name ?: "Decrypted file"
                
                // Try to show image preview if it's an image
                tryShowImagePreview(decryptedFile!!)
                
                // Setup click to open file
                cardAttachedFile.setOnClickListener {
                    openDecryptedFile()
                }
                
                Toast.makeText(this@ViewReceivedReportActivity, 
                    "File decrypted. Tap to open.", 
                    Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading/decrypting file", e)
                tvFilePath.text = "Error: ${e.message}"
                Toast.makeText(this@ViewReceivedReportActivity, 
                    "Failed to download file: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Try to show image preview if file is an image
     */
    private fun tryShowImagePreview(file: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                ivFilePreview.setImageBitmap(bitmap)
                ivFilePreview.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.d(TAG, "Not an image file or couldn't decode")
            ivFilePreview.visibility = View.GONE
        }
    }
    
    /**
     * Open the decrypted file with appropriate app
     */
    private fun openDecryptedFile() {
        decryptedFile?.let { file ->
            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                startActivity(Intent.createChooser(intent, "Open with"))
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error opening file", e)
            }
        }
    }
}
