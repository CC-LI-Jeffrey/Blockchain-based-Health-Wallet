package com.fyp.blockchainhealthwallet.ui.partialshare

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.blockchain.MerkleTreeHelper
import com.fyp.blockchainhealthwallet.models.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
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
    private lateinit var receiverAddressInput: EditText
    private lateinit var expiryHoursInput: EditText
    private lateinit var generateButton: Button
    private lateinit var qrCodeImage: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    
    private val merkleHelper = MerkleTreeHelper()
    private val selectedAttributes = mutableSetOf<String>()
    private var currentRecordType: RecordSchemas.RecordType? = null
    private var fullRecord: Map<String, String>? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_partial_share)
        
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
        receiverAddressInput = findViewById(R.id.receiverAddressInput)
        expiryHoursInput = findViewById(R.id.expiryHoursInput)
        generateButton = findViewById(R.id.generateButton)
        qrCodeImage = findViewById(R.id.qrCodeImage)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
    }
    
    private fun setupListeners() {
        // Record type spinner
        recordTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val types = RecordSchemas.RecordType.values()
                if (position < types.size) {
                    currentRecordType = types[position]
                    updateAttributesList()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Share method radio group
        shareMethodGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.qrCodeRadio -> {
                    receiverAddressInput.visibility = View.GONE
                }
                R.id.blockchainRadio -> {
                    receiverAddressInput.visibility = View.VISIBLE
                }
            }
        }
        
        // Generate button
        generateButton.setOnClickListener {
            generatePartialShare()
        }
    }
    
    private fun loadRecordData() {
        // TODO: Load from intent extras
        val recordId = intent.getStringExtra("RECORD_ID")
        val recordTypeStr = intent.getStringExtra("RECORD_TYPE")
        val recordDataJson = intent.getStringExtra("RECORD_DATA")
        
        if (recordTypeStr != null && recordDataJson != null) {
            currentRecordType = RecordSchemas.RecordType.valueOf(recordTypeStr)
            fullRecord = Json.decodeFromString(recordDataJson)
            updateAttributesList()
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
        if (selectedAttributes.isEmpty()) {
            Toast.makeText(this, "Please select at least one attribute", Toast.LENGTH_SHORT).show()
            return
        }
        
        val recordType = currentRecordType ?: return
        val data = fullRecord ?: return
        
        val shareMethod = if (qrCodeRadio.isChecked) {
            ShareMethod.QR_CODE
        } else {
            ShareMethod.BLOCKCHAIN
        }
        
        // Validate blockchain method requirements
        if (shareMethod == ShareMethod.BLOCKCHAIN) {
            val receiverAddress = receiverAddressInput.text.toString().trim()
            if (receiverAddress.isEmpty()) {
                Toast.makeText(this, "Please enter receiver address", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                statusText.text = "Building Merkle tree..."
                
                // Build Merkle tree
                val merkleTree = merkleHelper.buildMerkleTree(recordType, data)
                
                // Filter to selected attributes
                val partialData = selectedAttributes.associateWith { data[it] ?: "" }
                
                statusText.text = "Generating proofs..."
                
                // Generate proofs
                val proofs = merkleHelper.generateProofs(merkleTree, partialData)
                
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
                
                when (shareMethod) {
                    ShareMethod.QR_CODE -> {
                        generateQRCode(sharePackage)
                    }
                    ShareMethod.BLOCKCHAIN -> {
                        uploadToBlockchain(sharePackage)
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                statusText.text = "Error: ${e.message}"
                Toast.makeText(this@PartialShareActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
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
        
        qrCodeImage.setImageBitmap(bitmap)
        qrCodeImage.visibility = View.VISIBLE
        statusText.text = "QR code generated! Scan to access shared data."
        
        Toast.makeText(this, "QR Code ready for scanning", Toast.LENGTH_LONG).show()
    }
    
    private suspend fun uploadToBlockchain(sharePackage: PartialSharePackage) {
        statusText.text = "Uploading to IPFS..."
        
        // TODO: Implement IPFS upload and blockchain transaction
        // 1. Encrypt package for receiver (optional RSA encryption)
        // 2. Upload to IPFS via backend
        // 3. Call smart contract grantPartialAccessWithIPFS()
        
        statusText.text = "Uploading to blockchain..."
        
        // Placeholder implementation
        Toast.makeText(this, "Blockchain upload - Implementation pending", Toast.LENGTH_SHORT).show()
        
        statusText.text = "Share granted on blockchain! Record ID: [TODO]"
    }
}
