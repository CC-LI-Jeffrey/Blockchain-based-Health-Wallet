package com.fyp.blockchainhealthwallet.ui.partialshare

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.blockchain.MerkleTreeHelper
import com.fyp.blockchainhealthwallet.models.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity for scanning QR codes with partial share packages
 */
class ScanPartialShareActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var resultContainer: ScrollView
    private lateinit var statusText: TextView
    private lateinit var attributesContainer: LinearLayout
    private lateinit var verifyButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSelectPhoto: com.google.android.material.button.MaterialButton
    private lateinit var btnTestTampered: com.google.android.material.button.MaterialButton
    
    private lateinit var cameraExecutor: ExecutorService
    private val merkleHelper = MerkleTreeHelper()
    private var scannedPackage: PartialSharePackage? = null
    
    // Photo picker launcher
    private val photoPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scanQRFromImage(uri)
        }
    }
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_partial_share)
        
        initViews()
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    private fun initViews() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        previewView = findViewById(R.id.previewView)
        resultContainer = findViewById(R.id.resultContainer)
        statusText = findViewById(R.id.statusText)
        attributesContainer = findViewById(R.id.attributesContainer)
        verifyButton = findViewById(R.id.verifyButton)
        progressBar = findViewById(R.id.progressBar)
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto)
        btnTestTampered = findViewById(R.id.btnTestTampered)
        
        btnSelectPhoto.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }

        btnTestTampered.setOnClickListener {
            showTamperedQRCode()
        }
        
        verifyButton.setOnClickListener {
            verifyScannedData()
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCodeData ->
                        runOnUiThread {
                            processQRCode(qrCodeData)
                        }
                    })
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                runOnUiThread {
                    statusText.text = "Scanning... Point camera at QR code"
                    resultContainer.visibility = ScrollView.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                statusText.text = "Error starting camera: ${e.message}"
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun processQRCode(qrData: String) {
        try {
            // Parse QR code data
            val sharePackage = Json.decodeFromString<PartialSharePackage>(qrData)
            
            // Check expiry
            if (System.currentTimeMillis() > sharePackage.expiryTime) {
                statusText.text = "Error: Share has expired"
                Toast.makeText(this, "This share has expired", Toast.LENGTH_SHORT).show()
                return
            }
            
            scannedPackage = sharePackage
            displayScannedData(sharePackage)
            
        } catch (e: Exception) {
            e.printStackTrace()
            statusText.text = "Error parsing QR code: ${e.message}"
        }
    }
    
    private fun displayScannedData(sharePackage: PartialSharePackage) {
        resultContainer.visibility = ScrollView.VISIBLE
        attributesContainer.removeAllViews()
        
        statusText.text = "Scanned ${sharePackage.recordType} record with ${sharePackage.attributes.size} attributes"
        
        // Display attributes
        for ((attrName, attrValue) in sharePackage.attributes) {
            val displayName = RecordSchemas.getDisplayName(attrName)
            
            val textView = TextView(this).apply {
                text = "$displayName: $attrValue"
                textSize = 16f
                setPadding(16, 8, 16, 8)
            }
            
            attributesContainer.addView(textView)
        }
        
        // Show verification info
        val infoText = TextView(this).apply {
            text = "\nMerkle Root: ${sharePackage.merkleRoot.take(16)}...\n" +
                   "Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                       .format(java.util.Date(sharePackage.timestamp))}\n" +
                   "Expires: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                       .format(java.util.Date(sharePackage.expiryTime))}"
            textSize = 14f
            setPadding(16, 8, 16, 8)
        }
        attributesContainer.addView(infoText)
        
        verifyButton.visibility = Button.VISIBLE
    }
    
    /**
     * Generates a real scannable QR code image whose Merkle root has been replaced with a
     * wrong value. Scan this QR with the camera to trigger the VERIFICATION FAILED path.
     */
    private fun showTamperedQRCode() {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    val pkg = MerkleTreeHelper.createTamperedPackage(
                        recordType = RecordSchemas.RecordType.MEDICATION,
                        attributes = mapOf(
                            "medicineName" to "Aspirin",
                            "dosage" to "100mg",
                            "frequency" to "Once daily"
                        )
                    )
                    val json = Json.encodeToString(pkg)
                    val bitMatrix = QRCodeWriter().encode(json, BarcodeFormat.QR_CODE, 800, 800)
                    val bmp = Bitmap.createBitmap(800, 800, Bitmap.Config.RGB_565)
                    for (x in 0 until 800) {
                        for (y in 0 until 800) {
                            bmp.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                        }
                    }
                    bmp
                }

                // Show in a dialog so the user can point their phone at screen or save it
                val imageView = android.widget.ImageView(this@ScanPartialShareActivity).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    setPadding(32, 32, 32, 32)
                }

                AlertDialog.Builder(this@ScanPartialShareActivity)
                    .setTitle("Tampered QR Code")
                    .setMessage("Scan this QR code — proofs are real but the Merkle root is wrong. Verification will FAIL.")
                    .setView(imageView)
                    .setPositiveButton("Save to Gallery") { _, _ -> saveTamperedQR(bitmap) }
                    .setNegativeButton("Close", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(this@ScanPartialShareActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveTamperedQR(bitmap: Bitmap) {
        try {
            val filename = "TamperedQR_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HealthWallet")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Toast.makeText(this, "Saved to Pictures/HealthWallet", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyScannedData() {
        val sharePackage = scannedPackage ?: return

        lifecycleScope.launch {
            try {
                progressBar.visibility = ProgressBar.VISIBLE
                statusText.text = "Verifying Merkle proofs..."
                attributesContainer.removeAllViews()

                val proofs = sharePackage.proofs.mapValues { (_, proofData) ->
                    proofData.map { ProofNodeData.toProofNode(it) }
                }

                // --- Overview header ---
                addSectionHeader("Merkle Root (from QR)")
                addMonoText(sharePackage.merkleRoot.take(32) + "...")

                addSectionHeader("Verifying ${sharePackage.attributes.size} attribute(s)...")

                var allValid = true

                for ((attrName, attrValue) in sharePackage.attributes) {
                    val proof = proofs[attrName]
                    if (proof == null) {
                        allValid = false
                        addAttributeBlock(attrName, attrValue, null)
                        continue
                    }

                    val result = merkleHelper.verifyProofWithSteps(attrName, attrValue, proof, sharePackage.merkleRoot)
                    if (!result.isValid) allValid = false
                    addAttributeBlock(attrName, attrValue, result)
                }

                // --- Final verdict ---
                val verdictColor: Int
                val verdictText: String
                if (allValid) {
                    verdictText = "ALL PROOFS VALID - Data is authentic"
                    verdictColor = android.R.color.holo_green_dark
                    // Save verified record so it persists across navigation
                    ScannedQRShareRepository.save(this@ScanPartialShareActivity, sharePackage, true)
                    Toast.makeText(this@ScanPartialShareActivity, "Data verified and saved!", Toast.LENGTH_LONG).show()
                } else {
                    verdictText = "VERIFICATION FAILED - Data may be tampered"
                    verdictColor = android.R.color.holo_red_dark
                    // Also save failed scans so the user can review them
                    ScannedQRShareRepository.save(this@ScanPartialShareActivity, sharePackage, false)
                    Toast.makeText(this@ScanPartialShareActivity, "Warning: Some data may be tampered!", Toast.LENGTH_LONG).show()
                }

                val verdictView = TextView(this@ScanPartialShareActivity).apply {
                    text = verdictText
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(this@ScanPartialShareActivity, verdictColor))
                    setPadding(16, 24, 16, 16)
                }
                attributesContainer.addView(verdictView)
                statusText.text = verdictText
                statusText.setTextColor(ContextCompat.getColor(this@ScanPartialShareActivity,
                    if (allValid) android.R.color.holo_green_dark else android.R.color.holo_red_dark))

                // Offer quick navigation to saved scans list
                val viewSavedBtn = com.google.android.material.button.MaterialButton(this@ScanPartialShareActivity).apply {
                    text = "View Saved Scans"
                    setPadding(16, 16, 16, 16)
                    val params = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(16, 16, 16, 16)
                    layoutParams = params
                }
                viewSavedBtn.setOnClickListener {
                    startActivity(android.content.Intent(this@ScanPartialShareActivity,
                        ViewScannedQRSharesActivity::class.java))
                }
                attributesContainer.addView(viewSavedBtn)

            } catch (e: Exception) {
                e.printStackTrace()
                statusText.text = "Verification error: ${e.message}"
                Toast.makeText(this@ScanPartialShareActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = ProgressBar.GONE
            }
        }
    }

    private fun addSectionHeader(title: String) {
        attributesContainer.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@ScanPartialShareActivity, android.R.color.black))
            setPadding(16, 20, 16, 4)
        })
    }

    private fun addMonoText(text: String) {
        attributesContainer.addView(TextView(this).apply {
            this.text = text
            textSize = 11f
            setTypeface(android.graphics.Typeface.MONOSPACE)
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(16, 2, 16, 8)
        })
    }

    private fun addAttributeBlock(
        attrName: String,
        attrValue: String,
        result: com.fyp.blockchainhealthwallet.blockchain.MerkleTreeHelper.AttributeVerificationResult?
    ) {
        val displayName = RecordSchemas.getDisplayName(attrName)
        val isValid = result?.isValid ?: false
        val icon = if (isValid) "[OK]" else "[FAIL]"

        // Attribute card background
        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(if (isValid) 0xFFE8F5E9.toInt() else 0xFFFFEBEE.toInt())
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(16, 8, 16, 4)
            layoutParams = params
        }

        // Field name + value
        card.addView(TextView(this).apply {
            text = "$icon  $displayName"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.BLACK)
        })
        card.addView(TextView(this).apply {
            text = "Value: $attrValue"
            textSize = 13f
            setPadding(0, 4, 0, 8)
            setTextColor(android.graphics.Color.DKGRAY)
        })

        if (result != null) {
            // Proof steps
            for (step in result.steps) {
                card.addView(TextView(this).apply {
                    text = "▸ ${step.stepTitle}"
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.DKGRAY)
                    setPadding(0, 6, 0, 0)
                })
                card.addView(TextView(this).apply {
                    text = "   ${step.detail}"
                    textSize = 11f
                    setTextColor(android.graphics.Color.DKGRAY)
                })
                card.addView(TextView(this).apply {
                    text = "   → ${step.hash}"
                    textSize = 11f
                    setTypeface(android.graphics.Typeface.MONOSPACE)
                    setTextColor(if (step.stepTitle.startsWith("Final"))
                        (if (result.isValid) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
                    else android.graphics.Color.DKGRAY)
                })
            }
        } else {
            card.addView(TextView(this).apply {
                text = "   No proof found for this attribute"
                textSize = 12f
                setTextColor(0xFFC62828.toInt())
            })
        }

        attributesContainer.addView(card)
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    
    /**
     * Scan QR code from selected photo
     */
    private fun scanQRFromImage(uri: android.net.Uri) {
        try {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            
            val image = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val qrData = barcodes[0].rawValue
                        if (!qrData.isNullOrEmpty()) {
                            processQRCode(qrData)
                        } else {
                            runOnUiThread {
                                Toast.makeText(this, "No valid QR code found in photo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "No QR code found in photo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    runOnUiThread {
                        Toast.makeText(this, "Failed to scan photo: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error processing photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // QR Code Analyzer using ML Kit
    private class QRCodeAnalyzer(
        private val onQRCodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {
        
        private val scanner = BarcodeScanning.getClient()
        private var lastProcessedTime = 0L
        private val PROCESS_INTERVAL = 500L // 0.5 seconds between scans
        private var isProcessing = false
        
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessedTime < PROCESS_INTERVAL || isProcessing) {
                imageProxy.close()
                return
            }
            isProcessing = true
            
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            if (barcode.format == Barcode.FORMAT_QR_CODE) {
                                barcode.rawValue?.let { qrData ->
                                    lastProcessedTime = currentTime
                                    onQRCodeDetected(qrData)
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        android.util.Log.e("QRAnalyzer", "Scan failed: ${it.message}")
                    }
                    .addOnCompleteListener {
                        isProcessing = false
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
