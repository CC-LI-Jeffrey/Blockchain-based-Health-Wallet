package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupStatusBar()
        setupUI()
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