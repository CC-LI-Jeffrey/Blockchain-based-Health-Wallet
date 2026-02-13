package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fyp.blockchainhealthwallet.databinding.ActivityFullShareHubBinding

class FullShareHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullShareHubBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullShareHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.cardViewSharedRecords.setOnClickListener {
            startActivity(Intent(this, SharedRecordsActivity::class.java))
        }

        binding.cardViewReceivedRecords.setOnClickListener {
            startActivity(Intent(this, ReceivedRecordsActivity::class.java))
        }

        binding.cardShareNew.setOnClickListener {
            startActivity(Intent(this, RecordSelectorActivity::class.java))
        }
    }
}
