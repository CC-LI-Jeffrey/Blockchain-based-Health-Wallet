package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fyp.blockchainhealthwallet.adapter.ShareRecordAdapter
import com.fyp.blockchainhealthwallet.databinding.ActivityShareRecordBinding
import com.fyp.blockchainhealthwallet.model.ShareRecord
import com.google.android.material.textfield.TextInputEditText

class ShareRecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareRecordBinding
    private lateinit var adapter: ShareRecordAdapter
    private val shareRecords = mutableListOf<ShareRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadMockData()
        setupClickListeners()
        updateStatistics()
    }

    private fun setupRecyclerView() {
        adapter = ShareRecordAdapter(shareRecords) { shareRecord ->
            // Navigate to detail activity
            val intent = Intent(this, ShareRecordDetailActivity::class.java)
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

        binding.recyclerViewShareRecords.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewShareRecords.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.cardShareNew.setOnClickListener {
            Toast.makeText(
                this,
                "Share new record feature will be implemented",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.cardImportRecord.setOnClickListener {
            showImportDialog()
        }
    }

    private fun loadMockData() {
        shareRecords.clear()
        shareRecords.addAll(
            listOf(
                ShareRecord(
                    id = "SH001",
                    recipientName = "Dr. Sarah Johnson",
                    recipientType = "Doctor",
                    sharedData = "Vaccination Records, Medical History",
                    shareDate = "Oct 15, 2025",
                    shareTime = "14:30",
                    expiryDate = "Jan 15, 2026",
                    accessLevel = "View Only",
                    status = "Active",
                    recipientEmail = "sarah.johnson@clinic.com"
                ),
                ShareRecord(
                    id = "SH002",
                    recipientName = "Queen Mary Hospital",
                    recipientType = "Hospital",
                    sharedData = "Full Medical Records",
                    shareDate = "Oct 10, 2025",
                    shareTime = "09:00",
                    expiryDate = "Apr 10, 2026",
                    accessLevel = "Full Access",
                    status = "Active",
                    recipientEmail = "records@qmh.hk"
                ),
                ShareRecord(
                    id = "SH003",
                    recipientName = "AIA Insurance",
                    recipientType = "Insurance Company",
                    sharedData = "Vaccination Records",
                    shareDate = "Sep 20, 2025",
                    shareTime = "16:45",
                    expiryDate = "Oct 20, 2025",
                    accessLevel = "View Only",
                    status = "Expired",
                    recipientEmail = "claims@aia.com"
                ),
                ShareRecord(
                    id = "SH004",
                    recipientName = "Family Clinic Central",
                    recipientType = "Clinic",
                    sharedData = "Lab Results, Prescription History",
                    shareDate = "Oct 1, 2025",
                    shareTime = "11:20",
                    expiryDate = "Dec 1, 2025",
                    accessLevel = "View Only",
                    status = "Active",
                    recipientEmail = "admin@familyclinic.hk"
                ),
                ShareRecord(
                    id = "SH005",
                    recipientName = "Dr. Michael Chen",
                    recipientType = "Doctor",
                    sharedData = "Medical History",
                    shareDate = "Aug 15, 2025",
                    shareTime = "10:00",
                    expiryDate = "Sep 15, 2025",
                    accessLevel = "View Only",
                    status = "Revoked",
                    recipientEmail = "m.chen@medical.hk"
                ),
                ShareRecord(
                    id = "SH006",
                    recipientName = "Prudential Insurance",
                    recipientType = "Insurance Company",
                    sharedData = "Vaccination Records, Lab Results",
                    shareDate = "Oct 25, 2025",
                    shareTime = "13:15",
                    expiryDate = "Jan 25, 2026",
                    accessLevel = "View Only",
                    status = "Active",
                    recipientEmail = "healthclaims@prudential.com"
                )
            )
        )
        adapter.notifyDataSetChanged()
    }

    private fun updateStatistics() {
        val totalShares = shareRecords.size
        val activeShares = shareRecords.count { it.status == "Active" }
        val expiredShares = shareRecords.count { it.status == "Expired" }

        binding.tvTotalShares.text = totalShares.toString()
        binding.tvActiveShares.text = activeShares.toString()
        binding.tvExpiredShares.text = expiredShares.toString()
    }

    private fun showImportDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import_record, null)
        val etAccessKey = dialogView.findViewById<TextInputEditText>(R.id.etAccessKey)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnImport).setOnClickListener {
            val accessKey = etAccessKey.text.toString().trim()
            
            if (accessKey.isEmpty()) {
                Toast.makeText(this, "Please enter an access key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate and import record
            importRecord(accessKey)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun importRecord(accessKey: String) {
        // Simulate importing a record based on access key
        // In a real app, this would make a blockchain/API call
        
        // Mock validation - accept keys in format: ABC123XYZ456DEF789
        if (accessKey.length < 10) {
            Toast.makeText(
                this,
                "Invalid access key. Key must be at least 10 characters.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Simulate successful import
        Toast.makeText(
            this,
            "Successfully imported record from access key: ${accessKey.take(8)}...",
            Toast.LENGTH_LONG
        ).show()

        // In real implementation, you would:
        // 1. Validate the key with blockchain/backend
        // 2. Retrieve the shared record data
        // 3. Add it to the user's local database
        // 4. Refresh the list
        
        // For demo purposes, show success message
        AlertDialog.Builder(this)
            .setTitle("Import Successful")
            .setMessage("The health record has been imported and is now accessible in your records.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
