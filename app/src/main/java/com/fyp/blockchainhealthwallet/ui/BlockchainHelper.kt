package com.fyp.blockchainhealthwallet.ui

import android.app.ProgressDialog
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

/**
 * Helper class for blockchain-related UI operations.
 * Provides dialogs and workflows for granting/revoking access.
 */
object BlockchainHelper {
    
    /**
     * Show dialog to grant access to a provider.
     * User enters provider address and selects records to share.
     */
    fun showGrantAccessDialog(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        recordIds: List<BigInteger>
    ) {
        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(android.R.layout.select_dialog_item, null)
        
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Grant Access to Provider")
        
        // Create input fields
        val providerAddressInput = EditText(context).apply {
            hint = "Provider Wallet Address (0x...)"
            setPadding(50, 40, 50, 40)
        }
        
        val durationInput = EditText(context).apply {
            hint = "Duration in Days (e.g., 30)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(50, 40, 50, 40)
        }
        
        // Create container layout
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
            addView(providerAddressInput)
            addView(durationInput)
        }
        
        builder.setView(container)
        builder.setPositiveButton("Grant Access") { _, _ ->
            val providerAddress = providerAddressInput.text.toString().trim()
            val durationText = durationInput.text.toString().trim()
            
            if (providerAddress.isEmpty() || durationText.isEmpty()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            val duration = durationText.toBigIntegerOrNull() ?: BigInteger.ZERO
            if (duration <= BigInteger.ZERO) {
                Toast.makeText(context, "Invalid duration", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            grantAccess(context, lifecycleScope, providerAddress, recordIds, duration)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    /**
     * Grant access to provider on blockchain.
     */
    private fun grantAccess(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        providerAddress: String,
        recordIds: List<BigInteger>,
        durationInDays: BigInteger
    ) {
        var progressDialog: ProgressDialog? = null
        
        lifecycleScope.launch {
            try {
                progressDialog = ProgressDialog(context).apply {
                    setMessage("Waiting for wallet signature...")
                    setCancelable(false)
                    show()
                }
                
                // User signs transaction with their wallet
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.grantAccess(
                        granteeAddress = providerAddress,
                        recordIds = recordIds,
                        durationInDays = durationInDays
                    )
                }
                
                progressDialog?.dismiss()
                
                // Show success
                AlertDialog.Builder(context)
                    .setTitle("Access Granted!")
                    .setMessage("Provider can now access your records for $durationInDays days.\n\nTransaction: ${txHash.take(10)}...")
                    .setPositiveButton("OK", null)
                    .show()
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                
                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true -> 
                        "Transaction cancelled by user"
                    e.message?.contains("insufficient funds", ignoreCase = true) == true -> 
                        "Insufficient funds for gas fees"
                    else -> "Error: ${e.message}"
                }
                
                AlertDialog.Builder(context)
                    .setTitle("Transaction Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    /**
     * Show dialog to revoke access from a provider.
     */
    fun showRevokeAccessDialog(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope
    ) {
        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(android.R.layout.select_dialog_item, null)
        
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Revoke Access")
        
        val providerAddressInput = EditText(context).apply {
            hint = "Provider Wallet Address (0x...)"
            setPadding(50, 40, 50, 40)
        }
        
        builder.setView(providerAddressInput)
        builder.setPositiveButton("Revoke Access") { _, _ ->
            val providerAddress = providerAddressInput.text.toString().trim()
            
            if (providerAddress.isEmpty()) {
                Toast.makeText(context, "Please enter provider address", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            revokeAccess(context, lifecycleScope, providerAddress)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    /**
     * Revoke access from provider on blockchain.
     */
    private fun revokeAccess(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        providerAddress: String
    ) {
        var progressDialog: ProgressDialog? = null
        
        lifecycleScope.launch {
            try {
                progressDialog = ProgressDialog(context).apply {
                    setMessage("Revoking access...")
                    setCancelable(false)
                    show()
                }
                
                // User signs transaction with their wallet
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.revokeAccess(granteeAddress = providerAddress)
                }
                
                progressDialog?.dismiss()
                
                // Show success
                AlertDialog.Builder(context)
                    .setTitle("Access Revoked!")
                    .setMessage("Provider no longer has access to your records.\n\nTransaction: ${txHash.take(10)}...")
                    .setPositiveButton("OK", null)
                    .show()
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                
                val errorMessage = when {
                    e.message?.contains("user rejected", ignoreCase = true) == true -> 
                        "Transaction cancelled by user"
                    else -> "Error: ${e.message}"
                }
                
                AlertDialog.Builder(context)
                    .setTitle("Transaction Failed")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    /**
     * Check wallet connection and show dialog if not connected.
     * @return true if connected, false otherwise
     */
    fun checkWalletConnection(context: Context): Boolean {
        if (!BlockchainService.isWalletConnected()) {
            AlertDialog.Builder(context)
                .setTitle("Wallet Not Connected")
                .setMessage("Please connect your wallet first to use blockchain features.")
                .setPositiveButton("OK", null)
                .show()
            return false
        }
        return true
    }
}
