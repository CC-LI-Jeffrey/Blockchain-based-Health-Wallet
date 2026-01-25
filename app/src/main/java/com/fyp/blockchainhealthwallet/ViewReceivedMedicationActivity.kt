package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.RSAHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ViewReceivedMedicationActivity : AppCompatActivity() {

    private val TAG = "ViewReceivedMedication"
    
    private lateinit var tvShareInfo: TextView
    private lateinit var tvMedicationName: TextView
    private lateinit var tvDosage: TextView
    private lateinit var tvFrequency: TextView
    private lateinit var tvRoute: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView
    private lateinit var cardPurpose: CardView
    private lateinit var tvPurpose: TextView
    private lateinit var tvDoctor: TextView
    private lateinit var tvPharmacy: TextView
    private lateinit var cardNotes: CardView
    private lateinit var tvNotes: TextView
    private lateinit var cardAttachedPrescription: CardView
    private lateinit var tvPrescriptionPath: TextView
    private lateinit var ivPrescriptionPreview: ImageView
    
    private var decryptedPrescFile: File? = null
    private var aesKey: javax.crypto.SecretKey? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_received_medication)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        setupToolbar()
        setupViews()
        loadMedicationData()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up decrypted prescription file when leaving
        decryptedPrescFile?.delete()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Medication Details"
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        tvShareInfo = findViewById(R.id.tvShareInfo)
        tvMedicationName = findViewById(R.id.tvMedicationName)
        tvDosage = findViewById(R.id.tvDosage)
        tvFrequency = findViewById(R.id.tvFrequency)
        tvRoute = findViewById(R.id.tvRoute)
        tvStatus = findViewById(R.id.tvStatus)
        tvStartDate = findViewById(R.id.tvStartDate)
        tvEndDate = findViewById(R.id.tvEndDate)
        cardPurpose = findViewById(R.id.cardPurpose)
        tvPurpose = findViewById(R.id.tvPurpose)
        tvDoctor = findViewById(R.id.tvDoctor)
        tvPharmacy = findViewById(R.id.tvPharmacy)
        cardNotes = findViewById(R.id.cardNotes)
        tvNotes = findViewById(R.id.tvNotes)
        cardAttachedPrescription = findViewById(R.id.cardAttachedPrescription)
        tvPrescriptionPath = findViewById(R.id.tvPrescriptionPath)
        ivPrescriptionPreview = findViewById(R.id.ivPrescriptionPreview)
    }

    private fun loadMedicationData() {
        val shareId = intent.getStringExtra("SHARE_ID") ?: ""
        val ownerAddress = intent.getStringExtra("OWNER_ADDRESS") ?: ""
        val name = intent.getStringExtra("MEDICATION_NAME") ?: "N/A"
        val dosage = intent.getStringExtra("DOSAGE") ?: "N/A"
        val frequency = intent.getStringExtra("FREQUENCY") ?: "N/A"
        val route = intent.getStringExtra("ROUTE") ?: "N/A"
        val purpose = intent.getStringExtra("PURPOSE") ?: ""
        val doctor = intent.getStringExtra("DOCTOR") ?: "N/A"
        val pharmacy = intent.getStringExtra("PHARMACY") ?: "N/A"
        val notes = intent.getStringExtra("NOTES") ?: ""
        val isActive = intent.getBooleanExtra("IS_ACTIVE", true)
        val startDateMs = intent.getLongExtra("START_DATE", 0L)
        val endDateMs = intent.getLongExtra("END_DATE", 0L)
        val hasPrescription = intent.getBooleanExtra("HAS_PRESCRIPTION", false)
        val prescriptionIpfsHash = intent.getStringExtra("PRESCRIPTION_IPFS_HASH") ?: ""
        val encryptedRecordKey = intent.getStringExtra("ENCRYPTED_RECORD_KEY") ?: ""

        // Set share info
        tvShareInfo.text = "Share ID: $shareId"

        // Set medication information
        tvMedicationName.text = name
        tvDosage.text = dosage
        tvFrequency.text = frequency
        tvRoute.text = route
        tvStatus.text = if (isActive) "✅ Active" else "⏸️ Completed"

        // Format and set start date
        if (startDateMs > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvStartDate.text = dateFormat.format(Date(startDateMs))
        } else {
            tvStartDate.text = "N/A"
        }

        // Format and set end date
        if (endDateMs > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvEndDate.text = dateFormat.format(Date(endDateMs))
        } else {
            tvEndDate.text = "N/A"
        }

        // Set prescribing information
        tvDoctor.text = doctor
        tvPharmacy.text = pharmacy

        // Show purpose card only if purpose exists
        if (purpose.isNotEmpty() && purpose != "N/A") {
            cardPurpose.visibility = View.VISIBLE
            tvPurpose.text = purpose
        } else {
            cardPurpose.visibility = View.GONE
        }

        // Show notes card only if notes exist
        if (notes.isNotEmpty() && notes != "N/A") {
            cardNotes.visibility = View.VISIBLE
            tvNotes.text = notes
        } else {
            cardNotes.visibility = View.GONE
        }
        
        // Show attached prescription if available
        if (hasPrescription && prescriptionIpfsHash.isNotEmpty() && encryptedRecordKey.isNotEmpty()) {
            cardAttachedPrescription.visibility = View.VISIBLE
            tvPrescriptionPath.text = "Downloading prescription..."
            
            // Decrypt the AES key first, then download and decrypt prescription
            decryptKeyAndPrescription(encryptedRecordKey, prescriptionIpfsHash)
        } else {
            cardAttachedPrescription.visibility = View.GONE
        }
    }
    
    /**
     * Decrypt the RSA-encrypted AES key, then download and decrypt the prescription
     */
    private fun decryptKeyAndPrescription(encryptedRecordKey: String, prescriptionIpfsHash: String) {
        lifecycleScope.launch {
            try {
                tvPrescriptionPath.text = "Decrypting access key..."
                
                // Step 1: Decrypt the AES key using our RSA private key
                aesKey = withContext(Dispatchers.IO) {
                    RSAHelper.decryptKeyWithPrivateKey(encryptedRecordKey)
                }
                
                Log.d(TAG, "Successfully decrypted AES key")
                
                // Step 2: Download and decrypt the prescription
                downloadAndDecryptPrescription(prescriptionIpfsHash)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error decrypting key or prescription", e)
                tvPrescriptionPath.text = "Error: ${e.message}"
            }
        }
    }
    
    /**
     * Download encrypted prescription from IPFS and decrypt it
     */
    private fun downloadAndDecryptPrescription(ipfsHash: String) {
        lifecycleScope.launch {
            try {
                tvPrescriptionPath.text = "Downloading prescription..."
                
                // 1. Download encrypted prescription from IPFS
                val encryptedFile = withContext(Dispatchers.IO) {
                    val response = ApiClient.api.getFromIPFS(ipfsHash)
                    if (!response.isSuccessful || response.body() == null) {
                        throw Exception("Failed to download prescription from IPFS")
                    }

                    val tempFile = File(cacheDir, "presc_${System.currentTimeMillis()}.enc")
                    val fileBytes = response.body()!!.bytes()
                    tempFile.writeBytes(fileBytes)
                    tempFile
                }
                
                Log.d(TAG, "Downloaded prescription from IPFS")
                
                // 2. Decrypt prescription
                tvPrescriptionPath.text = "Decrypting prescription..."
                
                val decryptedFile = withContext(Dispatchers.IO) {
                    val outputFile = File(cacheDir, "presc_${System.currentTimeMillis()}.dat")
                    
                    // Decrypt using AES/CBC
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
                
                // 3. Detect file type and update UI
                val header = decryptedFile.inputStream().use { it.readBytes().take(10).toByteArray() }
                Log.d(TAG, "Prescription header: ${header.joinToString(" ") { "%02x".format(it) }}")
                
                val (fileType, extension) = when {
                    header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() -> Pair("PDF", "pdf")
                    header.size >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> Pair("Image", "jpg")
                    header.size >= 2 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() -> Pair("Image", "png")
                    else -> Pair("Unknown", "dat")
                }
                
                // Rename file with correct extension
                val correctFile = if (decryptedFile.extension != extension) {
                    val renamed = File(decryptedFile.parent, "presc_${System.currentTimeMillis()}.$extension")
                    decryptedFile.copyTo(renamed, overwrite = true)
                    decryptedFile.delete()
                    renamed
                } else {
                    decryptedFile
                }
                
                decryptedPrescFile = correctFile
                
                // Update UI
                tvPrescriptionPath.text = "prescription.$extension"
                
                // Show preview if it's an image
                if (fileType == "Image") {
                    val bitmap = BitmapFactory.decodeFile(correctFile.absolutePath)
                    if (bitmap != null) {
                        ivPrescriptionPreview.setImageBitmap(bitmap)
                        ivPrescriptionPreview.visibility = View.VISIBLE
                    }
                }
                
                // Make prescription clickable to open
                cardAttachedPrescription.setOnClickListener {
                    viewPrescription()
                }
                
                Log.d(TAG, "✅ Prescription loaded successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading/decrypting prescription", e)
                tvPrescriptionPath.text = "Error loading prescription: ${e.message}"
            }
        }
    }
    
    /**
     * Open prescription in appropriate viewer
     */
    private fun viewPrescription() {
        if (decryptedPrescFile == null) {
            return
        }

        val file = decryptedPrescFile!!
        val extension = file.extension.lowercase()

        try {
            when (extension) {
                "pdf" -> {
                    val intent = Intent(this, PdfViewerActivity::class.java)
                    intent.putExtra("PDF_PATH", file.absolutePath)
                    intent.putExtra("TITLE", "Prescription")
                    startActivity(intent)
                }
                in listOf("jpg", "jpeg", "png", "gif", "bmp") -> {
                    val intent = Intent(this, ImageViewerActivity::class.java)
                    intent.putExtra("IMAGE_PATH", file.absolutePath)
                    intent.putExtra("TITLE", "Prescription")
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening prescription", e)
        }
    }
}
