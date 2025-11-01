package com.fyp.blockchainhealthwallet

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fyp.blockchainhealthwallet.databinding.ActivityAccessLogDetailBinding

class AccessLogDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccessLogDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccessLogDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadAccessLogDetails()
        setupClickListeners()
    }

    private fun loadAccessLogDetails() {
        // Get data from intent
        val accessId = intent.getStringExtra("ACCESS_LOG_ID") ?: ""
        val accessorName = intent.getStringExtra("ACCESSOR_NAME") ?: ""
        val accessorType = intent.getStringExtra("ACCESSOR_TYPE") ?: ""
        val accessedData = intent.getStringExtra("ACCESSED_DATA") ?: ""
        val accessTime = intent.getStringExtra("ACCESS_TIME") ?: ""
        val accessDate = intent.getStringExtra("ACCESS_DATE") ?: ""
        val location = intent.getStringExtra("LOCATION") ?: ""
        val purpose = intent.getStringExtra("PURPOSE") ?: ""
        val status = intent.getStringExtra("STATUS") ?: ""
        val ipAddress = intent.getStringExtra("IP_ADDRESS") ?: ""

        // Set data to views
        binding.tvAccessId.text = accessId
        binding.tvAccessorName.text = accessorName
        binding.tvAccessorType.text = accessorType
        binding.tvAccessedData.text = accessedData
        binding.tvAccessTime.text = accessTime
        binding.tvAccessDate.text = accessDate
        binding.tvLocation.text = location
        binding.tvPurpose.text = purpose
        binding.tvIpAddress.text = ipAddress

        // Set status with color
        binding.tvStatus.text = status
        when (status) {
            "Approved" -> {
                binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.tvStatus.setBackgroundColor(Color.parseColor("#E8F5E9"))
            }
            "Denied" -> {
                binding.tvStatus.setTextColor(Color.parseColor("#F44336"))
                binding.tvStatus.setBackgroundColor(Color.parseColor("#FFEBEE"))
            }
            "Pending" -> {
                binding.tvStatus.setTextColor(Color.parseColor("#FF9800"))
                binding.tvStatus.setBackgroundColor(Color.parseColor("#FFF3E0"))
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnReportIssue.setOnClickListener {
            Toast.makeText(
                this,
                "Report Issue feature will be implemented",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnContactAccessor.setOnClickListener {
            Toast.makeText(
                this,
                "Contact Accessor feature will be implemented",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
