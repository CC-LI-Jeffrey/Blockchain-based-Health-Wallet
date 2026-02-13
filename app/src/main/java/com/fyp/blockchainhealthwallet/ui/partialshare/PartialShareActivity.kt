package com.fyp.blockchainhealthwallet.ui.partialshare

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.core.content.FileProvider
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.blockchain.MerkleTreeHelper
import com.fyp.blockchainhealthwallet.models.*
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Activity for creating partial shares (QR code or blockchain)
 */
class PartialShareActivity : AppCompatActivity() {
    
    private lateinit var recordTypeSpinner: Spinner
    private lateinit var attributesContainer: LinearLayout
    private lateinit var shareMethodGroup: RadioGroup
    private lateinit var qrCodeRadio: RadioButton
    private lateinit var blockchainRadio: RadioButton
    private lateinit var receiverAddressLabel: TextView
    private lateinit var receiverAddressContainer: LinearLayout
    private lateinit var btnScanQR: MaterialButton
    private lateinit var btnSelectPhoto: MaterialButton
    private lateinit var receiverAddressInput: EditText
    private lateinit var expiryHoursInput: EditText
    private lateinit var generateButton: Button
    private lateinit var qrCodeImage: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var qrActionButtons: LinearLayout
    private lateinit var btnSaveQR: MaterialButton
    private lateinit var btnShareQR: MaterialButton
    private var currentQRBitmap: Bitmap? = null
    
    // Activity result launchers
    private val qrScannerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val address = result.data?.getStringExtra("ADDRESS")
            if (!address.isNullOrEmpty()) {
                receiverAddressInput.setText(address)
                Toast.makeText(this, "Address scanned successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private val photoPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scanQRFromImage(uri)
        }
    }
    
