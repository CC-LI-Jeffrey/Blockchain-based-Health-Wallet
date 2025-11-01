package com.fyp.blockchainhealthwallet

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fyp.blockchainhealthwallet.databinding.ActivityShareRecordDetailBinding

class ShareRecordDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareRecordDetailBinding

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
            Toast.makeText(
                this,
                "Revoke Access feature will be implemented",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnExtendAccess.setOnClickListener {
            Toast.makeText(
                this,
                "Extend Access feature will be implemented",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
