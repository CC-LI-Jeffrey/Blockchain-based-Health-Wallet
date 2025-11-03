package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Set status bar color using modern API
        setupStatusBar()

        setupUI()
    }

    private fun setupStatusBar() {
        val statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        window.statusBarColor = statusBarColor
    }

    private fun setupUI() {
        // Set user name (in future, this will be fetched from blockchain/database)
        findViewById<TextView>(R.id.tvUserName).text = "Harry Dwa"

        // Setup click listeners for all cards
        findViewById<CardView>(R.id.cardMedication).setOnClickListener {
            startActivity(android.content.Intent(this, MedicationActivity::class.java))
        }

        findViewById<CardView>(R.id.cardVaccination).setOnClickListener {
            val intent = Intent(this, VaccinationRecordActivity::class.java)
            startActivity(intent)
        }

        findViewById<CardView>(R.id.cardProfile).setOnClickListener {
            startActivity(android.content.Intent(this, ProfileActivity::class.java))
        }

        findViewById<CardView>(R.id.cardReports).setOnClickListener {
            val intent = Intent(this, ReportsActivity::class.java)
            startActivity(intent)
        }

        findViewById<CardView>(R.id.cardShare).setOnClickListener {
            val intent = Intent(this, ShareRecordActivity::class.java)
            startActivity(intent)
        }

        findViewById<CardView>(R.id.cardAccessLogs).setOnClickListener {
            val intent = Intent(this, AccessLogActivity::class.java)
            startActivity(intent)
        }

        findViewById<CardView>(R.id.cardSettings).setOnClickListener {
            navigateToSettings()
        }
    }

    private fun navigateToSettings() {
        val intent = android.content.Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToScreen(screenName: String) {
        // Placeholder for navigation - will be implemented when creating actual screens
        Toast.makeText(this, "Navigating to $screenName", Toast.LENGTH_SHORT).show()
        // TODO: Implement actual navigation using Intents or Navigation Component
    }
}