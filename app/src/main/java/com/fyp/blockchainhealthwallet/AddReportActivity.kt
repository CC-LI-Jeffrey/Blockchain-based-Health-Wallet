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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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
    private var uploadedFileUrl: String? = null
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

        setupToolbar()
        setupViews()
        setupReportTypeDropdown()
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
        
        // Upload file to IPFS
        uploadFileToIPFS(uri, fileName)
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
    
    private fun uploadFileToIPFS(uri: Uri, fileName: String) {
        lifecycleScope.launch {
            try {
                showProgressDialog("Uploading file to IPFS...")
                
                // Create temporary file from URI
                val file = createTempFileFromUri(uri, fileName)
                
                // Get file metadata
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val fileSize = file.length()
                
                // Prepare metadata
                val metadata = mapOf(
                    "reportType" to (actvReportType.text.toString().takeIf { it.isNotEmpty() } ?: "Medical Report"),
                    "uploadDate" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                )
                val metadataJson = Gson().toJson(metadata)
                
                // Create multipart request
                val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", fileName, requestFile)
                val metadataBody = metadataJson.toRequestBody("application/json".toMediaTypeOrNull())
                
                // Upload file
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.uploadFile(filePart, metadataBody)
                }
                
                // Clean up temp file
                file.delete()
                
                dismissProgressDialog()
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    uploadedIpfsHash = data?.ipfsHash
                    uploadedFileUrl = data?.fileUrl
                    attachedFilePath = data?.fileUrl

                    Toast.makeText(
                        this@AddReportActivity,
                        "File uploaded successfully to IPFS",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val errorMsg = response.body()?.error ?: response.message() ?: "Upload failed"
                    Toast.makeText(
                        this@AddReportActivity,
                        "Upload failed: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Clear file selection
                    selectedFileUri = null
                    tvAttachedFile.visibility = View.GONE
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                e.printStackTrace()
                Toast.makeText(
                    this@AddReportActivity,
                    "Upload error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                
                // Clear file selection
                selectedFileUri = null
                tvAttachedFile.visibility = View.GONE
            }
        }
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

        // Find the report type
        val reportType = ReportType.values().find { it.displayName == typeString } 
            ?: ReportType.OTHER

        // Create new report
        val newReport = Report(
            id = UUID.randomUUID().toString(),
            title = title,
            reportType = reportType,
            date = date,
            doctorName = doctorName,
            hospital = hospital,
            description = description,
            filePath = attachedFilePath,
            ipfsHash = uploadedIpfsHash,
            timestamp = System.currentTimeMillis()
        )

        // Return result
        val resultIntent = Intent().apply {
            putExtra("NEW_REPORT", newReport)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
