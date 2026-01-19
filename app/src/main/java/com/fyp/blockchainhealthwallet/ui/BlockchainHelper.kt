package com.fyp.blockchainhealthwallet.ui

import android.app.ProgressDialog
import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.fyp.blockchainhealthwallet.ShareRecordActivity
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.blockchain.RSAHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.withContext
import java.math.BigInteger

/**
 * Helper class for blockchain-related UI operations (HealthWalletV2.05).
 * Provides dialogs and workflows for data sharing with RSA encryption.
 */
object BlockchainHelper {
    
    private const val TAG = "BlockchainHelper"
    private val gson = Gson()
    
    /**
     * Show dialog to share data with a recipient (HealthWalletV2.05).
     * Uses per-record sharing with RSA encryption.
     */
    fun showShareDataDialog(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope
    ) {
        // Create input fields
        val recipientAddressInput = EditText(context).apply {
            hint = "Recipient Wallet Address (0x...)"
            setPadding(50, 20, 50, 20)
        }
        
        val recipientNameInput = EditText(context).apply {
            hint = "Recipient Name (e.g., Dr. Smith)"
            setPadding(50, 20, 50, 20)
        }
        
        // Data category selection (using Personal Info for testing until medical records are implemented)
        val categories = arrayOf(
            "Personal Info (for testing)",
            "Medication Records (coming soon)",
            "Vaccination Records (coming soon)",
            "Medical Reports (coming soon)"
        )
        var selectedCategory = BlockchainService.DataCategory.PERSONAL_INFO
        val categoryInput = android.widget.Spinner(context).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_item, categories)
            setPadding(50, 20, 50, 20)
            setSelection(0) // Default to Personal Info
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    selectedCategory = when (position) {
                        0 -> BlockchainService.DataCategory.PERSONAL_INFO
                        1 -> BlockchainService.DataCategory.MEDICATION_RECORDS
                        2 -> BlockchainService.DataCategory.VACCINATION_RECORDS
                        3 -> BlockchainService.DataCategory.MEDICAL_REPORTS
                        else -> BlockchainService.DataCategory.PERSONAL_INFO
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        
        // Recipient type selection
        val recipientTypes = arrayOf("Doctor", "Hospital", "Clinic", "Insurance", "Pharmacy", "Laboratory", "Other")
        var selectedRecipientType = BlockchainService.RecipientType.DOCTOR
        val recipientTypeInput = android.widget.Spinner(context).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_item, recipientTypes)
            setPadding(50, 20, 50, 20)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    selectedRecipientType = when (position) {
                        0 -> BlockchainService.RecipientType.DOCTOR
                        1 -> BlockchainService.RecipientType.HOSPITAL
                        2 -> BlockchainService.RecipientType.CLINIC
                        3 -> BlockchainService.RecipientType.INSURANCE_COMPANY
                        4 -> BlockchainService.RecipientType.PHARMACY
                        5 -> BlockchainService.RecipientType.LABORATORY
                        else -> BlockchainService.RecipientType.OTHER
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        
        val durationInput = EditText(context).apply {
            hint = "Duration in Days (e.g., 30)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(50, 20, 50, 20)
        }
        
        // Create container layout
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
            addView(android.widget.TextView(context).apply {
                text = "Recipient Information"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 20)
            })
            addView(recipientAddressInput)
            addView(recipientNameInput)
            addView(android.widget.TextView(context).apply {
                text = "Recipient Type"
                setPadding(0, 20, 0, 10)
            })
            addView(recipientTypeInput)
            addView(android.widget.TextView(context).apply {
                text = "Data to Share"
                setPadding(0, 20, 0, 10)
            })
            addView(categoryInput)
            addView(durationInput)
        }
        
        AlertDialog.Builder(context)
            .setTitle("Share Health Data")
            .setView(container)
            .setPositiveButton("Share") { _, _ ->
                val recipientAddress = recipientAddressInput.text.toString().trim()
                val recipientName = recipientNameInput.text.toString().trim()
                val durationText = durationInput.text.toString().trim()
                
                if (recipientAddress.isEmpty() || recipientName.isEmpty() || durationText.isEmpty()) {
                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val durationDays = durationText.toLongOrNull() ?: 0L
                if (durationDays <= 0) {
                    Toast.makeText(context, "Invalid duration", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Calculate expiry timestamp (current time + duration)
                val expiryTimestamp = BigInteger.valueOf((System.currentTimeMillis() / 1000) + (durationDays * 24 * 60 * 60))
                
                shareData(
                    context,
                    lifecycleScope,
                    recipientAddress,
                    recipientName,
                    selectedRecipientType,
                    selectedCategory,
                    expiryTimestamp
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Share data with recipient on blockchain (HealthWalletV2).
     */
    private fun shareData(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        recipientAddress: String,
        recipientName: String,
        recipientType: BlockchainService.RecipientType,
        dataCategory: BlockchainService.DataCategory,
        expiryTimestamp: BigInteger
    ) {
        var progressDialog: ProgressDialog? = null
        
        lifecycleScope.launch {
            try {
                progressDialog = ProgressDialog(context).apply {
                    setMessage("Preparing to share data...\\nPlease wait...")
                    setCancelable(false)
                    show()
                }
                
                // Get current user's address
                val userAddress = WalletManager.getAddress()
                    ?: throw Exception("Wallet not connected")
                
                // Step 1: Get recipient's public key from blockchain
                progressDialog?.setMessage("Checking recipient's public key...")
                Log.d(TAG, "Step 1: Getting recipient's public key")
                
                val recipientPublicKeyIpfsHash = withContext(Dispatchers.IO) {
                    BlockchainService.getUserPublicKey(recipientAddress)
                }
                
                if (recipientPublicKeyIpfsHash.isEmpty()) {
                    throw Exception("Recipient has not enabled receiving shares. They must set up their public key first.")
                }
                
                Log.d(TAG, "Recipient public key IPFS hash: $recipientPublicKeyIpfsHash")
                
                // Step 2: Download recipient's public key from IPFS
                progressDialog?.setMessage("Downloading recipient's public key...")
                Log.d(TAG, "Step 2: Downloading public key from IPFS")
                
                val publicKeyResponse = withContext(Dispatchers.IO) {
                    ApiClient.api.getFromIPFS(recipientPublicKeyIpfsHash)
                }
                
                if (!publicKeyResponse.isSuccessful) {
                    throw Exception("Failed to download recipient's public key from IPFS")
                }
                
                val publicKeyJson = publicKeyResponse.body()?.string()
                    ?: throw Exception("Empty public key response")
                
                val publicKeyData = gson.fromJson(publicKeyJson, com.google.gson.JsonObject::class.java)
                val recipientPublicKeyBase64 = publicKeyData.get("publicKey")?.asString
                    ?: throw Exception("No public key in IPFS data")
                
                Log.d(TAG, "Downloaded recipient public key, length: ${recipientPublicKeyBase64.length}")
                
                // Step 3: Get own personal info from blockchain
                progressDialog?.setMessage("Fetching your personal info...")
                Log.d(TAG, "Step 3: Getting personal info")
                
                val personalInfoRef = withContext(Dispatchers.IO) {
                    BlockchainService.getPersonalInfoRef(userAddress)
                } ?: throw Exception("No personal info found. Please set your personal info first in Profile.")
                
                val personalInfoIpfsHash = personalInfoRef.encryptedDataIpfsHash
                Log.d(TAG, "Personal info IPFS hash: $personalInfoIpfsHash")
                
                // Step 4: Decrypt own AES key
                progressDialog?.setMessage("Preparing encryption keys...")
                Log.d(TAG, "Step 4: Decrypting own AES key")
                
                val encryptedAesKeyBase64 = personalInfoRef.encryptedKey
                val aesKey: javax.crypto.SecretKey = withContext(Dispatchers.IO) {
                    EncryptionHelper.decryptKeyFromBlockchain(encryptedAesKeyBase64)
                }
                
                Log.d(TAG, "Decrypted AES key")
                
                // Step 5: Re-encrypt AES key with recipient's RSA public key
                progressDialog?.setMessage("Encrypting with recipient's key...")
                Log.d(TAG, "Step 5: Encrypting AES key with recipient's RSA public key")
                
                // Pass SecretKey directly to RSA encryption
                val encryptedRecordKey: String = withContext(Dispatchers.IO) {
                    RSAHelper.encryptKeyWithPublicKey(aesKey, recipientPublicKeyBase64)
                }
                
                Log.d(TAG, "Encrypted record key length: ${encryptedRecordKey.length}")
                
                // Step 6: Generate recipient name hash
                val recipientNameHash = "0x" + recipientName.hashCode().toString().padStart(64, '0').take(64)
                
                // Step 7: Share data on blockchain
                progressDialog?.setMessage("Sending to wallet...\\nPlease approve transaction")
                Log.d(TAG, "Step 6: Sharing on blockchain")
                
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.shareData(
                        recipientAddress = recipientAddress,
                        recipientNameHash = recipientNameHash,
                        encryptedRecipientDataIpfsHash = personalInfoIpfsHash,
                        recipientType = recipientType,
                        recordType = BlockchainService.RecordType.PERSONAL_INFO,
                        recordId = BigInteger.ZERO,  // Personal info doesn't have ID
                        expiryDate = expiryTimestamp,
                        accessLevel = BlockchainService.AccessLevel.VIEW_ONLY,
                        encryptedRecordKey = encryptedRecordKey
                    )
                }
                
                progressDialog?.dismiss()
                
                Log.d(TAG, "✅ Share transaction successful: $txHash")
                
                // Show success
                AlertDialog.Builder(context)
                    .setTitle("Profile Shared Successfully!")
                    .setMessage("Recipient can now decrypt and view your personal profile using their private key.\\n\\nTransaction: ${txHash.take(10)}...\\n\\nFull TX: $txHash\\n\\nRefresh share list to see new share.")
                    .setPositiveButton("OK") { _, _ ->
                        // Refresh the share list if context is ShareRecordActivity
                        if (context is ShareRecordActivity) {
                            context.recreate()
                        }
                    }
                    .show()
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                Log.e(TAG, "Error sharing data", e)
                
                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true -> 
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true -> 
                        "Insufficient funds for gas fees"
                    e.message?.contains("not enabled receiving", ignoreCase = true) == true ->
                        e.message ?: "Recipient setup error"
                    e.message?.contains("No personal info", ignoreCase = true) == true ->
                        "You must set up your personal profile first before sharing."
                    else -> "Error: ${e.message}"
                }
                
                AlertDialog.Builder(context)
                    .setTitle("Transaction Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    /**
     * Show dialog to revoke data sharing (HealthWalletV2).
     * User enters share ID to revoke.
     */
    fun showRevokeShareDialog(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope
    ) {
        val shareIdInput = EditText(context).apply {
            hint = "Share ID (get from share records list)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(50, 40, 50, 40)
        }
        
        AlertDialog.Builder(context)
            .setTitle("Revoke Data Share")
            .setMessage("Enter the Share ID you want to revoke.\\nYou can find this in your shared records list.")
            .setView(shareIdInput)
            .setPositiveButton("Revoke") { _, _ ->
                val shareIdText = shareIdInput.text.toString().trim()
                
                if (shareIdText.isEmpty()) {
                    Toast.makeText(context, "Please enter Share ID", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val shareId = shareIdText.toBigIntegerOrNull()
                if (shareId == null || shareId <= BigInteger.ZERO) {
                    Toast.makeText(context, "Invalid Share ID", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                revokeShare(context, lifecycleScope, shareId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Revoke a data share on blockchain (HealthWalletV2).
     */
    private fun revokeShare(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        shareId: BigInteger
    ) {
        var progressDialog: ProgressDialog? = null
        
        lifecycleScope.launch {
            try {
                progressDialog = ProgressDialog(context).apply {
                    setMessage("Revoking share...")
                    setCancelable(false)
                    show()
                }
                
                // User signs transaction with their wallet
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.revokeShare(shareId = shareId)
                }
                
                progressDialog?.dismiss()
                
                // Show success
                AlertDialog.Builder(context)
                    .setTitle("Share Revoked!")
                    .setMessage("The recipient no longer has access to your shared data.\n\nTransaction: ${txHash.take(10)}...")
                    .setPositiveButton("OK", null)
                    .show()
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                
                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true -> 
                        "Transaction cancelled by user"
                    else -> "Error: ${e.message}"
                }
                
                AlertDialog.Builder(context)
                    .setTitle("Transaction Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    /**
     * Share a specific medical report with a recipient.
     * Similar to shareData() but for per-record medical reports.
     */
    fun shareReport(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        report: com.fyp.blockchainhealthwallet.Report,
        recipientAddress: String,
        recipientName: String,
        recipientType: BlockchainService.RecipientType,
        expiryTimestamp: BigInteger
    ) {
        var progressDialog: ProgressDialog? = null
        
        lifecycleScope.launch {
            try {
                progressDialog = ProgressDialog(context).apply {
                    setMessage("Preparing to share report...\\nPlease wait...")
                    setCancelable(false)
                    show()
                }
                
                // Get current user's address
                val userAddress = WalletManager.getAddress()
                    ?: throw Exception("Wallet not connected")
                
                // Step 1: Get recipient's public key from blockchain
                progressDialog?.setMessage("Checking recipient's public key...")
                Log.d(TAG, "Step 1: Getting recipient's public key")
                
                val recipientPublicKeyIpfsHash = withContext(Dispatchers.IO) {
                    BlockchainService.getUserPublicKey(recipientAddress)
                }
                
                if (recipientPublicKeyIpfsHash.isEmpty()) {
                    throw Exception("Recipient has not enabled receiving shares. They must set up their public key first.")
                }
                
                Log.d(TAG, "Recipient public key IPFS hash: $recipientPublicKeyIpfsHash")
                
                // Step 2: Download recipient's public key from IPFS
                progressDialog?.setMessage("Downloading recipient's public key...")
                Log.d(TAG, "Step 2: Downloading public key from IPFS")
                
                val publicKeyResponse = withContext(Dispatchers.IO) {
                    ApiClient.api.getFromIPFS(recipientPublicKeyIpfsHash)
                }
                
                if (!publicKeyResponse.isSuccessful) {
                    throw Exception("Failed to download recipient's public key from IPFS")
                }
                
                val publicKeyJson = publicKeyResponse.body()?.string()
                    ?: throw Exception("Empty public key response")
                
                val publicKeyData = gson.fromJson(publicKeyJson, com.google.gson.JsonObject::class.java)
                val recipientPublicKeyBase64 = publicKeyData.get("publicKey")?.asString
                    ?: throw Exception("No public key in IPFS data")
                
                Log.d(TAG, "Downloaded recipient public key, length: ${recipientPublicKeyBase64.length}")
                
                // Step 3: Get report reference from blockchain
                progressDialog?.setMessage("Fetching report from blockchain...")
                Log.d(TAG, "Step 3: Getting report ref for ID: ${report.id}")
                
                val reportRef = withContext(Dispatchers.IO) {
                    BlockchainService.getReportRef(report.id.toBigInteger())
                } ?: throw Exception("Report not found on blockchain")
                
                Log.d(TAG, "Report IPFS hash: ${reportRef.encryptedDataIpfsHash}")
                
                // Step 4: Decrypt report's random AES key
                progressDialog?.setMessage("Preparing encryption keys...")
                Log.d(TAG, "Step 4: Decrypting report's AES key")
                
                val encryptedAesKeyBase64 = reportRef.encryptedKey
                if (encryptedAesKeyBase64.isEmpty()) {
                    throw Exception("Report has no encryption key - cannot share")
                }
                
                val reportAesKey: javax.crypto.SecretKey = withContext(Dispatchers.IO) {
                    EncryptionHelper.decryptKeyFromBlockchain(encryptedAesKeyBase64, userAddress)
                }
                
                Log.d(TAG, "Decrypted report's AES key")
                
                // Step 5: Re-encrypt report's AES key with recipient's RSA public key
                progressDialog?.setMessage("Encrypting with recipient's key...")
                Log.d(TAG, "Step 5: Encrypting report's AES key with recipient's RSA public key")
                
                val encryptedRecordKey: String = withContext(Dispatchers.IO) {
                    RSAHelper.encryptKeyWithPublicKey(reportAesKey, recipientPublicKeyBase64)
                }
                
                Log.d(TAG, "Encrypted record key length: ${encryptedRecordKey.length}")
                
                // Step 6: Create recipient info JSON and upload to IPFS
                progressDialog?.setMessage("Uploading recipient info...")
                Log.d(TAG, "Step 6: Creating and uploading recipient info to IPFS")
                
                val recipientInfoJson = com.google.gson.JsonObject().apply {
                    addProperty("name", recipientName)
                    addProperty("type", recipientType.name)
                }.toString()
                
                val recipientDataIpfsHash = withContext(Dispatchers.IO) {
                    val ipfsResponse = ApiClient.api.uploadToIPFS(
                        okhttp3.MultipartBody.Part.createFormData(
                            "file",
                            "recipient_info.json",
                            okhttp3.RequestBody.create(
                                "application/json".toMediaTypeOrNull(),
                                recipientInfoJson
                            )
                        )
                    )
                    
                    if (!ipfsResponse.isSuccessful) {
                        throw Exception("Failed to upload recipient info to IPFS")
                    }
                    
                    ipfsResponse.body()?.ipfsHash 
                        ?: throw Exception("No IPFS hash in upload response")
                }
                
                Log.d(TAG, "Recipient info uploaded to IPFS: $recipientDataIpfsHash")
                
                // Step 7: Generate recipient name hash
                val recipientNameHash = "0x" + recipientName.hashCode().toString().padStart(64, '0').take(64)
                
                // Step 8: Share report on blockchain
                progressDialog?.setMessage("Sending to wallet...\\nPlease approve transaction")
                Log.d(TAG, "Step 8: Sharing report on blockchain")
                
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.shareData(
                        recipientAddress = recipientAddress,
                        recipientNameHash = recipientNameHash,
                        encryptedRecipientDataIpfsHash = recipientDataIpfsHash,  // Recipient info IPFS
                        recipientType = recipientType,
                        recordType = BlockchainService.RecordType.MEDICAL_REPORT,  // ← Medical report type
                        recordId = report.id.toBigInteger(),  // ← Report's actual ID
                        expiryDate = expiryTimestamp,
                        accessLevel = BlockchainService.AccessLevel.VIEW_ONLY,
                        encryptedRecordKey = encryptedRecordKey  // RSA-encrypted AES key
                    )
                }
                
                progressDialog?.dismiss()
                
                Log.d(TAG, "✅ Share report transaction successful: $txHash")
                
                // Show success
                AlertDialog.Builder(context)
                    .setTitle("Report Shared Successfully!")
                    .setMessage("Recipient can now decrypt and view your medical report.\\n\\nReport: ${report.title}\\n\\nTransaction: ${txHash.take(10)}...\\n\\nFull TX: $txHash")
                    .setPositiveButton("OK", null)
                    .show()
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                Log.e(TAG, "Error sharing report", e)
                
                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true -> 
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true -> 
                        "Insufficient funds for gas fees"
                    e.message?.contains("not enabled receiving", ignoreCase = true) == true ->
                        e.message ?: "Recipient setup error"
                    e.message?.contains("Report not found", ignoreCase = true) == true ->
                        "Report not found on blockchain. Please try refreshing."
                    else -> "Error: ${e.message}"
                }
                
                AlertDialog.Builder(context)
                    .setTitle("Share Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    /**
     * Check wallet connection and show dialog if not connected.
     * @return true if connected, false otherwise
     */
    fun checkWalletConnection(context: Context): Boolean {
        if (!BlockchainService.isWalletConnected()) {
            AlertDialog.Builder(context)
                .setTitle("Wallet Not Connected")
                .setMessage("Please connect your wallet first to use blockchain features.")
                .setPositiveButton("OK", null)
                .show()
            return false
        }
        return true
    }
}
