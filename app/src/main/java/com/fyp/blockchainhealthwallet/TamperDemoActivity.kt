package com.fyp.blockchainhealthwallet

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class TamperDemoActivity : AppCompatActivity() {

    private lateinit var tvBlockchainHash: TextView
    private lateinit var tvDownloadedData: TextView
    private lateinit var tvLocalHash: TextView
    private lateinit var tvVerificationResult: TextView
    private lateinit var btnVerify: Button
    private lateinit var btnSimulateTamper: Button
    private lateinit var btnReset: Button

    // Mock Original Data
    private val originalData = """{
        "id": "REC-9981",
        "diagnosis": "Seasonal Influenza",
        "medication": "Paracetamol 500mg"
    }""".trimIndent()

    // Mock Tampered Data
    private val tamperedData = """{
        "id": "REC-9981",
        "diagnosis": "Seasonal Influenza",
        "medication": "Oxycodone 15mg"
    }""".trimIndent()

    private var currentData = originalData
    private var blockchainAnchorHash = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tamper_demo)
        supportActionBar?.title = "Security Verification Demo"

        tvBlockchainHash = findViewById(R.id.tvBlockchainHash)
        tvDownloadedData = findViewById(R.id.tvDownloadedData)
        tvLocalHash = findViewById(R.id.tvLocalHash)
        tvVerificationResult = findViewById(R.id.tvVerificationResult)
        btnVerify = findViewById(R.id.btnVerify)
        btnSimulateTamper = findViewById(R.id.btnSimulateTamper)
        btnReset = findViewById(R.id.btnReset)

        // Generate the immutable Truth On-Chain hash
        blockchainAnchorHash = generateHash(originalData)
        tvBlockchainHash.text = "0x$blockchainAnchorHash"

        // Initialize UI
        updateUI()

        btnVerify.setOnClickListener {
            verifyDataIntegrity()
        }

        btnSimulateTamper.setOnClickListener {
            // Simulate intercepting the IPFS response and changing the payload
            currentData = tamperedData
            updateUI()
            tvVerificationResult.text = "⚠️ Network payload intercepted & modified."
            tvVerificationResult.setTextColor(Color.parseColor("#E65100")) // Orange
        }

        btnReset.setOnClickListener {
            currentData = originalData
            updateUI()
            tvVerificationResult.text = ""
        }
    }

    private fun updateUI() {
        tvDownloadedData.text = currentData
        val currentHash = generateHash(currentData)
        tvLocalHash.text = "Computed Hash: 0x$currentHash"
    }

    private fun verifyDataIntegrity() {
        val currentHash = generateHash(currentData)
        
        if (currentHash == blockchainAnchorHash) {
            tvVerificationResult.text = "✅ SUCCESS: Data is Authentic\nHash matches Blockchain Anchor."
            tvVerificationResult.setTextColor(Color.parseColor("#2E7D32")) // Green
        } else {
            tvVerificationResult.text = "🚨 TAMPER DETECTED\nLocal hash DOES NOT match On-Chain Anchor."
            tvVerificationResult.setTextColor(Color.parseColor("#C62828")) // Red
        }
    }

    // Standard SHA-256 Hash function simulating your cryptography logic
    private fun generateHash(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}