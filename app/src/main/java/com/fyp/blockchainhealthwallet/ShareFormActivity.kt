package com.fyp.blockchainhealthwallet

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.databinding.ActivityShareFormBinding
import com.fyp.blockchainhealthwallet.ui.BlockchainHelper
import com.fyp.blockchainhealthwallet.ui.MedicationShareHelper
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigInteger

/**
 * Activity for entering recipient details and sharing record
 * Step 2 of the sharing process - Simplified version
 */
class ShareFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareFormBinding
    private var recordId: String = ""
    private var recordCategory: String = ""
    private var recordTitle: String = ""

    // QR Scanner result launcher
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scannedData = result.data?.getStringExtra("SCAN_RESULT")
            if (scannedData != null) {
                handleScannedQRCode(scannedData)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recordId = intent.getStringExtra("RECORD_ID") ?: ""
        recordCategory = intent.getStringExtra("RECORD_CATEGORY") ?: ""
        recordTitle = intent.getStringExtra("RECORD_TITLE") ?: ""

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        binding.tvRecordInfo.text = "Sharing: $recordTitle"
        
        // Setup recipient type spinner
        val recipientTypes = resources.getStringArray(R.array.recipient_types)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, recipientTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRecipientType.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnScanQR.setOnClickListener {
            launchQRScanner()
        }

        binding.btnManualInput.setOnClickListener {
            // Just indicate user can type
            binding.etRecipientAddress.requestFocus()
            Toast.makeText(this, "Enter recipient address manually", Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            performShare()
        }
    }

    private fun launchQRScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        qrScannerLauncher.launch(intent)
    }

    private fun handleScannedQRCode(scannedData: String) {
        try {
            // Try parsing as JSON first
            val json = JSONObject(scannedData)
            val address = json.optString("address", "")
            if (address.isNotEmpty()) {
                binding.etRecipientAddress.setText(address)
                // Optional: Also fill name if available
                val name = json.optString("name", "")
                if (name.isNotEmpty()) {
                    binding.etRecipientName.setText(name)
                }
                Toast.makeText(this, "QR code scanned successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // If not JSON, treat as plain address
            if (scannedData.startsWith("0x")) {
                binding.etRecipientAddress.setText(scannedData)
                Toast.makeText(this, "QR code scanned successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid wallet address", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performShare() {
        val recipientAddress = binding.etRecipientAddress.text.toString().trim()
        val recipientName = binding.etRecipientName.text.toString().trim()
        val durationText = binding.etDuration.text.toString().trim()
        val recipientType = binding.spinnerRecipientType.selectedItem.toString()

        // Validation
        if (recipientAddress.isEmpty()) {
            Toast.makeText(this, "Please enter recipient address", Toast.LENGTH_SHORT).show()
            return
        }

        if (recipientName.isEmpty()) {
            Toast.makeText(this, "Please enter recipient name", Toast.LENGTH_SHORT).show()
            return
        }

        if (durationText.isEmpty()) {
            Toast.makeText(this, "Please enter duration", Toast.LENGTH_SHORT).show()
            return
        }

        val durationDays = durationText.toLongOrNull() ?: 0L
        if (durationDays <= 0) {
            Toast.makeText(this, "Please enter a valid duration", Toast.LENGTH_SHORT).show()
            return
        }

        // Map recipient type
        val recipientTypeEnum = when (recipientType) {
            "Doctor" -> BlockchainService.RecipientType.DOCTOR
            "Hospital" -> BlockchainService.RecipientType.HOSPITAL
            "Clinic" -> BlockchainService.RecipientType.CLINIC
            "Insurance Company" -> BlockchainService.RecipientType.INSURANCE_COMPANY
            "Pharmacy" -> BlockchainService.RecipientType.PHARMACY
            "Laboratory" -> BlockchainService.RecipientType.LABORATORY
            else -> BlockchainService.RecipientType.OTHER
        }

        val expiryTimestamp = (System.currentTimeMillis() / 1000 + (durationDays * 24 * 60 * 60)).toBigInteger()

        // Call appropriate share method based on category
        when (recordCategory) {
            "PERSONAL_INFO" -> {
                // Personal info sharing
                BlockchainHelper.sharePersonalInfo(
                    context = this,
                    lifecycleScope = lifecycleScope,
                    recipientAddress = recipientAddress,
                    recipientName = recipientName,
                    recipientType = recipientTypeEnum,
                    expiryTimestamp = expiryTimestamp
                )
            }
            "MEDICATIONS" -> {
                // Medication sharing
                val medicationId = recordId.toBigInteger()
                MedicationShareHelper.shareMedication(
                    context = this,
                    lifecycleScope = lifecycleScope,
                    medicationId = medicationId,
                    medicationName = recordTitle,
                    recipientAddress = recipientAddress,
                    recipientName = recipientName,
                    recipientType = recipientTypeEnum,
                    expiryTimestamp = expiryTimestamp
                )
            }
            "VACCINATIONS" -> {
                // Vaccination sharing
                val vaccinationId = recordId.toBigInteger()
                
                BlockchainHelper.shareVaccination(
                    activity = this,
                    vaccinationId = vaccinationId,
                    recipientAddress = recipientAddress,
                    recipientName = recipientName,
                    recipientType = recipientTypeEnum,
                    expiryTimestamp = expiryTimestamp
                )
            }
            "REPORTS" -> {
                // Report sharing
                lifecycleScope.launch {
                    try {
                        val reportId = recordId.toBigInteger()
                        val reportRef = BlockchainService.getReportRef(reportId)
                            ?: throw IllegalStateException("Report not found")
                        
                        // Convert to Report object expected by BlockchainHelper
                        val fullReport = com.fyp.blockchainhealthwallet.Report(
                            id = reportId.toString(),
                            title = recordTitle,
                            reportType = ReportType.OTHER,
                            date = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                .format(java.util.Date()),
                            doctorName = "",
                            hospital = "",
                            description = "",
                            timestamp = reportRef.createdAt.toLong()
                        )
                        
                        BlockchainHelper.shareReport(
                            context = this@ShareFormActivity,
                            lifecycleScope = lifecycleScope,
                            report = fullReport,
                            recipientAddress = recipientAddress,
                            recipientName = recipientName,
                            recipientType = recipientTypeEnum,
                            expiryTimestamp = expiryTimestamp
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this@ShareFormActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            else -> {
                Toast.makeText(this, "Unknown record category", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
