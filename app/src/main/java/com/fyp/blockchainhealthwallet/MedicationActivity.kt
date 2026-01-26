package com.fyp.blockchainhealthwallet

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.network.ApiClient
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

class MedicationActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var emptyState: LinearLayout
    private lateinit var tvActiveCount: TextView
    private lateinit var tvTotalCount: TextView
    
    private val medications = mutableListOf<Medication>()
    private lateinit var adapter: MedicationAdapter

    companion object {
        private const val TAG = "MedicationActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medication)
        
        // Initialize BlockchainService
        BlockchainService.initialize(this)
        
        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Initialize views
        emptyState = findViewById(R.id.emptyState)
        tvActiveCount = findViewById(R.id.tvActiveCount)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        
        setupRecyclerView()
        setupFab()
    }
    
    override fun onResume() {
        super.onResume()
        // Load medications on resume (handles both initial load and returning from add/edit)
        loadMedicationsFromBlockchain()
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewMedication)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = MedicationAdapter(
            medications = medications,
            onShareClick = { medication ->
                showShareMedicationDialog(medication)
            }
        )
        recyclerView.adapter = adapter
    }
    
    private fun showShareMedicationDialog(medication: Medication) {
        if (medication.id == null) {
            Toast.makeText(this, "Cannot share medication: ID not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        com.fyp.blockchainhealthwallet.ui.MedicationShareHelper.showShareMedicationDialog(
            context = this,
            lifecycleScope = lifecycleScope,
            medicationId = medication.id,
            medicationName = medication.name
        )
    }
    
    private fun updateCounts() {
        val activeCount = medications.count { it.isActive }
        val totalCount = medications.size
        
        tvActiveCount.text = activeCount.toString()
        tvTotalCount.text = totalCount.toString()
    }
    
    private fun setupFab() {
        fabAdd = findViewById(R.id.fabAddMedication)
        fabAdd.setOnClickListener {
            val intent = Intent(this, AddMedicationActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadMedicationsFromBlockchain() {
        val address = WalletManager.getAddress()
        
        if (address == null) {
            Toast.makeText(this, "Please connect wallet first", Toast.LENGTH_SHORT).show()
            showEmptyState()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Loading medications from blockchain...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "LOADING MEDICATIONS")
                Log.d(TAG, "========================================")
                Log.d(TAG, "User address: $address")

                // Step 1: Get medication IDs
                val medicationIds = withContext(Dispatchers.IO) {
                    BlockchainService.getMedicationIds(address)
                }

                Log.d(TAG, "Found ${medicationIds.size} medication IDs")

                medications.clear()

                // Step 2: Load each medication
                medicationIds.forEach { medicationId ->
                    try {
                        val medication = loadMedicationById(medicationId)
                        // Filter out deleted records
                        if (medication != null && !medication.isDeleted) {
                            medications.add(medication)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading medication $medicationId", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    
                    if (medications.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        adapter.notifyDataSetChanged()
                        updateCounts()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading medications", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@MedicationActivity,
                        "Error loading medications: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    showEmptyState()
                }
            }
        }
    }

    private suspend fun loadMedicationById(medicationId: BigInteger): Medication? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading medication ID: $medicationId")

                // Get medication reference from blockchain
                val medicationRef = BlockchainService.getMedicationRef(medicationId)
                    ?: return@withContext null

                Log.d(TAG, "MedicationRef: IPFS=${medicationRef.encryptedDataIpfsHash}")

                // Download encrypted data from IPFS
                val response = ApiClient.api.getFromIPFS(medicationRef.encryptedDataIpfsHash)

                if (!response.isSuccessful || response.body() == null) {
                    Log.e(TAG, "Failed to download from IPFS")
                    return@withContext null
                }

                val encryptedDataBase64 = response.body()!!.string()

                // Decrypt data
                val encryptedBytes = android.util.Base64.decode(encryptedDataBase64, android.util.Base64.NO_WRAP)
                val aesKey = EncryptionHelper.decryptKeyFromBlockchain(medicationRef.encryptedKey)
                val jsonData = EncryptionHelper.decryptBytesWithKey(encryptedBytes, aesKey)

                // Parse JSON
                val dataMap = Gson().fromJson(jsonData, Map::class.java) as Map<String, Any>

                Medication(
                    id = medicationId,
                    name = dataMap["name"] as? String ?: "",
                    dosage = dataMap["dosage"] as? String ?: "",
                    frequency = dataMap["frequency"] as? String ?: "",
                    route = dataMap["route"] as? String ?: "",
                    isActive = dataMap["isActive"] as? Boolean ?: true,
                    startDate = (dataMap["startDate"] as? Double)?.toLong() ?: 0L,
                    endDate = (dataMap["endDate"] as? Double)?.toLong()?.takeIf { it > 0 },
                    purpose = dataMap["purpose"] as? String ?: "",
                    prescribingDoctor = dataMap["prescribingDoctor"] as? String ?: "",
                    pharmacy = dataMap["pharmacy"] as? String ?: "",
                    notes = dataMap["notes"] as? String ?: "",
                    createdAt = (dataMap["createdAt"] as? Double)?.toLong(),
                    isDeleted = medicationRef.isDeleted  // Get from blockchain
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error parsing medication", e)
                null
            }
        }
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvActiveCount.text = "0"
        tvTotalCount.text = "0"
    }

    private fun hideEmptyState() {
        emptyState.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

