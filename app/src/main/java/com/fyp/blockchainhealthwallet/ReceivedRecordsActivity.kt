package com.fyp.blockchainhealthwallet

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
                val ipfsResponse = withContext(Dispatchers.IO) {
                    com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(ipfsHash)
                }
                
                if (!ipfsResponse.isSuccessful || ipfsResponse.body() == null) {
                    throw Exception("Failed to download from IPFS")
                }

                Log.d(TAG, "IPFS data downloaded successfully")

                container.removeAllViews()

                when (share.recordType) {
                    BlockchainService.RecordType.PERSONAL_INFO -> {
                        // Query the personal info reference from blockchain using owner address
                        Log.d(TAG, "Querying personal info ref for owner: ${share.ownerAddress}")
                        
                        val personalInfoRef = withContext(Dispatchers.IO) {
                            BlockchainService.getPersonalInfoRef(share.ownerAddress)
                        } ?: throw Exception("Personal info not found on blockchain")
                        
                        Log.d(TAG, "Personal info ref retrieved - IPFS: ${personalInfoRef.encryptedDataIpfsHash}")
                        
                        // Download encrypted personal info from IPFS
                        val personalInfoResponse = withContext(Dispatchers.IO) {
                            com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(personalInfoRef.encryptedDataIpfsHash)
                        }
                        
                        if (!personalInfoResponse.isSuccessful || personalInfoResponse.body() == null) {
                            throw Exception("Failed to download personal info from IPFS")
                        }
                        
                        val encryptedJsonData = personalInfoResponse.body()!!.string()
                        Log.d(TAG, "Encrypted IPFS data received, length: ${encryptedJsonData.length}")
                        
                        // Decrypt the record key using our RSA private key
                        Log.d(TAG, "Decrypting personal info with RSA")
                        addDataRow(container, "🔐 Decrypting", "Using your private key...")
                        
                        val encryptedRecordKey = share.encryptedRecordKey
                        Log.d(TAG, "Encrypted record key length: ${encryptedRecordKey.length}")
                        
                        val aesKey: javax.crypto.SecretKey = withContext(Dispatchers.IO) {
                            RSAHelper.decryptKeyWithPrivateKey(encryptedRecordKey)
                        }
                        
                        Log.d(TAG, "Decrypted AES key")
                        
                        // Decrypt the actual data using the AES key
                        addDataRow(container, "🔓 Decrypting", "Decrypting data...")
                        
                        // Convert Base64 string to bytes if needed
                        val encryptedBytes = android.util.Base64.decode(encryptedJsonData, android.util.Base64.NO_WRAP)
                        
                        val decryptedJsonData: String = withContext(Dispatchers.IO) {
                            EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                        }
                        
                        Log.d(TAG, "Decrypted personal info, length: ${decryptedJsonData.length}")
                        
                        // Parse and display the data
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
                        // Query the medication record from blockchain using recordId
                        Log.d(TAG, "Querying medication record for recordId: ${share.recordId}")
                        
                        val medicationRef = withContext(Dispatchers.IO) {
                            BlockchainService.getMedicationRef(share.recordId)
                        } ?: throw Exception("Medication record not found on blockchain")
                        
                        Log.d(TAG, "Medication ref retrieved - IPFS: ${medicationRef.encryptedDataIpfsHash}")
                        
                        // Download encrypted medication data from IPFS
                        val medicationResponse = withContext(Dispatchers.IO) {
                            com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(medicationRef.encryptedDataIpfsHash)
                        }
                        
                        if (!medicationResponse.isSuccessful || medicationResponse.body() == null) {
                            throw Exception("Failed to download medication data from IPFS")
                        }
                        
                        val encryptedJsonData = medicationResponse.body()!!.string()
                        
                        // Decrypt with RSA-encrypted AES key
                        val aesKey = withContext(Dispatchers.IO) {
                            RSAHelper.decryptKeyWithPrivateKey(share.encryptedRecordKey)
                        }
                        
                        val encryptedBytes = android.util.Base64.decode(encryptedJsonData, android.util.Base64.NO_WRAP)
                        val decryptedJsonData = withContext(Dispatchers.IO) {
                            EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                        }
                        
                        // Parse the medication data
                        container.removeAllViews()
                        val dataMap = Gson().fromJson(decryptedJsonData, Map::class.java) as Map<String, Any>

                        // Show brief preview in a nicer format
                        val name = (dataMap["name"] as? String)?.takeIf { it.isNotBlank() } ?: "N/A"
                        val dosage = (dataMap["dosage"] as? String)?.takeIf { it.isNotBlank() } ?: "N/A"
                        val frequency = (dataMap["frequency"] as? String)?.takeIf { it.isNotBlank() } ?: "N/A"
                        val isActive = (dataMap["isActive"] as? Boolean) ?: true

                        addDataRow(container, "💊 Medication Name", name)
                        addDataRow(container, "💉 Dosage", dosage)
                        addDataRow(container, "⏰ Frequency", frequency)
                        addDataRow(container, "📊 Status", if (isActive) "✅ Active" else "⏸️ Completed")

                        // Create a button to view full details
                        val btnViewMedication = android.widget.Button(this@ReceivedRecordsActivity).apply {
                            text = "📋 View Full Medication Details"
                            setBackgroundColor(ContextCompat.getColor(this@ReceivedRecordsActivity, R.color.medication))
                            setTextColor(ContextCompat.getColor(this@ReceivedRecordsActivity, android.R.color.white))
                            setPadding(32, 24, 32, 24)
                            setOnClickListener {
                                openReceivedMedicationDetails(share, dataMap)
                            }
                        }
                        container.addView(btnViewMedication)

                        Log.d(TAG, "✅ Successfully displayed medication preview")
                    }
                    BlockchainService.RecordType.VACCINATION -> {
                        // Query the vaccination record from blockchain using recordId
                        Log.d(TAG, "Querying vaccination record for recordId: ${share.recordId}")
                        
                        val vaccinationRef = withContext(Dispatchers.IO) {
                            BlockchainService.getVaccinationRef(share.recordId)
                        } ?: throw Exception("Vaccination record not found on blockchain")
                        
                        Log.d(TAG, "Vaccination ref retrieved - IPFS: ${vaccinationRef.encryptedDataIpfsHash}")
                        
                        // Download encrypted vaccination data from IPFS
                        val vaccinationResponse = withContext(Dispatchers.IO) {
                            com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(vaccinationRef.encryptedDataIpfsHash)
                        }
                        
                        if (!vaccinationResponse.isSuccessful || vaccinationResponse.body() == null) {
                            throw Exception("Failed to download vaccination data from IPFS")
                        }
                        
                        val encryptedJsonData = vaccinationResponse.body()!!.string()
                        
                        // Decrypt with RSA-encrypted AES key
                        val aesKey = withContext(Dispatchers.IO) {
                            RSAHelper.decryptKeyWithPrivateKey(share.encryptedRecordKey)
                        }
                        
                        val encryptedBytes = android.util.Base64.decode(encryptedJsonData, android.util.Base64.NO_WRAP)
                        val decryptedJsonData = withContext(Dispatchers.IO) {
                            EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                        }
                        
                        // Parse vaccination data
                        val vaccinationData = org.json.JSONObject(decryptedJsonData)

                        // Display the data
                        container.removeAllViews()
                        addDataRow(container, "💉 Vaccine Name", vaccinationData.optString("vaccineName", "N/A"))
                        addDataRow(container, "🏥 Manufacturer", vaccinationData.optString("manufacturer", "N/A"))
                        addDataRow(container, "🌍 Country", vaccinationData.optString("country", "N/A"))
                        addDataRow(container, "👨‍⚕️ Provider", vaccinationData.optString("provider", "N/A"))
                        addDataRow(container, "📍 Location", vaccinationData.optString("location", "N/A"))
                        addDataRow(container, "🔢 Batch Number", vaccinationData.optString("batchNumber", "N/A"))

                        // Add "View Certificate" button if certificate exists
                        if (!vaccinationRef.encryptedCertificateIpfsHash.isNullOrEmpty()) {
                            val btnViewCertificate = android.widget.Button(this@ReceivedRecordsActivity).apply {
                                text = "📄 View Certificate"
                                setBackgroundColor(ContextCompat.getColor(this@ReceivedRecordsActivity, R.color.primary))
                                setTextColor(ContextCompat.getColor(this@ReceivedRecordsActivity, android.R.color.white))
                                setPadding(32, 24, 32, 24)
                                setOnClickListener {
                                    openReceivedVaccinationCertificate(
                                        vaccinationRef.encryptedCertificateIpfsHash,
                                        aesKey,
                                        vaccinationData.optString("vaccineName", "Vaccination")
                                    )
                                }
                            }
                            container.addView(btnViewCertificate)

                            Log.d(TAG, "✅ Added certificate viewing button")
                        }
                    }
                    BlockchainService.RecordType.MEDICAL_REPORT -> {
                        // For medical reports, show brief summary and "View Details" button
                        lifecycleScope.launch {
                            try {
                                // Query report ref to get basic info
                                val reportRef = withContext(Dispatchers.IO) {
                                    BlockchainService.getReportRef(share.recordId)
                                }
                                
                                if (reportRef != null) {
                                    // Download and decrypt just to get title and type for preview
                                    val metadataResponse = withContext(Dispatchers.IO) {
                                        com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(reportRef.encryptedDataIpfsHash)
                                    }
                                    
                                    if (metadataResponse.isSuccessful && metadataResponse.body() != null) {
                                        val metadataBody = metadataResponse.body()!!
                                        val contentType = metadataResponse.headers()["Content-Type"] ?: "application/octet-stream"
                                        
                                        val encryptedBytes = if (contentType.contains("text/plain") || contentType.contains("application/json")) {
                                            android.util.Base64.decode(metadataBody.string(), android.util.Base64.NO_WRAP)
                                        } else {
                                            metadataBody.bytes()
                                        }
                                        
                                        // Decrypt to get preview info
                                        val aesKey = withContext(Dispatchers.IO) {
                                            RSAHelper.decryptKeyWithPrivateKey(share.encryptedRecordKey)
                                        }
                                        
                                        val decryptedJson = withContext(Dispatchers.IO) {
                                            EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                                        }
                                        
                                        val data = org.json.JSONObject(decryptedJson)
                                        
                                        // Show brief summary
                                        container.removeAllViews()
                                        addDataRow(container, "📄 Title", data.optString("title", "N/A"))
                                        addDataRow(container, "🏥 Type", data.optString("type", "N/A"))
                                        addDataRow(container, "📅 Date", data.optString("date", "N/A"))
                                        
                                        // Add "View Full Details" button
                                        val btnViewDetails = android.widget.Button(this@ReceivedRecordsActivity).apply {
                                            text = "📋 View Full Details"
                                            setBackgroundColor(ContextCompat.getColor(this@ReceivedRecordsActivity, R.color.primary))
                                            setTextColor(ContextCompat.getColor(this@ReceivedRecordsActivity, android.R.color.white))
                                            setPadding(32, 24, 32, 24)
                                            setOnClickListener {
                                                openReceivedReportDetails(share, reportRef, data, aesKey)
                                            }
                                        }
                                        container.addView(btnViewDetails)
                                        
                                        Log.d(TAG, "✅ Successfully displayed medical report preview")
                                    }
                                } else {
                                    addDataRow(container, "❌ Error", "Report not found")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error loading medical report preview", e)
                                addDataRow(container, "❌ Error", "Failed to load: ${e.message}")
                            }
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
    
    /**
     * Open received report details in new activity
     */
    private fun openReceivedReportDetails(
        share: BlockchainService.ShareRecord,
        reportRef: BlockchainService.MedicalReportRef,
        reportData: org.json.JSONObject,
        aesKey: javax.crypto.SecretKey
    ) {
        val intent = android.content.Intent(this, ViewReceivedReportActivity::class.java).apply {
            putExtra("SHARE_ID", share.id.toString())
            putExtra("RECORD_ID", share.recordId.toString())
            putExtra("TITLE", reportData.optString("title", "N/A"))
            putExtra("TYPE", reportData.optString("type", "N/A"))
            putExtra("DATE", reportData.optString("date", "N/A"))
            putExtra("DOCTOR", reportData.optString("doctorName", "N/A"))
            putExtra("HOSPITAL", reportData.optString("hospital", "N/A"))
            putExtra("DESCRIPTION", reportData.optString("description", "N/A"))
            putExtra("HAS_FILE", reportRef.hasFile)
            putExtra("FILE_IPFS_HASH", reportRef.encryptedFileIpfsHash)
            putExtra("ENCRYPTED_RECORD_KEY", share.encryptedRecordKey)
        }
        startActivity(intent)
    }

    /**
     * Open received medication details in new activity
     */
    private fun openReceivedMedicationDetails(
        share: BlockchainService.ShareRecord,
        dataMap: Map<String, Any>
    ) {
        val intent = android.content.Intent(this, ViewReceivedMedicationActivity::class.java).apply {
            putExtra("SHARE_ID", share.id.toString())
            putExtra("OWNER_ADDRESS", share.ownerAddress)
            putExtra("MEDICATION_NAME", (dataMap["name"] as? String)?.takeIf { it.isNotBlank() } ?: "")
            putExtra("DOSAGE", (dataMap["dosage"] as? String)?.takeIf { it.isNotBlank() } ?: "")
            putExtra("FREQUENCY", (dataMap["frequency"] as? String)?.takeIf { it.isNotBlank() } ?: "")
            putExtra("ROUTE", (dataMap["route"] as? String)?.takeIf { it.isNotBlank() } ?: "")
            putExtra("IS_ACTIVE", (dataMap["isActive"] as? Boolean) ?: true)
            putExtra("START_DATE", ((dataMap["startDate"] as? Number)?.toLong() ?: 0L))
            putExtra("END_DATE", ((dataMap["endDate"] as? Number)?.toLong() ?: 0L))
            putExtra("PURPOSE", (dataMap["purpose"] as? String)?.takeIf { it.isNotBlank() } ?: "")
            putExtra("DOCTOR", (dataMap["prescribingDoctor"] as? String)?.takeIf { it.isNotBlank() } ?: "")
            putExtra("PHARMACY", (dataMap["pharmacy"] as? String)?.takeIf { it.isNotBlank() } ?: "")
            putExtra("NOTES", (dataMap["notes"] as? String)?.takeIf { it.isNotBlank() } ?: "")
            putExtra("CREATED_AT", ((dataMap["createdAt"] as? Number)?.toLong() ?: 0L))
        }
        startActivity(intent)
    }

    /**
     * Download, decrypt, and view vaccination certificate (PDF/PNG)
     * Same logic as VaccinationDetailActivity but using shared AES key
     */
    private fun openReceivedVaccinationCertificate(
        certificateIpfsHash: String,
        aesKey: javax.crypto.SecretKey,
        vaccineName: String
    ) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@ReceivedRecordsActivity, "Loading certificate...", Toast.LENGTH_SHORT).show()

                // Download encrypted certificate from IPFS
                val encryptedFile = withContext(Dispatchers.IO) {
                    val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(certificateIpfsHash)
                    if (!response.isSuccessful || response.body() == null) {
                        throw Exception("Failed to download certificate from IPFS")
                    }

                    val tempFile = java.io.File(cacheDir, "shared_cert_${System.currentTimeMillis()}.enc")
                    val fileBytes = response.body()!!.bytes()
                    tempFile.writeBytes(fileBytes)
                    tempFile
                }

                // Decrypt certificate using shared AES key
                val decryptedFile = withContext(Dispatchers.IO) {
                    val outputFile = java.io.File(cacheDir, "shared_cert_${System.currentTimeMillis()}.dat")

                    // Decrypt to get the decrypted bytes
                    val encryptedBytes = encryptedFile.readBytes()
                    val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
                    val iv = encryptedBytes.copyOfRange(0, 16)
                    val encryptedData = encryptedBytes.copyOfRange(16, encryptedBytes.size)

                    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, aesKey, javax.crypto.spec.IvParameterSpec(iv))
                    val decryptedBytes = cipher.doFinal(encryptedData)

                    outputFile.writeBytes(decryptedBytes)
                    outputFile
                }

                // Clean up encrypted file
                encryptedFile.delete()

                // Detect file type and open appropriate viewer
                val header = decryptedFile.inputStream().use { it.readBytes().take(10).toByteArray() }
                Log.d(TAG, "Certificate header: ${header.joinToString(" ") { "%02x".format(it) }}")

                val (fileType, extension) = when {
                    header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() -> Pair("PDF", "pdf")
                    header.size >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> Pair("Image", "jpg")
                    header.size >= 2 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() -> Pair("Image", "png")
                    else -> Pair("Unknown", "dat")
                }

                // Rename file with correct extension
                val correctFile = if (decryptedFile.extension != extension) {
                    val renamed = java.io.File(decryptedFile.parent, "shared_cert_${System.currentTimeMillis()}.$extension")
                    decryptedFile.copyTo(renamed, overwrite = true)
                    decryptedFile.delete()
                    renamed
                } else {
                    decryptedFile
                }

                // Open appropriate viewer
                when (fileType) {
                    "PDF" -> {
                        val intent = android.content.Intent(this@ReceivedRecordsActivity, PdfViewerActivity::class.java)
                        intent.putExtra("PDF_PATH", correctFile.absolutePath)
                        intent.putExtra("TITLE", "$vaccineName Certificate")
                        startActivity(intent)
                    }
                    "Image" -> {
                        val intent = android.content.Intent(this@ReceivedRecordsActivity, ImageViewerActivity::class.java)
                        intent.putExtra("IMAGE_PATH", correctFile.absolutePath)
                        intent.putExtra("TITLE", "$vaccineName Certificate")
                        startActivity(intent)
                    }
                    else -> {
                        Toast.makeText(
                            this@ReceivedRecordsActivity,
                            "Unsupported file type",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading shared certificate", e)
                Toast.makeText(
                    this@ReceivedRecordsActivity,
                    "Error loading certificate: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