    private val merkleHelper = MerkleTreeHelper()
    private val selectedAttributes = mutableSetOf<String>()
    private var currentRecordType: RecordSchemas.RecordType? = null
    private var fullRecord: Map<String, String>? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_partial_share)
        
        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        initViews()
        setupListeners()
        loadRecordData()
    }
    
    private fun initViews() {
        recordTypeSpinner = findViewById(R.id.recordTypeSpinner)
        attributesContainer = findViewById(R.id.attributesContainer)
        shareMethodGroup = findViewById(R.id.shareMethodGroup)
        qrCodeRadio = findViewById(R.id.qrCodeRadio)
        blockchainRadio = findViewById(R.id.blockchainRadio)
        receiverAddressLabel = findViewById(R.id.receiverAddressLabel)
        receiverAddressContainer = findViewById(R.id.receiverAddressContainer)
        btnScanQR = findViewById(R.id.btnScanQR)
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto)
        receiverAddressInput = findViewById(R.id.receiverAddressInput)
        expiryHoursInput = findViewById(R.id.expiryHoursInput)
        generateButton = findViewById(R.id.generateButton)
        qrCodeImage = findViewById(R.id.qrCodeImage)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        qrActionButtons = findViewById(R.id.qrActionButtons)
        btnSaveQR = findViewById(R.id.btnSaveQR)
        btnShareQR = findViewById(R.id.btnShareQR)
    }
    
    private fun setupListeners() {
        // Record type spinner
        recordTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Map spinner position to RecordType
                // Spinner order: ["Personal Info", "Medication", "Vaccination", "Medical Report"]
                // Enum order: [PERSONAL_INFO, MEDICATION, VACCINATION, MEDICAL_REPORT]
                currentRecordType = when (position) {
                    0 -> RecordSchemas.RecordType.PERSONAL_INFO
                    1 -> RecordSchemas.RecordType.MEDICATION
                    2 -> RecordSchemas.RecordType.VACCINATION
                    3 -> RecordSchemas.RecordType.MEDICAL_REPORT
                    else -> RecordSchemas.RecordType.MEDICATION
                }
                updateAttributesList()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Share method radio group
        shareMethodGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.qrCodeRadio -> {
                    receiverAddressLabel.visibility = View.GONE
                    receiverAddressContainer.visibility = View.GONE
                }
                R.id.blockchainRadio -> {
                    receiverAddressLabel.visibility = View.VISIBLE
                    receiverAddressContainer.visibility = View.VISIBLE
                }
            }
        }
        
        // Scan QR button
        btnScanQR.setOnClickListener {
            openQRScanner()
        }
        
        // Select Photo button
        btnSelectPhoto.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }
        
        // Generate button
        generateButton.setOnClickListener {
            generatePartialShare()
        }
        
        // Save QR button
        btnSaveQR.setOnClickListener {
            saveQRCode()
        }
        
        // Share QR button
        btnShareQR.setOnClickListener {
            shareQRCode()
        }
    }
    
    private fun loadRecordData() {
        // Check if opened from PartialShareSelectorActivity
        val recordCategory = intent.getStringExtra("RECORD_CATEGORY")
        val recordId = intent.getStringExtra("RECORD_ID")
        
        if (recordCategory != null) {
            // New flow: Load from blockchain based on category and ID
            loadFromBlockchain(recordCategory, recordId)
        } else {
            // Old flow: Load from intent extras (backward compatibility)
            loadFromIntent()
        }
    }
    
    private fun loadFromBlockchain(category: String, recordId: String?) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                
                when (category) {
                    "PERSONAL_INFO" -> {
                        currentRecordType = RecordSchemas.RecordType.PERSONAL_INFO
                        fullRecord = loadPersonalInfoFromBlockchain()
                    }
                    "MEDICATIONS" -> {
                        currentRecordType = RecordSchemas.RecordType.MEDICATION
                        fullRecord = loadMedicationFromBlockchain(recordId ?: "0")
                    }
                    "VACCINATIONS" -> {
                        currentRecordType = RecordSchemas.RecordType.VACCINATION
                        fullRecord = loadVaccinationFromBlockchain(recordId ?: "0")
                    }
                    "REPORTS" -> {
                        currentRecordType = RecordSchemas.RecordType.MEDICAL_REPORT
                        fullRecord = loadReportFromBlockchain(recordId ?: "0")
                    }
                }
                
                progressBar.visibility = View.GONE
                
                // Update UI
                setupRecordTypeSpinner()
                updateAttributesList()
                
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@PartialShareActivity, "Error loading record: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun loadFromIntent() {
        // Load medication data from intent (old flow)
        val medicationName = intent.getStringExtra("medicationName") ?: ""
        val dosage = intent.getStringExtra("dosage") ?: ""
        val frequency = intent.getStringExtra("frequency") ?: ""
        val duration = intent.getStringExtra("duration") ?: ""
        val purpose = intent.getStringExtra("purpose") ?: ""
        val prescribedBy = intent.getStringExtra("prescribedBy") ?: ""
        val prescribedDate = intent.getStringExtra("prescribedDate") ?: ""
        val sideEffects = intent.getStringExtra("sideEffects") ?: ""
        val notes = intent.getStringExtra("notes") ?: ""
        val instructions = intent.getStringExtra("instructions") ?: ""
        val pharmacy = intent.getStringExtra("pharmacy") ?: ""
        val refillsRemaining = intent.getStringExtra("refillsRemaining") ?: ""
        val startDate = intent.getStringExtra("startDate") ?: ""
        val endDate = intent.getStringExtra("endDate") ?: ""
        val price = intent.getStringExtra("price") ?: ""
        
        // For now, only support MEDICATION type
        currentRecordType = RecordSchemas.RecordType.MEDICATION
        
        fullRecord = mapOf(
            "medicationName" to medicationName,
            "dosage" to dosage,
            "frequency" to frequency,
            "duration" to duration,
            "purpose" to purpose,
            "prescribedBy" to prescribedBy,
            "prescribedDate" to prescribedDate,
            "sideEffects" to sideEffects,
            "notes" to notes,
            "instructions" to instructions,
            "pharmacy" to pharmacy,
            "refillsRemaining" to refillsRemaining,
            "startDate" to startDate,
            "endDate" to endDate,
            "price" to price
        )
        
        setupRecordTypeSpinner()
        updateAttributesList()
    }
    
    private fun setupRecordTypeSpinner() {
        // Initialize spinner based on current record type
        // Order matches the mapping in setupListeners
        val types = arrayOf("Personal Info", "Medication", "Vaccination", "Medical Report")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        recordTypeSpinner.adapter = adapter
        
        // Set selection based on current record type
        val selection = when (currentRecordType) {
            RecordSchemas.RecordType.PERSONAL_INFO -> 0
            RecordSchemas.RecordType.MEDICATION -> 1
            RecordSchemas.RecordType.VACCINATION -> 2
            RecordSchemas.RecordType.MEDICAL_REPORT -> 3
            null -> 0
        }
        recordTypeSpinner.setSelection(selection)
    }
    
    // Blockchain data loading methods
    private suspend fun loadPersonalInfoFromBlockchain(): Map<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val address = com.fyp.blockchainhealthwallet.wallet.WalletManager.getAddress()
                    ?: throw Exception("Wallet not connected")
                
                // Get PersonalInfoRef from blockchain
                val personalInfoRef = com.fyp.blockchainhealthwallet.blockchain.BlockchainService.getPersonalInfoRef(address)
                    ?: throw Exception("No personal info found")
                
                if (!personalInfoRef.exists) {
                    throw Exception("Personal info does not exist")
                }
                
                // Download encrypted data from IPFS
                val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(personalInfoRef.encryptedDataIpfsHash)
                if (!response.isSuccessful || response.body() == null) {
                    throw Exception("Failed to download from IPFS")
                }
                
                val encryptedDataBase64 = response.body()!!.string()
                
                // Decrypt data
                val encryptedBytes = android.util.Base64.decode(encryptedDataBase64, android.util.Base64.NO_WRAP)
                val aesKey = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptKeyFromBlockchain(personalInfoRef.encryptedKey)
                val jsonData = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                
                // Parse JSON as Map
                val gson = com.google.gson.Gson()
                val dataMap = gson.fromJson(jsonData, Map::class.java) as Map<*, *>
                
                // Convert to Map<String, String> - flatten nested structures and map field names
                val result = mutableMapOf<String, String>()
                
                // Combine firstName and lastName into fullName
                val firstName = dataMap["firstName"]?.toString() ?: ""
                val lastName = dataMap["lastName"]?.toString() ?: ""
                result["fullName"] = "$firstName $lastName".trim()
                
                // Map other fields
                result["dateOfBirth"] = dataMap["dateOfBirth"]?.toString() ?: ""
                result["gender"] = dataMap["gender"]?.toString() ?: ""
                result["bloodType"] = dataMap["bloodType"]?.toString() ?: ""
                result["address"] = dataMap["address"]?.toString() ?: ""
                result["phoneNumber"] = dataMap["phone"]?.toString() ?: "" // Map phone -> phoneNumber
                result["email"] = dataMap["email"]?.toString() ?: ""
                
                // Flatten emergencyContact nested object
                val emergencyContact = dataMap["emergencyContact"] as? Map<*, *>
                if (emergencyContact != null) {
                    result["emergencyContact"] = emergencyContact["name"]?.toString() ?: ""
                    result["emergencyPhone"] = emergencyContact["phone"]?.toString() ?: ""
                }
                
                // Add empty fields for allergies and chronicConditions (not in PersonalInfo model yet)
                result["allergies"] = ""
                result["chronicConditions"] = ""
                
                result
            } catch (e: Exception) {
                throw Exception("Error loading personal info: ${e.message}")
            }
        }
    }
    
    private suspend fun loadMedicationFromBlockchain(id: String): Map<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val medicationId = java.math.BigInteger(id)
                
                // Get MedicationRef from blockchain
                val medicationRef = com.fyp.blockchainhealthwallet.blockchain.BlockchainService.getMedicationRef(medicationId)
                    ?: throw Exception("Medication not found")
                
                // Download encrypted data from IPFS
                val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(medicationRef.encryptedDataIpfsHash)
                if (!response.isSuccessful || response.body() == null) {
                    throw Exception("Failed to download from IPFS")
                }
                
                val encryptedDataBase64 = response.body()!!.string()
                
                // Decrypt data
                val encryptedBytes = android.util.Base64.decode(encryptedDataBase64, android.util.Base64.NO_WRAP)
                val aesKey = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptKeyFromBlockchain(medicationRef.encryptedKey)
                val jsonData = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                
                // Parse JSON as Map
                val gson = com.google.gson.Gson()
                val dataMap = gson.fromJson(jsonData, Map::class.java) as Map<*, *>
                
                // Convert to Map<String, String> with proper field name mapping
                val result = mutableMapOf<String, String>()
                result["medicineName"] = dataMap["name"]?.toString() ?: ""
                result["dosage"] = dataMap["dosage"]?.toString() ?: ""
                result["prescribedBy"] = dataMap["prescribingDoctor"]?.toString() ?: ""
                result["startDate"] = dataMap["startDate"]?.toString() ?: ""
                result["endDate"] = dataMap["endDate"]?.toString() ?: ""
                result["frequency"] = dataMap["frequency"]?.toString() ?: ""
                result["purpose"] = dataMap["purpose"]?.toString() ?: ""
                result["sideEffects"] = "" // Not in model
                result["pharmacy"] = dataMap["pharmacy"]?.toString() ?: ""
                result["prescriptionNumber"] = "" // Not in model
                result["refillsRemaining"] = "" // Not in model
                result["cost"] = "" // Not in model
                result["insurance"] = "" // Not in model
                result["notes"] = dataMap["notes"]?.toString() ?: ""
                result["doctorPhone"] = "" // Not in model
                result
            } catch (e: Exception) {
                throw Exception("Error loading medication: ${e.message}")
            }
        }
    }
    
    private suspend fun loadVaccinationFromBlockchain(id: String): Map<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val vaccinationId = java.math.BigInteger(id)
                
                // Get VaccinationRef from blockchain
                val vaccinationRef = com.fyp.blockchainhealthwallet.blockchain.BlockchainService.getVaccinationRef(vaccinationId)
                    ?: throw Exception("Vaccination not found")
                
                // Download encrypted data from IPFS
                val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(vaccinationRef.encryptedDataIpfsHash)
                if (!response.isSuccessful || response.body() == null) {
                    throw Exception("Failed to download from IPFS")
                }
                
                val encryptedDataBase64 = response.body()!!.string()
                
                // Decrypt data
                val encryptedBytes = android.util.Base64.decode(encryptedDataBase64, android.util.Base64.NO_WRAP)
                val aesKey = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptKeyFromBlockchain(vaccinationRef.encryptedKey)
                val jsonData = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                
                // Parse JSON as Map
                val gson = com.google.gson.Gson()
                val dataMap = gson.fromJson(jsonData, Map::class.java) as Map<*, *>
                
                // Convert to Map<String, String> with proper field name mapping
                val result = mutableMapOf<String, String>()
                result["vaccineName"] = dataMap["vaccineName"]?.toString() ?: ""
                result["manufacturer"] = dataMap["manufacturer"]?.toString() ?: ""
                result["lotNumber"] = dataMap["batchNumber"]?.toString() ?: ""
                result["doseNumber"] = "" // Not in model
                result["totalDoses"] = "" // Not in model
                result["vaccinationDate"] = dataMap["date"]?.toString() ?: ""
                result["administeredBy"] = dataMap["provider"]?.toString() ?: ""
                result["facilityName"] = dataMap["location"]?.toString() ?: ""
                result["facilityAddress"] = "" // Not in model
                result["nextDoseDate"] = "" // Not in model
                result["reactions"] = "" // Not in model
                result["certificateNumber"] = "" // Not in model
                result["notes"] = "" // Not in model
                result["boosterRequired"] = "" // Not in model
                result
            } catch (e: Exception) {
                throw Exception("Error loading vaccination: ${e.message}")
            }
        }
    }
    
    private suspend fun loadReportFromBlockchain(id: String): Map<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val reportId = java.math.BigInteger(id)
                
                // Get ReportRef from blockchain
                val reportRef = com.fyp.blockchainhealthwallet.blockchain.BlockchainService.getReportRef(reportId)
                    ?: throw Exception("Report not found")
                
                // Download encrypted data from IPFS
                val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.getFromIPFS(reportRef.encryptedDataIpfsHash)
                if (!response.isSuccessful || response.body() == null) {
                    throw Exception("Failed to download from IPFS")
                }
                
                val encryptedDataBase64 = response.body()!!.string()
                
                // Decrypt data
                val encryptedBytes = android.util.Base64.decode(encryptedDataBase64, android.util.Base64.NO_WRAP)
                val aesKey = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptKeyFromBlockchain(reportRef.encryptedKey)
                val jsonData = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                
                // Parse JSON as Map
                val gson = com.google.gson.Gson()
                val dataMap = gson.fromJson(jsonData, Map::class.java) as Map<*, *>
                
                // Convert to Map<String, String> with proper field name mapping
                val result = mutableMapOf<String, String>()
                result["reportTitle"] = dataMap["title"]?.toString() ?: ""
                result["reportType"] = dataMap["reportType"]?.toString() ?: ""
                result["reportDate"] = dataMap["date"]?.toString() ?: ""
                result["facilityName"] = dataMap["hospital"]?.toString() ?: ""
                result["doctorName"] = dataMap["doctorName"]?.toString() ?: ""
                result["doctorSpecialty"] = "" // Not in model
                result["chiefComplaint"] = dataMap["description"]?.toString() ?: ""
                result["diagnosis"] = "" // Not in model
                result["treatmentPlan"] = "" // Not in model
                result["medications"] = "" // Not in model
                result["labResults"] = "" // Not in model
                result["imagingResults"] = "" // Not in model
                result["vitalSigns"] = "" // Not in model
                result["followUpDate"] = "" // Not in model
                result["referrals"] = "" // Not in model
                result["notes"] = dataMap["description"]?.toString() ?: ""
                result["billingCode"] = "" // Not in model
                result
            } catch (e: Exception) {
                throw Exception("Error loading report: ${e.message}")
            }
        }
    }
    
    private fun updateAttributesList() {
        attributesContainer.removeAllViews()
        selectedAttributes.clear()
        
        val recordType = currentRecordType ?: return
        val schema = RecordSchemas.getSchema(recordType)
        val data = fullRecord ?: return
        
        for (attrName in schema) {
            val attrValue = data[attrName] ?: ""
            val displayName = RecordSchemas.getDisplayName(attrName)
            
            val checkBox = CheckBox(this).apply {
                text = "$displayName: $attrValue"
                tag = attrName
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedAttributes.add(attrName)
                    } else {
                        selectedAttributes.remove(attrName)
                    }
                }
            }
            
            attributesContainer.addView(checkBox)
        }
    }
    
    private fun generatePartialShare() {
        android.util.Log.d("PartialShare", "=== GENERATE PARTIAL SHARE STARTED ===")
        android.util.Log.d("PartialShare", "Selected attributes: $selectedAttributes")
        
        if (selectedAttributes.isEmpty()) {
            Toast.makeText(this, "Please select at least one attribute", Toast.LENGTH_SHORT).show()
            return
        }
        
        val recordType = currentRecordType ?: run {
            android.util.Log.e("PartialShare", "Record type is null!")
            return
        }
        val data = fullRecord ?: run {
            android.util.Log.e("PartialShare", "Full record is null!")
            return
        }
        
        android.util.Log.d("PartialShare", "Record type: $recordType")
        android.util.Log.d("PartialShare", "Full record keys: ${data.keys}")
        
        val shareMethod = if (qrCodeRadio.isChecked) {
            ShareMethod.QR_CODE
        } else {
            ShareMethod.BLOCKCHAIN
        }
        
        android.util.Log.d("PartialShare", "Share method: $shareMethod")
        
        // Validate blockchain method requirements
        if (shareMethod == ShareMethod.BLOCKCHAIN) {
            val receiverAddress = receiverAddressInput.text.toString().trim()
            android.util.Log.d("PartialShare", "Receiver address: $receiverAddress")
            if (receiverAddress.isEmpty()) {
                Toast.makeText(this, "Please enter receiver address", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("PartialShare", "Launching coroutine for share generation")
                progressBar.visibility = View.VISIBLE
                statusText.text = "Building Merkle tree..."
                
                android.util.Log.d("PartialShare", "Building Merkle tree for recordType=$recordType")
                android.util.Log.d("PartialShare", "Data entries: ${data.size}")
                
                // Build Merkle tree
                val merkleTree = merkleHelper.buildMerkleTree(recordType, data)
                android.util.Log.d("PartialShare", "Merkle tree built. Root: ${merkleTree.root}")
                android.util.Log.d("PartialShare", "Merkle tree leaves: ${merkleTree.leaves.size}")
                
                // Filter to selected attributes
                val partialData = selectedAttributes.associateWith { data[it] ?: "" }
                android.util.Log.d("PartialShare", "=== PARTIAL DATA DETAILS ===")
                android.util.Log.d("PartialShare", "Full record data:")
                data.forEach { (k, v) ->
                    android.util.Log.d("PartialShare", "  '$k' = '$v'")
                }
                android.util.Log.d("PartialShare", "Selected attributes: $selectedAttributes")
                android.util.Log.d("PartialShare", "Partial data:")
                partialData.forEach { (k, v) ->
                    android.util.Log.d("PartialShare", "  '$k' = '$v' (empty: ${v.isEmpty()})")
                }
                android.util.Log.d("PartialShare", "=============================")
                
                statusText.text = "Generating proofs..."
                
                // Generate proofs
                android.util.Log.d("PartialShare", "Generating proofs for ${partialData.size} attributes")
                val proofs = merkleHelper.generateProofs(merkleTree, partialData)
                android.util.Log.d("PartialShare", "Proofs generated for: ${proofs.keys}")
                
                // Convert to serializable format
                val proofsData = proofs.mapValues { (_, proof) ->
                    proof.map { ProofNodeData.fromProofNode(it) }
                }
                
                // Get expiry time
                val expiryHours = expiryHoursInput.text.toString().toIntOrNull() ?: 24
                val expiryTime = System.currentTimeMillis() + (expiryHours * 3600 * 1000)
                
                // Create share package
                val sharePackage = PartialSharePackage(
                    version = "1.0",
                    recordType = recordType.name,
                    merkleRoot = merkleTree.root,
                    attributes = partialData,
                    proofs = proofsData,
                    timestamp = System.currentTimeMillis(),
                    expiryTime = expiryTime
                )
                
                android.util.Log.d("PartialShare", "Share package created:")
                android.util.Log.d("PartialShare", "  - Record type: ${sharePackage.recordType}")
                android.util.Log.d("PartialShare", "  - Merkle root: ${sharePackage.merkleRoot}")
                android.util.Log.d("PartialShare", "  - Attributes: ${sharePackage.attributes.keys}")
                android.util.Log.d("PartialShare", "  - Expiry: ${sharePackage.expiryTime}")
                
                when (shareMethod) {
                    ShareMethod.QR_CODE -> {
                        android.util.Log.d("PartialShare", "Generating QR code...")
                        generateQRCode(sharePackage)
                    }
                    ShareMethod.BLOCKCHAIN -> {
                        android.util.Log.d("PartialShare", "Uploading to blockchain...")
                        uploadToBlockchain(sharePackage)
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PartialShare", "=== ERROR IN generatePartialShare ===", e)
                android.util.Log.e("PartialShare", "Error type: ${e.javaClass.simpleName}")
                android.util.Log.e("PartialShare", "Error message: ${e.message}")
                android.util.Log.e("PartialShare", "Stack trace:", e)
                e.printStackTrace()
                statusText.text = "Error: ${e.message}"
                Toast.makeText(this@PartialShareActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
                android.util.Log.d("PartialShare", "=== generatePartialShare FINISHED ===")
            }
        }
    }
    
    private fun generateQRCode(sharePackage: PartialSharePackage) {
        statusText.text = "Generating QR code..."
        
        // Serialize to JSON
        val json = Json.encodeToString(sharePackage)
        
        // Check size
        if (json.length > 2000) {
            statusText.text = "Warning: QR code may be too large (${json.length} chars)"
        }
        
        // Generate QR code
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(json, BarcodeFormat.QR_CODE, 512, 512)
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        
        // Store bitmap for save/share functionality
        currentQRBitmap = bitmap
        
        qrCodeImage.setImageBitmap(bitmap)
        qrCodeImage.visibility = View.VISIBLE
        qrActionButtons.visibility = View.VISIBLE
        statusText.text = "QR code generated! Scan to access shared data."
        
        Toast.makeText(this, "QR Code ready for scanning", Toast.LENGTH_LONG).show()
    }
    
    private fun saveQRCode() {
        if (currentQRBitmap == null) {
            Toast.makeText(this, "Please generate QR code first", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val filename = "PartialShare_QR_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HealthWallet")
                }
            }
            
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    currentQRBitmap?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                Toast.makeText(this, "QR code saved to gallery", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save QR code", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareQRCode() {
        if (currentQRBitmap == null) {
            Toast.makeText(this, "Please generate QR code first", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Save to cache directory
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "partial_share_qr.png")
            val fileOutputStream = FileOutputStream(file)
            currentQRBitmap?.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
            
            // Get URI using FileProvider
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            
            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Scan this QR code to access shared health data")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private suspend fun uploadToBlockchain(sharePackage: PartialSharePackage) {
        try {
            // Check if contract is deployed
            if (!com.fyp.blockchainhealthwallet.blockchain.BlockchainService.isPartialShareDeployed()) {
                statusText.text = "Contract not deployed"
                Toast.makeText(this, 
                    "PartialShareExtension contract not deployed yet.\n" +
                    "Deploy the contract and update PARTIAL_SHARE_CONTRACT address.\n\n" +
                    "Use QR Code method for now (fully functional).",
                    Toast.LENGTH_LONG
                ).show()
                progressBar.visibility = View.GONE
                return
            }
            
            val receiverAddress = receiverAddressInput.text.toString().trim()
            if (receiverAddress.isEmpty()) {
                Toast.makeText(this, "Please enter receiver address", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                return
            }
            
            // Get recordId from intent (from PartialShareSelectorActivity)
            val recordIdString = intent.getStringExtra("RECORD_ID") ?: "0"
            val recordId = try {
                java.math.BigInteger(recordIdString)
            } catch (e: Exception) {
                android.util.Log.e("PartialShare", "Invalid recordId: $recordIdString", e)
                Toast.makeText(this, "Invalid record ID", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                return
            }
            
            android.util.Log.d("PartialShare", "Starting blockchain upload for recordId: $recordId")
            
            statusText.text = "Encrypting share package..."
            
            // Convert package to JSON
            val packageJson = kotlinx.serialization.json.Json.encodeToString(sharePackage)
            android.util.Log.d("PartialShare", "Package JSON size: ${packageJson.length} bytes")
            
            // TODO: Encrypt with receiver's public key (optional for now)
            // For now, upload unencrypted (Merkle proofs are already secure)
            
            statusText.text = "Uploading to IPFS..."
            android.util.Log.d("PartialShare", "Uploading to IPFS...")
            
            // Upload to IPFS via backend
            val ipfsHash = try {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    uploadPackageToIPFS(packageJson)
                }
            } catch (e: Exception) {
                android.util.Log.e("PartialShare", "IPFS upload failed", e)
                throw Exception("IPFS upload failed: ${e.message}")
            }
            
            android.util.Log.d("PartialShare", "IPFS upload successful: $ipfsHash")
            statusText.text = "Registering record ownership..."
            
            // Register record if not already registered (required before granting access)
            try {
                android.util.Log.d("PartialShare", "Registering record $recordId...")
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.fyp.blockchainhealthwallet.blockchain.BlockchainService.registerPartialShareRecord(recordId)
                }
                android.util.Log.d("PartialShare", "Record registered successfully")
                // Wait a bit for transaction to be mined
                kotlinx.coroutines.delay(3000)
            } catch (e: Exception) {
                // If already registered, this will fail - that's okay
                android.util.Log.d("PartialShare", "Record registration: ${e.message} (may already be registered)")
            }
            
            statusText.text = "Granting access on blockchain..."
            
            // Get expiry time from package
            val expiryTime = java.math.BigInteger.valueOf(sharePackage.expiryTime / 1000)
            
            // Validate parameters before sending transaction
            android.util.Log.d("PartialShare", "========================================")
            android.util.Log.d("PartialShare", "PRE-TRANSACTION VALIDATION")
            android.util.Log.d("PartialShare", "========================================")
            android.util.Log.d("PartialShare", "Record ID: $recordId")
            android.util.Log.d("PartialShare", "Receiver: $receiverAddress")
            android.util.Log.d("PartialShare", "IPFS Hash: $ipfsHash")
            android.util.Log.d("PartialShare", "Merkle Root: ${sharePackage.merkleRoot}")
            android.util.Log.d("PartialShare", "Expiry Time: $expiryTime (${java.util.Date(expiryTime.toLong() * 1000)})")
            
            // Validate receiver address format
            if (!receiverAddress.startsWith("0x") || receiverAddress.length != 42) {
                throw IllegalArgumentException("Invalid receiver address format. Must be 42 characters starting with 0x")
            }
            
            // Validate merkle root
            if (sharePackage.merkleRoot.isEmpty()) {
                throw IllegalArgumentException("Merkle root is empty")
            }
            
            // Validate expiry time
            val currentTime = java.math.BigInteger.valueOf(System.currentTimeMillis() / 1000)
            if (expiryTime <= currentTime) {
                throw IllegalArgumentException("Expiry time is in the past! Current: $currentTime, Expiry: $expiryTime")
            }
            
            android.util.Log.d("PartialShare", "✅ Pre-transaction validation passed")
            android.util.Log.d("PartialShare", "========================================")
            
            // Grant partial access on blockchain
            android.util.Log.d("PartialShare", "Calling grantPartialAccess...")
            val txHash = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.fyp.blockchainhealthwallet.blockchain.BlockchainService.grantPartialAccess(
                    recordId = recordId,
                    receiver = receiverAddress,
                    shareIPFSHash = ipfsHash,
                    merkleRoot = sharePackage.merkleRoot,
                    expiryTime = expiryTime
                )
            }
            
            android.util.Log.d("PartialShare", "========================================")
            android.util.Log.d("PartialShare", "TRANSACTION COMPLETED")
            android.util.Log.d("PartialShare", "TX Hash: $txHash")
            android.util.Log.d("PartialShare", "========================================")
            progressBar.visibility = View.GONE
            statusText.text = "Share granted! Tx: ${txHash.take(10)}..."
            
            Toast.makeText(this, 
                "Partial access granted successfully!\n\n" +
                "Record ID: $recordId\n" +
                "Transaction: ${txHash.take(20)}...\n" +
                "IPFS: $ipfsHash",
                Toast.LENGTH_LONG
            ).show()
            
        } catch (e: IllegalStateException) {
            android.util.Log.e("PartialShare", "State error", e)
            progressBar.visibility = View.GONE
            statusText.text = "Error: ${e.message}"
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("PartialShare", "Upload error", e)
            progressBar.visibility = View.GONE
            statusText.text = "Error: ${e.message}"
            Toast.makeText(this, "Failed to grant access: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private suspend fun uploadPackageToIPFS(packageJson: String): String {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                android.util.Log.d("PartialShare", "=== IPFS UPLOAD STARTED ===")
                android.util.Log.d("PartialShare", "Package JSON length: ${packageJson.length}")
                android.util.Log.d("PartialShare", "Package JSON preview: ${packageJson.take(200)}...")
                
                val request = com.fyp.blockchainhealthwallet.network.PartialShareUploadRequest(
                    sharePackage = packageJson
                )
                android.util.Log.d("PartialShare", "Request object created")
                
                android.util.Log.d("PartialShare", "Making API call to uploadPartialSharePackage...")
                val response = com.fyp.blockchainhealthwallet.network.ApiClient.api.uploadPartialSharePackage(request)
                android.util.Log.d("PartialShare", "API call completed. Response code: ${response.code()}")
                android.util.Log.d("PartialShare", "Response message: ${response.message()}")
                
                if (!response.isSuccessful) {
                    android.util.Log.e("PartialShare", "Upload not successful: ${response.code()} ${response.message()}")
                    android.util.Log.e("PartialShare", "Error body: ${response.errorBody()?.string()}")
                    throw Exception("Upload failed: ${response.code()} ${response.message()}")
                }
                
                val body = response.body()
                android.util.Log.d("PartialShare", "Response body: $body")
                
                if (body?.success == true && body.ipfsHash != null) {
                    android.util.Log.d("PartialShare", "=== IPFS UPLOAD SUCCESS ===")
                    android.util.Log.d("PartialShare", "IPFS Hash: ${body.ipfsHash}")
                    body.ipfsHash
                } else {
                    android.util.Log.e("PartialShare", "Upload body indicated failure: ${body?.error}")
                    throw Exception(body?.error ?: "Failed to upload to IPFS")
                }
            } catch (e: Exception) {
                android.util.Log.e("PartialShare", "=== IPFS UPLOAD ERROR ===", e)
                android.util.Log.e("PartialShare", "Error type: ${e.javaClass.simpleName}")
                android.util.Log.e("PartialShare", "Error message: ${e.message}")
                throw Exception("IPFS upload error: ${e.message}")
            }
        }
    }
    
    /**
     * Open QR scanner to scan receiver's wallet address QR code
     */
    private fun openQRScanner() {
        try {
            val intent = Intent(this, Class.forName("com.fyp.blockchainhealthwallet.ui.partialshare.AddressQRScannerActivity"))
            qrScannerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "QR Scanner not available: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Scan QR code from selected image
     */
    private fun scanQRFromImage(uri: Uri) {
        try {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            
            // Use ML Kit to scan for QR code in the image
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val rawValue = barcodes[0].rawValue
                        if (!rawValue.isNullOrEmpty()) {
                            val address = extractWalletAddress(rawValue)
                            if (address != null) {
                                receiverAddressInput.setText(address)
                                Toast.makeText(this, "Address extracted from photo", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "No valid wallet address found in QR", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "No valid QR code found in photo", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "No QR code found in photo", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to scan photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error processing photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Extract wallet address from QR code content
     * Supports both plain addresses (0x...) and JSON format {\"type\":\"WALLET_ADDRESS\",\"address\":\"0x...\"}
     */
    private fun extractWalletAddress(rawValue: String): String? {
        // Try plain Ethereum address
        if (rawValue.startsWith("0x") && rawValue.length == 42) {
            return rawValue
        }
        
        // Try JSON format
        try {
            val json = org.json.JSONObject(rawValue)
            if (json.has("type") && json.getString("type") == "WALLET_ADDRESS") {
                val address = json.getString("address")
                if (address.startsWith("0x") && address.length == 42) {
                    return address
                }
            }
        } catch (e: Exception) {
            // Not JSON, ignore
        }
        
        return null
    }
}

