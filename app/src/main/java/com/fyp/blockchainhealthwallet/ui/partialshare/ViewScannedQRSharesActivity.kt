package com.fyp.blockchainhealthwallet.ui.partialshare

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.models.RecordSchemas

/**
 * Lists all QR partial share records that were scanned and saved on this device.
 * Unlike blockchain shares, these are stored locally in SharedPreferences.
 */
class ViewScannedQRSharesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var adapter: ScannedQRShareAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_scanned_qr_shares)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Scanned QR Records"
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)

        adapter = ScannedQRShareAdapter(
            onDelete = { savedShare ->
                confirmDelete(savedShare)
            },
            onView = { savedShare ->
                showDetails(savedShare)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadShares()
    }

    override fun onResume() {
        super.onResume()
        loadShares()
    }

    private fun loadShares() {
        val shares = ScannedQRShareRepository.getAll(this)
        adapter.submitList(shares)
        if (shares.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun confirmDelete(savedShare: SavedQRShare) {
        AlertDialog.Builder(this)
            .setTitle("Delete record?")
            .setMessage("This will remove the saved QR scan from your device. The original data will not be affected.")
            .setPositiveButton("Delete") { _, _ ->
                ScannedQRShareRepository.delete(this, savedShare.id)
                loadShares()
                Toast.makeText(this, "Record deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetails(savedShare: SavedQRShare) {
        val pkg = savedShare.sharePackage
        val sb = StringBuilder()
        sb.append("Record type: ${pkg.recordType}\n")
        sb.append("Merkle root: ${pkg.merkleRoot.take(20)}...\n\n")
        sb.append("Attributes:\n")
        for ((key, value) in pkg.attributes) {
            val display = RecordSchemas.getDisplayName(key)
            sb.append("  $display: $value\n")
        }
        val scannedAt = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(savedShare.scanTime))
        val expiresAt = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(pkg.expiryTime))
        sb.append("\nScanned: $scannedAt")
        sb.append("\nExpires: $expiresAt")
        sb.append("\n\nVerification: ${if (savedShare.isValid) "ALL PROOFS VALID" else "VERIFICATION FAILED"}")

        AlertDialog.Builder(this)
            .setTitle("Shared Record Details")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .show()
    }
}
