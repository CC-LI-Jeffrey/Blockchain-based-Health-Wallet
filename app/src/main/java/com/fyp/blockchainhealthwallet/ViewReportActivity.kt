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
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger

class ViewReportActivity : AppCompatActivity() {

    private lateinit var tvReportTitle: TextView
    private lateinit var tvReportType: TextView
    private lateinit var tvReportDate: TextView
    private lateinit var tvDoctorName: TextView
    private lateinit var tvHospital: TextView
    private lateinit var tvDescription: TextView
    private lateinit var cardAttachedFile: CardView
    private lateinit var tvFilePath: TextView
    private lateinit var ivFilePreview: ImageView
    
    private var currentReport: Report? = null
    private var decryptedFile: File? = null

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

        val btnPartialShareReport = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPartialShareReport)
        btnPartialShareReport?.setOnClickListener {
            openPartialShareActivity()
        }

        val btnDeleteReport = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDeleteReport)
        btnDeleteReport?.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun loadReportData() {
        val report = intent.getParcelableExtra<Report>("REPORT")

        if (report != null) {
            currentReport = report
            
            tvReportTitle.text = report.title
            tvReportType.text = report.reportType.displayName
            tvReportDate.text = report.date
            tvDoctorName.text = report.doctorName
            tvHospital.text = report.hospital
            tvDescription.text = report.description

            // Show attached file if available
            if (report.filePath != null) {
                cardAttachedFile.visibility = View.VISIBLE
                tvFilePath.text = "Downloading file..."
                
                // Automatically download and decrypt file on page load
                downloadAndDecryptFile(report.filePath!!)
            } else {
                cardAttachedFile.visibility = View.GONE
            }
        } else {
            // If no report data, finish activity
            finish()
        }
    }
    
    /**
     * Download encrypted file from IPFS and decrypt it automatically
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
                    tempEncryptedFile
                }
                
                tvFilePath.text = "Decrypting file..."
                
                // 2. Decrypt file using random key
                decryptedFile = withContext(Dispatchers.IO) {
                    val outputFile = File(cacheDir, "report_${System.currentTimeMillis()}.pdf")
                    
                    // Use the encryptedKey from the report
                    val encryptedKey = currentReport?.encryptedKey ?: ""
                    if (encryptedKey.isNotEmpty()) {
                        EncryptionHelper.decryptDownloadedFile(
                            encryptedFile,
                            encryptedKey,
                            outputFile
                        )
                    } else {
                        throw Exception("No encryption key found for this report")
                    }
                    
                    // Delete encrypted temp file
                    encryptedFile.delete()
                    
                    outputFile
                }
                
                // 3. File ready - show tap to view
                Log.d("ViewReportActivity", "File decrypted successfully: ${decryptedFile?.absolutePath}")
                Log.d("ViewReportActivity", "File size: ${decryptedFile?.length()} bytes")
                Log.d("ViewReportActivity", "File exists: ${decryptedFile?.exists()}")
                
                // Detect actual file type from magic bytes
                var fileType = "File"
                decryptedFile?.let { file ->
                    try {
                        val header = file.inputStream().use { it.readBytes().take(10).toByteArray() }
                        Log.d("ViewReportActivity", "File header: ${header.joinToString(" ") { "%02x".format(it) }}")
                        
                        // Detect file type
                        val (detectedType, extension) = when {
                            header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() -> Pair("PDF", "pdf")
                            header.size >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> Pair("Image", "jpg")
                            header.size >= 2 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() -> Pair("Image", "png")
                            else -> Pair("File", "dat")
                        }
                        
                        fileType = detectedType
                        Log.d("ViewReportActivity", "Detected file type: $fileType")
                        
                        // Rename file with correct extension using copy instead of rename
                        if (file.extension != extension) {
                            val correctFile = File(file.parent, "report_${System.currentTimeMillis()}.$extension")
                            file.copyTo(correctFile, overwrite = true)
                            file.delete()
                            decryptedFile = correctFile
                            Log.d("ViewReportActivity", "Renamed to: ${correctFile.absolutePath}")
                        }
                        
                        tvFilePath.text = "Tap to view $fileType"
                        
                        // If it's an image, display it inline
                        if (fileType == "Image") {
                            try {
                                val bitmap = BitmapFactory.decodeFile(decryptedFile?.absolutePath)
                                if (bitmap != null) {
                                    ivFilePreview.setImageBitmap(bitmap)
                                    ivFilePreview.visibility = View.VISIBLE
                                    tvFilePath.text = "Image Preview (tap to view fullscreen)"
                                    Log.d("ViewReportActivity", "Image displayed inline")
                                }
                            } catch (e: Exception) {
                                Log.e("ViewReportActivity", "Error displaying image", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ViewReportActivity", "Error detecting file type", e)
                        tvFilePath.text = "Tap to view file"
                    }
                }
                
                // Click to open externally or view fullscreen
                cardAttachedFile.setOnClickListener {
                    decryptedFile?.let { openFile(it) }
                }
                
            } catch (e: Exception) {
                Log.e("ViewReportActivity", "Error downloading/decrypting file", e)
                tvFilePath.text = "Failed to load file"
                Toast.makeText(
                    this@ViewReportActivity,
                    "Failed to load file: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Open file with system viewer (PDF viewer, image viewer, etc.)
     */
    private fun openFile(file: File) {
        try {
            Log.d("ViewReportActivity", "Opening file: ${file.absolutePath}")
            Log.d("ViewReportActivity", "File exists: ${file.exists()}")
            Log.d("ViewReportActivity", "File size: ${file.length()} bytes")
            
            if (!file.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (file.length() == 0L) {
                Toast.makeText(this, "File is empty", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Check file extension
            when (file.extension.lowercase()) {
                "jpg", "jpeg", "png", "gif" -> {
                    // Open in custom image viewer
                    Log.d("ViewReportActivity", "Opening ImageViewerActivity with path: ${file.absolutePath}")
                    val intent = Intent(this, ImageViewerActivity::class.java)
                    intent.putExtra("IMAGE_PATH", file.absolutePath)
                    startActivity(intent)
                }
                "pdf" -> {
                    // Try to open PDF externally
                    openExternalViewer(file, "application/pdf")
                }
                else -> {
                    // Try generic viewer
                    openExternalViewer(file, "*/*")
                }
            }
        } catch (e: Exception) {
            Log.e("ViewReportActivity", "Error opening file", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun openExternalViewer(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val activities = packageManager.queryIntentActivities(intent, 0)
            if (activities.isNotEmpty()) {
                startActivity(Intent.createChooser(intent, "Open with"))
            } else {
                Toast.makeText(
                    this,
                    "No app found to open ${file.extension} files",
                    Toast.LENGTH_LONG
                ).show()
            }
            
        } catch (e: Exception) {
            Log.e("ViewReportActivity", "Error opening file", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeleteConfirmationDialog() {
        val report = currentReport ?: return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Report")
            .setMessage("Are you sure you want to delete this medical report?\n\nNote: This will mark it as deleted but data remains on blockchain. Shared records remain accessible to recipients.")
            .setPositiveButton("Delete") { _, _ ->
                performDelete(report.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDelete(reportId: String) {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Deleting report...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val repId = BigInteger(reportId)
                val txHash = BlockchainService.deleteReport(repId)

                progressDialog.dismiss()

                if (txHash.startsWith("pending_")) {
                    androidx.appcompat.app.AlertDialog.Builder(this@ViewReportActivity)
                        .setTitle("⏳ Waiting for Approval")
                        .setMessage("Delete request sent!\n\n📱 Open your wallet app to approve.")
                        .setPositiveButton("OK") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    androidx.appcompat.app.AlertDialog.Builder(this@ViewReportActivity)
                        .setTitle("✅ Deleted")
                        .setMessage("Report deleted successfully.\n\nTransaction: ${txHash.take(10)}...")
                        .setPositiveButton("OK") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }

            } catch (e: Exception) {
                progressDialog.dismiss()
                android.util.Log.e("ViewReportActivity", "Error deleting report", e)

                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true ->
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true ->
                        "Insufficient funds for gas fees"
                    e.message?.contains("Already deleted", ignoreCase = true) == true ->
                        "This report has already been deleted"
                    else -> "Delete failed: ${e.message}"
                }

                androidx.appcompat.app.AlertDialog.Builder(this@ViewReportActivity)
                    .setTitle("Delete Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    private fun openPartialShareActivity() {
        val report = currentReport
        if (report == null) {
            Toast.makeText(this, "Report data not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Collect all report data
        val recordData = mapOf(
            "title" to report.title,
            "reportType" to report.reportType.name,
            "reportTypeDisplay" to report.reportType.displayName,
            "date" to report.date,
            "doctorName" to report.doctorName,
            "hospital" to report.hospital,
            "description" to report.description,
            "filePath" to (report.filePath ?: ""),
            "ipfsHash" to (report.ipfsHash ?: ""),
            "timestamp" to report.timestamp.toString(),
            "encryptedKey" to report.encryptedKey
        )
        
        try {
            val intent = android.content.Intent(this, Class.forName("com.fyp.blockchainhealthwallet.ui.partialshare.PartialShareActivity"))
            intent.putExtra("RECORD_ID", report.id)
            intent.putExtra("RECORD_TYPE", "MEDICAL_REPORT")
            intent.putExtra("RECORD_DATA", kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<Map<String, String>>(),
                recordData
            ))
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ViewReportActivity", "Error opening partial share", e)
            Toast.makeText(this, "Partial share feature not available: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

