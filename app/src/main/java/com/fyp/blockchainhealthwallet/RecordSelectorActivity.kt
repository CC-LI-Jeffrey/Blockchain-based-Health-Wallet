package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.databinding.ActivityRecordSelectorBinding
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity to select which record to share
 * Step 1 of the sharing process
 */
class RecordSelectorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordSelectorBinding
    private var selectedCategory = RecordCategory.PERSONAL_INFO

    enum class RecordCategory {
        PERSONAL_INFO,
        MEDICATIONS,
        VACCINATIONS,
        REPORTS
    }

    data class SelectableRecord(
        val id: String,
        val title: String,
        val subtitle: String,
        val category: RecordCategory
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupClickListeners()
        loadRecords(selectedCategory)
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Personal Info"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Medications"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Vaccinations"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Reports"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedCategory = when (tab?.position) {
                    0 -> RecordCategory.PERSONAL_INFO
                    1 -> RecordCategory.MEDICATIONS
                    2 -> RecordCategory.VACCINATIONS
                    3 -> RecordCategory.REPORTS
                    else -> RecordCategory.PERSONAL_INFO
                }
                loadRecords(selectedCategory)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadRecords(category: RecordCategory) {
        val address = WalletManager.getAddress()
        if (address == null) {
            Toast.makeText(this, "Wallet not connected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.emptyState.visibility = View.GONE

            try {
                val records = when (category) {
                    RecordCategory.PERSONAL_INFO -> loadPersonalInfo(address)
                    RecordCategory.MEDICATIONS -> loadMedications(address)
                    RecordCategory.VACCINATIONS -> loadVaccinations(address)
                    RecordCategory.REPORTS -> loadReports(address)
                }

                binding.progressBar.visibility = View.GONE
                if (records.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                } else {
                    binding.recyclerView.visibility = View.VISIBLE
                    setupRecyclerView(records)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                Toast.makeText(this@RecordSelectorActivity, "Error loading records", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadPersonalInfo(address: String): List<SelectableRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val hasInfo = BlockchainService.hasPersonalInfo(address)
                if (hasInfo) {
                    listOf(
                        SelectableRecord(
                            id = "0",
                            title = "Personal Information",
                            subtitle = "Name, DOB, Blood Type, etc.",
                            category = RecordCategory.PERSONAL_INFO
                        )
                    )
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun loadMedications(address: String): List<SelectableRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val ids = BlockchainService.getMedicationIds(address)
                ids.mapNotNull { id ->
                    try {
                        val med = BlockchainService.getMedicationRef(id)
                        med?.let {
                            SelectableRecord(
                                id = id.toString(),
                                title = "Medication #$id",
                                subtitle = if (it.isActive) "Active medication" else "Inactive",
                                category = RecordCategory.MEDICATIONS
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun loadVaccinations(address: String): List<SelectableRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val ids = BlockchainService.getVaccinationIds(address)
                ids.mapNotNull { id ->
                    try {
                        SelectableRecord(
                            id = id.toString(),
                            title = "Vaccination #$id",
                            subtitle = "Vaccination record",
                            category = RecordCategory.VACCINATIONS
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun loadReports(address: String): List<SelectableRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val ids = BlockchainService.getReportIds(address)
                ids.mapNotNull { id ->
                    try {
                        val report = BlockchainService.getReportRef(id)
                        report?.let {
                            SelectableRecord(
                                id = id.toString(),
                                title = "Report #$id",
                                subtitle = it.reportType.name.replace("_", " "),
                                category = RecordCategory.REPORTS
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun setupRecyclerView(records: List<SelectableRecord>) {
        val adapter = RecordSelectorAdapter(records) { record ->
            // Navigate to share form with selected record
            val intent = Intent(this, ShareFormActivity::class.java)
            intent.putExtra("RECORD_ID", record.id)
            intent.putExtra("RECORD_CATEGORY", record.category.name)
            intent.putExtra("RECORD_TITLE", record.title)
            startActivity(intent)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
}
