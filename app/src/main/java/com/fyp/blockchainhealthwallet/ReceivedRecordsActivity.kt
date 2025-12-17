package com.fyp.blockchainhealthwallet

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.CategoryKeyManager
import com.fyp.blockchainhealthwallet.crypto.PublicKeyRegistry
import com.fyp.blockchainhealthwallet.databinding.ActivityReceivedRecordsBinding
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class ReceivedRecordsActivity : AppCompatActivity() {

    private val TAG = "ReceivedRecords"
    private lateinit var binding: ActivityReceivedRecordsBinding
    private var selectedCategory: BlockchainService.DataCategory? = null
    private val receivedShares = mutableListOf<BlockchainService.ShareRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceivedRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCategoryFilters()
        setupClickListeners()
        autoDiscoverReceivedShares() // Auto-discover shares for this user
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

        // Import Share FAB
        binding.fabImportShare.setOnClickListener {
            val address = WalletManager.getAddress()
            if (address != null) {
                showImportShareDialog(address)
            } else {
                Toast.makeText(this, "Please connect wallet first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun autoDiscoverReceivedShares() {
        val address = WalletManager.getAddress()
        if (address == null) {
            showEmptyState()
            Toast.makeText(this, "Please connect wallet first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val progressDialog = android.app.ProgressDialog(this@ReceivedRecordsActivity).apply {
                    setMessage("Loading received shares...")
                    setCancelable(false)
                    show()
                }

                receivedShares.clear()
                Log.d(TAG, "Getting received share IDs for: $address")

                // Use efficient contract function to get shares where user is recipient
                val shareIds = withContext(Dispatchers.IO) {
                    BlockchainService.getReceivedShareIds(address)
                }

                Log.d(TAG, "Found ${shareIds.size} received share IDs: $shareIds")

                // Fetch each share record
                for (shareId in shareIds) {
                    try {
                        val share = withContext(Dispatchers.IO) {
                            BlockchainService.getShareRecord(shareId)
                        }
                        share?.let {
                            Log.d(TAG, "Loaded share $shareId: category=${it.sharedDataCategory}, status=${it.status}")
                            receivedShares.add(it)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading share $shareId: ${e.message}")
                    }
                }

                progressDialog.dismiss()

                if (receivedShares.isEmpty()) {
                    Log.d(TAG, "No shares found for address: $address")
                    showEmptyState()
                    Toast.makeText(this@ReceivedRecordsActivity, "No shares found for your address", Toast.LENGTH_LONG).show()
                } else {
                    Log.d(TAG, "Found ${receivedShares.size} shares for current user")
                    filterByCategory(null)
                    Toast.makeText(this@ReceivedRecordsActivity, "Found ${receivedShares.size} share(s)", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading received shares", e)
                showEmptyState()
                Toast.makeText(this@ReceivedRecordsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImportShareDialog(recipientAddress: String) {
        val input = android.widget.EditText(this)
        input.hint = "Enter Share ID (e.g., 1)"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Import Received Share")
            .setMessage("To view shares someone sent you, enter the Share ID they provided:")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val shareIdText = input.text.toString()
                if (shareIdText.isNotBlank()) {
                    importShareById(shareIdText, recipientAddress)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun importShareById(shareIdText: String, recipientAddress: String) {
        lifecycleScope.launch {
            try {
                val shareId = java.math.BigInteger(shareIdText)
                
                val share = withContext(Dispatchers.IO) {
                    BlockchainService.getShareRecord(shareId)
                }

                if (share == null) {
                    Toast.makeText(this@ReceivedRecordsActivity, "Share not found", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                    return@launch
                }

                // Verify this share is for current user
                if (!share.recipientAddress.equals(recipientAddress, ignoreCase = true)) {
                    androidx.appcompat.app.AlertDialog.Builder(this@ReceivedRecordsActivity)
                        .setTitle("Access Denied")
                        .setMessage("This share is not for your address.\n\nShare recipient: ${share.recipientAddress}\nYour address: $recipientAddress")
                        .setPositiveButton("OK") { _, _ ->
                            showEmptyState()
                        }
                        .show()
                    return@launch
                }

                receivedShares.clear()
                receivedShares.add(share)
                filterByCategory(null)
                Toast.makeText(this@ReceivedRecordsActivity, "Share imported successfully!", Toast.LENGTH_SHORT).show()

            } catch (e: NumberFormatException) {
                Toast.makeText(this@ReceivedRecordsActivity, "Invalid Share ID format", Toast.LENGTH_SHORT).show()
                showEmptyState()
            } catch (e: Exception) {
                Log.e(TAG, "Error importing share", e)
                Toast.makeText(this@ReceivedRecordsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
        }
    }

    private fun filterByCategory(category: BlockchainService.DataCategory?) {
        selectedCategory = category

        // Clear all existing cards
        binding.containerAllRecords.removeAllViews()

        val filteredShares = if (category == null) {
            receivedShares
        } else {
            receivedShares.filter { it.sharedDataCategory == category }
        }

        if (filteredShares.isEmpty()) {
            showEmptyState()
            return
        }

        hideEmptyState()

        // Create a card for each share
        filteredShares.forEach { share ->
            createShareCard(share)
        }
    }

    private fun createShareCard(share: BlockchainService.ShareRecord) {
        val cardView = LayoutInflater.from(this).inflate(
            R.layout.item_received_share_card,
            binding.containerAllRecords,
            false
        )

        // Get category info (icon and title)
        val (icon, title) = when (share.sharedDataCategory) {
            BlockchainService.DataCategory.PERSONAL_INFO -> "👤" to "Personal Information"
            BlockchainService.DataCategory.MEDICATION_RECORDS -> "💊" to "Medications"
            BlockchainService.DataCategory.VACCINATION_RECORDS -> "💉" to "Vaccinations"
            BlockchainService.DataCategory.MEDICAL_REPORTS -> "📄" to "Medical Reports"
            else -> "📋" to "Unknown"
        }

        // Set up card header
        val tvCategoryIcon = cardView.findViewById<TextView>(R.id.tvCategoryIcon)
        val tvCategoryTitle = cardView.findViewById<TextView>(R.id.tvCategoryTitle)
        val tvShareInfo = cardView.findViewById<TextView>(R.id.tvShareInfo)
        val btnCollapseCard = cardView.findViewById<View>(R.id.btnCollapseCard)
        val containerCardData = cardView.findViewById<LinearLayout>(R.id.containerCardData)

        tvCategoryIcon.text = icon
        tvCategoryTitle.text = title
        tvShareInfo.text = "Share ID: ${share.id}"

        // Set up collapse/expand functionality
        var isExpanded = true
        btnCollapseCard.setOnClickListener {
            isExpanded = !isExpanded
            containerCardData.visibility = if (isExpanded) View.VISIBLE else View.GONE
            btnCollapseCard.rotation = if (isExpanded) 90f else 0f
        }

        // Load the actual data into the card
        loadActualData(share, containerCardData)

        // Add the card to the container
        binding.containerAllRecords.addView(cardView)
    }

    private fun loadActualData(share: BlockchainService.ShareRecord, container: LinearLayout) {
        container.removeAllViews()
        
        // Check if the share is revoked
        if (share.status == BlockchainService.ShareStatus.REVOKED) {
            addDataRow(container, "🚫 Access Revoked", "This share has been revoked by the owner")
            addDataRow(container, "ℹ️ Status", "You no longer have access to this data")
            return
        }

        // Check if the share is expired
        if (share.status == BlockchainService.ShareStatus.EXPIRED) {
            addDataRow(container, "⏰ Access Expired", "This share has expired")
            addDataRow(container, "ℹ️ Status", "The access period for this data has ended")
            return
        }

        addDataRow(container, "⏳ Loading", "Fetching and decrypting data...")

        lifecycleScope.launch {
            try {
                val ipfsHash = share.encryptedRecipientDataIpfsHash
                val encryptedCategoryKey = share.encryptedCategoryKey
                val ownerAddress = share.ownerAddress

                Log.d(TAG, "Fetching IPFS data from: $ipfsHash")
                Log.d(TAG, "Owner address: $ownerAddress")
                Log.d(TAG, "Encrypted category key: ${encryptedCategoryKey.take(30)}...")

                // Step 1: Get owner's public key for ECDH decryption
                val ownerPublicKey = withContext(Dispatchers.IO) {
                    PublicKeyRegistry.getPublicKeyForAddress(ownerAddress)
                }

                if (ownerPublicKey == null) {
                    container.removeAllViews()
                    addDataRow(container, "⚠️ Decryption Error", "Owner's encryption key not found")
                    addDataRow(container, "ℹ️ Info", "Cannot decrypt without owner's public key")
                    return@launch
                }

                // Step 2: Decrypt the category key using ECDH
                Log.d(TAG, "Attempting to decrypt category key...")
                Log.d(TAG, "Owner public key: ${ownerPublicKey.take(20)}... (${ownerPublicKey.length} chars)")
                Log.d(TAG, "Encrypted category key: ${encryptedCategoryKey.take(50)}...")
                
                // Verify recipient has their own ECDH key
                val recipientPrivateKey = try {
                    PublicKeyRegistry.getPrivateKey()
                } catch (e: Exception) {
                    container.removeAllViews()
                    addDataRow(container, "⚠️ Decryption Error", "Failed to load your encryption key")
                    addDataRow(container, "ℹ️ Solution", "Please reconnect your wallet or re-register your encryption key in Profile")
                    return@launch
                }
                
                if (recipientPrivateKey == null) {
                    container.removeAllViews()
                    addDataRow(container, "⚠️ Decryption Error", "You haven't registered your encryption key")
                    addDataRow(container, "ℹ️ Solution", "Please open Profile and create/update your profile to register your encryption key")
                    return@launch
                }
                
                // Verify our public key on blockchain matches our private key
                val recipientAddress = WalletManager.getAddress()
                val recipientPublicKeyOnChain = withContext(Dispatchers.IO) {
                    PublicKeyRegistry.getPublicKeyForAddress(recipientAddress!!)
                }
                val recipientLocalPublicKey = PublicKeyRegistry.getPublicKeyHex()
                
                if (recipientPublicKeyOnChain != recipientLocalPublicKey) {
                    Log.w(TAG, "⚠️ Public key mismatch!")
                    Log.w(TAG, "On-chain: ${recipientPublicKeyOnChain?.take(20)}...")
                    Log.w(TAG, "Local: ${recipientLocalPublicKey?.take(20)}...")
                    container.removeAllViews()
                    addDataRow(container, "⚠️ Key Mismatch", "Your encryption keys are out of sync")
                    addDataRow(container, "ℹ️ Solution", "Please update your profile to re-register your encryption key")
                    return@launch
                }
                
                Log.d(TAG, "✅ Recipient key verification passed")
                
                val categoryKey = try {
                    CategoryKeyManager.decryptSharedCategoryKey(encryptedCategoryKey, ownerPublicKey)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt category key", e)
                    Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                    
                    // Special case: Old personal info shares (before encryption was implemented)
                    // These have encrypted category key but plain JSON data on IPFS
                    if (share.sharedDataCategory == BlockchainService.DataCategory.PERSONAL_INFO && 
                        e.message?.contains("BadTag") == true) {
                        Log.w(TAG, "Detected old personal info share (pre-encryption). Attempting to load as plain JSON...")
                        
                        try {
                            // Fetch data from IPFS as plain text
                            val plainData = withContext(Dispatchers.IO) {
                                val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(ipfsHash)
                                response.body()?.string() ?: throw Exception("Empty IPFS response")
                            }
                            
                            // Parse and display
                            val data = Gson().fromJson(plainData, PersonalInfo::class.java)
                            container.removeAllViews()
                            addDataRow(container, "⚠️ Legacy Share", "This is an old share format (unencrypted)")
                            addDataRow(container, "👤 Name", "${data.firstName} ${data.lastName}")
                            addDataRow(container, "✉️ Email", data.email)
                            addDataRow(container, "📞 Phone", data.phone)
                            addDataRow(container, "🆔 HKID", data.hkid)
                            addDataRow(container, "🎂 Date of Birth", data.dateOfBirth)
                            addDataRow(container, "⚧ Gender", data.gender)
                            addDataRow(container, "🩸 Blood Type", data.bloodType)
                            addDataRow(container, "🏠 Address", data.address)
                            addDataRow(container, "🚨 Emergency Contact", "${data.emergencyContact.name} (${data.emergencyContact.relationship}) - ${data.emergencyContact.phone}")
                            return@launch
                        } catch (fallbackError: Exception) {
                            Log.e(TAG, "Fallback to plain JSON also failed", fallbackError)
                            // Continue to show original error
                        }
                    }
                    
                    Log.e(TAG, "Stack trace: ", e)
                    container.removeAllViews()
                    addDataRow(container, "⚠️ Decryption Failed", "Could not decrypt the encryption key")
                    addDataRow(container, "ℹ️ Error Type", e.javaClass.simpleName)
                    addDataRow(container, "ℹ️ Error Details", e.message ?: "Unknown error")
                    addDataRow(container, "💡 Suggestion", "This share may be incompatible. Ask the sender to re-share with updated settings.")
                    return@launch
                }

                Log.d(TAG, "✅ Category key decrypted successfully")

                // Step 3: Fetch encrypted data from IPFS
                val jsonData = if (share.sharedDataCategory == BlockchainService.DataCategory.PERSONAL_INFO) {
                    // Personal info is stored as Base64-encoded encrypted string
                    val encryptedDataBase64 = withContext(Dispatchers.IO) {
                        val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(ipfsHash)
                        response.body()?.string() ?: throw Exception("Empty IPFS response")
                    }
                    
                    Log.d(TAG, "IPFS encrypted data received: ${encryptedDataBase64.length} chars (Base64)")
                    
                    // Decrypt using EncryptionHelper for Base64 encrypted data
                    try {
                        com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptDataWithCategory(
                            encryptedDataBase64,
                            share.sharedDataCategory
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decrypt personal info data", e)
                        // Try as plain text (for old unencrypted shares)
                        encryptedDataBase64
                    }
                } else {
                    // Medical reports and other files are stored as raw encrypted bytes
                    val encryptedData = withContext(Dispatchers.IO) {
                        val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(ipfsHash)
                        response.body()?.bytes() ?: throw Exception("Empty IPFS response")
                    }

                    Log.d(TAG, "IPFS encrypted data received: ${encryptedData.size} bytes")

                    // Step 4: Decrypt the data using the category key
                    try {
                        decryptData(encryptedData, categoryKey)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decrypt IPFS data", e)
                        // Try as plain text (for backward compatibility with unencrypted data)
                        String(encryptedData, Charsets.UTF_8)
                    }
                }

                Log.d(TAG, "Decrypted data: ${jsonData.take(200)}...")

                container.removeAllViews()

                when (share.sharedDataCategory) {
                    BlockchainService.DataCategory.PERSONAL_INFO -> {
                        var data: PersonalInfo? = null
                        var legacyWarning = false
                        try {
                            data = Gson().fromJson(jsonData, PersonalInfo::class.java)
                        } catch (jsonEx: Exception) {
                            // Try to Base64-decode and parse as JSON (handles accidental double-encoding)
                            try {
                                val decoded = android.util.Base64.decode(jsonData, android.util.Base64.DEFAULT)
                                val decodedString = String(decoded, Charsets.UTF_8)
                                data = Gson().fromJson(decodedString, PersonalInfo::class.java)
                                legacyWarning = true
                            } catch (b64Ex: Exception) {
                                // Still failed, treat as legacy plain JSON
                                legacyWarning = true
                            }
                        }
                        if (data != null) {
                            if (legacyWarning) addDataRow(container, "⚠️ Legacy Share", "This is an old or non-standard share format.")
                            addDataRow(container, "👤 Name", "${data.firstName} ${data.lastName}")
                            addDataRow(container, "✉️ Email", data.email)
                            addDataRow(container, "📞 Phone", data.phone)
                            addDataRow(container, "🆔 HKID", data.hkid)
                            addDataRow(container, "🎂 Date of Birth", data.dateOfBirth)
                            addDataRow(container, "⚧ Gender", data.gender)
                            addDataRow(container, "🩸 Blood Type", data.bloodType)
                            addDataRow(container, "🏠 Address", data.address)
                            addDataRow(container, "🚨 Emergency Contact", "${data.emergencyContact.name} (${data.emergencyContact.relationship}) - ${data.emergencyContact.phone}")
                        } else {
                            addDataRow(container, "❌ Error", "Could not parse personal info data. This share may be corrupted or incompatible.")
                        }
                    }
                    BlockchainService.DataCategory.MEDICATION_RECORDS -> {
                        // Parse as JSON object and display fields
                        val dataMap = Gson().fromJson(jsonData, Map::class.java) as Map<String, Any>
                        dataMap.forEach { (key, value) ->
                            addDataRow(container, "💊 ${key.replaceFirstChar { it.uppercase() }}", value.toString())
                        }
                    }
                    BlockchainService.DataCategory.VACCINATION_RECORDS -> {
                        val dataMap = Gson().fromJson(jsonData, Map::class.java) as Map<String, Any>
                        dataMap.forEach { (key, value) ->
                            addDataRow(container, "💉 ${key.replaceFirstChar { it.uppercase() }}", value.toString())
                        }
                    }
                    BlockchainService.DataCategory.MEDICAL_REPORTS -> {
                        val dataMap = Gson().fromJson(jsonData, Map::class.java) as Map<String, Any>
                        dataMap.forEach { (key, value) ->
                            addDataRow(container, "📄 ${key.replaceFirstChar { it.uppercase() }}", value.toString())
                        }
                    }
                    else -> {
                        addDataRow(container, "📊 Data", jsonData)
                    }
                }

                Log.d(TAG, "✅ Successfully loaded and displayed IPFS data")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading IPFS data", e)
                container.removeAllViews()
                addDataRow(container, "❌ Error", "Failed to load data: ${e.message}")
                addDataRow(container, "🔗 IPFS Hash", share.encryptedRecipientDataIpfsHash)
            }
        }
    }

    // Data models matching ProfileActivity
    data class PersonalInfo(
        val firstName: String,
        val lastName: String,
        val email: String,
        val hkid: String,
        val dateOfBirth: String,
        val gender: String,
        val bloodType: String,
        val phone: String,
        val address: String,
        val emergencyContact: EmergencyContact
    )

    data class EmergencyContact(
        val name: String,
        val relationship: String,
        val phone: String
    )

    private fun addDataRow(container: LinearLayout, label: String, value: String) {
        val rowView = LayoutInflater.from(this).inflate(
            android.R.layout.simple_list_item_2,
            container,
            false
        )
        rowView.findViewById<TextView>(android.R.id.text1).apply {
            text = label
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
        }
        rowView.findViewById<TextView>(android.R.id.text2).apply {
            text = value
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
        }
        container.addView(rowView)
    }

    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        binding.emptyState.visibility = View.GONE
    }

    /**
     * Decrypt data using the category key.
     * The encrypted data format is: IV (12 bytes) + ciphertext + auth tag
     */
    private fun decryptData(encryptedData: ByteArray, categoryKey: javax.crypto.SecretKey): String {
        // Check if data might be plain text (starts with { or [)
        if (encryptedData.isNotEmpty() &&
            (encryptedData[0] == '{'.code.toByte() || encryptedData[0] == '['.code.toByte())) {
            Log.d(TAG, "Data appears to be unencrypted JSON, returning as-is")
            return String(encryptedData, Charsets.UTF_8)
        }

        // AES-GCM decryption
        val ivSize = 12
        if (encryptedData.size <= ivSize) {
            throw IllegalArgumentException("Encrypted data too short")
        }

        val iv = encryptedData.copyOfRange(0, ivSize)
        val ciphertext = encryptedData.copyOfRange(ivSize, encryptedData.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, categoryKey, gcmSpec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
