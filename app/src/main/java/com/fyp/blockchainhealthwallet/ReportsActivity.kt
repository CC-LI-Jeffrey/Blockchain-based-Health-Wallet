package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.CategoryKeyManager
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private lateinit var recyclerViewReports: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var fabAddReport: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var reportsAdapter: ReportsAdapter
    private val reportsList = mutableListOf<Report>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        setupViews()
        loadReportsFromBlockchain()
    }

    private fun setupViews() {
        recyclerViewReports = findViewById(R.id.recyclerViewReports)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        fabAddReport = findViewById(R.id.fabAddReport)
        progressBar = findViewById(R.id.progressBar)

        // Setup RecyclerView
        reportsAdapter = ReportsAdapter(
            reports = reportsList,
            onItemClick = { report ->
                openReportDetails(report)
            },
            onDeleteClick = { report ->
                showDeleteConfirmation(report)
            }
        )

        recyclerViewReports.apply {
            layoutManager = LinearLayoutManager(this@ReportsActivity)
            adapter = reportsAdapter
        }

        // Setup FAB
        fabAddReport.setOnClickListener {
            openAddReport()
        }
    }

    /**
     * Load medical reports from blockchain
     */
    private fun loadReportsFromBlockchain() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                recyclerViewReports.visibility = View.GONE
                emptyStateLayout.visibility = View.GONE
                
                // Get user's wallet address
                val userAddress = BlockchainService.getUserAddress()
                if (userAddress == null) {
                    Log.e("ReportsActivity", "Wallet not connected")
                    showError("Wallet not connected. Please connect your wallet.")
                    progressBar.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                    return@launch
                }
                
                Log.d("ReportsActivity", "Fetching reports for: $userAddress")
                
                // Get all report IDs from blockchain
                val reportIds = withContext(Dispatchers.IO) {
                    BlockchainService.getReportIds(userAddress)
                }
                
                Log.d("ReportsActivity", "Found ${reportIds.size} report IDs: $reportIds")
                
                if (reportIds.isEmpty()) {
                    Log.w("ReportsActivity", "No reports found for this address")
                    progressBar.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                    return@launch
                }
                
                // Fetch each report's metadata from blockchain + IPFS
                val reports = mutableListOf<Report>()
                for (reportId in reportIds) {
                    try {
                        Log.d("ReportsActivity", "Fetching report ID: $reportId")
                        val report = fetchReportDetails(reportId)
                        if (report != null) {
                            reports.add(report)
                            Log.d("ReportsActivity", "Report loaded: ${report.title}")
                        } else {
                            Log.w("ReportsActivity", "Report $reportId returned null")
                        }
                    } catch (e: Exception) {
                        Log.e("ReportsActivity", "❌ Failed to fetch report $reportId", e)
                    }
                }
                
                Log.d("ReportsActivity", "Total reports loaded: ${reports.size}")
                
                reportsList.clear()
                reportsList.addAll(reports)
                reportsAdapter.updateReports(reportsList)
                
                progressBar.visibility = View.GONE
                updateEmptyState()
                
            } catch (e: Exception) {
                Log.e("ReportsActivity", "❌ Error loading reports", e)
                progressBar.visibility = View.GONE
                showError("Failed to load reports: ${e.message}")
                emptyStateLayout.visibility = View.VISIBLE
            }
        }
    }
    
    /**
     * Fetch individual report details from blockchain and decrypt metadata from IPFS
     */
    private suspend fun fetchReportDetails(reportId: java.math.BigInteger): Report? = withContext(Dispatchers.IO) {
        try {
            // 1. Get report reference from blockchain
            val reportRef = BlockchainService.getReportRef(reportId)
            if (reportRef == null) {
                Log.w("ReportsActivity", "Report $reportId not found")
                return@withContext null
            }
            
            Log.d("ReportsActivity", "Report $reportId: ${reportRef.encryptedDataIpfsHash}")
            
            // 2. Download encrypted metadata from IPFS
            val metadataResponse = ApiClient.api.getFromIPFS(reportRef.encryptedDataIpfsHash)
            if (!metadataResponse.isSuccessful || metadataResponse.body() == null) {
                Log.e("ReportsActivity", "Failed to download metadata from IPFS")
                return@withContext null
            }
            
            val responseBody = metadataResponse.body()!!
            val contentType = metadataResponse.headers()["Content-Type"] ?: "application/octet-stream"
            
            // Handle both Base64 string and raw binary data from IPFS
            val encryptedBytes = if (contentType.contains("text/plain") || contentType.contains("application/json")) {
                // Response is Base64 string
                val base64String = responseBody.string()
                android.util.Base64.decode(base64String, android.util.Base64.NO_WRAP)
            } else {
                // Response is raw binary (octet-stream)
                responseBody.bytes()
            }
            
            // 3. Decrypt metadata using category key (expects raw bytes with IV)
            val decryptedJson = EncryptionHelper.decryptBytesWithCategory(
                encryptedBytes,
                BlockchainService.DataCategory.MEDICAL_REPORTS
            )
            
            // 4. Parse JSON metadata
            val json = JSONObject(decryptedJson)
            val title = json.optString("title", "Untitled Report")
            val description = json.optString("description", "")
            val doctorName = json.optString("doctorName", "Unknown")
            val hospital = json.optString("hospital", "Unknown")
            
            // 5. Map reportType enum
            val reportType = mapBlockchainReportType(reportRef.reportType)
            
            // 6. Format date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = dateFormat.format(Date(reportRef.reportDate.toLong() * 1000))
            
            Report(
                id = reportId.toString(),
                title = title,
                reportType = reportType,
                date = date,
                doctorName = doctorName,
                hospital = hospital,
                description = description,
                filePath = if (reportRef.hasFile) reportRef.encryptedFileIpfsHash else null,
                ipfsHash = reportRef.encryptedDataIpfsHash,
                timestamp = reportRef.createdAt.toLong() * 1000
            )
            
        } catch (e: Exception) {
            Log.e("ReportsActivity", "Error fetching report $reportId", e)
            null
        }
    }
    
    /**
     * Map blockchain ReportType enum to app ReportType enum
     */
    private fun mapBlockchainReportType(blockchainType: BlockchainService.ReportType): ReportType {
        return when (blockchainType) {
            BlockchainService.ReportType.LAB_RESULT -> ReportType.LAB_RESULT
            BlockchainService.ReportType.DOCTOR_NOTE -> ReportType.DOCTOR_NOTE
            BlockchainService.ReportType.PRESCRIPTION -> ReportType.PRESCRIPTION
            BlockchainService.ReportType.IMAGING -> ReportType.IMAGING
            BlockchainService.ReportType.PATHOLOGY -> ReportType.PATHOLOGY
            BlockchainService.ReportType.CONSULTATION -> ReportType.CONSULTATION
            BlockchainService.ReportType.DISCHARGE_SUMMARY -> ReportType.DISCHARGE_SUMMARY
            BlockchainService.ReportType.OTHER -> ReportType.OTHER
        }
    }

    private fun openReportDetails(report: Report) {
        val intent = Intent(this, ViewReportActivity::class.java).apply {
            putExtra("REPORT", report)
        }
        startActivity(intent)
    }

    private fun openAddReport() {
        val intent = Intent(this, AddReportActivity::class.java)
        startActivityForResult(intent, REQUEST_ADD_REPORT)
    }

    private fun showDeleteConfirmation(report: Report) {
        AlertDialog.Builder(this)
            .setTitle("Delete Report")
            .setMessage("Are you sure you want to delete '${report.title}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteReport(report)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteReport(report: Report) {
        // TODO: Implement blockchain deletion if contract supports it
        // For now, just remove from local list
        reportsList.remove(report)
        reportsAdapter.updateReports(reportsList)
        updateEmptyState()
        Toast.makeText(this, "Report removed from view (not deleted from blockchain)", Toast.LENGTH_SHORT).show()
    }

    private fun updateEmptyState() {
        if (reportsList.isEmpty()) {
            recyclerViewReports.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            recyclerViewReports.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_REPORT && resultCode == RESULT_OK) {
            // Reload all reports from blockchain to get the newly added one
            loadReportsFromBlockchain()
        }
    }

    companion object {
        private const val REQUEST_ADD_REPORT = 100
    }
}
