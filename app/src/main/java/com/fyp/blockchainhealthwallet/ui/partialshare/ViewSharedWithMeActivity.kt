package com.fyp.blockchainhealthwallet.ui.partialshare

import android.content.Intent
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
 * Activity for viewing partial shares that have been shared with the current user
 */
class ViewSharedWithMeActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var adapter: SharedRecordAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_shared_with_me)
        
        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Shared With Me"
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        initViews()
        setupRecyclerView()
        loadSharedRecords()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        btnRefresh = findViewById(R.id.btnRefresh)
        
        btnRefresh.setOnClickListener {
            loadSharedRecords()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = SharedRecordAdapter { shareInfo ->
            viewSharedRecord(shareInfo)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadSharedRecords() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.GONE
                
                val myAddress = WalletManager.getAddress()
                if (myAddress == null) {
                    Toast.makeText(this@ViewSharedWithMeActivity, 
                        "Please connect wallet first", 
                        Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "Wallet not connected"
                    return@launch
                }
                
                android.util.Log.d("SharedWithMe", "Loading shares for: $myAddress")
                
                // Query blockchain for active shares
                val shares = BlockchainService.getActiveSharesForReceiver(myAddress)
                
                android.util.Log.d("SharedWithMe", "Found ${shares.size} active shares")
                
                if (shares.isEmpty()) {
                    progressBar.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "No records have been shared with you yet"
                } else {
                    adapter.submitList(shares)
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SharedWithMe", "Error loading shared records", e)
                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                emptyView.text = "Error: ${e.message}"
                Toast.makeText(this@ViewSharedWithMeActivity, 
                    "Failed to load: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun viewSharedRecord(shareInfo: BlockchainService.PartialShareInfo) {
        android.util.Log.d("SharedWithMe", "Viewing record ${shareInfo.recordId}")
        android.util.Log.d("SharedWithMe", "IPFS Hash: ${shareInfo.ipfsHash}")
        android.util.Log.d("SharedWithMe", "Merkle Root: ${shareInfo.merkleRoot}")
        
        // Launch detail activity
        val intent = Intent(this, PartialShareDetailActivity::class.java).apply {
            putExtra("RECORD_ID", shareInfo.recordId.toString())
            putExtra("OWNER", shareInfo.owner)
            putExtra("IPFS_HASH", shareInfo.ipfsHash)
            putExtra("MERKLE_ROOT", shareInfo.merkleRoot)
            putExtra("EXPIRY_TIME", shareInfo.expiryTime.toLong())
        }
        startActivity(intent)
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp * 1000) // Convert from seconds to milliseconds
        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return formatter.format(date)
    }
}
