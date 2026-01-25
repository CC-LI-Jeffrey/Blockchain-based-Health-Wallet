package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class QRCodeDisplayActivity : AppCompatActivity() {

    private lateinit var ivQRCode: ImageView
    private lateinit var tvShareId: TextView
    private lateinit var tvRecordType: TextView
    private lateinit var tvExpiryInfo: TextView
    private lateinit var btnSaveQRCode: MaterialButton
    private lateinit var btnShareQRCode: MaterialButton
    private lateinit var btnDone: MaterialButton
    
    private var qrBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_display)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        setupToolbar()
        setupViews()
        loadShareInfo()
        generateQRCode()
        setupClickListeners()
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
        ivQRCode = findViewById(R.id.ivQRCode)
        tvShareId = findViewById(R.id.tvShareId)
        tvRecordType = findViewById(R.id.tvRecordType)
        tvExpiryInfo = findViewById(R.id.tvExpiryInfo)
        btnSaveQRCode = findViewById(R.id.btnSaveQRCode)
        btnShareQRCode = findViewById(R.id.btnShareQRCode)
        btnDone = findViewById(R.id.btnDone)
    }

    private fun loadShareInfo() {
        // Check if this is a wallet address QR
        val walletAddress = intent.getStringExtra("WALLET_ADDRESS")
        val title = intent.getStringExtra("TITLE")
        
        if (walletAddress != null) {
            // Wallet address mode
            supportActionBar?.title = title ?: "My Wallet Address"
            tvShareId.text = walletAddress
            tvRecordType.text = "Wallet Address"
            tvExpiryInfo.text = "Scan this QR code to send shares to me"
        } else {
            // Share record mode
            supportActionBar?.title = "Share QR Code"
            val shareId = intent.getStringExtra("SHARE_ID") ?: ""
            val recordType = intent.getStringExtra("RECORD_TYPE") ?: "Health Record"
            val expiryDate = intent.getLongExtra("EXPIRY_DATE", 0L)

            tvShareId.text = shareId
            tvRecordType.text = recordType

            if (expiryDate > 0) {
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                tvExpiryInfo.text = "Expires: ${dateFormat.format(Date(expiryDate))}"
            } else {
                tvExpiryInfo.text = "Expires: Never"
            }
        }
    }

    private fun generateQRCode() {
        // Check if this is a wallet address QR
        val walletAddress = intent.getStringExtra("WALLET_ADDRESS")
        
        val qrContent: String = if (walletAddress != null) {
            // Wallet address mode - create simple JSON with address
            JSONObject().apply {
                put("type", "WALLET_ADDRESS")
                put("address", walletAddress)
                put("timestamp", System.currentTimeMillis())
            }.toString()
        } else {
            // Share record mode - create share JSON
            val shareId = intent.getStringExtra("SHARE_ID") ?: ""
            val recipientAddress = intent.getStringExtra("RECIPIENT_ADDRESS") ?: ""
            val recordType = intent.getStringExtra("RECORD_TYPE") ?: ""
            val expiryDate = intent.getLongExtra("EXPIRY_DATE", 0L)
            
            JSONObject().apply {
                put("type", "HEALTH_WALLET_SHARE")
                put("shareId", shareId)
                put("recipientAddress", recipientAddress)
                put("recordType", recordType)
                put("expiryDate", expiryDate)
                put("timestamp", System.currentTimeMillis())
            }.toString()
        }

        try {
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(
                qrContent,
                BarcodeFormat.QR_CODE,
                512,
                512
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    )
                }
            }

            qrBitmap = bitmap
            ivQRCode.setImageBitmap(bitmap)

        } catch (e: Exception) {
            Toast.makeText(this, "Error generating QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        btnSaveQRCode.setOnClickListener {
            saveQRCodeToGallery()
        }

        btnShareQRCode.setOnClickListener {
            shareQRCode()
        }

        btnDone.setOnClickListener {
            finish()
        }
    }

    private fun saveQRCodeToGallery() {
        val bitmap = qrBitmap
        if (bitmap == null) {
            Toast.makeText(this, "QR code not available", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val shareId = intent.getStringExtra("SHARE_ID") ?: "share"
            val fileName = "HealthWallet_QR_${shareId}_${System.currentTimeMillis()}.png"
            
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val healthWalletDir = File(picturesDir, "HealthWallet")
            if (!healthWalletDir.exists()) {
                healthWalletDir.mkdirs()
            }

            val file = File(healthWalletDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Notify media scanner
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(file)
            sendBroadcast(mediaScanIntent)

            Toast.makeText(this, "QR code saved to Pictures/HealthWallet", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareQRCode() {
        val bitmap = qrBitmap
        if (bitmap == null) {
            Toast.makeText(this, "QR code not available", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val shareId = intent.getStringExtra("SHARE_ID") ?: "share"
            val cachePath = File(cacheDir, "qr_codes")
            cachePath.mkdirs()
            
            val file = File(cachePath, "qr_code_$shareId.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val contentUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Health Wallet Share - ID: $shareId")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share QR Code"))

        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
