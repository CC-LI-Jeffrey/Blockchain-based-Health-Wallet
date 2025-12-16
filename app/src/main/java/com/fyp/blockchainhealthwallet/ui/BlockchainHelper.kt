package com.fyp.blockchainhealthwallet.ui

import android.app.ProgressDialog
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

/**
 * Helper class for blockchain-related UI operations (HealthWalletV2).
 * Provides dialogs and workflows for data sharing.
 */
object BlockchainHelper {
    
    /**
     * Show dialog to share data with a recipient (HealthWalletV2).
     * Uses category-based sharing with cryptographic isolation.
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
        
        // Data category selection
        val categories = arrayOf(
            "Personal Info",
            "Medication Records",
            "Vaccination Records",
            "Medical Reports",
            "All Data"
        )
        var selectedCategory = BlockchainService.DataCategory.MEDICAL_REPORTS
        val categoryInput = android.widget.Spinner(context).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_item, categories)
            setPadding(50, 20, 50, 20)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    selectedCategory = when (position) {
                        0 -> BlockchainService.DataCategory.PERSONAL_INFO
                        1 -> BlockchainService.DataCategory.MEDICATION_RECORDS
                        2 -> BlockchainService.DataCategory.VACCINATION_RECORDS
                        3 -> BlockchainService.DataCategory.MEDICAL_REPORTS
                        else -> BlockchainService.DataCategory.ALL_DATA
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
                    setMessage("Preparing to share data...\nPlease wait...")
                    setCancelable(false)
                    show()
                }
                
                // Generate dummy values for HealthWalletV2 (in production, these would be real encrypted data)
                val recipientNameHash = "0x" + recipientName.hashCode().toString().padStart(64, '0').take(64)
                val dummyIpfsHash = "QmDummyRecipientData" + System.currentTimeMillis()
                val dummyCategoryKey = "encrypted-category-key-" + System.currentTimeMillis()
                
                progressDialog?.setMessage("Sending to wallet...\nPlease approve transaction")
                
                // User signs transaction with their wallet
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.shareData(
                        recipientAddress = recipientAddress,
                        recipientNameHash = recipientNameHash,
                        encryptedRecipientDataIpfsHash = dummyIpfsHash,
                        recipientType = recipientType,
                        dataCategory = dataCategory,
                        expiryDate = expiryTimestamp,
                        accessLevel = BlockchainService.AccessLevel.VIEW_ONLY,
                        encryptedCategoryKey = dummyCategoryKey
                    )
                }
                
                progressDialog?.dismiss()
                
                // Show success
                AlertDialog.Builder(context)
                    .setTitle("Data Shared Successfully!")
                    .setMessage("Recipient can now access your ${dataCategory.name.lowercase().replace('_', ' ')} until expiry.\n\nTransaction: ${txHash.take(10)}...")
                    .setPositiveButton("OK", null)
                    .show()
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                
                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true -> 
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true -> 
                        "Insufficient funds for gas fees"
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
