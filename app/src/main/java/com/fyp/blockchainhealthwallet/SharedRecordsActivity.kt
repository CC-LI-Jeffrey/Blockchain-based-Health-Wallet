package com.fyp.blockchainhealthwallet

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fyp.blockchainhealthwallet.adapter.ShareRecordAdapter
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.databinding.ActivitySharedRecordsBinding
import com.fyp.blockchainhealthwallet.model.ShareRecord
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*

class SharedRecordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySharedRecordsBinding
    private lateinit var adapter: ShareRecordAdapter
    private val allShareRecords = mutableListOf<ShareRecord>()
    private val filteredShareRecords = mutableListOf<ShareRecord>()
    private var selectedCategory: BlockchainService.DataCategory? = null

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
        binding = ActivitySharedRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupCategoryFilters()
        setupClickListeners()
        loadSharedRecords()
    }

    private fun setupRecyclerView() {
        adapter = ShareRecordAdapter(
            filteredShareRecords,
            onItemClick = { shareRecord ->
                // Navigate to detail activity
                val intent = Intent(this, ShareRecordDetailActivity::class.java)
                intent.putExtra("SHARE_ID", shareRecord.id)
                intent.putExtra("RECIPIENT_NAME", shareRecord.recipientName)
                intent.putExtra("RECIPIENT_TYPE", shareRecord.recipientType)
                intent.putExtra("SHARED_DATA", shareRecord.sharedData)
                intent.putExtra("SHARE_DATE", shareRecord.shareDate)
                intent.putExtra("SHARE_TIME", shareRecord.shareTime)
                intent.putExtra("EXPIRY_DATE", shareRecord.expiryDate)
                intent.putExtra("ACCESS_LEVEL", shareRecord.accessLevel)
                intent.putExtra("STATUS", shareRecord.status)
                intent.putExtra("RECIPIENT_EMAIL", shareRecord.recipientEmail)
                startActivity(intent)
            },
            onQRClick = { shareRecord ->
                showQRCodeForShare(shareRecord)
            }
        )

        binding.recyclerViewShared.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewShared.adapter = adapter
    }

    private fun setupCategoryFilters() {
        binding.chipAll.setOnClickListener { filterByCategory(null) }
        binding.chipPersonalInfo.setOnClickListener { filterByCategory(BlockchainService.DataCategory.PERSONAL_INFO) }
        binding.chipMedications.setOnClickListener { filterByCategory(BlockchainService.DataCategory.MEDICATION_RECORDS) }
        binding.chipVaccinations.setOnClickListener { filterByCategory(BlockchainService.DataCategory.VACCINATION_RECORDS) }
        binding.chipReports.setOnClickListener { filterByCategory(BlockchainService.DataCategory.MEDICAL_REPORTS) }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // FAB to scan QR code for quick access to shared records
        binding.fabScanQR.setOnClickListener {
            launchQRScanner()
        }
    }

    private fun loadSharedRecords() {
        val address = WalletManager.getAddress()
        if (address == null) {
            showEmptyState()
            return
        }

        lifecycleScope.launch {
            try {
                val shareIds = withContext(Dispatchers.IO) {
                    BlockchainService.getShareIds(address)
                }

                allShareRecords.clear()

                shareIds.forEach { shareId ->
                    val share = withContext(Dispatchers.IO) {
                        BlockchainService.getShareRecord(shareId)
                    }

                    share?.let {
                        val shareRecord = convertToShareRecord(it, shareId)
                        allShareRecords.add(shareRecord)
                    }
                }

                filterByCategory(selectedCategory)

            } catch (e: Exception) {
                showEmptyState()
            }
        }
    }

    private fun filterByCategory(category: BlockchainService.DataCategory?) {
        selectedCategory = category
        filteredShareRecords.clear()

        if (category == null) {
            filteredShareRecords.addAll(allShareRecords)
        } else {
            // Map DataCategory to RecordType for filtering
            val recordTypeFilter = when (category) {
                BlockchainService.DataCategory.PERSONAL_INFO -> "PERSONAL_INFO"
                BlockchainService.DataCategory.MEDICATION_RECORDS -> "MEDICATION"
                BlockchainService.DataCategory.VACCINATION_RECORDS -> "VACCINATION"
                BlockchainService.DataCategory.MEDICAL_REPORTS -> "MEDICAL_REPORT"
                BlockchainService.DataCategory.ALL_DATA -> null
            }
            
            filteredShareRecords.addAll(allShareRecords.filter {
                recordTypeFilter == null || it.sharedData.contains(recordTypeFilter, ignoreCase = true)
            })
        }

        adapter.notifyDataSetChanged()

        if (filteredShareRecords.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
        }
    }

    private fun convertToShareRecord(
        blockchainShare: BlockchainService.ShareRecord,
        shareId: BigInteger
    ): ShareRecord {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val shareDate = Date(blockchainShare.shareDate.toLong() * 1000)
        val expiryDate = Date(blockchainShare.expiryDate.toLong() * 1000)

        return ShareRecord(
            id = shareId.toString(),
            recipientName = blockchainShare.recipientAddress.take(10) + "...",
            recipientType = blockchainShare.recipientType.name,
            sharedData = "${blockchainShare.recordType.name} (ID: ${blockchainShare.recordId})",
            shareDate = dateFormat.format(shareDate),
            shareTime = timeFormat.format(shareDate),
            expiryDate = dateFormat.format(expiryDate),
            accessLevel = blockchainShare.accessLevel.name,
            status = blockchainShare.status.name,
            recipientEmail = blockchainShare.recipientAddress
        )
    }

    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
        binding.recyclerViewShared.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyState.visibility = View.GONE
        binding.recyclerViewShared.visibility = View.VISIBLE
    }

    private fun showQRCodeForShare(shareRecord: ShareRecord) {
        // Launch QRCodeDisplayActivity with share data
        val intent = Intent(this, QRCodeDisplayActivity::class.java)
        intent.putExtra("SHARE_ID", shareRecord.id)
        intent.putExtra("RECIPIENT_ADDRESS", shareRecord.recipientEmail)
        intent.putExtra("RECORD_TYPE", shareRecord.sharedData)
        intent.putExtra("EXPIRY_DATE", shareRecord.expiryDate)
        startActivity(intent)
    }

    private fun launchQRScanner() {
        val scannerIntent = Intent(this, QRScannerActivity::class.java)
        qrScannerLauncher.launch(scannerIntent)
    }

    private fun handleScannedQRCode(qrContent: String) {
        try {
            val jsonData = JSONObject(qrContent)
            
            // Validate it's a Health Wallet share
            if (jsonData.optString("type") != "HEALTH_WALLET_SHARE") {
                Toast.makeText(this, "Invalid QR code. Please scan a Health Wallet share QR code.", Toast.LENGTH_LONG).show()
                return
            }

            // Extract share information
            val shareId = jsonData.optString("shareId")
            val recipientAddress = jsonData.optString("recipientAddress")
            val recordType = jsonData.optString("recordType")
            val expiryDate = jsonData.optString("expiryDate")

            // Show dialog with share information
            AlertDialog.Builder(this)
                .setTitle("Share Information")
                .setMessage(
                    "Share ID: $shareId\n" +
                    "Record Type: $recordType\n" +
                    "Recipient: ${recipientAddress.take(10)}...\n" +
                    "Expires: $expiryDate"
                )
                .setPositiveButton("View Details") { _, _ ->
                    // Navigate to share detail page
                    val intent = Intent(this, ShareRecordDetailActivity::class.java)
                    intent.putExtra("SHARE_ID", shareId)
                    intent.putExtra("RECIPIENT_EMAIL", recipientAddress)
                    startActivity(intent)
                }
                .setNegativeButton("Close", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_SHORT).show()
        }
    }
}
