package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Set status bar color to match gradient
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
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
            navigateToScreen("Reports")
        }

        findViewById<CardView>(R.id.cardShare).setOnClickListener {
            navigateToScreen("Share")
        }

        findViewById<CardView>(R.id.cardAccessLogs).setOnClickListener {
            navigateToScreen("Access Logs")
        }

        findViewById<CardView>(R.id.cardSettings).setOnClickListener {
            navigateToScreen("Settings")
        }
    }

    private fun navigateToScreen(screenName: String) {
        // Placeholder for navigation - will be implemented when creating actual screens
        Toast.makeText(this, "Navigating to $screenName", Toast.LENGTH_SHORT).show()
        // TODO: Implement actual navigation using Intents or Navigation Component
    }
}