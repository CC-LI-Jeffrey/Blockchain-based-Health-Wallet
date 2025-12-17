package com.fyp.blockchainhealthwallet

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fyp.blockchainhealthwallet.adapter.ShareRecordAdapter
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.databinding.ActivitySharedRecordsBinding
import com.fyp.blockchainhealthwallet.model.ShareRecord
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*

class SharedRecordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySharedRecordsBinding
    private lateinit var adapter: ShareRecordAdapter
    private val allShareRecords = mutableListOf<ShareRecord>()
    private val filteredShareRecords = mutableListOf<ShareRecord>()
    private var selectedCategory: BlockchainService.DataCategory? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySharedRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupCategoryFilters()
        setupClickListeners()
        loadSharedRecords()
    }

    private fun setupRecyclerView() {
        adapter = ShareRecordAdapter(filteredShareRecords) { shareRecord ->
            // Navigate to detail activity
            val intent = android.content.Intent(this, ShareRecordDetailActivity::class.java)
            intent.putExtra("SHARE_ID", shareRecord.id)
            intent.putExtra("RECIPIENT_NAME", shareRecord.recipientName)
            intent.putExtra("RECIPIENT_TYPE", shareRecord.recipientType)
            intent.putExtra("SHARED_DATA", shareRecord.sharedData)
            intent.putExtra("SHARE_DATE", shareRecord.shareDate)
            intent.putExtra("SHARE_TIME", shareRecord.shareTime)
            intent.putExtra("EXPIRY_DATE", shareRecord.expiryDate)
            intent.putExtra("ACCESS_LEVEL", shareRecord.accessLevel)
            intent.putExtra("STATUS", shareRecord.status)
            intent.putExtra("RECIPIENT_EMAIL", shareRecord.recipientEmail)
            startActivity(intent)
        }

        binding.recyclerViewShared.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewShared.adapter = adapter
    }

    private fun setupCategoryFilters() {
        binding.chipAll.setOnClickListener { filterByCategory(null) }
        binding.chipPersonalInfo.setOnClickListener { filterByCategory(BlockchainService.DataCategory.PERSONAL_INFO) }
        binding.chipMedications.setOnClickListener { filterByCategory(BlockchainService.DataCategory.MEDICATION_RECORDS) }
        binding.chipVaccinations.setOnClickListener { filterByCategory(BlockchainService.DataCategory.VACCINATION_RECORDS) }
        binding.chipReports.setOnClickListener { filterByCategory(BlockchainService.DataCategory.MEDICAL_REPORTS) }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadSharedRecords() {
        val address = WalletManager.getAddress()
        if (address == null) {
            showEmptyState()
            return
        }

        lifecycleScope.launch {
            try {
                val shareIds = withContext(Dispatchers.IO) {
                    BlockchainService.getShareIds(address)
                }

                allShareRecords.clear()

                shareIds.forEach { shareId ->
                    val share = withContext(Dispatchers.IO) {
                        BlockchainService.getShareRecord(shareId)
                    }

                    share?.let {
                        val shareRecord = convertToShareRecord(it, shareId)
                        allShareRecords.add(shareRecord)
                    }
                }

                filterByCategory(selectedCategory)

            } catch (e: Exception) {
                showEmptyState()
            }
        }
    }

    private fun filterByCategory(category: BlockchainService.DataCategory?) {
        selectedCategory = category
        filteredShareRecords.clear()

        if (category == null) {
            filteredShareRecords.addAll(allShareRecords)
        } else {
            filteredShareRecords.addAll(allShareRecords.filter {
                it.sharedData.contains(category.name, ignoreCase = true)
            })
        }

        adapter.notifyDataSetChanged()

        if (filteredShareRecords.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
        }
    }

    private fun convertToShareRecord(
        blockchainShare: BlockchainService.ShareRecord,
        shareId: BigInteger
    ): ShareRecord {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val shareDate = Date(blockchainShare.shareDate.toLong() * 1000)
        val expiryDate = Date(blockchainShare.expiryDate.toLong() * 1000)

        return ShareRecord(
            id = shareId.toString(),
            recipientName = blockchainShare.recipientAddress.take(10) + "...",
            recipientType = blockchainShare.recipientType.name,
            sharedData = blockchainShare.sharedDataCategory.name,
            shareDate = dateFormat.format(shareDate),
            shareTime = timeFormat.format(shareDate),
            expiryDate = dateFormat.format(expiryDate),
            accessLevel = blockchainShare.accessLevel.name,
            status = blockchainShare.status.name,
            recipientEmail = blockchainShare.recipientAddress
        )
    }

    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
        binding.recyclerViewShared.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyState.visibility = View.GONE
        binding.recyclerViewShared.visibility = View.VISIBLE
    }
}
