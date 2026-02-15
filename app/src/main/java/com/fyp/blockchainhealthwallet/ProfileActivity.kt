package com.fyp.blockchainhealthwallet

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.blockchain.RSAHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

/**
 * ProfileActivity - Displays and manages user's personal information
 * Data flow:
 * - GET: Blockchain (hash) -> IPFS (encrypted JSON) -> Decrypt -> Display
 * - SET: Collect data -> Encrypt -> IPFS upload (hash) -> Store hash on blockchain
 */
class ProfileActivity : AppCompatActivity() {
    
    private val TAG = "ProfileActivity"
    private val gson = Gson()
    
    // UI components
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileEmail: TextView
    private lateinit var tvHKID: TextView
    private lateinit var tvDOB: TextView
    private lateinit var tvGender: TextView
    private lateinit var tvBloodType: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvEmergencyName: TextView
    private lateinit var tvEmergencyRelation: TextView
    private lateinit var tvEmergencyPhone: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var btnPartialShareProfile: Button
    private lateinit var btnEnableReceive: Button
    private lateinit var tvReceiveStatus: TextView
    
    private var currentPersonalInfo: PersonalInfo? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        initializeViews()
        setupClickListeners()
        loadPersonalInfo()
        checkReceiveStatus()
    }
    
    private fun initializeViews() {
        tvProfileName = findViewById(R.id.tvProfileName)
        tvProfileEmail = findViewById(R.id.tvProfileEmail)
        tvHKID = findViewById(R.id.tvHKID)
        tvDOB = findViewById(R.id.tvDOB)
        tvGender = findViewById(R.id.tvGender)
        tvBloodType = findViewById(R.id.tvBloodType)
        tvPhone = findViewById(R.id.tvPhone)
        tvAddress = findViewById(R.id.tvAddress)
        tvEmergencyName = findViewById(R.id.tvEmergencyName)
        tvEmergencyRelation = findViewById(R.id.tvEmergencyRelation)
        tvEmergencyPhone = findViewById(R.id.tvEmergencyPhone)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnPartialShareProfile = findViewById(R.id.btnPartialShareProfile)
        btnEnableReceive = findViewById(R.id.btnEnableReceive)
        tvReceiveStatus = findViewById(R.id.tvReceiveStatus)
    }
    
    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }
        
        btnPartialShareProfile.setOnClickListener {
            openPartialShareActivity()
        }
        
        btnEnableReceive.setOnClickListener {
            enableReceiveShares()
        }
    }
    
    /**
     * Load personal information from blockchain + IPFS
     * Flow: getPersonalInfoRef() -> get hash -> fetch from IPFS -> display
     */
    private fun loadPersonalInfo() {
        val address = WalletManager.getAddress()
        
        if (address == null) {
            Toast.makeText(this, "Please connect wallet first", Toast.LENGTH_SHORT).show()
            displayPlaceholderData()
            return
        }
        
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Loading profile from blockchain...")
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "LOADING PERSONAL INFO")
                Log.d(TAG, "========================================")
                Log.d(TAG, "User address: $address")
                
                // Step 1: Get IPFS hash from blockchain
                Log.d(TAG, "Step 1: Calling BlockchainService.getPersonalInfoRef()...")
                val personalInfoRef = withContext(Dispatchers.IO) {
                    BlockchainService.getPersonalInfoRef(address)
                }
                
                Log.d(TAG, "Step 1 Result: personalInfoRef = $personalInfoRef")
                
                if (personalInfoRef == null) {
                    Log.w(TAG, "personalInfoRef is NULL")
                } else {
                    Log.d(TAG, "PersonalInfoRef details:")
                    Log.d(TAG, "  - exists: ${personalInfoRef.exists}")
                    Log.d(TAG, "  - encryptedDataIpfsHash: ${personalInfoRef.encryptedDataIpfsHash}")
                    Log.d(TAG, "  - createdAt: ${personalInfoRef.createdAt}")
                    Log.d(TAG, "  - lastUpdated: ${personalInfoRef.lastUpdated}")
                }
                
                if (personalInfoRef == null || !personalInfoRef.exists) {
                    Log.d(TAG, "No personal info stored on blockchain")
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            this@ProfileActivity,
                            "No profile data found. Please create your profile.",
                            Toast.LENGTH_LONG
                        ).show()
                        displayPlaceholderData()
                    }
                    return@launch
                }
                
                val ipfsHash = personalInfoRef.encryptedDataIpfsHash
                Log.d(TAG, "========================================")
                Log.d(TAG, "Step 2: Fetching from IPFS")
                Log.d(TAG, "========================================")
                Log.d(TAG, "IPFS hash: $ipfsHash")
                Log.d(TAG, "Hash length: ${ipfsHash.length}")
                Log.d(TAG, "Timestamp: ${Date(personalInfoRef.lastUpdated.toLong() * 1000)}")
                Log.d(TAG, "Has encryptedKey: ${personalInfoRef.encryptedKey.isNotEmpty()}")
                Log.d(TAG, "Calling ApiClient.api.getFromIPFS()...")
                
                // Step 2: Fetch encrypted data from IPFS
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getFromIPFS(ipfsHash)
                }
                
                Log.d(TAG, "IPFS Response:")
                Log.d(TAG, "  - isSuccessful: ${response.isSuccessful}")
                Log.d(TAG, "  - code: ${response.code()}")
                Log.d(TAG, "  - message: ${response.message()}")
                Log.d(TAG, "  - body is null: ${response.body() == null}")
                
                if (!response.isSuccessful || response.body() == null) {
                    val errorBody = try { response.errorBody()?.string() } catch (e: Exception) { "Unable to read error" }
                    Log.e(TAG, "❌ Failed to retrieve data from IPFS")
                    Log.e(TAG, "Error body: $errorBody")
                    throw Exception("Failed to retrieve data from IPFS: ${response.code()} - ${response.message()}")
                }
                
                // Step 3: Decrypt the data
                Log.d(TAG, "========================================")
                Log.d(TAG, "Step 3: Decrypting data")
                Log.d(TAG, "========================================")
                val encryptedDataBase64 = response.body()!!.string()
                Log.d(TAG, "Retrieved encrypted data from IPFS (${encryptedDataBase64.length} chars)")
                
                val jsonData = if (personalInfoRef.encryptedKey.isNotEmpty()) {
                    // Use random key decryption
                    Log.d(TAG, "Using random key decryption")
                    val encryptedBytes = android.util.Base64.decode(encryptedDataBase64, android.util.Base64.NO_WRAP)
                    val aesKey = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptKeyFromBlockchain(personalInfoRef.encryptedKey)
                    com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)
                } else {
                    // Fallback for old data without encryption (shouldn't happen with new contract)
                    Log.d(TAG, "No encryptedKey found - using plain data")
                    encryptedDataBase64
                }
                
                Log.d(TAG, "Decrypted JSON length: ${jsonData.length}")
                
                // Step 4: Parse JSON data
                Log.d(TAG, "========================================")
                Log.d(TAG, "Step 4: Parsing JSON")
                Log.d(TAG, "========================================")
                
                val personalInfo = gson.fromJson(jsonData, PersonalInfo::class.java)
                currentPersonalInfo = personalInfo
                
                Log.d(TAG, "✅ Parsed PersonalInfo:")
                Log.d(TAG, "  - Name: ${personalInfo.firstName} ${personalInfo.lastName}")
                Log.d(TAG, "  - Email: ${personalInfo.email}")
                Log.d(TAG, "  - HKID: ${personalInfo.hkid}")
                
                // Step 5: Display data
                Log.d(TAG, "========================================")
                Log.d(TAG, "Step 5: Displaying data in UI")
                Log.d(TAG, "========================================")
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    displayPersonalInfo(personalInfo)
                    Toast.makeText(
                        this@ProfileActivity,
                        "Profile loaded from blockchain",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Timeout loading profile: ${e.message}")
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@ProfileActivity,
                        "Network timeout. Please check your connection.",
                        Toast.LENGTH_LONG
                    ).show()
                    displayPlaceholderData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@ProfileActivity,
                        "Failed to load profile: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    displayPlaceholderData()
                }
            }
        }
    }
    
    /**
     * Display personal information in UI
     */
    private fun displayPersonalInfo(info: PersonalInfo) {
        val notSet = "Not Set"
        
        // Show "Not Set" for empty fields
        val fullName = "${info.firstName.ifEmpty { "" }} ${info.lastName.ifEmpty { "" }}".trim()
        tvProfileName.text = fullName.ifEmpty { notSet }
        tvProfileEmail.text = info.email.ifEmpty { notSet }
        tvHKID.text = info.hkid.ifEmpty { notSet }
        tvDOB.text = info.dateOfBirth.ifEmpty { notSet }
        tvGender.text = info.gender.ifEmpty { notSet }
        tvBloodType.text = info.bloodType.ifEmpty { notSet }
        tvPhone.text = info.phone.ifEmpty { notSet }
        tvAddress.text = info.address.ifEmpty { notSet }
        tvEmergencyName.text = info.emergencyContact.name.ifEmpty { notSet }
        tvEmergencyRelation.text = info.emergencyContact.relationship.ifEmpty { notSet }
        tvEmergencyPhone.text = info.emergencyContact.phone.ifEmpty { notSet }
    }
    
    /**
     * Display placeholder data when no blockchain data is available
     */
    private fun displayPlaceholderData() {
        tvProfileName.text = "Not Set"
        tvProfileEmail.text = "Not Set"
        tvHKID.text = "Not Set"
        tvDOB.text = "Not Set"
        tvGender.text = "Not Set"
        tvBloodType.text = "Not Set"
        tvPhone.text = "Not Set"
        tvAddress.text = "Not Set"
        tvEmergencyName.text = "Not Set"
        tvEmergencyRelation.text = "Not Set"
        tvEmergencyPhone.text = "Not Set"
    }
    
    /**
     * Check if user has enabled receiving shares
     * Updates UI to show current status
     */
    private fun checkReceiveStatus() {
        val address = WalletManager.getAddress() ?: return
        
        lifecycleScope.launch {
            try {
                val publicKeyIpfsHash = withContext(Dispatchers.IO) {
                    BlockchainService.getUserPublicKey(address)
                }
                
                withContext(Dispatchers.Main) {
                    if (publicKeyIpfsHash.isNotEmpty()) {
                        // User has enabled receiving - allow re-uploading for key rotation
                        btnEnableReceive.isEnabled = true
                        btnEnableReceive.text = "Update Public Key"
                        btnEnableReceive.setBackgroundColor(getColor(R.color.primary))
                        btnEnableReceive.setTextColor(getColor(R.color.white))
                        tvReceiveStatus.text = "Public key: ${publicKeyIpfsHash.take(20)}..."
                        tvReceiveStatus.setTextColor(getColor(R.color.success))
                    } else {
                        // User has not enabled receiving
                        btnEnableReceive.isEnabled = true
                        btnEnableReceive.text = "Enable Receive Shares"
                        tvReceiveStatus.text = "Enable to receive encrypted shares from others"
                        tvReceiveStatus.setTextColor(getColor(R.color.text_secondary))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking receive status", e)
                withContext(Dispatchers.Main) {
                    btnEnableReceive.isEnabled = true
                    btnEnableReceive.text = "Enable Receive Shares"
                    tvReceiveStatus.text = "Enable to receive encrypted shares from others"
                    tvReceiveStatus.setTextColor(getColor(R.color.text_secondary))
                }
            }
        }
    }
    
    /**
     * Enable receiving shares by setting up RSA key pair
     * Flow: Generate RSA keys → Upload public key to IPFS → Store hash on blockchain
     */
    private fun enableReceiveShares() {
        val address = WalletManager.getAddress()
        if (address == null) {
            Toast.makeText(this, "Please connect wallet first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if public key already exists
        lifecycleScope.launch {
            val existingKey = withContext(Dispatchers.IO) {
                try {
                    BlockchainService.getUserPublicKey(address)
                } catch (e: Exception) {
                    ""
                }
            }
            
            val title = if (existingKey.isNotEmpty()) "Update Public Key" else "Enable Receive Shares"
            val message = if (existingKey.isNotEmpty()) {
                "This will update your public key:\n\n1. Upload new public key to IPFS\n2. Update the blockchain with new hash\n\nYour new RSA keys have been generated.\n\nNote: This requires a blockchain transaction (gas fees apply)."
            } else {
                "This will:\n\n1. Generate a secure RSA key pair in your device\n2. Upload your public key to IPFS\n3. Store the public key hash on blockchain\n\nYour private key never leaves this device.\n\nNote: This requires a blockchain transaction (gas fees apply)."
            }
            
            AlertDialog.Builder(this@ProfileActivity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(if (existingKey.isNotEmpty()) "Update" else "Enable") { _, _ ->
                    performEnableReceive()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    /**
     * Perform the actual enable receive process
     */
    private fun performEnableReceive() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Setting up encryption keys...")
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch {
            try {
                // Step 1: Generate RSA key pair
                progressDialog.setMessage("Generating RSA key pair...")
                Log.d(TAG, "Step 1: Generating RSA key pair")
                
                withContext(Dispatchers.IO) {
                    RSAHelper.ensureKeyPairExists()
                }
                
                // Step 2: Get public key
                progressDialog.setMessage("Exporting public key...")
                Log.d(TAG, "Step 2: Getting public key")
                
                val publicKeyBase64: String = withContext(Dispatchers.IO) {
                    RSAHelper.getPublicKey()
                } ?: throw Exception("Failed to get public key")
                
                Log.d(TAG, "Public key length: ${publicKeyBase64.length}")
                
                // Step 3: Upload public key to IPFS
                progressDialog.setMessage("Uploading public key to IPFS...")
                Log.d(TAG, "Step 3: Uploading to IPFS")
                
                val publicKeyJson = gson.toJson(mapOf("publicKey" to publicKeyBase64))
                val ipfsHash: String = withContext(Dispatchers.IO) {
                    uploadJsonToIPFS(publicKeyJson)
                }
                
                Log.d(TAG, "Public key IPFS hash: $ipfsHash")
                
                // Step 4: Calculate public key hash (SHA-256)
                progressDialog.setMessage("Calculating key hash...")
                Log.d(TAG, "Step 4: Calculating hash")
                
                val publicKeyHash: String = withContext(Dispatchers.IO) {
                    RSAHelper.getPublicKeyHash()
                }
                
                Log.d(TAG, "Public key hash: $publicKeyHash")
                
                // Step 5: Store on blockchain
                progressDialog.setMessage("Sending to wallet...\\nPlease approve transaction")
                Log.d(TAG, "Step 5: Storing on blockchain")
                
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.setUserPublicKey(ipfsHash, publicKeyHash)
                }
                
                progressDialog.dismiss()
                
                Log.d(TAG, "✅ Success! Transaction: $txHash")
                
                AlertDialog.Builder(this@ProfileActivity)
                    .setTitle("Receive Enabled!")
                    .setMessage("You can now receive encrypted shares from others.\n\nTransaction: ${txHash.take(10)}...\n\nPublic key IPFS: $ipfsHash")
                    .setPositiveButton("OK") { _, _ ->
                        checkReceiveStatus()
                    }
                    .show()
                    
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error enabling receive", e)
                
                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true -> 
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true -> 
                        "Insufficient funds for gas fees"
                    else -> "Error: ${e.message}"
                }
                
                AlertDialog.Builder(this@ProfileActivity)
                    .setTitle("Enable Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    /**
     * Show dialog to choose between sample data or custom form
     */
    private fun showEditProfileDialog() {
        AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setMessage("Choose how to create/update your profile:")
            .setPositiveButton("Use Real Form") { _, _ ->
                showProfileForm()
            }
            .setNeutralButton("Create Sample Data") { _, _ ->
                createSampleProfile()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show form to collect real profile data from user
     */
    private fun showProfileForm() {
        val formView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        
        // Setup Gender dropdown
        val genderField = formView.findViewById<android.widget.AutoCompleteTextView>(R.id.etGender)
        val genderOptions = arrayOf("Male", "Female", "Other", "Prefer not to say")
        val genderAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderOptions)
        genderField?.setAdapter(genderAdapter)
        
        // Setup Blood Type dropdown
        val bloodTypeField = formView.findViewById<android.widget.AutoCompleteTextView>(R.id.etBloodType)
        val bloodTypeOptions = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        val bloodTypeAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bloodTypeOptions)
        bloodTypeField?.setAdapter(bloodTypeAdapter)
        
        // Setup Date Picker for DOB
        val dobField = formView.findViewById<android.widget.EditText>(R.id.etDOB)
        dobField?.setOnClickListener {
            showDatePicker { selectedDate ->
                dobField.setText(selectedDate)
            }
        }
        
        // Populate with existing data if available
        currentPersonalInfo?.let { info ->
            formView.findViewById<android.widget.EditText>(R.id.etFirstName)?.setText(info.firstName)
            formView.findViewById<android.widget.EditText>(R.id.etLastName)?.setText(info.lastName)
            formView.findViewById<android.widget.EditText>(R.id.etEmail)?.setText(info.email)
            formView.findViewById<android.widget.EditText>(R.id.etHKID)?.setText(info.hkid)
            dobField?.setText(info.dateOfBirth)
            genderField?.setText(info.gender, false)
            bloodTypeField?.setText(info.bloodType, false)
            formView.findViewById<android.widget.EditText>(R.id.etPhone)?.setText(info.phone)
            formView.findViewById<android.widget.EditText>(R.id.etAddress)?.setText(info.address)
            formView.findViewById<android.widget.EditText>(R.id.etEmergencyName)?.setText(info.emergencyContact.name)
            formView.findViewById<android.widget.EditText>(R.id.etEmergencyRelation)?.setText(info.emergencyContact.relationship)
            formView.findViewById<android.widget.EditText>(R.id.etEmergencyPhone)?.setText(info.emergencyContact.phone)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(formView)
            .setPositiveButton("Save to Blockchain") { _, _ ->
                saveProfileFromForm(formView)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    /**
     * Show date picker dialog for date selection
     */
    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = java.util.Calendar.getInstance()
        
        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // Format date as YYYY-MM-DD
                val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(formattedDate)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        
        // Set max date to today (can't be born in the future)
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        
        // Set min date to 150 years ago (reasonable limit)
        calendar.add(java.util.Calendar.YEAR, -150)
        datePickerDialog.datePicker.minDate = calendar.timeInMillis
        
        datePickerDialog.show()
    }
    
    /**
     * Save profile data from form to blockchain + IPFS
     */
    private fun saveProfileFromForm(formView: android.view.View) {
        val address = WalletManager.getAddress()
        
        if (address == null) {
            Toast.makeText(this, "Please connect wallet first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Extract data from form fields
        val firstName = formView.findViewById<android.widget.EditText>(R.id.etFirstName)?.text.toString().trim()
        val lastName = formView.findViewById<android.widget.EditText>(R.id.etLastName)?.text.toString().trim()
        val email = formView.findViewById<android.widget.EditText>(R.id.etEmail)?.text.toString().trim()
        val hkid = formView.findViewById<android.widget.EditText>(R.id.etHKID)?.text.toString().trim()
        val dob = formView.findViewById<android.widget.EditText>(R.id.etDOB)?.text.toString().trim()
        val gender = formView.findViewById<android.widget.EditText>(R.id.etGender)?.text.toString().trim()
        val bloodType = formView.findViewById<android.widget.EditText>(R.id.etBloodType)?.text.toString().trim()
        val phone = formView.findViewById<android.widget.EditText>(R.id.etPhone)?.text.toString().trim()
        val addressText = formView.findViewById<android.widget.EditText>(R.id.etAddress)?.text.toString().trim()
        val emergencyName = formView.findViewById<android.widget.EditText>(R.id.etEmergencyName)?.text.toString().trim()
        val emergencyRelation = formView.findViewById<android.widget.EditText>(R.id.etEmergencyRelation)?.text.toString().trim()
        val emergencyPhone = formView.findViewById<android.widget.EditText>(R.id.etEmergencyPhone)?.text.toString().trim()
        
        // Validate required fields
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill in required fields (Name, Email)", Toast.LENGTH_SHORT).show()
            return
        }
        
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Saving profile to blockchain...")
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch {
            try {
                // Create PersonalInfo from form data
                val personalInfo = PersonalInfo(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    hkid = hkid,
                    dateOfBirth = dob,
                    gender = gender,
                    bloodType = bloodType,
                    phone = phone,
                    address = addressText,
                    emergencyContact = EmergencyContact(
                        name = emergencyName,
                        relationship = emergencyRelation,
                        phone = emergencyPhone
                    )
                )
                
                progressDialog.setMessage("Uploading to IPFS...")
                
                // Convert to JSON
                val jsonData = gson.toJson(personalInfo)
                Log.d(TAG, "Personal info JSON length: ${jsonData.length}")
                
                // Generate random key and encrypt data
                val randomKey = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.generateAESKey()
                val encryptedDataBase64 = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.encryptDataWithKey(
                    jsonData,
                    randomKey
                )
                
                // Encrypt the random key for storage on blockchain
                val encryptedKey = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.encryptKeyForBlockchain(randomKey)
                
                Log.d(TAG, "Encrypted data length: ${encryptedDataBase64.length}")
                Log.d(TAG, "Encrypted key length: ${encryptedKey.length}")
                
                // Upload encrypted data to IPFS (send Base64 string as bytes)
                val ipfsHash = withContext(Dispatchers.IO) {
                    uploadBytesToIPFS(encryptedDataBase64.toByteArray(Charsets.UTF_8))
                }
                
                Log.d(TAG, "Uploaded to IPFS with hash: $ipfsHash")
                progressDialog.setMessage("Storing on blockchain...")
                
                // Store IPFS hash and encrypted key on blockchain
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.setPersonalInfo(ipfsHash, encryptedKey)
                }
                
                Log.d(TAG, "Stored on blockchain. Transaction: $txHash")
                
                // Update UI
                currentPersonalInfo = personalInfo
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    displayPersonalInfo(personalInfo)
                    
                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle("Success!")
                        .setMessage("Profile saved to blockchain!\n\nTransaction: ${txHash.take(20)}...")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving profile", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle("Error")
                        .setMessage("Failed to save profile: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
    
    /**
     * Create and save sample profile to blockchain + IPFS
     * Flow: Create JSON -> Upload to IPFS -> Get hash -> Store hash on blockchain
     */
    private fun createSampleProfile() {
        val address = WalletManager.getAddress()
        
        if (address == null) {
            Toast.makeText(this, "Please connect wallet first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Creating profile on blockchain...")
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch {
            try {
                // Step 1: Create personal info data
                val personalInfo = PersonalInfo(
                    firstName = "Chung",
                    lastName = "Dwa",
                    email = "harry.dwa@example.com",
                    hkid = "A123456(7)",
                    dateOfBirth = "January 15, 2004",
                    gender = "Male",
                    bloodType = "O+",
                    phone = "+852 1234 5678",
                    address = "123 Main Street, Hong Kong",
                    emergencyContact = EmergencyContact(
                        name = "Jenny Dwa",
                        relationship = "Spouse",
                        phone = "+852 1234 5678"
                    )
                )
                
                progressDialog.setMessage("Uploading to IPFS...")
                
                // Step 2: Convert to JSON
                val jsonData = gson.toJson(personalInfo)
                Log.d(TAG, "Personal info JSON length: ${jsonData.length}")
                
                // Generate random key and encrypt data
                val randomKey = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.generateAESKey()
                val encryptedDataBase64 = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.encryptDataWithKey(
                    jsonData,
                    randomKey
                )
                
                // Encrypt the random key for storage on blockchain
                val encryptedKey = com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper.encryptKeyForBlockchain(randomKey)
                
                Log.d(TAG, "Encrypted data length: ${encryptedDataBase64.length}")
                Log.d(TAG, "Encrypted key length: ${encryptedKey.length}")
                
                // Step 3: Upload encrypted data to IPFS (send Base64 string as bytes)
                val ipfsHash = withContext(Dispatchers.IO) {
                    uploadBytesToIPFS(encryptedDataBase64.toByteArray(Charsets.UTF_8))
                }
                
                Log.d(TAG, "Uploaded to IPFS with hash: $ipfsHash")
                progressDialog.setMessage("Storing on blockchain...")
                
                // Step 4: Store IPFS hash and encrypted key on blockchain
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.setPersonalInfo(ipfsHash, encryptedKey)
                }
                
                Log.d(TAG, "Stored on blockchain. Transaction: $txHash")
                
                // Step 5: Update UI
                currentPersonalInfo = personalInfo
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    displayPersonalInfo(personalInfo)
                    
                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle("Success!")
                        .setMessage("Profile created and stored on blockchain!\n\nTransaction: ${txHash.take(20)}...")
                        .setPositiveButton("OK", null)
                        .show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error creating profile", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle("Error")
                        .setMessage("Failed to create profile: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
    
    /**
     * Upload JSON data to IPFS via backend
     * @param jsonData JSON string to upload
     * @return IPFS hash
     */
    private suspend fun uploadJsonToIPFS(jsonData: String): String {
        // Convert JSON string to bytes
        val jsonBytes = jsonData.toByteArray(Charsets.UTF_8)
        
        // Create multipart request
        val requestBody = jsonBytes.toRequestBody("application/json".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "data.json",
            requestBody
        )
        
        // Upload to IPFS via backend
        val response = ApiClient.api.uploadToIPFS(filePart)
        
        if (!response.isSuccessful) {
            throw Exception("IPFS upload failed: ${response.code()}")
        }
        
        val responseData = response.body()
            ?: throw Exception("Empty IPFS response")
        
        return responseData.ipfsHash
            ?: throw Exception("No IPFS hash in response")
    }
    
    /**
     * Upload encrypted bytes to IPFS via backend
     * @param encryptedData The encrypted byte array to upload
     * @return IPFS hash
     */
    private suspend fun uploadBytesToIPFS(encryptedData: ByteArray): String {
        // Create multipart request
        val requestBody = encryptedData.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "encrypted_data.bin",
            requestBody
        )
        
        // Upload to IPFS via backend
        val response = ApiClient.api.uploadToIPFS(filePart)
        
        if (!response.isSuccessful || response.body()?.success != true) {
            throw Exception("IPFS upload failed: ${response.body()?.error ?: response.code()}")
        }
        
        return response.body()!!.ipfsHash!!
    }
    
    private fun openPartialShareActivity() {
        val personalInfo = currentPersonalInfo
        if (personalInfo == null) {
            Toast.makeText(this, "Please load profile data first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Log the raw PersonalInfo object
        android.util.Log.d(TAG, "=== OPENING PARTIAL SHARE ===")
        android.util.Log.d(TAG, "currentPersonalInfo object:")
        android.util.Log.d(TAG, "  firstName: '${personalInfo.firstName}'")
        android.util.Log.d(TAG, "  lastName: '${personalInfo.lastName}'")
        android.util.Log.d(TAG, "  email: '${personalInfo.email}'")
        android.util.Log.d(TAG, "  hkid: '${personalInfo.hkid}'")
        android.util.Log.d(TAG, "  dateOfBirth: '${personalInfo.dateOfBirth}'")
        android.util.Log.d(TAG, "  gender: '${personalInfo.gender}'")
        android.util.Log.d(TAG, "  bloodType: '${personalInfo.bloodType}'")
        android.util.Log.d(TAG, "  phone: '${personalInfo.phone}'")
        android.util.Log.d(TAG, "  address: '${personalInfo.address}'")
        android.util.Log.d(TAG, "  emergencyContact.name: '${personalInfo.emergencyContact.name}'")
        android.util.Log.d(TAG, "  emergencyContact.relationship: '${personalInfo.emergencyContact.relationship}'")
        android.util.Log.d(TAG, "  emergencyContact.phone: '${personalInfo.emergencyContact.phone}'")
        
        // Collect all personal info data
        val recordData = mapOf(
            "firstName" to personalInfo.firstName,
            "lastName" to personalInfo.lastName,
            "email" to personalInfo.email,
            "hkid" to personalInfo.hkid,
            "dateOfBirth" to personalInfo.dateOfBirth,
            "gender" to personalInfo.gender,
            "bloodType" to personalInfo.bloodType,
            "phone" to personalInfo.phone,
            "address" to personalInfo.address,
            "emergencyContactName" to personalInfo.emergencyContact.name,
            "emergencyContactRelationship" to personalInfo.emergencyContact.relationship,
            "emergencyContactPhone" to personalInfo.emergencyContact.phone
        )
        
        android.util.Log.d(TAG, "recordData map created:")
        recordData.forEach { (k, v) ->
            android.util.Log.d(TAG, "  $k: '$v' (empty: ${v.isEmpty()})")
        }
        
        try {
            val jsonString = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<Map<String, String>>(),
                recordData
            )
            android.util.Log.d(TAG, "JSON string to send: $jsonString")
            
            val intent = android.content.Intent(this, Class.forName("com.fyp.blockchainhealthwallet.ui.partialshare.PartialShareActivity"))
            intent.putExtra("RECORD_ID", WalletManager.getAddress())
            intent.putExtra("RECORD_TYPE", "PERSONAL_INFO")
            intent.putExtra("RECORD_DATA", jsonString)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error opening partial share", e)
            Toast.makeText(this, "Partial share feature not available: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Data class for personal information
 * This will be serialized to JSON and stored on IPFS
 */
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
