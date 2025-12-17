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
                    setMessage("Scanning blockchain for shares...")
                    setCancelable(false)
                    show()
                }

                // Get the latest share ID from blockchain to know how many exist
                val maxShareId = withContext(Dispatchers.IO) {
                    // Try to get share IDs from a known address to find max ID
                    // We'll scan first 100 shares (can be adjusted)
                    100
                }

                receivedShares.clear()
                Log.d(TAG, "Scanning for shares where recipient = $address")

                // Scan all possible share IDs
                for (shareId in 1..maxShareId) {
                    try {
                        val share = withContext(Dispatchers.IO) {
                            BlockchainService.getShareRecord(java.math.BigInteger.valueOf(shareId.toLong()))
                        }

                        share?.let {
                            Log.d(TAG, "Share $shareId: recipient=${it.recipientAddress}, category=${it.sharedDataCategory}")
                            // Check if this share is for current user
                            if (it.recipientAddress.equals(address, ignoreCase = true)) {
                                Log.d(TAG, "✅ Found share $shareId for current user!")
                                receivedShares.add(it)
                            }
                        }
                    } catch (e: Exception) {
                        // Share doesn't exist, continue scanning
                        if (shareId <= 10) { // Only log first 10 to avoid spam
                            Log.d(TAG, "Share $shareId doesn't exist or error: ${e.message}")
                        }
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
                Log.e(TAG, "Error auto-discovering shares", e)
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

        addDataRow(container, "⏳ Loading", "Fetching data from IPFS...")

        lifecycleScope.launch {
            try {
                val ipfsHash = share.encryptedRecipientDataIpfsHash
                Log.d(TAG, "Fetching IPFS data from: $ipfsHash")

                val jsonData = withContext(Dispatchers.IO) {
                    val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(ipfsHash)
                    response.body()?.string() ?: throw Exception("Empty IPFS response")
                }

                Log.d(TAG, "IPFS data received: ${jsonData.take(200)}...")

                container.removeAllViews()

                when (share.sharedDataCategory) {
                    BlockchainService.DataCategory.PERSONAL_INFO -> {
                        val data = Gson().fromJson(jsonData, PersonalInfo::class.java)
                        addDataRow(container, "👤 Name", "${data.firstName} ${data.lastName}")
                        addDataRow(container, "✉️ Email", data.email)
                        addDataRow(container, "📞 Phone", data.phone)
                        addDataRow(container, "🆔 HKID", data.hkid)
                        addDataRow(container, "🎂 Date of Birth", data.dateOfBirth)
                        addDataRow(container, "⚧ Gender", data.gender)
                        addDataRow(container, "🩸 Blood Type", data.bloodType)
                        addDataRow(container, "🏠 Address", data.address)
                        addDataRow(container, "🚨 Emergency Contact", "${data.emergencyContact.name} (${data.emergencyContact.relationship}) - ${data.emergencyContact.phone}")
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
}
