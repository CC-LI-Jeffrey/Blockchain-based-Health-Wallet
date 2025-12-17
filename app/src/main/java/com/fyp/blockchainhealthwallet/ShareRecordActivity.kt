package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.databinding.ActivityShareRecordHubBinding

class ShareRecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareRecordHubBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareRecordHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
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
            com.fyp.blockchainhealthwallet.ui.BlockchainHelper.showShareDataDialog(this, lifecycleScope)
        }
    }

    
}
