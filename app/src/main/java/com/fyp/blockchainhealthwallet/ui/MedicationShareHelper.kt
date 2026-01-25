package com.fyp.blockchainhealthwallet.ui

import android.app.ProgressDialog
import android.content.Context
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.blockchain.RSAHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

/**
 * Helper class for sharing medication records with healthcare providers.
 */
object MedicationShareHelper {
    
    private const val TAG = "MedicationShareHelper"
    private val gson = Gson()
    
    /**
     * Show dialog to share a specific medication record.
     */
    fun showShareMedicationDialog(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        medicationId: BigInteger,
        medicationName: String
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
        
        // Recipient type selection
        val recipientTypes = arrayOf("Doctor", "Hospital", "Clinic", "Pharmacy", "Insurance", "Laboratory", "Other")
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
                        3 -> BlockchainService.RecipientType.PHARMACY
                        4 -> BlockchainService.RecipientType.INSURANCE_COMPANY
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
                text = "Share Medication: $medicationName"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 20)
            })
            addView(android.widget.TextView(context).apply {
                text = "Recipient Information"
                textSize = 14f
                setPadding(0, 0, 0, 10)
            })
            addView(recipientAddressInput)
            addView(recipientNameInput)
            addView(android.widget.TextView(context).apply {
                text = "Recipient Type"
                setPadding(0, 20, 0, 10)
            })
            addView(recipientTypeInput)
            addView(durationInput)
        }
        
        AlertDialog.Builder(context)
            .setTitle("Share Medication Record")
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
                
                shareMedication(
                    context,
                    lifecycleScope,
                    medicationId,
                    medicationName,
                    recipientAddress,
                    recipientName,
                    selectedRecipientType,
                    expiryTimestamp
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Share medication record with recipient on blockchain.
     */
    fun shareMedication(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        medicationId: BigInteger,
        medicationName: String,
        recipientAddress: String,
        recipientName: String,
        recipientType: BlockchainService.RecipientType,
        expiryTimestamp: BigInteger
    ) {
        var progressDialog: ProgressDialog? = null
        
        lifecycleScope.launch {
            try {
                progressDialog = ProgressDialog(context).apply {
                    setMessage("Preparing to share medication...\nPlease wait...")
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
                
                // Step 3: Get medication record from blockchain
                progressDialog?.setMessage("Fetching medication record...")
                Log.d(TAG, "Step 3: Getting medication record")
                
                val medicationRef = withContext(Dispatchers.IO) {
                    BlockchainService.getMedicationRef(medicationId)
                } ?: throw Exception("Medication record not found")
                
                val medicationIpfsHash = medicationRef.encryptedDataIpfsHash
                Log.d(TAG, "Medication IPFS hash: $medicationIpfsHash")
                
                // Step 4: Decrypt own AES key
                progressDialog?.setMessage("Preparing encryption keys...")
                Log.d(TAG, "Step 4: Decrypting own AES key")
                
                val encryptedAesKeyBase64 = medicationRef.encryptedKey
                val aesKey: javax.crypto.SecretKey = withContext(Dispatchers.IO) {
                    EncryptionHelper.decryptKeyFromBlockchain(encryptedAesKeyBase64)
                }
                
                Log.d(TAG, "Decrypted AES key")
                
                // Step 5: Re-encrypt AES key with recipient's RSA public key
                progressDialog?.setMessage("Encrypting with recipient's key...")
                Log.d(TAG, "Step 5: Encrypting AES key with recipient's RSA public key")
                
                val encryptedRecordKey: String = withContext(Dispatchers.IO) {
                    RSAHelper.encryptKeyWithPublicKey(aesKey, recipientPublicKeyBase64)
                }
                
                Log.d(TAG, "Encrypted record key length: ${encryptedRecordKey.length}")
                
                // Step 6: Generate recipient name hash
                val recipientNameHash = "0x" + recipientName.hashCode().toString().padStart(64, '0').take(64)
                
                // Step 7: Share medication on blockchain
                progressDialog?.setMessage("Sending to wallet...\nPlease approve transaction")
                Log.d(TAG, "Step 7: Sharing medication on blockchain")
                
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.shareData(
                        recipientAddress = recipientAddress,
                        recipientNameHash = recipientNameHash,
                        encryptedRecipientDataIpfsHash = medicationIpfsHash,
                        recipientType = recipientType,
                        recordType = BlockchainService.RecordType.MEDICATION,
                        recordId = medicationId,
                        expiryDate = expiryTimestamp,
                        accessLevel = BlockchainService.AccessLevel.VIEW_ONLY,
                        encryptedRecordKey = encryptedRecordKey
                    )
                }
                
                progressDialog?.dismiss()
                
                Log.d(TAG, "✅ Share medication transaction successful: $txHash")
                
                // Show success
                AlertDialog.Builder(context)
                    .setTitle("Medication Shared Successfully!")
                    .setMessage("\"$medicationName\" has been shared with $recipientName.\n\nRecipient can now decrypt and view this medication record using their private key.\n\nTransaction: ${txHash.take(10)}...")
                    .setPositiveButton("OK") { _, _ ->
                        if (context is android.app.Activity) {
                            context.finish()
                        }
                    }
                    .show()
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                Log.e(TAG, "Error sharing medication", e)
                
                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true -> 
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true -> 
                        "Insufficient funds for gas fees"
                    e.message?.contains("not enabled receiving", ignoreCase = true) == true ->
                        e.message ?: "Recipient setup error"
                    e.message?.contains("not found", ignoreCase = true) == true ->
                        "Medication record not found on blockchain"
                    else -> "Error: ${e.message}"
                }
                
                AlertDialog.Builder(context)
                    .setTitle("Share Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK") { _, _ ->
                        if (context is android.app.Activity) {
                            context.finish()
                        }
                    }
                    .show()
            }
        }
    }
}
