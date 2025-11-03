package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import com.reown.appkit.ui.appKit
import com.reown.appkit.ui.openAppKit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupNavigation()
        setupStatusBar()
        setupUI()
        setupWalletButton()
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
                    appKit()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Navigation setup error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupWalletButton() {
        findViewById<Button>(R.id.btnConnectWallet)?.setOnClickListener {
            try {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                if (navHostFragment != null) {
                    navHostFragment.navController.openAppKit(
                        shouldOpenChooseNetwork = true,
                        onError = { error ->
                            Toast.makeText(this, "Error opening wallet: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Toast.makeText(this, "Navigation not initialized", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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