package com.fyp.blockchainhealthwallet

import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.RSAHelper
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalletInfoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WalletInfoActivity"
    }

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvWalletAddress: TextView
    private lateinit var tvChainId: TextView
    private lateinit var tvSessionTopic: TextView
    private lateinit var tvSessionExpiry: TextView
    private lateinit var btnCopyAddress: Button
    private lateinit var btnShowQR: Button
    private lateinit var btnFixRSAKeys: Button
    private lateinit var btnDisconnect: Button
    private lateinit var statusIndicator: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_info)

        initViews()
        setupListeners()
        observeWalletState()
    }

    private fun initViews() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvWalletAddress = findViewById(R.id.tvWalletAddress)
        tvChainId = findViewById(R.id.tvChainId)
        tvSessionTopic = findViewById(R.id.tvSessionTopic)
        tvSessionExpiry = findViewById(R.id.tvSessionExpiry)
        btnCopyAddress = findViewById(R.id.btnCopyAddress)
        btnShowQR = findViewById(R.id.btnShowQR)
        btnFixRSAKeys = findViewById(R.id.btnFixRSAKeys)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        statusIndicator = findViewById(R.id.statusIndicator)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        btnCopyAddress.setOnClickListener {
            val address = WalletManager.getAddress()
            if (address != null) {
                copyToClipboard(address)
                Toast.makeText(this, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        btnShowQR.setOnClickListener {
            showWalletQRCode()
        }

        btnFixRSAKeys.setOnClickListener {
            fixRSAKeys()
        }

        btnDisconnect.setOnClickListener {
            showDisconnectDialog()
        }
    }

    private fun observeWalletState() {
        lifecycleScope.launch {
            WalletManager.connectionState.collect { state ->
                runOnUiThread {
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: WalletManager.WalletConnectionState) {
        when (state) {
            is WalletManager.WalletConnectionState.Connected -> {
                // Update status
                tvConnectionStatus.text = "Connected"
                statusIndicator.setBackgroundResource(android.R.color.holo_green_dark)

                // Update wallet address
                val fullAddress = state.address
                tvWalletAddress.text = fullAddress
                btnCopyAddress.isEnabled = true

                // Update chain ID with warning for wrong network
                val chainId = state.chainId
                val chainName = when (chainId) {
                    "1" -> "Ethereum Mainnet"
                    "5" -> "Goerli Testnet"
                    "11155111" -> "Sepolia Testnet"
                    else -> "Chain ID: $chainId"
                }
                
                // Show warning if not on Sepolia
                if (chainId != "11155111") {
                    tvChainId.text = "⚠️ $chainName ($chainId) - WRONG NETWORK!"
                    tvChainId.setTextColor(getColor(android.R.color.holo_red_dark))
                    
                    // Show alert dialog
                    AlertDialog.Builder(this)
                        .setTitle("⚠️ Wrong Network")
                        .setMessage("You are currently on $chainName.\n\nThis app requires Sepolia Testnet.\n\nPlease switch to Sepolia in your wallet app.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    tvChainId.text = "✅ $chainName ($chainId)"
                    tvChainId.setTextColor(getColor(android.R.color.holo_green_dark))
                }

                // Update session info
                val session = WalletManager.getActiveSession()
                if (session != null) {
                    // Session object doesn't have topic/expiry in the same way
                // Show available session details
                tvSessionTopic.text = "Session: ${session.toString()}"
                tvSessionExpiry.text = "Connected"
            } else {
                tvSessionTopic.text = "No session info available"
                tvSessionExpiry.text = "N/A"
            }
            btnDisconnect.isEnabled = true
        }

            is WalletManager.WalletConnectionState.Connecting -> {
                tvConnectionStatus.text = "Connecting..."
                statusIndicator.setBackgroundResource(android.R.color.holo_orange_dark)
                tvWalletAddress.text = "Connecting..."
                btnCopyAddress.isEnabled = false
                btnDisconnect.isEnabled = false
            }

            is WalletManager.WalletConnectionState.Disconnected -> {
                tvConnectionStatus.text = "Disconnected"
                statusIndicator.setBackgroundResource(android.R.color.darker_gray)
                tvWalletAddress.text = "Not connected"
                tvChainId.text = "No network"
                tvSessionTopic.text = "No session"
                tvSessionExpiry.text = "N/A"
                btnCopyAddress.isEnabled = false
                btnDisconnect.isEnabled = false
            }

            is WalletManager.WalletConnectionState.Error -> {
                tvConnectionStatus.text = "Error: ${state.message}"
                statusIndicator.setBackgroundResource(android.R.color.holo_red_dark)
                tvWalletAddress.text = "Connection error"
                btnCopyAddress.isEnabled = false
                btnDisconnect.isEnabled = false
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Wallet Address", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun showDisconnectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Disconnect Wallet")
            .setMessage("Are you sure you want to disconnect your wallet?")
            .setPositiveButton("Disconnect") { _, _ ->
                WalletManager.disconnectWallet()
                Toast.makeText(this, "Wallet disconnected", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Fix incompatible RSA keys using auto-detection
     * This will check if the existing keys are compatible with current OAEP configuration,
     * and automatically regenerate them if they're incompatible
     */
    private fun fixRSAKeys() {
        // Check if wallet is connected
        if (!WalletManager.isConnected()) {
            Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Verify & Fix RSA Keys")
            .setMessage("This will check your RSA encryption keys and automatically fix them if they're incompatible.\n\nNote: If keys are regenerated, previously shared records will need to be re-shared.")
            .setPositiveButton("Proceed") { _, _ ->
                performRSAKeyFix()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Perform the actual RSA key verification and fix
     */
    @Suppress("DEPRECATION")
    private fun performRSAKeyFix() {
        // Show options: Auto-detect or Force regenerate
        AlertDialog.Builder(this)
            .setTitle("Choose Fix Method")
            .setMessage("Auto-detect: Tests keys and regenerates only if needed\n\nForce Regenerate: Always creates new keys (recommended if still getting errors)")
            .setPositiveButton("Force Regenerate") { _, _ ->
                forceRegenerateKeys()
            }
            .setNegativeButton("Auto-detect") { _, _ ->
                autoDetectKeys()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun autoDetectKeys() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Checking RSA keys...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "Auto-detecting RSA key compatibility")
                Log.d(TAG, "========================================")

                progressDialog.setMessage("Testing key compatibility...")
                
                val publicKey = withContext(Dispatchers.IO) {
                    RSAHelper.ensureKeyPairExists()
                }

                Log.d(TAG, "✅ Keys verified - length: ${publicKey.length}")

                progressDialog.dismiss()

                AlertDialog.Builder(this@WalletInfoActivity)
                    .setTitle("✅ Keys Verified")
                    .setMessage("RSA keys are compatible!\n\nIf you're still getting decryption errors, use 'Force Regenerate' instead.")
                    .setPositiveButton("OK", null)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Auto-detect failed", e)
                progressDialog.dismiss()

                AlertDialog.Builder(this@WalletInfoActivity)
                    .setTitle("Error")
                    .setMessage("Auto-detect failed: ${e.message}\n\nTry 'Force Regenerate' instead.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun forceRegenerateKeys() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Force regenerating RSA keys...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "FORCE REGENERATING RSA KEYS")
                Log.d(TAG, "========================================")

                progressDialog.setMessage("Deleting old keys...")
                
                withContext(Dispatchers.IO) {
                    // Force regenerate by deleting existing keys first
                    RSAHelper.regenerateKeyPair()
                }

                Log.d(TAG, "✅ Keys forcefully regenerated")

                progressDialog.dismiss()

                AlertDialog.Builder(this@WalletInfoActivity)
                    .setTitle("✅ Keys Regenerated")
                    .setMessage("NEW RSA keys have been created!\n\n⚠️ IMPORTANT NEXT STEPS:\n\n" +
                            "1. Go to Profile → Enable Receive\n" +
                            "   (This uploads your NEW public key to blockchain)\n\n" +
                            "2. Old shares are now INVALID\n" +
                            "   (Senders must re-share records with your new key)\n\n" +
                            "3. You can now receive new shares without errors")
                    .setPositiveButton("Go to Profile") { _, _ ->
                        finish()
                    }
                    .setNegativeButton("OK", null)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Force regenerate failed", e)
                progressDialog.dismiss()

                AlertDialog.Builder(this@WalletInfoActivity)
                    .setTitle("Error")
                    .setMessage("Failed to regenerate keys: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showWalletQRCode() {
        val address = WalletManager.getAddress()
        if (address == null) {
            Toast.makeText(this, "Wallet not connected", Toast.LENGTH_SHORT).show()
            return
        }

        // Launch QR code display activity with wallet address
        val intent = Intent(this, QRCodeDisplayActivity::class.java).apply {
            putExtra("WALLET_ADDRESS", address)
            putExtra("TITLE", "My Wallet Address")
        }
        startActivity(intent)
    }
}
