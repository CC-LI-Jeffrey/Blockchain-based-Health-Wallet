package com.fyp.blockchainhealthwallet.ui.partialshare

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fyp.blockchainhealthwallet.R
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Simple QR Scanner Activity for scanning wallet addresses
 * Returns scanned address back to calling activity
 */
class AddressQRScannerActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var btnSelectPhoto: com.google.android.material.button.MaterialButton
    private lateinit var cameraExecutor: ExecutorService
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CODE_PHOTO = 20
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address_qr_scanner)
        
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
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        btnSelectPhoto.setOnClickListener {
            openPhotoPicker()
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
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient()
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null) {
                            val address = extractWalletAddress(rawValue)
                            if (address != null) {
                                returnAddress(address)
                                return@addOnSuccessListener
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
    
    /**
     * Extract wallet address from QR code content.
     * Supports:
     *   - Plain Ethereum address (0x...)
     *   - JSON {"type":"WALLET_ADDRESS","address":"0x..."}
     *   - JSON {"type":"VACCINE_PASSPORT","address":"0x...","vaccineCode":1,"vaccineName":"COVID-19",...}
     *
     * For VACCINE_PASSPORT QR codes, also stores vaccineCode + vaccineName in [pendingVaccineCode]
     * and [pendingVaccineName] so they can be returned alongside the address.
     */
    private var pendingVaccineCode: Int = -1
    private var pendingVaccineName: String = ""

    private fun extractWalletAddress(rawValue: String): String? {
        // Try plain Ethereum address
        if (rawValue.startsWith("0x") && rawValue.length == 42) {
            return rawValue
        }

        // Try JSON format
        try {
            val json = org.json.JSONObject(rawValue)
            val type = json.optString("type")
            val address = json.optString("address")
            if (address.startsWith("0x") && address.length == 42) {
                when (type) {
                    "WALLET_ADDRESS" -> return address
                    "VACCINE_PASSPORT" -> {
                        pendingVaccineCode = json.optInt("vaccineCode", -1)
                        pendingVaccineName = json.optString("vaccineName", "")
                        return address
                    }
                    "AGE_PASSPORT" -> return address
                    else -> if (type.isEmpty()) return address // bare JSON with address field
                }
            }
        } catch (e: Exception) {
            // Not JSON, ignore
        }

        return null
    }
    
    private fun openPhotoPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_PHOTO)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PHOTO && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                scanQRFromImage(uri)
            }
        }
    }
    
    private fun scanQRFromImage(uri: Uri) {
        try {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            
            val image = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val rawValue = barcodes[0].rawValue
                        if (!rawValue.isNullOrEmpty()) {
                            val address = extractWalletAddress(rawValue)
                            if (address != null) {
                                returnAddress(address)
                            } else {
                                Toast.makeText(this, "No valid wallet address found", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "No valid address found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "No QR code found in photo", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun returnAddress(address: String) {
        val resultIntent = Intent()
        resultIntent.putExtra("ADDRESS", address)
        resultIntent.putExtra("SCANNED_ADDRESS", address)   // compat alias
        if (pendingVaccineCode > 0) {
            resultIntent.putExtra("VACCINE_CODE", pendingVaccineCode)
            resultIntent.putExtra("VACCINE_NAME", pendingVaccineName)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
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
}
