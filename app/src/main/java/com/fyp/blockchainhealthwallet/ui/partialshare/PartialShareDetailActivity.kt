package com.fyp.blockchainhealthwallet.ui.partialshare

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.network.HealthWalletApi
import com.fyp.blockchainhealthwallet.network.RetrofitClient
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.blockchain.MerkleTreeHelper
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity to view details of a received partial share
 * Downloads IPFS package and verifies Merkle proofs
 */
class PartialShareDetailActivity : AppCompatActivity() {
    
    private lateinit var progressBar: ProgressBar
    private lateinit var contentLayout: LinearLayout
    private lateinit var tvRecordId: TextView
    private lateinit var tvOwner: TextView
    private lateinit var tvExpiry: TextView
    private lateinit var tvMerkleRoot: TextView
    private lateinit var tvIpfsHash: TextView
    private lateinit var attributesContainer: LinearLayout
    private lateinit var verificationCard: MaterialCardView
    private lateinit var tvVerificationStatus: TextView
    
    private val api: HealthWalletApi by lazy {
        RetrofitClient.healthWalletApi
    }
    
    private val merkleHelper = MerkleTreeHelper()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_partial_share_detail)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Partial Share Details"
        toolbar.setNavigationOnClickListener { finish() }
        
        initViews()
        
        // Get data from intent
        val recordId = intent.getStringExtra("RECORD_ID") ?: return
        val owner = intent.getStringExtra("OWNER") ?: return
        val ipfsHash = intent.getStringExtra("IPFS_HASH") ?: return
        val merkleRoot = intent.getStringExtra("MERKLE_ROOT") ?: return
        val expiryTime = intent.getLongExtra("EXPIRY_TIME", 0L)
        
        displayBasicInfo(recordId, owner, ipfsHash, merkleRoot, expiryTime)
        downloadAndVerify(ipfsHash, merkleRoot)
    }
    
    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        contentLayout = findViewById(R.id.contentLayout)
        tvRecordId = findViewById(R.id.tvRecordId)
        tvOwner = findViewById(R.id.tvOwner)
        tvExpiry = findViewById(R.id.tvExpiry)
        tvMerkleRoot = findViewById(R.id.tvMerkleRoot)
        tvIpfsHash = findViewById(R.id.tvIpfsHash)
        attributesContainer = findViewById(R.id.attributesContainer)
        verificationCard = findViewById(R.id.verificationCard)
        tvVerificationStatus = findViewById(R.id.tvVerificationStatus)
    }
    
    private fun displayBasicInfo(
        recordId: String,
        owner: String,
        ipfsHash: String,
        merkleRoot: String,
        expiryTime: Long
    ) {
        // Display simple title (actual recordId stored for blockchain operations)
        tvRecordId.text = "Shared Record Details"
        tvOwner.text = "Owner: ${owner.take(10)}...${owner.takeLast(8)}"
        tvIpfsHash.text = "IPFS: ${ipfsHash.take(20)}..."
        tvMerkleRoot.text = "Root: ${merkleRoot.take(20)}..."
        
        val date = Date(expiryTime * 1000)
        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        tvExpiry.text = "Expires: ${formatter.format(date)}"
    }
    
    private fun downloadAndVerify(ipfsHash: String, merkleRoot: String) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                contentLayout.visibility = View.GONE
                
                Log.d(TAG, "=== DOWNLOADING PARTIAL SHARE PACKAGE ===")
                Log.d(TAG, "IPFS Hash: $ipfsHash")
                Log.d(TAG, "Expected Merkle Root: $merkleRoot")
                
                // Download from IPFS via backend
                val packageJson = withContext(Dispatchers.IO) {
                    val response = api.downloadPartialSharePackage(ipfsHash).execute()
                    if (!response.isSuccessful) {
                        throw Exception("Failed to download: ${response.code()}")
                    }
                    response.body()?.string() ?: throw Exception("Empty response")
                }
                
                Log.d(TAG, "Package downloaded, size: ${packageJson.length} bytes")
                Log.d(TAG, "Raw JSON: ${packageJson.take(500)}...")  // Log first 500 chars
                
                // Parse package - handle both wrapped and unwrapped formats
                val gson = Gson()
                
                // Try parsing as wrapped response first
                val packageData = try {
                    val wrappedResponse = gson.fromJson(packageJson, WrappedPackageResponse::class.java)
                    if (wrappedResponse?.success == true && wrappedResponse.packageData != null) {
                        Log.d(TAG, "Parsed as wrapped response")
                        wrappedResponse.packageData
                    } else {
                        // Try parsing as direct package
                        Log.d(TAG, "Trying to parse as direct package")
                        gson.fromJson(packageJson, PartialSharePackage::class.java)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parsing error: ${e.message}")
                    Log.e(TAG, "Full JSON: $packageJson")
                    throw Exception("Failed to parse package JSON: ${e.message}")
                }
                
                if (packageData == null) {
                    throw Exception("Parsed package is null")
                }
                
                Log.d(TAG, "Package parsed:")
                Log.d(TAG, "  Record Type: ${packageData.recordType}")
                Log.d(TAG, "  Merkle Root: ${packageData.merkleRoot}")
                Log.d(TAG, "  Attributes: ${packageData.attributes?.keys ?: "null"}")
                Log.d(TAG, "  Proofs count: ${packageData.proofs?.size ?: 0}")
                
                // Validate required fields
                if (packageData.merkleRoot == null) {
                    throw Exception("Package missing merkleRoot field")
                }
                if (packageData.attributes == null) {
                    throw Exception("Package missing attributes field")
                }
                if (packageData.proofs == null) {
                    throw Exception("Package missing proofs field")
                }
                
                // Verify package merkle root matches on-chain
                if (packageData.merkleRoot != merkleRoot.removePrefix("0x")) {
                    throw Exception("Merkle root mismatch! Package has different root than blockchain")
                }
                
                Log.d(TAG, "✓ Package merkle root matches blockchain")
                
                // Verify each attribute with its Merkle proof
                Log.d(TAG, "\n=== VERIFYING MERKLE PROOFS ===")
                val verificationResults = mutableListOf<Pair<String, Boolean>>()
                
                for ((attrName, attrValue) in packageData.attributes!!) {
                    val proof = packageData.proofs!![attrName]
                    if (proof == null) {
                        Log.w(TAG, "⚠ No proof for attribute: $attrName")
                        verificationResults.add(attrName to false)
                        continue
                    }
                    
                    Log.d(TAG, "\n════════════════════════════════════════════════════")
                    Log.d(TAG, "Starting verification for attribute: $attrName")
                    Log.d(TAG, "════════════════════════════════════════════════════")
                    
                    // Convert proof map to ProofNode list
                    val proofNodes = proof.map { proofNodeMap ->
                        MerkleTreeHelper.ProofNode(
                            hash = proofNodeMap["hash"] ?: "",
                            position = proofNodeMap["position"] ?: ""
                        )
                    }
                    
                    // Verify proof with detailed logging
                    val isValid = merkleHelper.verifyProofWithDetailedLogging(
                        attrName,
                        attrValue,
                        proofNodes,
                        packageData.merkleRoot!!,
                        TAG
                    )
                    
                    verificationResults.add(attrName to isValid)
                }
                
                // Display results
                val allValid = verificationResults.all { it.second }
                Log.d(TAG, "\n=== VERIFICATION COMPLETE ===")
                Log.d(TAG, "Total attributes: ${verificationResults.size}")
                Log.d(TAG, "Valid proofs: ${verificationResults.count { it.second }}")
                Log.d(TAG, "Invalid proofs: ${verificationResults.count { !it.second }}")
                Log.d(TAG, "Overall status: ${if (allValid) "✓ ALL VALID" else "✗ SOME FAILED"}")
                
                displayAttributes(packageData, verificationResults, allValid)
                
                progressBar.visibility = View.GONE
                contentLayout.visibility = View.VISIBLE
                
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading/verifying package", e)
                progressBar.visibility = View.GONE
                Toast.makeText(this@PartialShareDetailActivity,
                    "Failed: ${e.message}",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    private fun displayAttributes(
        packageData: PartialSharePackage,
        verificationResults: List<Pair<String, Boolean>>,
        allValid: Boolean
    ) {
        // Display verification status
        tvVerificationStatus.text = if (allValid) {
            "✓ All Merkle proofs verified successfully"
        } else {
            "⚠ Some proofs failed verification"
        }
        tvVerificationStatus.setTextColor(
            getColor(if (allValid) R.color.success else R.color.rejected)
        )
        verificationCard.setCardBackgroundColor(
            getColor(if (allValid) R.color.info_background else R.color.rejected_light)
        )
        
        // Display attributes
        attributesContainer.removeAllViews()
        
        Log.d(TAG, "\n=== DISPLAYING ATTRIBUTES ===")
        Log.d(TAG, "PackageData attributes map:")
        packageData.attributes?.forEach { (key, value) ->
            Log.d(TAG, "  '$key' = '$value' (length: ${value.length})")
        }
        
        for ((attrName, isValid) in verificationResults) {
            val attrValue = packageData.attributes!![attrName] ?: ""
            
            Log.d(TAG, "Display loop - Key: '$attrName', Value from map: '$attrValue'")
            
            val cardView = layoutInflater.inflate(
                R.layout.item_attribute_verified,
                attributesContainer,
                false
            ) as MaterialCardView
            
            val tvAttrName = cardView.findViewById<TextView>(R.id.tvAttrName)
            val tvAttrValue = cardView.findViewById<TextView>(R.id.tvAttrValue)
            val tvProofStatus = cardView.findViewById<TextView>(R.id.tvProofStatus)
            
            tvAttrName.text = formatAttributeName(attrName)
            tvAttrValue.text = if (attrValue.isEmpty()) "(empty)" else attrValue
            tvProofStatus.text = if (isValid) "✓ Verified" else "✗ Failed"
            tvProofStatus.setTextColor(
                getColor(if (isValid) R.color.success else R.color.rejected)
            )
            
            attributesContainer.addView(cardView)
        }
    }
    
    private fun formatAttributeName(name: String): String {
        return name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .split("_", " ")
            .joinToString(" ") { it.capitalize() }
    }
    
    // Wrapper for backend response format
    data class WrappedPackageResponse(
        val success: Boolean?,
        @com.google.gson.annotations.SerializedName("package")
        val packageData: PartialSharePackage?
    )
    
    data class PartialSharePackage(
        val recordType: String?,
        val merkleRoot: String?,
        val attributes: Map<String, String>?,
        val proofs: Map<String, List<Map<String, String>>>?,  // List of {hash, position} maps
        val expiryTime: Long?,
        val version: String?,
        val timestamp: Long?
    )
    
    companion object {
        private const val TAG = "PartialShareDetail"
    }
}
