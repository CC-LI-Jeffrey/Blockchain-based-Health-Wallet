package com.fyp.blockchainhealthwallet

import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import java.text.SimpleDateFormat
import java.util.*

class AddReportActivity : AppCompatActivity() {

    private lateinit var etReportTitle: TextInputEditText
    private lateinit var actvReportType: AutoCompleteTextView
    private lateinit var etDate: TextInputEditText
    private lateinit var etDoctorName: TextInputEditText
    private lateinit var etHospital: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var btnAttachFile: MaterialButton
    private lateinit var tvAttachedFile: TextView
    private lateinit var btnSaveReport: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private var selectedDate: Calendar = Calendar.getInstance()
    private var attachedFilePath: String? = null
    private var selectedFileUri: Uri? = null
    private var uploadedIpfsHash: String? = null
    private var encryptedKeyForBlockchain: String? = null
    private var progressDialog: ProgressDialog? = null
    
    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedFile(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_report)

        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        // Check wallet connection
        if (!BlockchainService.isWalletConnected()) {
            showWalletNotConnectedDialog()
            return
        }

        setupToolbar()
        setupViews()
        setupReportTypeDropdown()
    }
    
    private fun showWalletNotConnectedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Wallet Not Connected")
            .setMessage("You need to connect your wallet to add health records to the blockchain.")
            .setPositiveButton("Connect Wallet") { _, _ ->
                // Navigate to wallet connection screen
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
        etReportTitle = findViewById(R.id.etReportTitle)
        actvReportType = findViewById(R.id.actvReportType)
        etDate = findViewById(R.id.etDate)
        etDoctorName = findViewById(R.id.etDoctorName)
        etHospital = findViewById(R.id.etHospital)
        etDescription = findViewById(R.id.etDescription)
        btnAttachFile = findViewById(R.id.btnAttachFile)
        tvAttachedFile = findViewById(R.id.tvAttachedFile)
        btnSaveReport = findViewById(R.id.btnSaveReport)
        btnCancel = findViewById(R.id.btnCancel)

        // Set default date
        updateDateField()

        // Date picker
        etDate.setOnClickListener {
            showDatePicker()
        }

        // Attach file
        btnAttachFile.setOnClickListener {
            openFilePicker()
        }

        // Save button
        btnSaveReport.setOnClickListener {
            saveReport()
        }

        // Cancel button
        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupReportTypeDropdown() {
        val reportTypes = ReportType.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, reportTypes)
        actvReportType.setAdapter(adapter)
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                updateDateField()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateField() {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        etDate.setText(dateFormat.format(selectedDate.time))
    }

    private fun openFilePicker() {
        // Accept common medical document formats
        filePickerLauncher.launch("*/*")
    }
    
    private fun handleSelectedFile(uri: Uri) {
        selectedFileUri = uri
        val fileName = getFileName(uri)
        tvAttachedFile.text = fileName
        tvAttachedFile.visibility = View.VISIBLE
        
        // Start encryption and upload process
        encryptAndUploadFile(uri, fileName)
    }
    
    private fun encryptAndUploadFile(uri: Uri, fileName: String) {
        lifecycleScope.launch {
            try {
                showProgressDialog("Encrypting file...")
                
                // Create temporary file from URI
                val tempFile = createTempFileFromUri(uri, fileName)
                
                // Encrypt file locally
                updateProgressDialog("Encrypting file locally...")
                val (encryptedFile, encryptedKey) = withContext(Dispatchers.IO) {
                    EncryptionHelper.prepareFileForUpload(tempFile, cacheDir)
                }
                
                // Clean up original temp file
                tempFile.delete()
                
                // Save encrypted key for blockchain
                encryptedKeyForBlockchain = encryptedKey
                
                // Upload encrypted file to IPFS
                uploadEncryptedFileToIPFS(encryptedFile)
                
            } catch (e: Exception) {
                dismissProgressDialog()
                e.printStackTrace()
                Toast.makeText(
                    this@AddReportActivity,
                    "Encryption error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                
                // Clear file selection
                selectedFileUri = null
                tvAttachedFile.visibility = View.GONE
            }
        }
    }
    
    private suspend fun uploadEncryptedFileToIPFS(encryptedFile: File) {
        try {
            updateProgressDialog("Uploading encrypted file to IPFS...")
            
            val requestFile = encryptedFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", encryptedFile.name, requestFile)
            
            val response = withContext(Dispatchers.IO) {
                ApiClient.api.uploadToIPFS(filePart)
            }
            
            // Clean up encrypted file
            encryptedFile.delete()
            
            dismissProgressDialog()
            
            if (response.isSuccessful && response.body()?.success == true) {
                uploadedIpfsHash = response.body()!!.ipfsHash
                
                Toast.makeText(
                    this,
                    "File encrypted and uploaded to IPFS successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Upload failed"
                Toast.makeText(
                    this,
                    "IPFS upload failed: $errorMsg",
                    Toast.LENGTH_LONG
                ).show()
                
                // Clear file selection
                selectedFileUri = null
                tvAttachedFile.visibility = View.GONE
                uploadedIpfsHash = null
                encryptedKeyForBlockchain = null
            }
        } catch (e: Exception) {
            dismissProgressDialog()
            e.printStackTrace()
            Toast.makeText(
                this,
                "Upload error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            
            // Clear file selection
            selectedFileUri = null
            tvAttachedFile.visibility = View.GONE
            uploadedIpfsHash = null
            encryptedKeyForBlockchain = null
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var result = "Unknown"
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        result = it.getString(columnIndex)
                    }
                }
            }
        }
        if (result == "Unknown") {
            val path = uri.path
            val cut = path?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = path?.substring(cut + 1) ?: "Unknown"
            }
        }
        return result
    }
    
    
    private fun createTempFileFromUri(uri: Uri, fileName: String): File {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream")
        
        val tempFile = File(cacheDir, fileName)
        val outputStream = FileOutputStream(tempFile)
        
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        
        return tempFile
    }
    
    private fun showProgressDialog(message: String) {
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

    private fun saveReport() {
        // Validate inputs
        val title = etReportTitle.text.toString().trim()
        val typeString = actvReportType.text.toString().trim()
        val date = etDate.text.toString().trim()
        val doctorName = etDoctorName.text.toString().trim()
        val hospital = etHospital.text.toString().trim()
        val description = etDescription.text.toString().trim()

        if (title.isEmpty()) {
            etReportTitle.error = "Please enter report title"
            etReportTitle.requestFocus()
            return
        }

        if (typeString.isEmpty()) {
            Toast.makeText(this, "Please select report type", Toast.LENGTH_SHORT).show()
            actvReportType.requestFocus()
            return
        }

        if (doctorName.isEmpty()) {
            etDoctorName.error = "Please enter doctor name"
            etDoctorName.requestFocus()
            return
        }

        if (hospital.isEmpty()) {
            etHospital.error = "Please enter hospital/clinic name"
            etHospital.requestFocus()
            return
        }

        if (description.isEmpty()) {
            etDescription.error = "Please enter description"
            etDescription.requestFocus()
            return
        }
        
        // Check if file is uploaded to IPFS
        if (uploadedIpfsHash == null || encryptedKeyForBlockchain == null) {
            Toast.makeText(this, "Please attach and upload a file first", Toast.LENGTH_SHORT).show()
            return
        }

        // Save to blockchain
        saveToBlockchain(typeString)
    }
    
    private fun saveToBlockchain(typeString: String) {
        lifecycleScope.launch {
            try {
                showProgressDialog("Preparing blockchain transaction...")
                
                // Map UI type to blockchain RecordType enum
                val recordType = when (typeString) {
                    "Lab Report" -> BlockchainService.RecordType.LAB_REPORT
                    "Prescription" -> BlockchainService.RecordType.PRESCRIPTION
                    "Medical Image" -> BlockchainService.RecordType.MEDICAL_IMAGE
                    "Diagnosis" -> BlockchainService.RecordType.DIAGNOSIS
                    "Vaccination" -> BlockchainService.RecordType.VACCINATION
                    else -> BlockchainService.RecordType.VISIT_SUMMARY
                }
                
                updateProgressDialog("Sending request to wallet...\nPlease open your wallet app to approve")
                
                // User signs transaction with their wallet
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.addHealthRecord(
                        ipfsHash = uploadedIpfsHash!!,
                        recordType = recordType,
                        encryptedKey = encryptedKeyForBlockchain!!
                    )
                }
                
                dismissProgressDialog()
                
                // Check if transaction is pending approval
                if (txHash.startsWith("pending_")) {
                    AlertDialog.Builder(this@AddReportActivity)
                        .setTitle("⏳ Waiting for Approval")
                        .setMessage("Transaction request has been sent!\n\n" +
                                "📱 IMPORTANT: Open your wallet app now\n" +
                                "(MetaMask, Trust Wallet, etc.)\n\n" +
                                "You should see a pending transaction request to approve.\n\n" +
                                "✓ Approve it to save your health record on the blockchain\n" +
                                "✗ Reject it to cancel")
                        .setPositiveButton("I Opened My Wallet") { _, _ ->
                            // User acknowledged
                            Toast.makeText(
                                this@AddReportActivity,
                                "Check your wallet app for the pending request",
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    // Show success with transaction hash
                    AlertDialog.Builder(this@AddReportActivity)
                        .setTitle("Success!")
                        .setMessage("Health record saved on blockchain.\n\nTransaction: ${txHash.take(10)}...")
                        .setPositiveButton("OK") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }
                
            } catch (e: Exception) {
                dismissProgressDialog()
                
                val errorMessage = when {
                    e.message?.contains("redirect", ignoreCase = true) == true ->
                        "Please open your wallet app manually to approve the transaction"
                    e.message?.contains("user rejected", ignoreCase = true) == true -> 
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true -> 
                        "Insufficient funds for gas fees"
                    else -> "Transaction error: ${e.message}"
                }
                
                AlertDialog.Builder(this@AddReportActivity)
                    .setTitle("Transaction Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
