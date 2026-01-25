package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.adapter.VaccinationRecordAdapter
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.model.VaccinationRecord
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * VaccinationRecordActivity - Displays user's vaccination records from blockchain
 * 
 * BLOCKCHAIN INTEGRATION:
 * 1. Fetch vaccination IDs from smart contract
 * 2. For each ID, fetch VaccinationRecordRef (contains IPFS hashes)
 * 3. Decrypt and fetch data from IPFS
 * 4. Display in RecyclerView
 * 
 * UPLOAD FLOW:
 * 1. User selects/fills vaccination data
 * 2. Encrypt data locally with random AES key
 * 3. Upload encrypted data to IPFS
 * 4. Encrypt certificate (if exists) and upload to IPFS
 * 5. Call BlockchainService.addVaccination(encryptedDataHash, encryptedCertHash, date)
 */
class VaccinationRecordActivity : AppCompatActivity() {

    private lateinit var rvVaccinationRecords: RecyclerView
    private lateinit var adapter: VaccinationRecordAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private val vaccinationList = mutableListOf<VaccinationRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vaccination_record)

        setupUI()
        loadVaccinationRecords()
    }

    private fun setupUI() {
        // Setup views
        rvVaccinationRecords = findViewById(R.id.rvVaccinationRecords)
        progressBar = findViewById(R.id.progressBar)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        
        rvVaccinationRecords.layoutManager = LinearLayoutManager(this)

        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Setup upload button
        findViewById<CardView>(R.id.btnUploadRecord).setOnClickListener {
            val intent = Intent(this, AddVaccinationActivity::class.java)
            startActivityForResult(intent, REQUEST_ADD_VACCINATION)
        }
    }

    private fun loadVaccinationRecords() {
        // Check if wallet is connected
        if (WalletManager.isConnected()) {
            loadFromBlockchain()
        } else {
            loadMockData()
        }
    }

    /**
     * Load vaccination records from blockchain
     * 
     * Flow:
     * 1. Get user's vaccination IDs from smart contract
     * 2. For each ID, fetch VaccinationRecordRef (IPFS hashes + encryptedKey)
     * 3. Download encrypted data from IPFS
     * 4. Decrypt using encryptedKey (recovered with user's wallet key)
     * 5. Parse JSON and display records
     */
    private fun loadFromBlockchain() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                rvVaccinationRecords.visibility = View.GONE
                emptyStateLayout.visibility = View.GONE
                
                val userAddress = BlockchainService.getUserAddress()
                if (userAddress == null) {
                    Log.e(TAG, "Wallet not connected")
                    showError("Wallet not connected. Please connect your wallet.")
                    progressBar.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                    return@launch
                }

                Log.d(TAG, "Fetching vaccination records for: $userAddress")

                // Get all vaccination IDs from blockchain
                val vaccinationIds = withContext(Dispatchers.IO) {
                    BlockchainService.getVaccinationIds(userAddress)
                }
                
                Log.d(TAG, "Found ${vaccinationIds.size} vaccination IDs: $vaccinationIds")
                
                if (vaccinationIds.isEmpty()) {
                    Log.w(TAG, "No vaccination records found")
                    progressBar.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                    return@launch
                }
                
                // Fetch each vaccination's data from blockchain + IPFS
                val vaccinations = mutableListOf<VaccinationRecord>()
                for (vaccinationId in vaccinationIds) {
                    try {
                        Log.d(TAG, "Fetching vaccination ID: $vaccinationId")
                        val vaccination = fetchVaccinationDetails(vaccinationId)
                        if (vaccination != null) {
                            vaccinations.add(vaccination)
                            Log.d(TAG, "Vaccination loaded: ${vaccination.vaccineName}")
                        } else {
                            Log.w(TAG, "Vaccination $vaccinationId returned null")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to fetch vaccination $vaccinationId", e)
                    }
                }
                
                Log.d(TAG, "Total vaccinations loaded: ${vaccinations.size}")
                
                vaccinationList.clear()
                vaccinationList.addAll(vaccinations)
                
                adapter = VaccinationRecordAdapter(
                    records = vaccinationList,
                    onItemClick = { record -> openVaccinationDetail(record) },
                    onShareClick = { record -> showShareVaccinationDialog(record) }
                )
                rvVaccinationRecords.adapter = adapter
                
                progressBar.visibility = View.GONE
                updateEmptyState()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading vaccinations from blockchain", e)
                progressBar.visibility = View.GONE
                showError("Failed to load vaccinations: ${e.message}")
                emptyStateLayout.visibility = View.VISIBLE
            }
        }
    }
    
    /**
     * Fetch individual vaccination details from blockchain and decrypt from IPFS
     */
    private suspend fun fetchVaccinationDetails(vaccinationId: java.math.BigInteger): VaccinationRecord? = withContext(Dispatchers.IO) {
        try {
            // 1. Get vaccination reference from blockchain
            val vaccinationRef = BlockchainService.getVaccinationRef(vaccinationId)
            if (vaccinationRef == null) {
                Log.w(TAG, "Vaccination $vaccinationId not found")
                return@withContext null
            }
            
            Log.d(TAG, "Vaccination $vaccinationId: ${vaccinationRef.encryptedDataIpfsHash}")
            
            // 2. Download encrypted data from IPFS
            val metadataResponse = ApiClient.api.getFromIPFS(vaccinationRef.encryptedDataIpfsHash)
            if (!metadataResponse.isSuccessful || metadataResponse.body() == null) {
                Log.e(TAG, "Failed to download vaccination data from IPFS")
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
            
            // 3. Decrypt data using encryptedKey from blockchain
            val decryptedJson = if (vaccinationRef.encryptedKey.isNotEmpty()) {
                // Random key per record - decrypt using the encrypted key from blockchain
                val userAddress = WalletManager.getAddress() ?: run {
                    Log.e(TAG, "No wallet address available for decryption")
                    return@withContext null
                }
                
                val decryptedAesKey = EncryptionHelper.decryptKeyFromBlockchain(
                    vaccinationRef.encryptedKey,
                    userAddress
                )
                
                EncryptionHelper.decryptBytesWithKey(encryptedBytes, decryptedAesKey)
            } else {
                // Fallback for legacy records without encryptedKey
                EncryptionHelper.decryptBytesWithCategory(
                    encryptedBytes,
                    BlockchainService.DataCategory.VACCINATION_RECORDS
                )
            }
            
            // 4. Parse JSON metadata
            val json = JSONObject(decryptedJson)
            val vaccineName = json.optString("vaccineName", "Unknown Vaccine")
            val vaccineNameEn = json.optString("vaccineNameEn", vaccineName)
            val vaccineFullName = json.optString("vaccineFullName", vaccineName)
            val manufacturer = json.optString("manufacturer", "Unknown")
            val country = json.optString("country", "Unknown")
            val provider = json.optString("provider", "Unknown Provider")
            val location = json.optString("location", "Unknown Location")
            val batchNumber = json.optString("batchNumber", "N/A")
            
            // 5. Format date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = dateFormat.format(Date(vaccinationRef.vaccinationDate.toLong() * 1000))
            
            // 6. Get certificate IPFS hash if exists
            val certificateIpfsHash = if (vaccinationRef.encryptedCertificateIpfsHash.isNotEmpty()) {
                vaccinationRef.encryptedCertificateIpfsHash
            } else null
            
            VaccinationRecord(
                id = vaccinationId.toString(),
                date = date,
                vaccineName = vaccineName,
                vaccineNameEn = vaccineNameEn,
                vaccineFullName = vaccineFullName,
                manufacturer = manufacturer,
                country = country,
                provider = provider,
                location = location,
                batchNumber = batchNumber,
                certificateUrl = certificateIpfsHash,
                blockchainId = vaccinationId,
                encryptedDataIpfsHash = vaccinationRef.encryptedDataIpfsHash,
                encryptedCertificateIpfsHash = certificateIpfsHash,
                vaccinationDate = vaccinationRef.vaccinationDate.toLong(),
                createdAt = vaccinationRef.createdAt.toLong(),
                encryptedKey = vaccinationRef.encryptedKey,
                isEncrypted = true,
                isOnBlockchain = true
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching vaccination $vaccinationId", e)
            null
        }
    }
    
    private fun updateEmptyState() {
        if (vaccinationList.isEmpty()) {
            rvVaccinationRecords.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            rvVaccinationRecords.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Load mock data for testing/demo purposes
     */
    private fun loadMockData() {
        val records = listOf(
            VaccinationRecord(
                id = "1",
                date = "June 4, 2022",
                vaccineName = "COVID-19 Vaccine",
                vaccineNameEn = "COVID-19 vaccine",
                vaccineFullName = "Comirnaty COVID-19 mRNA Vaccine (BNT162b2) Concentrate for Dispersion for Injection (3rd Dose)",
                manufacturer = "BioNTech",
                country = "Hong Kong",
                provider = "Department of Health COVID-19 Vaccination Programme (On-Site)",
                location = "Community Vaccination Centre, Hiu Kwong Street Sports Centre",
                batchNumber = "2A088A",
                certificateUrl = "https://drive.google.com/file/d/1UXrKDX2Urj8CaOHb674zZDj82naFt7aY/view?usp=drive_link",
                isEncrypted = false,
                isOnBlockchain = false
            ),
            VaccinationRecord(
                id = "2",
                date = "August 12, 2021",
                vaccineName = "COVID-19 Vaccine",
                vaccineNameEn = "COVID-19 vaccine",
                vaccineFullName = "Comirnaty COVID-19 mRNA Vaccine (BNT162b2) Concentrate for Dispersion for Injection (2nd Dose)",
                manufacturer = "BioNTech",
                country = "Hong Kong",
                provider = "Department of Health COVID-19 Vaccination Programme (On-Site)",
                location = "Community Vaccination Centre, Hiu Kwong Street Sports Centre",
                batchNumber = "2A088A",
                certificateUrl = "https://drive.google.com/file/d/1UXrKDX2Urj8CaOHb674zZDj82naFt7aY/view?usp=drive_link",
                isEncrypted = false,
                isOnBlockchain = false
            ),
            VaccinationRecord(
                id = "2",
                date = "August 12, 2021",
                vaccineName = "COVID-19 Vaccine",
                vaccineNameEn = "COVID-19 vaccine",
                vaccineFullName = "Comirnaty COVID-19 mRNA Vaccine (BNT162b2) Concentrate for Dispersion for Injection (2nd Dose)",
                manufacturer = "BioNTech",
                country = "Hong Kong",
                provider = "Department of Health COVID-19 Vaccination Programme (On-Site)",
                location = "Community Vaccination Centre, Hiu Kwong Street Sports Centre",
                batchNumber = "2A088B",
                certificateUrl = "https://drive.google.com/file/d/1UXrKDX2Urj8CaOHb674zZDj82naFt7aY/view?usp=drive_link",
                isEncrypted = false,
                isOnBlockchain = false
            ),
            VaccinationRecord(
                id = "3",
                date = "July 19, 2021",
                vaccineName = "COVID-19 Vaccine",
                vaccineNameEn = "COVID-19 vaccine",
                vaccineFullName = "Comirnaty COVID-19 mRNA Vaccine (BNT162b2) Concentrate for Dispersion for Injection (1st Dose)",
                manufacturer = "BioNTech",
                country = "Hong Kong",
                provider = "Department of Health COVID-19 Vaccination Programme (On-Site)",
                location = "Community Vaccination Centre, Hiu Kwong Street Sports Centre",
                batchNumber = "2A088C",
                certificateUrl = "https://drive.google.com/file/d/1UXrKDX2Urj8CaOHb674zZDj82naFt7aY/view?usp=drive_link",
                isEncrypted = false,
                isOnBlockchain = false
            )
        )

        adapter = VaccinationRecordAdapter(
            records = records,
            onItemClick = { record -> openVaccinationDetail(record) },
            onShareClick = { record -> showShareVaccinationDialog(record) }
        )
        rvVaccinationRecords.adapter = adapter
    }

    private fun openVaccinationDetail(record: VaccinationRecord) {
        val intent = Intent(this, ViewVaccinationActivity::class.java).apply {
            // Display fields
            putExtra("RECORD_ID", record.id)
            putExtra("DATE", record.date)
            putExtra("VACCINE_NAME", record.vaccineName)
            putExtra("VACCINE_NAME_EN", record.vaccineNameEn)
            putExtra("VACCINE_FULL_NAME", record.vaccineFullName)
            putExtra("MANUFACTURER", record.manufacturer)
            putExtra("COUNTRY", record.country)
            putExtra("PROVIDER", record.provider)
            putExtra("LOCATION", record.location)
            putExtra("BATCH_NUMBER", record.batchNumber)
            putExtra("CERTIFICATE_URL", record.certificateUrl)
            
            // Certificate IPFS hash and encryption key for viewing
            if (!record.encryptedCertificateIpfsHash.isNullOrEmpty()) {
                putExtra("CERTIFICATE_HASH", record.encryptedCertificateIpfsHash)
            }
            if (!record.encryptedKey.isNullOrEmpty()) {
                putExtra("ENCRYPTED_KEY", record.encryptedKey)
            }
            
            // Blockchain fields
            if (record.blockchainId != null) {
                putExtra("BLOCKCHAIN_ID", record.blockchainId.toLong())
            }
            val createdAt = record.createdAt
            if (createdAt != null && createdAt > 0) {
                putExtra("CREATED_AT", createdAt)
            }
            putExtra("IS_ON_BLOCKCHAIN", record.isOnBlockchain)
            putExtra("IS_ENCRYPTED", record.isEncrypted)
        }
        startActivity(intent)
    }

    private fun showShareVaccinationDialog(record: VaccinationRecord) {
        // Create input fields
        val recipientAddressInput = android.widget.EditText(this).apply {
            hint = "Recipient Wallet Address (0x...)"
            setPadding(50, 20, 50, 20)
        }
        
        val recipientNameInput = android.widget.EditText(this).apply {
            hint = "Recipient Name (e.g., Dr. Smith)"
            setPadding(50, 20, 50, 20)
        }
        
        // Recipient type selection
        val recipientTypes = arrayOf("Doctor", "Hospital", "Clinic", "Insurance", "Pharmacy", "Laboratory", "Other")
        var selectedRecipientType = BlockchainService.RecipientType.DOCTOR
        val recipientTypeInput = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@VaccinationRecordActivity, android.R.layout.simple_spinner_item, recipientTypes)
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
        
        val durationInput = android.widget.EditText(this).apply {
            hint = "Duration in Days (e.g., 30)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("30")
            setPadding(50, 20, 50, 20)
        }
        
        // Create container layout
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
            addView(android.widget.TextView(this@VaccinationRecordActivity).apply {
                text = "Share: ${record.vaccineName}"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 20)
            })
            addView(android.widget.TextView(this@VaccinationRecordActivity).apply {
                text = "Date: ${record.date}"
                setPadding(0, 0, 0, 20)
            })
            addView(recipientAddressInput)
            addView(recipientNameInput)
            addView(android.widget.TextView(this@VaccinationRecordActivity).apply {
                text = "Recipient Type"
                setPadding(0, 20, 0, 10)
            })
            addView(recipientTypeInput)
            addView(durationInput)
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Share Vaccination Record")
            .setView(container)
            .setPositiveButton("Share") { _, _ ->
                val recipientAddress = recipientAddressInput.text.toString().trim()
                val recipientName = recipientNameInput.text.toString().trim()
                val durationText = durationInput.text.toString().trim()
                
                if (recipientAddress.isEmpty() || recipientName.isEmpty() || durationText.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val durationDays = durationText.toLongOrNull() ?: 0L
                if (durationDays <= 0) {
                    Toast.makeText(this, "Invalid duration", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Calculate expiry timestamp
                val expiryTimestamp = java.math.BigInteger.valueOf((System.currentTimeMillis() / 1000) + (durationDays * 24 * 60 * 60))
                
                // Share the vaccination record
                com.fyp.blockchainhealthwallet.ui.BlockchainHelper.shareVaccination(
                    this,
                    record.blockchainId ?: java.math.BigInteger.ZERO,
                    recipientAddress,
                    recipientName,
                    selectedRecipientType,
                    expiryTimestamp
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_VACCINATION && resultCode == RESULT_OK) {
            // Reload vaccinations from blockchain to get the newly added one
            loadVaccinationRecords()
        }
    }

    companion object {
        private const val TAG = "VaccinationActivity"
        private const val REQUEST_ADD_VACCINATION = 200
    }
}
