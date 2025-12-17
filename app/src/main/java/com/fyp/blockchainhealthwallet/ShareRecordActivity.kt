package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.databinding.ActivityShareRecordHubBinding
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareRecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareRecordHubBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareRecordHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        loadStatistics()
    }

    override fun onResume() {
        super.onResume()
        loadStatistics()
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

    private fun loadStatistics() {
        val address = WalletManager.getAddress() ?: return

        lifecycleScope.launch {
            try {
                val shareIds = withContext(Dispatchers.IO) {
                    BlockchainService.getShareIds(address)
                }

                var activeCount = 0
                var expiredCount = 0
                var revokedCount = 0

                shareIds.forEach { shareId ->
                    val share = withContext(Dispatchers.IO) {
                        BlockchainService.getShareRecord(shareId)
                    }
                    share?.let {
                        when (it.status) {
                            BlockchainService.ShareStatus.ACTIVE -> activeCount++
                            BlockchainService.ShareStatus.EXPIRED -> expiredCount++
                            BlockchainService.ShareStatus.REVOKED -> revokedCount++
                            else -> {}
                        }
                    }
                }

                binding.tvActiveShares.text = activeCount.toString()
                binding.tvExpiredShares.text = expiredCount.toString()
                binding.tvRevokedShares.text = revokedCount.toString()

            } catch (e: Exception) {
                // Silently fail - statistics optional
            }
        }
    }
}
