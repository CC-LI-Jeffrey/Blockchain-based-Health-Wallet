package com.fyp.blockchainhealthwallet.ui.partialshare

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for viewing partial shares that the current user has sent to others
 */
class ViewSentPartialSharesActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var adapter: SentPartialShareAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_sent_partial_shares)
        
        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Partial Shares I Sent"
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        initViews()
        setupRecyclerView()
        loadSentShares()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        btnRefresh = findViewById(R.id.btnRefresh)
        
        btnRefresh.setOnClickListener {
            loadSentShares()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = SentPartialShareAdapter { shareInfo ->
            viewShareDetails(shareInfo)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadSentShares() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.GONE
                
                val myAddress = WalletManager.getAddress()
                if (myAddress == null) {
                    Toast.makeText(this@ViewSentPartialSharesActivity, 
                        "Please connect wallet first", 
                        Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "Wallet not connected"
                    return@launch
                }
                
                android.util.Log.d("SentPartialShares", "Loading sent shares from: $myAddress")
                
                // Query blockchain for shares sent by this user
                val shares = BlockchainService.getSentPartialShares(myAddress)
                
                android.util.Log.d("SentPartialShares", "Found ${shares.size} sent shares")
                
                if (shares.isEmpty()) {
                    progressBar.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "You haven't sent any partial shares yet"
                } else {
                    adapter.submitList(shares)
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SentPartialShares", "Error loading sent shares", e)
                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                emptyView.text = "Error: ${e.message}"
                Toast.makeText(this@ViewSentPartialSharesActivity, 
                    "Failed to load: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun viewShareDetails(shareInfo: SentPartialShareInfo) {
        android.util.Log.d("SentPartialShares", "Viewing sent record ${shareInfo.recordId}")
        android.util.Log.d("SentPartialShares", "Receiver: ${shareInfo.receiver}")
        android.util.Log.d("SentPartialShares", "IPFS Hash: ${shareInfo.ipfsHash}")
        
        // Show details
        Toast.makeText(this, 
            "Share Details\n" +
            "Shared with: ${shareInfo.receiver.take(10)}...\n" +
            "IPFS: ${shareInfo.ipfsHash.take(20)}...\n" +
            "Expiry: ${formatTimestamp(shareInfo.expiryTime.toLong())}\n" +
            "Status: ${if (shareInfo.isActive) "Active" else "Revoked"}", 
            Toast.LENGTH_LONG).show()
        
        // TODO: Navigate to detail view with revoke option
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp * 1000)
        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return formatter.format(date)
    }
}

data class SentPartialShareInfo(
    val recordId: Long,
    val receiver: String,
    val ipfsHash: String,
    val merkleRoot: String,
    val expiryTime: Long,
    val isActive: Boolean
)
