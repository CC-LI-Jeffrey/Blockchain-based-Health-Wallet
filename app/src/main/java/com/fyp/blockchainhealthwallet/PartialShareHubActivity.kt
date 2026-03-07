package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fyp.blockchainhealthwallet.databinding.ActivityPartialShareHubBinding

class PartialShareHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPartialShareHubBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPartialShareHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.cardViewReceivedPartialShares.setOnClickListener {
            startActivity(Intent(this, com.fyp.blockchainhealthwallet.ui.partialshare.ViewSharedWithMeActivity::class.java))
        }

        binding.cardViewScannedQRShares.setOnClickListener {
            startActivity(Intent(this, com.fyp.blockchainhealthwallet.ui.partialshare.ViewScannedQRSharesActivity::class.java))
        }

        binding.cardViewSentPartialShares.setOnClickListener {
            startActivity(Intent(this, com.fyp.blockchainhealthwallet.ui.partialshare.ViewSentPartialSharesActivity::class.java))
        }

        binding.cardPartialShare.setOnClickListener {
            startActivity(Intent(this, com.fyp.blockchainhealthwallet.ui.partialshare.PartialShareSelectorActivity::class.java))
        }
    }
}
