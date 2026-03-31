package com.fyp.blockchainhealthwallet.ui.partialshare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.adapter.RecordItemAdapter
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.databinding.ActivityPartialShareSelectorBinding
import com.fyp.blockchainhealthwallet.model.RecordItem
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Activity to select which record to partially share
 * Similar to RecordSelectorActivity but navigates to PartialShareActivity
 */
class PartialShareSelectorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPartialShareSelectorBinding
    private lateinit var adapter: RecordItemAdapter
    private var selectedCategory = RecordCategory.PERSONAL_INFO

    enum class RecordCategory {
        PERSONAL_INFO,
        MEDICATIONS,
        VACCINATIONS,
        REPORTS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPartialShareSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        loadRecords(selectedCategory)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
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
                    setupRecyclerView(records, category)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                Toast.makeText(this@PartialShareSelectorActivity, "Error loading records", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView(records: List<RecordItem>, category: RecordCategory) {
        adapter = RecordItemAdapter(records) { record ->
            openPartialShareActivity(record, category)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun openPartialShareActivity(record: RecordItem, category: RecordCategory) {
        val intent = Intent(this, PartialShareActivity::class.java)
        intent.putExtra("RECORD_ID", record.id)
        intent.putExtra("RECORD_CATEGORY", category.name)
        intent.putExtra("RECORD_TITLE", record.title)
        startActivity(intent)
    }

    /**
     * Generate unique recordId for personal info based on user address
     * This prevents collisions when multiple users use the same contract
     */
    private fun generatePersonalInfoRecordId(userAddress: String): String {
        // Use hash of "PERSONAL_INFO" + address to generate unique recordId
        val data = "PERSONAL_INFO:$userAddress"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        val bigInt = BigInteger(1, hashBytes)
        return bigInt.toString()
    }

    private suspend fun loadPersonalInfo(address: String): List<RecordItem> {
        return withContext(Dispatchers.IO) {
            try {
                val hasInfo = BlockchainService.hasPersonalInfo(address)
                if (hasInfo) {
                    listOf(
                        RecordItem(
                            id = generatePersonalInfoRecordId(address),
                            title = "Personal Information",
                            subtitle = "Name, DOB, Blood Type, etc.",
                            icon = R.drawable.ic_person
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

    private suspend fun loadMedications(address: String): List<RecordItem> {
        return withContext(Dispatchers.IO) {
            try {
                val ids = BlockchainService.getMedicationIds(address)
                var counter = 1
                ids.mapNotNull { id ->
                    try {
                        val med = BlockchainService.getMedicationRef(id)
                        if (med != null && !med.isDeleted) {
                            RecordItem(
                                id = id.toString(),
                                title = "Medication #${counter++}",
                                subtitle = if (med.isActive) "Active medication" else "Inactive",
                                icon = R.drawable.ic_medication
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun loadVaccinations(address: String): List<RecordItem> {
        return withContext(Dispatchers.IO) {
            try {
                val ids = BlockchainService.getVaccinationIds(address)
                var counter = 1
                ids.mapNotNull { id ->
                    try {
                        val vac = BlockchainService.getVaccinationRef(id)
                        if (vac != null && !vac.isDeleted) {
                            RecordItem(
                                id = id.toString(),
                                title = "Vaccination #${counter++}",
                                subtitle = "Vaccination record",
                                icon = R.drawable.ic_vaccination
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun loadReports(address: String): List<RecordItem> {
        return withContext(Dispatchers.IO) {
            try {
                val ids = BlockchainService.getReportIds(address)
                var counter = 1
                ids.mapNotNull { id ->
                    try {
                        val report = BlockchainService.getReportRef(id)
                        if (report != null && !report.isDeleted) {
                            RecordItem(
                                id = id.toString(),
                                title = "Report #${counter++}",
                                subtitle = "Medical report",
                                icon = R.drawable.ic_document
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
