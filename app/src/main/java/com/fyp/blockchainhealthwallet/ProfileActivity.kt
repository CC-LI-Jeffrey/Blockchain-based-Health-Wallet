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
import com.fyp.blockchainhealthwallet.crypto.PublicKeyRegistry
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
    
    private var currentPersonalInfo: PersonalInfo? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        initializeViews()
        setupClickListeners()
        loadPersonalInfo()
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
    }
    
    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
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
                
                // Step 3: Decrypt and parse JSON data
                Log.d(TAG, "========================================")
                Log.d(TAG, "Step 3: Decrypting and Parsing JSON")
                Log.d(TAG, "========================================")
                val encryptedData = response.body()!!.string()
                Log.d(TAG, "Retrieved encrypted data from IPFS (${encryptedData.length} bytes)")
                
                // Decrypt the data using category key
                val jsonData = withContext(Dispatchers.IO) {
                    EncryptionHelper.decryptDataWithCategory(encryptedData, BlockchainService.DataCategory.PERSONAL_INFO)
                }
                
                Log.d(TAG, "Decrypted JSON content: $jsonData")
                
                val personalInfo = gson.fromJson(jsonData, PersonalInfo::class.java)
                currentPersonalInfo = personalInfo
                
                Log.d(TAG, "✅ Parsed PersonalInfo:")
                Log.d(TAG, "  - Name: ${personalInfo.firstName} ${personalInfo.lastName}")
                Log.d(TAG, "  - Email: ${personalInfo.email}")
                Log.d(TAG, "  - HKID: ${personalInfo.hkid}")
                
                // Step 4: Display data
                Log.d(TAG, "========================================")
                Log.d(TAG, "Step 4: Displaying data in UI")
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
        tvProfileName.text = "${info.firstName} ${info.lastName}"
        tvProfileEmail.text = info.email
        tvHKID.text = info.hkid
        tvDOB.text = info.dateOfBirth
        tvGender.text = info.gender
        tvBloodType.text = info.bloodType
        tvPhone.text = info.phone
        tvAddress.text = info.address
        tvEmergencyName.text = info.emergencyContact.name
        tvEmergencyRelation.text = info.emergencyContact.relationship
        tvEmergencyPhone.text = info.emergencyContact.phone
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
        
        // Populate with existing data if available
        currentPersonalInfo?.let { info ->
            formView.findViewById<android.widget.EditText>(R.id.etFirstName)?.setText(info.firstName)
            formView.findViewById<android.widget.EditText>(R.id.etLastName)?.setText(info.lastName)
            formView.findViewById<android.widget.EditText>(R.id.etEmail)?.setText(info.email)
            formView.findViewById<android.widget.EditText>(R.id.etHKID)?.setText(info.hkid)
            formView.findViewById<android.widget.EditText>(R.id.etDOB)?.setText(info.dateOfBirth)
            formView.findViewById<android.widget.EditText>(R.id.etGender)?.setText(info.gender)
            formView.findViewById<android.widget.EditText>(R.id.etBloodType)?.setText(info.bloodType)
            formView.findViewById<android.widget.EditText>(R.id.etPhone)?.setText(info.phone)
            formView.findViewById<android.widget.EditText>(R.id.etAddress)?.setText(info.address)
            formView.findViewById<android.widget.EditText>(R.id.etEmergencyName)?.setText(info.emergencyContact.name)
            formView.findViewById<android.widget.EditText>(R.id.etEmergencyRelation)?.setText(info.emergencyContact.relationship)
            formView.findViewById<android.widget.EditText>(R.id.etEmergencyPhone)?.setText(info.emergencyContact.phone)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(formView)
            .setPositiveButton("Save to Blockchain") { _, _ ->
                saveProfileFromForm(formView)
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                
                progressDialog.setMessage("Encrypting personal info...")
                
                // Convert to JSON
                val jsonData = gson.toJson(personalInfo)
                Log.d(TAG, "Personal info JSON: $jsonData")
                
                // Encrypt the JSON data using category key
                val encryptedData = withContext(Dispatchers.IO) {
                    EncryptionHelper.encryptDataWithCategory(jsonData, BlockchainService.DataCategory.PERSONAL_INFO)
                }
                
                progressDialog.setMessage("Uploading to IPFS...")
                
                // Upload encrypted data to IPFS
                val ipfsHash = withContext(Dispatchers.IO) {
                    uploadEncryptedDataToIPFS(encryptedData)
                }
                
                Log.d(TAG, "Uploaded to IPFS with hash: $ipfsHash")
                
                // Check if ECDH key needs to be registered (first time setup)
                val needsEcdhRegistration = withContext(Dispatchers.IO) {
                    !PublicKeyRegistry.isKeyRegisteredOnChain()
                }
                
                if (needsEcdhRegistration) {
                    // First time setup - need 2 transactions
                    progressDialog.setMessage("Step 1/2: Registering encryption key...\n\nPlease approve in your wallet")
                    
                    val ecdhRegistered = withContext(Dispatchers.IO) {
                        PublicKeyRegistry.registerPublicKey()
                    }
                    
                    if (!ecdhRegistered) {
                        throw Exception("Failed to register encryption key. Please try again.")
                    }
                    Log.d(TAG, "ECDH key registered successfully")
                    
                    // Small delay to let first transaction settle
                    kotlinx.coroutines.delay(5000)
                    
                    progressDialog.setMessage("Step 2/2: Saving profile...\n\nPlease approve in your wallet")
                } else {
                    progressDialog.setMessage("Saving profile...\n\nPlease approve in your wallet")
                }
                
                // Store IPFS hash on blockchain
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.setPersonalInfo(ipfsHash)
                }
                
                Log.d(TAG, "Stored on blockchain. Transaction: $txHash")
                
                // Update UI
                currentPersonalInfo = personalInfo
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    displayPersonalInfo(personalInfo)
                    
                    val message = if (needsEcdhRegistration) {
                        "Profile created successfully!\n\n✅ Encryption key registered\n✅ Profile saved to blockchain\n\nTransaction: ${txHash.take(20)}..."
                    } else {
                        "Profile saved to blockchain!\n\nTransaction: ${txHash.take(20)}..."
                    }
                    
                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle("Success!")
                        .setMessage(message)
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
                
                progressDialog.setMessage("Encrypting personal info...")
                
                // Step 2: Convert to JSON
                val jsonData = gson.toJson(personalInfo)
                Log.d(TAG, "Personal info JSON: $jsonData")
                
                // Step 3: Encrypt the JSON data using category key
                val encryptedData = withContext(Dispatchers.IO) {
                    EncryptionHelper.encryptDataWithCategory(jsonData, BlockchainService.DataCategory.PERSONAL_INFO)
                }
                
                progressDialog.setMessage("Uploading to IPFS...")
                
                // Step 4: Upload encrypted data to IPFS
                val ipfsHash = withContext(Dispatchers.IO) {
                    uploadEncryptedDataToIPFS(encryptedData)
                }
                
                Log.d(TAG, "Uploaded to IPFS with hash: $ipfsHash")
                
                // Check if ECDH key needs to be registered (first time setup)
                val needsEcdhRegistration = withContext(Dispatchers.IO) {
                    !PublicKeyRegistry.isKeyRegisteredOnChain()
                }
                
                if (needsEcdhRegistration) {
                    // First time setup - need 2 transactions
                    progressDialog.setMessage("Step 1/2: Registering encryption key...\n\nPlease approve in your wallet")
                    
                    val ecdhRegistered = withContext(Dispatchers.IO) {
                        PublicKeyRegistry.registerPublicKey()
                    }
                    
                    if (!ecdhRegistered) {
                        throw Exception("Failed to register encryption key. Please try again.")
                    }
                    Log.d(TAG, "ECDH key registered successfully")
                    
                    // Small delay to let first transaction settle
                    kotlinx.coroutines.delay(6000)
                    
                    progressDialog.setMessage("Step 2/2: Saving profile...\n\nPlease approve in your wallet")
                } else {
                    progressDialog.setMessage("Saving profile...\n\nPlease approve in your wallet")
                }
                
                // Step 4: Store IPFS hash on blockchain
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.setPersonalInfo(ipfsHash)
                }
                
                Log.d(TAG, "Stored on blockchain. Transaction: $txHash")
                
                // Step 5: Update UI
                currentPersonalInfo = personalInfo
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    displayPersonalInfo(personalInfo)
                    
                    val message = if (needsEcdhRegistration) {
                        "Profile created successfully!\n\n✅ Encryption key registered\n✅ Profile saved to blockchain\n\nTransaction: ${txHash.take(20)}..."
                    } else {
                        "Profile created and stored on blockchain!\n\nTransaction: ${txHash.take(20)}..."
                    }
                    
                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle("Success!")
                        .setMessage(message)
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
     * Upload encrypted data to IPFS via backend
     * @param encryptedData Base64 encrypted data string
     * @return IPFS hash
     */
    private suspend fun uploadEncryptedDataToIPFS(encryptedData: String): String {
        // Convert encrypted data to bytes
        val jsonBytes = encryptedData.toByteArray(Charsets.UTF_8)
        
        // Create multipart request
        val requestBody = jsonBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "personal_info_encrypted.dat",
            requestBody
        )
        
        // Upload to IPFS via backend
        val response = ApiClient.api.uploadToIPFS(filePart)
        
        if (!response.isSuccessful || response.body()?.success != true) {
            throw Exception("IPFS upload failed: ${response.body()?.error ?: response.code()}")
        }
        
        return response.body()!!.ipfsHash!!
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
