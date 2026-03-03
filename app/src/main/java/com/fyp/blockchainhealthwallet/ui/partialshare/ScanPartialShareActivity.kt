package com.fyp.blockchainhealthwallet.ui.partialshare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
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
import kotlinx.coroutines.launch
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
        
        btnSelectPhoto.setOnClickListener {
            photoPickerLauncher.launch("image/*")
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
    
    private fun verifyScannedData() {
        val sharePackage = scannedPackage ?: return
        
        lifecycleScope.launch {
            try {
                progressBar.visibility = ProgressBar.VISIBLE
                statusText.text = "Verifying Merkle proofs..."
                
                // Convert proofs back to MerkleTreeHelper format
                val proofs = sharePackage.proofs.mapValues { (_, proofData) ->
                    proofData.map { ProofNodeData.toProofNode(it) }
                }
                
                // Verify each attribute
                val results = merkleHelper.verifyProofs(
                    sharePackage.attributes,
                    proofs,
                    sharePackage.merkleRoot
                )
                
                // Display results
                displayVerificationResults(results)
                
            } catch (e: Exception) {
                e.printStackTrace()
                statusText.text = "Verification error: ${e.message}"
                Toast.makeText(this@ScanPartialShareActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = ProgressBar.GONE
            }
        }
    }
    
    private fun displayVerificationResults(results: Map<String, Boolean>) {
        val allValid = results.values.all { it }
        
        if (allValid) {
            statusText.text = "✓ All attributes verified successfully!"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            Toast.makeText(this, "Data verified and authentic!", Toast.LENGTH_LONG).show()
        } else {
            val invalidCount = results.values.count { !it }
            statusText.text = "✗ Verification failed for $invalidCount attribute(s)"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            Toast.makeText(this, "Warning: Some data may be tampered!", Toast.LENGTH_LONG).show()
        }
        
        // Show individual results
        val resultsText = TextView(this).apply {
            text = "\nVerification Details:\n" + results.entries.joinToString("\n") { (attr, valid) ->
                val icon = if (valid) "✓" else "✗"
                val displayName = RecordSchemas.getDisplayName(attr)
                "$icon $displayName"
            }
            textSize = 14f
            setPadding(16, 8, 16, 8)
        }
        attributesContainer.addView(resultsText)
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
        private val PROCESS_INTERVAL = 2000L // 2 seconds between scans
        
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessedTime < PROCESS_INTERVAL) {
                imageProxy.close()
                return
            }
            
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
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
