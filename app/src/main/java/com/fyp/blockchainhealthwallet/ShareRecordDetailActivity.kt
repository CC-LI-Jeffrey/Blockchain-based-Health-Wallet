package com.fyp.blockchainhealthwallet

import android.app.ProgressDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.databinding.ActivityShareRecordDetailBinding
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

class ShareRecordDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShareRecordDetail"
    }

    private lateinit var binding: ActivityShareRecordDetailBinding
    private var shareIdBigInt: BigInteger? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareRecordDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadShareDetails()
        setupClickListeners()
    }

    private fun loadShareDetails() {
        // Get data from intent
        val shareId = intent.getStringExtra("SHARE_ID") ?: ""
        
        // Try to parse the share ID as BigInteger for blockchain operations
        // Remove "SH" prefix if present (e.g., "SH1" -> "1")
        try {
            val numericId = shareId.removePrefix("SH")
            shareIdBigInt = BigInteger(numericId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse share ID: $shareId", e)
        }
        
        val recipientName = intent.getStringExtra("RECIPIENT_NAME") ?: ""
        val recipientType = intent.getStringExtra("RECIPIENT_TYPE") ?: ""
        val sharedData = intent.getStringExtra("SHARED_DATA") ?: ""
        val shareDate = intent.getStringExtra("SHARE_DATE") ?: ""
        val shareTime = intent.getStringExtra("SHARE_TIME") ?: ""
        val expiryDate = intent.getStringExtra("EXPIRY_DATE") ?: ""
        val accessLevel = intent.getStringExtra("ACCESS_LEVEL") ?: ""
        val status = intent.getStringExtra("STATUS") ?: ""
        val recipientEmail = intent.getStringExtra("RECIPIENT_EMAIL") ?: ""

        // Set data to views
        binding.tvShareId.text = shareId
        binding.tvRecipientName.text = recipientName
        binding.tvRecipientType.text = recipientType
        binding.tvSharedData.text = sharedData
        binding.tvShareDate.text = shareDate
        binding.tvShareTime.text = shareTime
        binding.tvExpiryDate.text = expiryDate
        binding.tvAccessLevel.text = accessLevel
        binding.tvRecipientEmail.text = recipientEmail

        // Set status with color
        binding.tvStatus.text = status
        when (status) {
            "Active" -> {
                binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.tvStatus.setBackgroundColor(Color.parseColor("#E8F5E9"))
                binding.btnRevokeAccess.isEnabled = true
                binding.btnExtendAccess.isEnabled = true
            }
            "Expired" -> {
                binding.tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                binding.tvStatus.setBackgroundColor(Color.parseColor("#F5F5F5"))
                binding.btnRevokeAccess.isEnabled = false
                binding.btnExtendAccess.isEnabled = true
            }
            "Revoked" -> {
                binding.tvStatus.setTextColor(Color.parseColor("#F44336"))
                binding.tvStatus.setBackgroundColor(Color.parseColor("#FFEBEE"))
                binding.btnRevokeAccess.isEnabled = false
                binding.btnExtendAccess.isEnabled = false
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnRevokeAccess.setOnClickListener {
            showRevokeConfirmationDialog()
        }

        binding.btnExtendAccess.setOnClickListener {
            Toast.makeText(
                this,
                "Extend Access feature will be implemented",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRevokeConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Revoke Access")
            .setMessage("Are you sure you want to revoke access for this recipient? This action cannot be undone.")
            .setPositiveButton("Revoke") { _, _ ->
                revokeAccess()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun revokeAccess() {
        val shareId = shareIdBigInt
        if (shareId == null) {
            Toast.makeText(this, "Invalid share ID", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Revoking access...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val txHash = withContext(Dispatchers.IO) {
                    BlockchainService.revokeShare(shareId)
                }

                progressDialog.dismiss()

                Log.d(TAG, "Access revoked successfully. TX: $txHash")
                
                Toast.makeText(
                    this@ShareRecordDetailActivity,
                    "Access revoked successfully",
                    Toast.LENGTH_SHORT
                ).show()

                // Update UI to show revoked status
                binding.tvStatus.text = "Revoked"
                binding.tvStatus.setTextColor(Color.parseColor("#F44336"))
                binding.tvStatus.setBackgroundColor(Color.parseColor("#FFEBEE"))
                binding.btnRevokeAccess.isEnabled = false
                binding.btnExtendAccess.isEnabled = false

                // Finish activity after a short delay
                binding.root.postDelayed({
                    setResult(RESULT_OK) // Signal that data changed
                    finish()
                }, 1500)
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error revoking access", e)
                Toast.makeText(
                    this@ShareRecordDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
