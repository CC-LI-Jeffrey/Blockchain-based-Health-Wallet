package com.fyp.blockchainhealthwallet

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        
        // Long-press to force disconnect
        btnConnectWallet.setOnLongClickListener {
            if (WalletManager.isConnected()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Disconnect Wallet?")
                    .setMessage("This will disconnect your wallet from this app.")
                    .setPositiveButton("Disconnect") { _, _ ->
                        WalletManager.disconnectWallet()
                        Toast.makeText(this, "Wallet disconnected", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            true
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
            Log.d("MainActivity", "Opening wallet modal...")
            
            // Check if already connected
            if (WalletManager.isConnected()) {
                Toast.makeText(
                    this, 
                    "Already connected! Click the button again to view details or disconnect.", 
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            
            // 1. Check network connectivity
            if (!isNetworkAvailable()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("⚠️ No Internet Connection")
                    .setMessage("WalletConnect requires an active internet connection. Please check your network and try again.")
                    .setPositiveButton("OK", null)
                    .show()
                Log.e("MainActivity", "No network connectivity")
                return
            }
            
            // 2. Check if AppKit is initialized
            if (!HealthWalletApplication.isAppKitInitialized) {
                Log.w("MainActivity", "AppKit not initialized yet, waiting...")
                
                // Show loading message
                Toast.makeText(
                    this,
                    "Initializing wallet connection...",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Wait for initialization in background
                lifecycleScope.launch {
                    val initialized = withTimeoutOrNull(10000) { // 10 second timeout
                        var attempts = 0
                        while (!HealthWalletApplication.isAppKitInitialized && attempts < 50) {
                            delay(200)
                            attempts++
                        }
                        HealthWalletApplication.isAppKitInitialized
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (initialized == true) {
                            Log.d("MainActivity", "AppKit now initialized, opening modal")
                            openModalNow()
                        } else {
                            val errorMsg = HealthWalletApplication.initializationError 
                                ?: "Initialization timed out"
                            
                            android.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("⚠️ Connection Error")
                                .setMessage(
                                    "Failed to initialize wallet connection.\n\n" +
                                    "Error: $errorMsg\n\n" +
                                    "Please try:\n" +
                                    "1. Check your internet connection\n" +
                                    "2. Restart the app\n" +
                                    "3. Clear app data if problem persists"
                                )
                                .setPositiveButton("Retry") { _, _ ->
                                    openWalletModal()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                            
                            Log.e("MainActivity", "AppKit initialization failed: $errorMsg")
                        }
                    }
                }
                return
            }
            
            // 3. AppKit is ready, open modal
            openModalNow()
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening wallet modal", e)
            Toast.makeText(
                this, 
                "⚠️ Error: ${e.message}\n\nPlease restart the app.", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun openModalNow() {
        try {
            val navHostFragment = supportFragmentManager.findFragmentById(
                R.id.nav_host_fragment
            ) as? NavHostFragment
            
            if (navHostFragment != null) {
                Log.d("MainActivity", "Opening AppKit modal")
                navHostFragment.navController.openAppKit(
                    shouldOpenChooseNetwork = true,
                    onError = { error ->
                        Log.e("MainActivity", "Modal error: ${error.message}")
                        runOnUiThread {
                            android.app.AlertDialog.Builder(this)
                                .setTitle("⚠️ Connection Error")
                                .setMessage(
                                    "Failed to open wallet connection.\n\n" +
                                    "${error.message}\n\n" +
                                    "Please ensure:\n" +
                                    "• You have internet connection\n" +
                                    "• You have a wallet app installed\n" +
                                    "• The wallet app is up to date"
                                )
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                )
            } else {
                Toast.makeText(this, "Navigation not ready. Please restart the app.", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "NavHostFragment is null")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in openModalNow", e)
            Toast.makeText(
                this, 
                "⚠️ Error: ${e.message}", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
                            tvWalletStatus.text = "User"
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

        findViewById<CardView>(R.id.cardMyWalletQR).setOnClickListener {
            showMyWalletQR()
        }

        findViewById<CardView>(R.id.cardSettings).setOnClickListener {
            navigateToSettings()
        }
        
        findViewById<CardView>(R.id.cardScanPartialShare).setOnClickListener {
            openScanPartialShare()
        }

        findViewById<CardView>(R.id.cardAgeVerify).setOnClickListener {
            startActivity(Intent(this, AgeVerifyActivity::class.java))
        }
    }

    private fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showMyWalletQR() {
        val address = WalletManager.getAddress()
        if (address == null) {
            Toast.makeText(this, "Please connect your wallet first", Toast.LENGTH_SHORT).show()
            return
        }

        // Launch QR code display with wallet address
        val intent = Intent(this, QRCodeDisplayActivity::class.java).apply {
            putExtra("WALLET_ADDRESS", address)
            putExtra("TITLE", "My Wallet Address")
        }
        startActivity(intent)
    }
    
    private fun openScanPartialShare() {
        try {
            val intent = Intent(this, Class.forName("com.fyp.blockchainhealthwallet.ui.partialshare.ScanPartialShareActivity"))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening scan partial share", e)
            Toast.makeText(this, "Scan feature not available yet", Toast.LENGTH_SHORT).show()
        }
    }
}