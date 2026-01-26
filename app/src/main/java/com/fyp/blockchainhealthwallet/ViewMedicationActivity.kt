package com.fyp.blockchainhealthwallet

import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.View
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
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*

class ViewMedicationActivity : AppCompatActivity() {

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
    private lateinit var tvCreatedAt: android.widget.TextView
    private lateinit var btnEdit: MaterialButton
    private lateinit var btnSaveChanges: MaterialButton

    private var medicationId: BigInteger? = null
    private var selectedStartDate: Calendar = Calendar.getInstance()
    private var selectedEndDate: Calendar? = null
    private var isEditMode = false
    private var progressDialog: ProgressDialog? = null

    companion object {
        private const val TAG = "ViewMedicationActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_medication)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        BlockchainService.initialize(this)

        setupToolbar()
        setupViews()
        loadDataFromIntent()
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
        tvCreatedAt = findViewById(R.id.tvCreatedAt)
        btnEdit = findViewById(R.id.btnEdit)
        btnSaveChanges = findViewById(R.id.btnSaveChanges)
        val btnShareMedication = findViewById<MaterialButton>(R.id.btnShareMedication)
        val btnDeleteMedication = findViewById<MaterialButton>(R.id.btnDeleteMedication)

        btnEdit.setOnClickListener {
            toggleEditMode()
        }

        btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        btnShareMedication.setOnClickListener {
            showShareMedicationDialog()
        }

        btnDeleteMedication.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        etStartDate.setOnClickListener {
            if (isEditMode) showStartDatePicker()
        }

        etEndDate.setOnClickListener {
            if (isEditMode) showEndDatePicker()
        }
    }

