package com.fyp.blockchainhealthwallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalletInfoActivity : AppCompatActivity() {

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvWalletAddress: TextView
    private lateinit var tvChainId: TextView
    private lateinit var tvSessionTopic: TextView
    private lateinit var tvSessionExpiry: TextView
    private lateinit var btnCopyAddress: Button
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

                // Update chain ID
                val chainId = state.chainId
                val chainName = when (chainId) {
                    "1" -> "Ethereum Mainnet"
                    "5" -> "Goerli Testnet"
                    "11155111" -> "Sepolia Testnet"
                    else -> "Chain ID: $chainId"
                }
                tvChainId.text = "$chainName ($chainId)"

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
}
