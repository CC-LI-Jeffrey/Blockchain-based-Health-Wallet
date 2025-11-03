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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    
    private lateinit var switchNotifications: Switch
    private lateinit var switchBiometric: Switch
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("HealthWalletPrefs", MODE_PRIVATE)
        
        // Setup biometric authentication
        setupBiometricAuthentication()
        
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
        
        // Change Password
        findViewById<CardView>(R.id.cardChangePassword).setOnClickListener {
            showChangePasswordDialog()
        }
        
        // Biometric Login switch
        switchBiometric = findViewById(R.id.switchBiometric)
        switchBiometric.isChecked = sharedPreferences.getBoolean("biometric_enabled", false)
        
        // Check if biometric is available
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Biometric is available
                findViewById<CardView>(R.id.cardBiometric).setOnClickListener {
                    switchBiometric.isChecked = !switchBiometric.isChecked
                    handleBiometricToggle(switchBiometric.isChecked)
                }
                
                switchBiometric.setOnCheckedChangeListener { _, isChecked ->
                    handleBiometricToggle(isChecked)
                }
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                switchBiometric.isEnabled = false
                Toast.makeText(this, "Device doesn't support biometric authentication", Toast.LENGTH_SHORT).show()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                switchBiometric.isEnabled = false
                Toast.makeText(this, "Biometric authentication is currently unavailable", Toast.LENGTH_SHORT).show()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                switchBiometric.isEnabled = false
                Toast.makeText(this, "Please enroll biometric authentication in device settings", Toast.LENGTH_SHORT).show()
            }
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
    
    private fun setupBiometricAuthentication() {
        executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                    switchBiometric.isChecked = false
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    saveBiometricPreference(true)
                    Toast.makeText(applicationContext, "Biometric login enabled", Toast.LENGTH_SHORT).show()
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })
        
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable Biometric Login")
            .setSubtitle("Authenticate to enable biometric login")
            .setNegativeButtonText("Cancel")
            .build()
    }
    
    private fun handleBiometricToggle(isEnabled: Boolean) {
        if (isEnabled) {
            // Authenticate before enabling
            biometricPrompt.authenticate(promptInfo)
        } else {
            // Disable biometric
            saveBiometricPreference(false)
            Toast.makeText(this, "Biometric login disabled", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveNotificationPreference(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notifications_enabled", enabled).apply()
        val message = if (enabled) "Notifications enabled" else "Notifications disabled"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun saveBiometricPreference(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("biometric_enabled", enabled).apply()
    }
    
    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        
        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Change") { dialog, _ ->
                val currentPassword = dialogView.findViewById<android.widget.EditText>(R.id.etCurrentPassword).text.toString()
                val newPassword = dialogView.findViewById<android.widget.EditText>(R.id.etNewPassword).text.toString()
                val confirmPassword = dialogView.findViewById<android.widget.EditText>(R.id.etConfirmPassword).text.toString()
                
                if (validatePasswordChange(currentPassword, newPassword, confirmPassword)) {
                    // TODO: Implement actual password change logic with blockchain/backend
                    Toast.makeText(this, "Password changed successfully", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    
    private fun validatePasswordChange(current: String, new: String, confirm: String): Boolean {
        when {
            current.isEmpty() || new.isEmpty() || confirm.isEmpty() -> {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return false
            }
            new.length < 6 -> {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            new != confirm -> {
                Toast.makeText(this, "New passwords don't match", Toast.LENGTH_SHORT).show()
                return false
            }
            // TODO: Verify current password with stored password
            else -> return true
        }
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