    private fun loadDataFromIntent() {
        medicationId = BigInteger(intent.getStringExtra("MEDICATION_ID") ?: "0")
        etMedicationName.setText(intent.getStringExtra("MEDICATION_NAME"))
        etDosage.setText(intent.getStringExtra("DOSAGE"))
        etFrequency.setText(intent.getStringExtra("FREQUENCY"))
        etRoute.setText(intent.getStringExtra("ROUTE"))
        switchIsActive.isChecked = intent.getBooleanExtra("IS_ACTIVE", true)
        etPurpose.setText(intent.getStringExtra("PURPOSE"))
        etDoctor.setText(intent.getStringExtra("DOCTOR"))
        etPharmacy.setText(intent.getStringExtra("PHARMACY"))
        etNotes.setText(intent.getStringExtra("NOTES"))

        val startDateMs = intent.getLongExtra("START_DATE", System.currentTimeMillis())
        selectedStartDate.timeInMillis = startDateMs
        updateStartDateField()

        val endDateMs = intent.getLongExtra("END_DATE", 0L)
        if (endDateMs > 0) {
            selectedEndDate = Calendar.getInstance().apply {
                timeInMillis = endDateMs
            }
            updateEndDateField()
        }

        val createdAtMs = intent.getLongExtra("CREATED_AT", 0L)
        if (createdAtMs > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            tvCreatedAt.text = "Created: ${dateFormat.format(Date(createdAtMs))}"
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode

        etMedicationName.isEnabled = isEditMode
        etDosage.isEnabled = isEditMode
        etFrequency.isEnabled = isEditMode
        etRoute.isEnabled = isEditMode
        etStartDate.isEnabled = isEditMode
        etStartDate.isFocusable = isEditMode
        etStartDate.isClickable = isEditMode
        etEndDate.isEnabled = isEditMode
        etEndDate.isFocusable = isEditMode
        etEndDate.isClickable = isEditMode
        switchIsActive.isEnabled = isEditMode
        etPurpose.isEnabled = isEditMode
        etDoctor.isEnabled = isEditMode
        etPharmacy.isEnabled = isEditMode
        etNotes.isEnabled = isEditMode

        if (isEditMode) {
            btnEdit.text = "Cancel"
            btnSaveChanges.visibility = View.VISIBLE
        } else {
            btnEdit.text = "Edit"
            btnSaveChanges.visibility = View.GONE
            // Reload original data
            loadDataFromIntent()
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

    private fun saveChanges() {
        // Validate
        val name = etMedicationName.text.toString().trim()
        val dosage = etDosage.text.toString().trim()
        val frequency = etFrequency.text.toString().trim()

        if (name.isEmpty() || dosage.isEmpty() || frequency.isEmpty()) {
            Toast.makeText(this, "Please fill in required fields", Toast.LENGTH_SHORT).show()
            return
        }

        selectedEndDate?.let { endDate ->
            if (endDate.timeInMillis < selectedStartDate.timeInMillis) {
                Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show()
                return
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Update")
            .setMessage("Update this medication on the blockchain?\n\nThis will require a blockchain transaction (gas fees apply).")
            .setPositiveButton("Update") { _, _ ->
                performSaveChanges()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performSaveChanges() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Updating medication...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                // Create updated metadata JSON
                updateProgressDialog("Encrypting updated data...")
                val metadataJson = createMedicationMetadataJson()

                // Encrypt and upload
                val (ipfsHash, encryptedKey) = withContext(Dispatchers.IO) {
                    uploadEncryptedMetadata(metadataJson)
                }

                // Update on blockchain
                updateProgressDialog("Sending request to wallet...\nPlease approve transaction")

                val startDateTimestamp = BigInteger.valueOf(selectedStartDate.timeInMillis / 1000)
                val endDateTimestamp = selectedEndDate?.let {
                    BigInteger.valueOf(it.timeInMillis / 1000)
                } ?: BigInteger.ZERO

                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.updateMedication(
                        medicationId = medicationId!!,
                        encryptedDataIpfsHash = ipfsHash,
                        encryptedKey = encryptedKey,
                        isActive = switchIsActive.isChecked,
                        startDate = startDateTimestamp,
                        endDate = endDateTimestamp
                    )
                }

                progressDialog.dismiss()

                if (txHash.startsWith("pending_")) {
                    AlertDialog.Builder(this@ViewMedicationActivity)
                        .setTitle("⏳ Waiting for Approval")
                        .setMessage("Transaction request has been sent!\n\n📱 Open your wallet app to approve.")
                        .setPositiveButton("OK") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    AlertDialog.Builder(this@ViewMedicationActivity)
                        .setTitle("Success!")
                        .setMessage("Medication updated on blockchain.\n\nTransaction: ${txHash.take(10)}...")
                        .setPositiveButton("OK") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }

            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error updating medication", e)

                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true ->
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true ->
                        "Insufficient funds for gas fees"
                    else -> "Transaction error: ${e.message}"
                }

                AlertDialog.Builder(this@ViewMedicationActivity)
                    .setTitle("Update Failed")
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
            "createdAt" to intent.getLongExtra("CREATED_AT", System.currentTimeMillis())
        )

        return org.json.JSONObject(metadata).toString()
    }

    private suspend fun uploadEncryptedMetadata(jsonData: String): Pair<String, String> {
        val randomKey = EncryptionHelper.generateAESKey()
        val encryptedBase64 = EncryptionHelper.encryptDataWithKey(jsonData, randomKey)

        val requestBody = encryptedBase64.toRequestBody("text/plain".toMediaTypeOrNull())
        val response = ApiClient.api.uploadToIPFS(
            okhttp3.MultipartBody.Part.createFormData("file", "medication.enc", requestBody)
        )

        if (!response.isSuccessful || response.body()?.success != true) {
            throw Exception("Failed to upload to IPFS: ${response.message()}")
        }

        val ipfsHash: String = response.body()!!.ipfsHash ?: throw Exception("IPFS hash is null")
        val encryptedKey: String = EncryptionHelper.encryptKeyForBlockchain(randomKey)

        return Pair(ipfsHash, encryptedKey)
    }

    private fun showShareMedicationDialog() {
        com.fyp.blockchainhealthwallet.ui.MedicationShareHelper.showShareMedicationDialog(
            context = this,
            lifecycleScope = lifecycleScope,
            medicationId = medicationId!!,
            medicationName = etMedicationName.text.toString()
        )
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Medication")
            .setMessage("Are you sure you want to delete this medication?\n\nNote: This will mark it as deleted but data remains on blockchain. Shared records remain accessible to recipients.")
            .setPositiveButton("Delete") { _, _ ->
                performDelete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDelete() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Deleting medication...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val txHash = BlockchainService.deleteMedication(medicationId!!)

                progressDialog.dismiss()

                if (txHash.startsWith("pending_")) {
                    AlertDialog.Builder(this@ViewMedicationActivity)
                        .setTitle("⏳ Waiting for Approval")
                        .setMessage("Delete request sent!\n\n📱 Open your wallet app to approve.")
                        .setPositiveButton("OK") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    AlertDialog.Builder(this@ViewMedicationActivity)
                        .setTitle("✅ Deleted")
                        .setMessage("Medication deleted successfully.\n\nTransaction: ${txHash.take(10)}...")
                        .setPositiveButton("OK") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }

            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error deleting medication", e)

                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true ->
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true ->
                        "Insufficient funds for gas fees"
                    e.message?.contains("Already deleted", ignoreCase = true) == true ->
                        "This medication has already been deleted"
                    else -> "Delete failed: ${e.message}"
                }

                AlertDialog.Builder(this@ViewMedicationActivity)
                    .setTitle("Delete Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
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
