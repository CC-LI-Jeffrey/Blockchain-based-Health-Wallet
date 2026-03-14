package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    
    private lateinit var switchNotifications: Switch
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("HealthWalletPrefs", MODE_PRIVATE)
        
        // Initialize UI components
        setupUI()
    }
    
    private fun setupUI() {
        // Notifications switch
        switchNotifications = findViewById(R.id.switchNotifications)
        switchNotifications.isChecked = sharedPreferences.getBoolean("notifications_enabled", true)
        
        findViewById<CardView>(R.id.cardNotifications).setOnClickListener {
            switchNotifications.isChecked = !switchNotifications.isChecked
            saveNotificationPreference(switchNotifications.isChecked)
        }
        
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationPreference(isChecked)
        }
        
        // Privacy Policy
        findViewById<CardView>(R.id.cardPrivacyPolicy).setOnClickListener {
            showPrivacyPolicyDialog()
        }
        
        // Logout button
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }
    
    private fun saveNotificationPreference(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notifications_enabled", enabled).apply()
        val message = if (enabled) "Notifications enabled" else "Notifications disabled"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showPrivacyPolicyDialog() {
        val privacyPolicyText = """
            Privacy Policy
            
            Last Updated: November 1, 2025
            
            1. Data Collection
            We collect and store your health records securely on the blockchain. This includes medication records, vaccination records, medical reports, and profile information.
            
            2. Data Usage
            Your health data is used solely for providing you with a secure health wallet service. We do not share your personal health information with third parties without your explicit consent.
            
            3. Data Security
            All your health records are encrypted and stored on a blockchain network, ensuring immutability and security. We implement industry-standard security measures to protect your data.
            
            4. Access Control
            You have complete control over who can access your health records. All access to your data is logged and can be reviewed in the Access Logs section.
            
            5. Data Sharing
            You can share your health records with healthcare providers using secure sharing features. All sharing activities are recorded and require your authorization.
            
            6. User Rights
            You have the right to:
            - Access your data at any time
            - Request data deletion (subject to legal requirements)
            - Control who can view your records
            - Export your health data
            
            7. Contact
            For privacy concerns or questions, please contact us at privacy@blockchainhealthwallet.com
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("Privacy Policy")
            .setMessage(privacyPolicyText)
            .setPositiveButton("Accept") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    
    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    
    private fun performLogout() {
        // Clear user session data
        sharedPreferences.edit().clear().apply()
        
        // TODO: Clear any cached data and blockchain connections
        
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        
        // Navigate back to login screen (for now, just finish the activity)
        // TODO: Navigate to login activity when implemented
        finishAffinity() // Close all activities
    }
}
