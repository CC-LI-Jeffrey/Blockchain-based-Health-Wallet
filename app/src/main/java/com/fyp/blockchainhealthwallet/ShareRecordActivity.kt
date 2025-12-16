package com.fyp.blockchainhealthwallet

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fyp.blockchainhealthwallet.adapter.ShareRecordAdapter
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.databinding.ActivityShareRecordBinding
import com.fyp.blockchainhealthwallet.model.ShareRecord
import com.fyp.blockchainhealthwallet.ui.BlockchainHelper
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*

class ShareRecordActivity : AppCompatActivity() {

    private val TAG = "ShareRecordActivity"
    private lateinit var binding: ActivityShareRecordBinding
    private lateinit var adapter: ShareRecordAdapter
    private val shareRecords = mutableListOf<ShareRecord>()
    private val blockchainShareRecords = mutableListOf<BlockchainService.ShareRecord>()
    private var isLoadingShares = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadBlockchainShares() // Load real data from blockchain
        setupClickListeners()
    }
    
    override fun onResume() {
        super.onResume()
        loadBlockchainShares() // Refresh on resume
    }

    private fun setupRecyclerView() {
        adapter = ShareRecordAdapter(shareRecords) { shareRecord ->
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
        }

        binding.recyclerViewShareRecords.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewShareRecords.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Use real blockchain sharing
        binding.cardShareNew.setOnClickListener {
            BlockchainHelper.showShareDataDialog(this, lifecycleScope)
        }

        binding.cardImportRecord.setOnClickListener {
            showReceivedShares()
        }
        
        // Add test verification button
        binding.cardImportRecord.setOnLongClickListener {
            verifyLastShare()
            true
        }        
        // Personal info category collapse/expand
        binding.btnCollapsePersonalInfo.setOnClickListener {
            if (binding.containerPersonalInfoData.visibility == android.view.View.VISIBLE) {
                binding.containerPersonalInfoData.visibility = android.view.View.GONE
                binding.btnCollapsePersonalInfo.rotation = 0f
            } else {
                binding.containerPersonalInfoData.visibility = android.view.View.VISIBLE
                binding.btnCollapsePersonalInfo.rotation = 90f
            }
        }    }
    
    /**
     * Verify the most recent share on blockchain
     */
    private fun verifyLastShare() {
        val address = WalletManager.getAddress()
        if (address == null) {
            Toast.makeText(this, "Connect wallet first", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "=== STARTING SHARE VERIFICATION ===")
        Log.d(TAG, "User Address: $address")
        Log.d(TAG, "Contract Address: ${BlockchainService.getContractAddress()}")
        
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Verifying shares on blockchain...")
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Calling getShareIds for address: $address")
                
                val shareIds = withContext(Dispatchers.IO) {
                    BlockchainService.getShareIds(address)
                }
                
                Log.d(TAG, "getShareIds returned: ${shareIds.size} shares")
                Log.d(TAG, "Share IDs: $shareIds")
                
                progressDialog.dismiss()
                
                val message = if (shareIds.isEmpty()) {
                    Log.w(TAG, "No shares found on blockchain for address: $address")
                    """
                    ✗ No shares found on blockchain.
                    
                    Debug Info:
                    • User Address: ${address.take(15)}...
                    • Contract: ${BlockchainService.getContractAddress().take(15)}...
                    • Network: Sepolia Testnet
                    
                    Troubleshooting:
                    1. Verify address in Remix matches this address
                    2. Check transaction was confirmed
                    3. Wait 30 seconds and try again
                    4. Check Logcat for errors
                    """.trimIndent()
                } else {
                    val lastShareId = shareIds.last()
                    Log.d(TAG, "Getting details for share ID: $lastShareId")
                    
                    val share = withContext(Dispatchers.IO) {
                        BlockchainService.getShareRecord(lastShareId)
                    }
                    
                    Log.d(TAG, "Share record retrieved: ${share != null}")
                    
                    """
                    ✓ Share Verification Successful!
                    
                    Total Shares: ${shareIds.size}
                    All Share IDs: ${shareIds.joinToString(", ")}
                    Last Share ID: $lastShareId
                    
                    Last Share Details:
                    • Recipient: ${share?.recipientAddress?.take(10)}...
                    • Full Recipient: ${share?.recipientAddress}
                    • Category: ${share?.sharedDataCategory?.name}
                    • Status: ${share?.status?.name}
                    • Access Level: ${share?.accessLevel?.name}
                    • Expiry: ${share?.expiryDate}
                    
                    ✓ Sharing is working correctly!
                    
                    Debug Info:
                    • Your Address: ${address.take(15)}...
                    • Contract: ${BlockchainService.getContractAddress().take(15)}...
                    """.trimIndent()
                }
                
                AlertDialog.Builder(this@ShareRecordActivity)
                    .setTitle("Blockchain Verification")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Copy Address") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Address", address)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@ShareRecordActivity, "Address copied!", Toast.LENGTH_SHORT).show()
                    }
                    .show()
                    
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Verification error", e)
                Log.e(TAG, "Error details: ${e.message}")
                Log.e(TAG, "Stack trace: ", e)
                
                AlertDialog.Builder(this@ShareRecordActivity)
                    .setTitle("Verification Failed")
                    .setMessage("""
                        Error: ${e.message}
                        
                        Debug Info:
                        • Address: ${address.take(15)}...
                        • Contract: ${BlockchainService.getContractAddress().take(15)}...
                        
                        Check Logcat for full details (tag: ShareRecordActivity)
                    """.trimIndent())
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Copy Error") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Error", e.toString())
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@ShareRecordActivity, "Error copied!", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }
    
    /**
     * Load actual shares from blockchain
     */
    private fun loadBlockchainShares() {
        // Prevent concurrent loads
        if (isLoadingShares) {
            Log.d(TAG, "Already loading shares, skipping...")
            return
        }
        
        val address = WalletManager.getAddress()
        
        if (address == null) {
            Toast.makeText(this, "Please connect wallet first", Toast.LENGTH_SHORT).show()
            loadMockData() // Fallback to mock data
            return
        }
        
        isLoadingShares = true
        
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Loading shares from blockchain...")
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch {
            try {
                // Get share IDs from blockchain
                val shareIds = withContext(Dispatchers.IO) {
                    BlockchainService.getShareIds(address)
                }
                
                Log.d(TAG, "Found ${shareIds.size} shares on blockchain")
                
                shareRecords.clear()
                
                // Fetch each share record
                for (shareId in shareIds) {
                    try {
                        val blockchainShare = withContext(Dispatchers.IO) {
                            BlockchainService.getShareRecord(shareId)
                        }
                        
                        blockchainShare?.let { share ->
                            // Convert blockchain share to UI model
                            val uiShare = ShareRecord(
                                id = "SH${shareId}",
                                recipientName = share.recipientAddress.take(10) + "...",
                                recipientType = share.recipientType.name,
                                sharedData = share.sharedDataCategory.name.replace("_", " "),
                                shareDate = formatDate(share.shareDate.toLong()),
                                shareTime = formatTime(share.shareDate.toLong()),
                                expiryDate = formatDate(share.expiryDate.toLong()),
                                accessLevel = share.accessLevel.name.replace("_", " "),
                                status = share.status.name,
                                recipientEmail = share.recipientAddress
                            )
                            shareRecords.add(uiShare)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading share $shareId", e)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    adapter.notifyDataSetChanged()
                    updateStatistics()
                    isLoadingShares = false
                    
                    if (shareRecords.isEmpty()) {
                        Toast.makeText(
                            this@ShareRecordActivity,
                            "No shares found. Tap 'Share New' to create one!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading blockchain shares", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    isLoadingShares = false
                    Toast.makeText(
                        this@ShareRecordActivity,
                        "Error loading shares. Showing demo data.",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadMockData() // Fallback
                }
            }
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp * 1000)
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return format.format(date)
    }
    
    private fun formatTime(timestamp: Long): String {
        val date = Date(timestamp * 1000)
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(date)
    }

    private fun loadMockData() {
        shareRecords.clear()
        shareRecords.addAll(
            listOf(
                ShareRecord(
                    id = "SH001",
                    recipientName = "Dr. Sarah Johnson",
                    recipientType = "Doctor",
                    sharedData = "Vaccination Records, Medical History",
                    shareDate = "Oct 15, 2025",
                    shareTime = "14:30",
                    expiryDate = "Jan 15, 2026",
                    accessLevel = "View Only",
                    status = "Active",
                    recipientEmail = "sarah.johnson@clinic.com"
                ),
                ShareRecord(
                    id = "SH002",
                    recipientName = "Queen Mary Hospital",
                    recipientType = "Hospital",
                    sharedData = "Full Medical Records",
                    shareDate = "Oct 10, 2025",
                    shareTime = "09:00",
                    expiryDate = "Apr 10, 2026",
                    accessLevel = "Full Access",
                    status = "Active",
                    recipientEmail = "records@qmh.hk"
                ),
                ShareRecord(
                    id = "SH003",
                    recipientName = "AIA Insurance",
                    recipientType = "Insurance Company",
                    sharedData = "Vaccination Records",
                    shareDate = "Sep 20, 2025",
                    shareTime = "16:45",
                    expiryDate = "Oct 20, 2025",
                    accessLevel = "View Only",
                    status = "Expired",
                    recipientEmail = "claims@aia.com"
                ),
                ShareRecord(
                    id = "SH004",
                    recipientName = "Family Clinic Central",
                    recipientType = "Clinic",
                    sharedData = "Lab Results, Prescription History",
                    shareDate = "Oct 1, 2025",
                    shareTime = "11:20",
                    expiryDate = "Dec 1, 2025",
                    accessLevel = "View Only",
                    status = "Active",
                    recipientEmail = "admin@familyclinic.hk"
                ),
                ShareRecord(
                    id = "SH005",
                    recipientName = "Dr. Michael Chen",
                    recipientType = "Doctor",
                    sharedData = "Medical History",
                    shareDate = "Aug 15, 2025",
                    shareTime = "10:00",
                    expiryDate = "Sep 15, 2025",
                    accessLevel = "View Only",
                    status = "Revoked",
                    recipientEmail = "m.chen@medical.hk"
                ),
                ShareRecord(
                    id = "SH006",
                    recipientName = "Prudential Insurance",
                    recipientType = "Insurance Company",
                    sharedData = "Vaccination Records, Lab Results",
                    shareDate = "Oct 25, 2025",
                    shareTime = "13:15",
                    expiryDate = "Jan 25, 2026",
                    accessLevel = "View Only",
                    status = "Active",
                    recipientEmail = "healthclaims@prudential.com"
                )
            )
        )
        adapter.notifyDataSetChanged()
    }

    private fun updateStatistics() {
        val totalShares = shareRecords.size
        // Match blockchain enum values: ACTIVE, EXPIRED, REVOKED
        val activeShares = shareRecords.count { it.status.uppercase() == "ACTIVE" }
        val expiredShares = shareRecords.count { it.status.uppercase() == "EXPIRED" }

        binding.tvTotalShares.text = totalShares.toString()
        binding.tvActiveShares.text = activeShares.toString()
        binding.tvExpiredShares.text = expiredShares.toString()
        
        Log.d(TAG, "Statistics updated - Total: $totalShares, Active: $activeShares, Expired: $expiredShares")
    }

    private fun showImportDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import_record, null)
        val etAccessKey = dialogView.findViewById<TextInputEditText>(R.id.etAccessKey)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnImport).setOnClickListener {
            val accessKey = etAccessKey.text.toString().trim()
            
            if (accessKey.isEmpty()) {
                Toast.makeText(this, "Please enter an access key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate and import record
            importRecord(accessKey)
            dialog.dismiss()
        }

        dialog.show()
    }
    
    /**
     * Show shares received by current user (where user is the recipient)
     */
    private fun showReceivedShares() {
        val address = WalletManager.getAddress()
        if (address == null) {
            Toast.makeText(this, "Connect wallet first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Loading received shares from blockchain...")
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Querying shares received by: $address")
                
                // Get total share count from contract
                val totalShares = withContext(Dispatchers.IO) {
                    BlockchainService.getTotalShareCount()
                }
                
                Log.d(TAG, "Total shares in contract: $totalShares")
                
                val receivedShares = mutableListOf<ShareRecord>()
                
                // Clear previous blockchain records before loading new ones
                blockchainShareRecords.clear()
                
                // Query each share and filter by recipient
                for (shareId in 1..totalShares.toInt()) {
                    try {
                        val share = withContext(Dispatchers.IO) {
                            BlockchainService.getShareRecord(shareId.toBigInteger())
                        }
                        
                        // Check if current user is the recipient
                        if (share != null && share.recipientAddress.equals(address, ignoreCase = true)) {
                            // Store blockchain record for decryption
                            blockchainShareRecords.add(share)
                            
                            val uiShare = ShareRecord(
                                id = "SH${shareId}",
                                recipientName = "You (Received)",
                                recipientType = share.recipientType.name,
                                sharedData = share.sharedDataCategory.name.replace("_", " "),
                                shareDate = formatDate(share.shareDate.toLong()),
                                shareTime = formatTime(share.shareDate.toLong()),
                                expiryDate = formatDate(share.expiryDate.toLong()),
                                accessLevel = share.accessLevel.name.replace("_", " "),
                                status = share.status.name,
                                recipientEmail = share.recipientAddress
                            )
                            receivedShares.add(uiShare)
                            Log.d(TAG, "Found received share ID: $shareId from owner")
                        }
                    } catch (e: Exception) {
                        // Share might not exist or no access - skip
                        Log.d(TAG, "Skipping share $shareId: ${e.message}")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    
                    // Update main list with received shares
                    shareRecords.clear()
                    shareRecords.addAll(receivedShares)
                    adapter.notifyDataSetChanged()
                    updateStatistics()
                    
                    // Load category-specific data with blockchain data
                    loadCategoryData()
                    
                    if (receivedShares.isEmpty()) {
                        Toast.makeText(
                            this@ShareRecordActivity,
                            "No received shares found. Your address: ${address.take(15)}...",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@ShareRecordActivity,
                            "Loaded ${receivedShares.size} received share(s)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading received shares", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this@ShareRecordActivity)
                        .setTitle("Error")
                        .setMessage("Failed to load received shares: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun importRecord(accessKey: String) {
        // Simulate importing a record based on access key
        // In a real app, this would make a blockchain/API call
        
        // Mock validation - accept keys in format: ABC123XYZ456DEF789
        if (accessKey.length < 10) {
            Toast.makeText(
                this,
                "Invalid access key. Key must be at least 10 characters.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Simulate successful import
        Toast.makeText(
            this,
            "Successfully imported record from access key: ${accessKey.take(8)}...",
            Toast.LENGTH_LONG
        ).show()

        // In real implementation, you would:
        // 1. Validate the key with blockchain/backend
        // 2. Retrieve the shared record data
        // 3. Add it to the user's local database
        // 4. Refresh the list
        
        // For demo purposes, show success message
        AlertDialog.Builder(this)
            .setTitle("Import Successful")
            .setMessage("The health record has been imported and is now accessible in your records.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Load and display category-specific data from received shares
     */
    private fun loadCategoryData() {
        // Filter for PERSONAL_INFO category
        val personalInfoShares = blockchainShareRecords.filter { 
            it.sharedDataCategory == BlockchainService.DataCategory.PERSONAL_INFO 
        }
        
        if (personalInfoShares.isEmpty()) {
            binding.cardPersonalInfoCategory.visibility = View.GONE
            return
        }
        
        // Show the card
        binding.cardPersonalInfoCategory.visibility = View.VISIBLE
        binding.tvPersonalInfoContent.text = "Loading decrypted data from IPFS..."
        
        // Load actual data from IPFS
        lifecycleScope.launch {
            try {
                val displayText = StringBuilder()
                
                personalInfoShares.forEachIndexed { index, share ->
                    if (index > 0) displayText.append("\n\n═══════════════════════\n\n")
                    
                    displayText.append("📋 Share ID: ${share.id}\n")
                    displayText.append("📅 Share Date: ${formatDate(share.shareDate.toLong())}\n")
                    displayText.append("⏰ Expiry: ${formatDate(share.expiryDate.toLong())}\n")
                    displayText.append("🔐 Status: ${share.status}\n\n")
                    
                    // Fetch and decrypt the actual data
                    try {
                        val personalInfo = fetchAndDecryptPersonalInfo(share)
                        displayText.append("👤 PERSONAL INFORMATION:\n")
                        displayText.append("━━━━━━━━━━━━━━━━━━━━━\n")
                        displayText.append("Name: ${personalInfo.firstName} ${personalInfo.lastName}\n")
                        displayText.append("Email: ${personalInfo.email}\n")
                        displayText.append("Phone: ${personalInfo.phone}\n")
                        displayText.append("HKID: ${personalInfo.hkid}\n")
                        displayText.append("Date of Birth: ${personalInfo.dateOfBirth}\n")
                        displayText.append("Gender: ${personalInfo.gender}\n")
                        displayText.append("Blood Type: ${personalInfo.bloodType}\n")
                        displayText.append("Address: ${personalInfo.address}\n")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error decrypting share ${share.id}", e)
                        displayText.append("❌ Error: ${e.message}\n")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    binding.tvPersonalInfoContent.text = displayText.toString()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading category data", e)
                withContext(Dispatchers.Main) {
                    binding.tvPersonalInfoContent.text = "Error loading data: ${e.message}"
                }
            }
        }
    }
    
    /**
     * Fetch and decrypt personal information from a share record
     */
    private suspend fun fetchAndDecryptPersonalInfo(share: BlockchainService.ShareRecord): PersonalInfo = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching personal info for share ${share.id}")
            Log.d(TAG, "IPFS hash: ${share.encryptedRecipientDataIpfsHash}")
            Log.d(TAG, "Encrypted category key: ${share.encryptedCategoryKey.take(20)}...")
            
            // Check if this is a dummy/test hash
            if (share.encryptedRecipientDataIpfsHash.contains("Dummy", ignoreCase = true) ||
                share.encryptedRecipientDataIpfsHash.length < 40) {
                Log.w(TAG, "Detected dummy/test IPFS hash - returning placeholder data")
                return@withContext PersonalInfo(
                    firstName = "[Test Data]",
                    lastName = "[Not Available]",
                    email = "This share uses dummy test data",
                    phone = "Real data not uploaded to IPFS",
                    hkid = "N/A",
                    dateOfBirth = "N/A",
                    gender = "N/A",
                    bloodType = "N/A",
                    address = "To see real data, the data owner must upload actual personal info to IPFS before sharing"
                )
            }
            
            // TODO: Decrypt the category key using recipient's private key
            // For now, we'll try to fetch the data directly (it will be encrypted)
            
            val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(
                share.encryptedRecipientDataIpfsHash
            )
            
            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = try {
                    response.errorBody()?.string() ?: "Unknown error"
                } catch (e: Exception) {
                    "Unable to read error"
                }
                Log.e(TAG, "Failed to fetch from IPFS: ${response.code()}")
                Log.e(TAG, "Error details: $errorMsg")
                
                // Return helpful error info
                return@withContext PersonalInfo(
                    firstName = "[Error ${response.code()}]",
                    lastName = "[Data Not Available]",
                    email = "Failed to fetch from IPFS",
                    phone = "The IPFS file may not exist or has been removed",
                    hkid = "N/A",
                    dateOfBirth = "N/A",
                    gender = "N/A",
                    bloodType = "N/A",
                    address = "IPFS hash: ${share.encryptedRecipientDataIpfsHash.take(30)}..."
                )
            }
            
            val jsonData = response.body()!!.string()
            Log.d(TAG, "Retrieved data from IPFS (${jsonData.length} bytes)")
            
            // Try to parse as PersonalInfo (will work if stored as plain JSON for demo)
            val gson = com.google.gson.Gson()
            val personalInfo = gson.fromJson(jsonData, PersonalInfo::class.java)
            
            Log.d(TAG, "Successfully parsed PersonalInfo: ${personalInfo.firstName} ${personalInfo.lastName}")
            personalInfo
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching/decrypting personal info", e)
            return@withContext PersonalInfo(
                firstName = "[Exception]",
                lastName = "[${e.javaClass.simpleName}]",
                email = e.message ?: "Unknown error",
                phone = "Unable to load shared data",
                hkid = "N/A",
                dateOfBirth = "N/A",
                gender = "N/A",
                bloodType = "N/A",
                address = "Check logs for details"
            )
        }
    }
    
    data class PersonalInfo(
        val firstName: String = "",
        val lastName: String = "",
        val email: String = "",
        val phone: String = "",
        val hkid: String = "",
        val dateOfBirth: String = "",
        val gender: String = "",
        val bloodType: String = "",
        val address: String = ""
    )
    
    private fun formatDate(timestamp: java.math.BigInteger): String {
        return try {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp.toLong() * 1000))
        } catch (e: Exception) {
            timestamp.toString()
        }
    }
}
