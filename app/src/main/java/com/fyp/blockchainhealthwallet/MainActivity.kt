package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.reown.appkit.ui.appKit
import com.reown.appkit.ui.openAppKit
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var btnConnectWallet: Button
    private lateinit var tvWalletStatus: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupNavigation()
        setupStatusBar()
        setupUI()
        setupWalletButton()
        observeWalletState()
        
        // Log wallet state on startup
        Log.d("MainActivity", "App started - Wallet connected: ${WalletManager.isConnected()}")
        Log.d("MainActivity", "Wallet address: ${WalletManager.getAddress()}")
    }
    
    override fun onResume() {
        super.onResume()
        // Log current wallet state
        Log.d("MainActivity", "onResume - Wallet connected: ${WalletManager.isConnected()}")
    }

    private fun setupNavigation() {
        try {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            if (navHostFragment != null) {
                val navController = navHostFragment.navController
                
                // Create navigation graph programmatically with appKit
                navController.graph = navController.createGraph(
                    startDestination = "main"
                ) {
                    fragment<MainFragment>("main")
                    appKit()  // This adds the AppKit modal navigation
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Navigation setup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupWalletButton() {
        btnConnectWallet = findViewById(R.id.btnConnectWallet)
        tvWalletStatus = findViewById(R.id.tvUserName) // Reuse this for wallet status
        
        btnConnectWallet.setOnClickListener {
            if (WalletManager.isConnected()) {
                // Open wallet info activity when connected
                startActivity(Intent(this, WalletInfoActivity::class.java))
            } else {
                // Open wallet connection modal
                openWalletModal()
            }
        }
        
        // Make the status text clickable when connected
        tvWalletStatus.setOnClickListener {
            if (WalletManager.isConnected()) {
                startActivity(Intent(this, WalletInfoActivity::class.java))
            }
        }
        
        updateWalletButton()
    }
    
    private fun openWalletModal() {
        try {
            // Check if already connected
            if (WalletManager.isConnected()) {
                Toast.makeText(
                    this, 
                    "Already connected! Click the button again to view details or disconnect.", 
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            if (navHostFragment != null) {
                navHostFragment.navController.openAppKit(
                    shouldOpenChooseNetwork = true,
                    onError = { error ->
                        Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } else {
                Toast.makeText(this, "Navigation not ready", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening wallet: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun observeWalletState() {
        lifecycleScope.launch {
            WalletManager.connectionState.collect { state ->
                runOnUiThread {
                    updateWalletButton()
                    when (state) {
                        is WalletManager.WalletConnectionState.Connected -> {
                            tvWalletStatus.text = "Connected: ${WalletManager.getFormattedAddress()}"
                            Log.d("MainActivity", "Wallet connected: ${state.address}")
                            Toast.makeText(
                                this@MainActivity,
                                "Wallet connected!\nTap button or address to view details",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is WalletManager.WalletConnectionState.Connecting -> {
                            tvWalletStatus.text = "Connecting..."
                        }
                        is WalletManager.WalletConnectionState.Disconnected -> {
                            tvWalletStatus.text = "Harry Dwa"
                        }
                        is WalletManager.WalletConnectionState.Error -> {
                            tvWalletStatus.text = "Connection Error"
                            Toast.makeText(
                                this@MainActivity,
                                "Error: ${state.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
    
    private fun updateWalletButton() {
        if (WalletManager.isConnected()) {
            btnConnectWallet.text = "View Wallet"
            btnConnectWallet.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
        } else {
            btnConnectWallet.text = "Connect Wallet"
            btnConnectWallet.setBackgroundColor(
                ContextCompat.getColor(this, R.color.primary_dark)
            )
        }
    }

    private fun setupStatusBar() {
        val statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        window.statusBarColor = statusBarColor
    }

    private fun setupUI() {
        findViewById<TextView>(R.id.tvUserName).text = "Harry Dwa"

        findViewById<CardView>(R.id.cardMedication).setOnClickListener {
            startActivity(Intent(this, MedicationActivity::class.java))
        }

        findViewById<CardView>(R.id.cardVaccination).setOnClickListener {
            startActivity(Intent(this, VaccinationRecordActivity::class.java))
        }

        findViewById<CardView>(R.id.cardProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<CardView>(R.id.cardReports).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }

        findViewById<CardView>(R.id.cardShare).setOnClickListener {
            startActivity(Intent(this, ShareRecordActivity::class.java))
        }

        findViewById<CardView>(R.id.cardAccessLogs).setOnClickListener {
            startActivity(Intent(this, AccessLogActivity::class.java))
        }

        findViewById<CardView>(R.id.cardSettings).setOnClickListener {
            navigateToSettings()
        }
    }

    private fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}