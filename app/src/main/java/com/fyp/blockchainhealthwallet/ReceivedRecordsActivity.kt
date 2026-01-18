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
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.blockchain.RSAHelper
import com.fyp.blockchainhealthwallet.databinding.ActivityReceivedRecordsBinding
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                Log.d(TAG, "Getting received shares for: $address")

                // Use getReceivedShareIds() to directly get shares for this recipient (MUCH FASTER!)
                val shareIds = withContext(Dispatchers.IO) {
                    BlockchainService.getReceivedShareIds(address)
                }

                Log.d(TAG, "Found ${shareIds.size} share ID(s): $shareIds")

                // Fetch each share record
                for (shareId in shareIds) {
                    try {
                        val share = withContext(Dispatchers.IO) {
                            BlockchainService.getShareRecord(shareId, address)
                        }

                        share?.let {
                            Log.d(TAG, "Share ${shareId}: recordType=${it.recordType}, recordId=${it.recordId}, status=${it.status}")
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
                    Toast.makeText(this@ReceivedRecordsActivity, "No shares received yet", Toast.LENGTH_LONG).show()
                } else {
                    Log.d(TAG, "Loaded ${receivedShares.size} share(s) successfully")
                    filterByCategory(null)
                    Toast.makeText(this@ReceivedRecordsActivity, "Loaded ${receivedShares.size} share(s)", Toast.LENGTH_SHORT).show()
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
            // Map RecordType to DataCategory for filtering
            receivedShares.filter {
                when (it.recordType) {
                    BlockchainService.RecordType.PERSONAL_INFO -> category == BlockchainService.DataCategory.PERSONAL_INFO
                    BlockchainService.RecordType.MEDICATION -> category == BlockchainService.DataCategory.MEDICATION_RECORDS
                    BlockchainService.RecordType.VACCINATION -> category == BlockchainService.DataCategory.VACCINATION_RECORDS
                    BlockchainService.RecordType.MEDICAL_REPORT -> category == BlockchainService.DataCategory.MEDICAL_REPORTS
                }
            }
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

        // Get category info (icon and title) based on record type
        val (icon, title) = when (share.recordType) {
            BlockchainService.RecordType.PERSONAL_INFO -> "👤" to "Personal Information"
            BlockchainService.RecordType.MEDICATION -> "💊" to "Medications"
            BlockchainService.RecordType.VACCINATION -> "💉" to "Vaccinations"
            BlockchainService.RecordType.MEDICAL_REPORT -> "📄" to "Medical Reports"
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

        addDataRow(container, "⏳ Loading", "Fetching data from IPFS...")

        lifecycleScope.launch {
            try {
                val ipfsHash = share.encryptedRecipientDataIpfsHash
                Log.d(TAG, "Fetching IPFS data from: $ipfsHash")

                // Step 1: Download encrypted data from IPFS
                val encryptedJsonData = withContext(Dispatchers.IO) {
                    val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(ipfsHash)
                    response.body()?.string() ?: throw Exception("Empty IPFS response")
                }

                Log.d(TAG, "Encrypted IPFS data received, length: ${encryptedJsonData.length}")

                container.removeAllViews()

                when (share.recordType) {
                    BlockchainService.RecordType.PERSONAL_INFO -> {
                        // Step 2: Decrypt the record key using our RSA private key
                        Log.d(TAG, "Decrypting personal info with RSA")
                        addDataRow(container, "🔐 Decrypting", "Using your private key...")
                        
                        val encryptedRecordKey = share.encryptedRecordKey  // This is the AES key encrypted with our RSA public key
                        Log.d(TAG, "Encrypted record key length: ${encryptedRecordKey.length}")
                        Log.d(TAG, "Encrypted record key (first 50 chars): ${encryptedRecordKey.take(50)}")
                        
                        // Verify we have the right RSA key
                        val currentPublicKeyHash = withContext(Dispatchers.IO) {
                            try {
                                RSAHelper.getPublicKeyHash()
                            } catch (e: Exception) {
                                Log.e(TAG, "No RSA key found for current user!", e)
                                null
                            }
                        }
                        Log.d(TAG, "Current user's public key hash: $currentPublicKeyHash")
                        
                        // Get the public key hash from blockchain
                        val blockchainPublicKeyHash = withContext(Dispatchers.IO) {
                            try {
                                val userAddress = com.fyp.blockchainhealthwallet.wallet.WalletManager.getAddress()
                                val ipfsHash = BlockchainService.getUserPublicKey(userAddress!!)
                                // Download and hash it
                                val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(ipfsHash)
                                val publicKeyJson = response.body()?.string() ?: ""
                                val publicKeyData = com.google.gson.Gson().fromJson(publicKeyJson, Map::class.java) as Map<String, String>
                                val publicKeyBase64 = publicKeyData["publicKey"] ?: ""
                                
                                // Calculate hash
                                val publicKeyBytes = android.util.Base64.decode(publicKeyBase64, android.util.Base64.NO_WRAP)
                                val digest = java.security.MessageDigest.getInstance("SHA-256")
                                val hashBytes = digest.digest(publicKeyBytes)
                                "0x" + hashBytes.joinToString("") { "%02x".format(it) }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error getting blockchain public key hash", e)
                                null
                            }
                        }
                        Log.d(TAG, "Blockchain stored public key hash: $blockchainPublicKeyHash")
                        
                        if (currentPublicKeyHash != blockchainPublicKeyHash) {
                            Log.e(TAG, "⚠️ KEY MISMATCH! Your current RSA key doesn't match the one on blockchain!")
                            Log.e(TAG, "Current:    $currentPublicKeyHash")
                            Log.e(TAG, "Blockchain: $blockchainPublicKeyHash")
                            withContext(Dispatchers.Main) {
                                addDataRow(container, "❌ Error", "Your RSA key has changed since the share was created")
                                addDataRow(container, "💡 Solution", "Ask the sender to share again with your new public key")
                                Toast.makeText(this@ReceivedRecordsActivity, "Key mismatch - cannot decrypt", Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        }
                        
                        val aesKey: javax.crypto.SecretKey = withContext(Dispatchers.IO) {
                            RSAHelper.decryptKeyWithPrivateKey(encryptedRecordKey)
                        }
                        
                        Log.d(TAG, "Decrypted AES key")
                        
                        // Step 3: Decrypt the actual data using the AES key
                        addDataRow(container, "🔓 Decrypting", "Decrypting data...")
                        
                        // Convert Base64 string to bytes if needed
                        val encryptedBytes = android.util.Base64.decode(encryptedJsonData, android.util.Base64.NO_WRAP)
                        
                        val decryptedJsonData: String = withContext(Dispatchers.IO) {
                            EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                        }
                        
                        Log.d(TAG, "Decrypted personal info, length: ${decryptedJsonData.length}")
                        
                        // Step 4: Parse and display the data
                        container.removeAllViews()
                        val data = Gson().fromJson(decryptedJsonData, PersonalInfo::class.java)
                        addDataRow(container, "👤 Name", "${data.firstName} ${data.lastName}")
                        addDataRow(container, "✉️ Email", data.email)
                        addDataRow(container, "📞 Phone", data.phone)
                        addDataRow(container, "🆔 HKID", data.hkid)
                        addDataRow(container, "🎂 Date of Birth", data.dateOfBirth)
                        addDataRow(container, "⚧ Gender", data.gender)
                        addDataRow(container, "🩸 Blood Type", data.bloodType)
                        addDataRow(container, "🏠 Address", data.address)
                        addDataRow(container, "🚨 Emergency Contact", "${data.emergencyContact.name} (${data.emergencyContact.relationship}) - ${data.emergencyContact.phone}")
                        
                        Log.d(TAG, "✅ Successfully decrypted and displayed personal info")
                    }
                    BlockchainService.RecordType.MEDICATION -> {
                        // Parse as JSON object and display fields
                        val dataMap = Gson().fromJson(encryptedJsonData, Map::class.java) as Map<String, Any>
                        dataMap.forEach { (key, value) ->
                            addDataRow(container, "💊 ${key.replaceFirstChar { it.uppercase() }}", value.toString())
                        }
                    }
                    BlockchainService.RecordType.VACCINATION -> {
                        val dataMap = Gson().fromJson(encryptedJsonData, Map::class.java) as Map<String, Any>
                        dataMap.forEach { (key, value) ->
                            addDataRow(container, "💉 ${key.replaceFirstChar { it.uppercase() }}", value.toString())
                        }
                    }
                    BlockchainService.RecordType.MEDICAL_REPORT -> {
                        val dataMap = Gson().fromJson(encryptedJsonData, Map::class.java) as Map<String, Any>
                        dataMap.forEach { (key, value) ->
                            addDataRow(container, "📄 ${key.replaceFirstChar { it.uppercase() }}", value.toString())
                        }
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
}
