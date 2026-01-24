package com.fyp.blockchainhealthwallet

import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

class AddMedicationActivity : AppCompatActivity() {

    private lateinit var etMedicationName: TextInputEditText
    private lateinit var etDosage: TextInputEditText
    private lateinit var etFrequency: TextInputEditText
    private lateinit var etRoute: TextInputEditText
    private lateinit var etStartDate: TextInputEditText
    private lateinit var etEndDate: TextInputEditText
    private lateinit var switchIsActive: SwitchMaterial
    private lateinit var etPurpose: TextInputEditText
    private lateinit var etDoctor: TextInputEditText
    private lateinit var etPharmacy: TextInputEditText
    private lateinit var etNotes: TextInputEditText
    private lateinit var btnSaveMedication: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private var selectedStartDate: Calendar = Calendar.getInstance()
    private var selectedEndDate: Calendar? = null
    private var progressDialog: ProgressDialog? = null

    companion object {
        private const val TAG = "AddMedicationActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_medication)

        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        // Initialize BlockchainService
        BlockchainService.initialize(this)

        // Check wallet connection
        if (!BlockchainService.isWalletConnected()) {
            showWalletNotConnectedDialog()
            return
        }

        setupToolbar()
        setupViews()
    }

    private fun showWalletNotConnectedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Wallet Not Connected")
            .setMessage("You need to connect your wallet to add medications to the blockchain.")
            .setPositiveButton("Connect Wallet") { _, _ ->
                val intent = Intent(this, WalletInfoActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        etMedicationName = findViewById(R.id.etMedicationName)
        etDosage = findViewById(R.id.etDosage)
        etFrequency = findViewById(R.id.etFrequency)
        etRoute = findViewById(R.id.etRoute)
        etStartDate = findViewById(R.id.etStartDate)
        etEndDate = findViewById(R.id.etEndDate)
        switchIsActive = findViewById(R.id.switchIsActive)
        etPurpose = findViewById(R.id.etPurpose)
        etDoctor = findViewById(R.id.etDoctor)
        etPharmacy = findViewById(R.id.etPharmacy)
        etNotes = findViewById(R.id.etNotes)
        btnSaveMedication = findViewById(R.id.btnSaveMedication)
        btnCancel = findViewById(R.id.btnCancel)

        // Set default start date
        updateStartDateField()

        // Date pickers
        etStartDate.setOnClickListener {
            showStartDatePicker()
        }

        etEndDate.setOnClickListener {
            showEndDatePicker()
        }

        // Save button
        btnSaveMedication.setOnClickListener {
            saveMedication()
        }

        // Cancel button
        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun showStartDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedStartDate.set(year, month, dayOfMonth)
                updateStartDateField()
            },
            selectedStartDate.get(Calendar.YEAR),
            selectedStartDate.get(Calendar.MONTH),
            selectedStartDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showEndDatePicker() {
        val calendar = selectedEndDate ?: Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val endDate = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                selectedEndDate = endDate
                updateEndDateField()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateStartDateField() {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        etStartDate.setText(dateFormat.format(selectedStartDate.time))
    }

    private fun updateEndDateField() {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        selectedEndDate?.let {
            etEndDate.setText(dateFormat.format(it.time))
        }
    }

    private fun saveMedication() {
        // Validate required fields
        val name = etMedicationName.text.toString().trim()
        val dosage = etDosage.text.toString().trim()
        val frequency = etFrequency.text.toString().trim()

        if (name.isEmpty()) {
            etMedicationName.error = "Required"
            etMedicationName.requestFocus()
            return
        }

        if (dosage.isEmpty()) {
            etDosage.error = "Required"
            etDosage.requestFocus()
            return
        }

        if (frequency.isEmpty()) {
            etFrequency.error = "Required"
            etFrequency.requestFocus()
            return
        }

        // Validate date range if end date is set
        selectedEndDate?.let { endDate ->
            if (endDate.timeInMillis < selectedStartDate.timeInMillis) {
                Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Show confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Confirm Save")
            .setMessage("Save this medication to the blockchain?\n\nThis will require a blockchain transaction (gas fees apply).")
            .setPositiveButton("Save") { _, _ ->
                performSaveMedication()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performSaveMedication() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Preparing medication data...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                // Step 1: Check personal info exists
                updateProgressDialog("Checking personal info...")
                val userAddress = com.fyp.blockchainhealthwallet.wallet.WalletManager.getAddress()
                    ?: throw Exception("No wallet connected")

                val personalInfoRef = withContext(Dispatchers.IO) {
                    BlockchainService.getPersonalInfoRef(userAddress)
                }

                if (personalInfoRef == null || !personalInfoRef.exists) {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this@AddMedicationActivity)
                        .setTitle("⚠️ Personal Info Required")
                        .setMessage("Before adding medications, you must set your personal information first.\n\nGo to Profile to complete your personal information.")
                        .setPositiveButton("Go to Profile") { _, _ ->
                            startActivity(Intent(this@AddMedicationActivity, ProfileActivity::class.java))
                            finish()
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                    return@launch
                }

                // Step 2: Create metadata JSON
                updateProgressDialog("Encrypting medication data...")
                val metadataJson = createMedicationMetadataJson()

                // Step 3: Encrypt and upload to IPFS
                val (ipfsHash, encryptedKey) = withContext(Dispatchers.IO) {
                    uploadEncryptedMetadata(metadataJson)
                }

                // Step 4: Send to blockchain
                updateProgressDialog("Sending request to wallet...\nPlease approve transaction")

                val startDateTimestamp = java.math.BigInteger.valueOf(selectedStartDate.timeInMillis / 1000)
                val endDateTimestamp = selectedEndDate?.let {
                    java.math.BigInteger.valueOf(it.timeInMillis / 1000)
                } ?: java.math.BigInteger.ZERO

                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.addMedication(
                        encryptedDataIpfsHash = ipfsHash,
                        encryptedKey = encryptedKey,
                        isActive = switchIsActive.isChecked,
                        startDate = startDateTimestamp,
                        endDate = endDateTimestamp
                    )
                }

                progressDialog.dismiss()

                // Show success
                if (txHash.startsWith("pending_")) {
                    AlertDialog.Builder(this@AddMedicationActivity)
                        .setTitle("⏳ Waiting for Approval")
                        .setMessage("Transaction request has been sent!\n\n📱 IMPORTANT: Open your wallet app now\n\nYou should see a pending transaction request to approve.")
                        .setPositiveButton("I Opened My Wallet") { _, _ ->
                            Toast.makeText(
                                this@AddMedicationActivity,
                                "Check your wallet app for the pending request",
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    AlertDialog.Builder(this@AddMedicationActivity)
                        .setTitle("Success!")
                        .setMessage("Medication saved on blockchain.\n\nTransaction: ${txHash.take(10)}...")
                        .setPositiveButton("OK") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }

            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error saving medication", e)

                val errorMessage = when {
                    e.message?.contains("redirect", ignoreCase = true) == true ->
                        "Please open your wallet app manually to approve the transaction"
                    e.message?.contains("user rejected", ignoreCase = true) == true ->
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true ->
                        "Insufficient funds for gas fees"
                    else -> "Transaction error: ${e.message}"
                }

                AlertDialog.Builder(this@AddMedicationActivity)
                    .setTitle("Transaction Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun createMedicationMetadataJson(): String {
        val metadata = mapOf(
            "name" to etMedicationName.text.toString().trim(),
            "dosage" to etDosage.text.toString().trim(),
            "frequency" to etFrequency.text.toString().trim(),
            "route" to etRoute.text.toString().trim(),
            "isActive" to switchIsActive.isChecked,
            "startDate" to selectedStartDate.timeInMillis,
            "endDate" to (selectedEndDate?.timeInMillis ?: 0L),
            "purpose" to etPurpose.text.toString().trim(),
            "prescribingDoctor" to etDoctor.text.toString().trim(),
            "pharmacy" to etPharmacy.text.toString().trim(),
            "notes" to etNotes.text.toString().trim(),
            "createdAt" to System.currentTimeMillis()
        )

        return org.json.JSONObject(metadata).toString()
    }

    private suspend fun uploadEncryptedMetadata(jsonData: String): Pair<String, String> {
        // Generate random AES key for this medication
        val randomKey = EncryptionHelper.generateAESKey()

        // Encrypt JSON with random key
        val encryptedBase64 = EncryptionHelper.encryptDataWithKey(jsonData, randomKey)

        // Upload encrypted data to IPFS
        val requestBody = encryptedBase64.toRequestBody("text/plain".toMediaTypeOrNull())
        val response = ApiClient.api.uploadToIPFS(
            okhttp3.MultipartBody.Part.createFormData("file", "medication.enc", requestBody)
        )

        if (!response.isSuccessful || response.body()?.success != true) {
            throw Exception("Failed to upload to IPFS: ${response.message()}")
        }

        val ipfsHash: String = response.body()!!.ipfsHash ?: throw Exception("IPFS hash is null")

        // Encrypt random key with user's wallet-derived master key
        val encryptedKey: String = EncryptionHelper.encryptKeyForBlockchain(randomKey)

        return Pair(ipfsHash, encryptedKey)
    }

    private fun updateProgressDialog(message: String) {
        runOnUiThread {
            progressDialog?.setMessage(message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
    }
}
