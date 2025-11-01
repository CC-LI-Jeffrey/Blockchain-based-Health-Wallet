package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fyp.blockchainhealthwallet.adapter.AccessLogAdapter
import com.fyp.blockchainhealthwallet.databinding.ActivityAccessLogBinding
import com.fyp.blockchainhealthwallet.model.AccessLog

class AccessLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccessLogBinding
    private lateinit var adapter: AccessLogAdapter
    private val accessLogs = mutableListOf<AccessLog>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccessLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadMockData()
        setupClickListeners()
        updateStatistics()
    }

    private fun setupRecyclerView() {
        adapter = AccessLogAdapter(accessLogs) { accessLog ->
            // Navigate to detail activity
            val intent = Intent(this, AccessLogDetailActivity::class.java)
            intent.putExtra("ACCESS_LOG_ID", accessLog.id)
            intent.putExtra("ACCESSOR_NAME", accessLog.accessorName)
            intent.putExtra("ACCESSOR_TYPE", accessLog.accessorType)
            intent.putExtra("ACCESSED_DATA", accessLog.accessedData)
            intent.putExtra("ACCESS_TIME", accessLog.accessTime)
            intent.putExtra("ACCESS_DATE", accessLog.accessDate)
            intent.putExtra("LOCATION", accessLog.location)
            intent.putExtra("PURPOSE", accessLog.purpose)
            intent.putExtra("STATUS", accessLog.status)
            intent.putExtra("IP_ADDRESS", accessLog.ipAddress)
            startActivity(intent)
        }

        binding.recyclerViewAccessLogs.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAccessLogs.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadMockData() {
        accessLogs.clear()
        accessLogs.addAll(
            listOf(
                AccessLog(
                    id = "AL001",
                    accessorName = "Queen Mary Hospital",
                    accessorType = "Hospital",
                    accessedData = "Vaccination Records, Medical History",
                    accessTime = "14:30",
                    accessDate = "Oct 15, 2025",
                    location = "Hong Kong",
                    purpose = "Emergency Treatment",
                    status = "Approved",
                    ipAddress = "192.168.1.100"
                ),
                AccessLog(
                    id = "AL002",
                    accessorName = "Dr. Sarah Johnson",
                    accessorType = "Doctor",
                    accessedData = "Lab Results, Prescription History",
                    accessTime = "09:15",
                    accessDate = "Oct 12, 2025",
                    location = "Central, Hong Kong",
                    purpose = "Regular Checkup",
                    status = "Approved",
                    ipAddress = "203.145.67.89"
                ),
                AccessLog(
                    id = "AL003",
                    accessorName = "Prudential Insurance",
                    accessorType = "Insurance Company",
                    accessedData = "Vaccination Records",
                    accessTime = "11:45",
                    accessDate = "Oct 10, 2025",
                    location = "Singapore",
                    purpose = "Claim Verification",
                    status = "Pending",
                    ipAddress = "182.24.56.12"
                ),
                AccessLog(
                    id = "AL004",
                    accessorName = "Unknown Third Party",
                    accessorType = "Unknown",
                    accessedData = "Personal Information",
                    accessTime = "23:58",
                    accessDate = "Oct 8, 2025",
                    location = "Unknown",
                    purpose = "Unauthorized Access Attempt",
                    status = "Denied",
                    ipAddress = "45.67.89.123"
                ),
                AccessLog(
                    id = "AL005",
                    accessorName = "Family Clinic Central",
                    accessorType = "Clinic",
                    accessedData = "Vaccination Records",
                    accessTime = "16:20",
                    accessDate = "Oct 5, 2025",
                    location = "Central, Hong Kong",
                    purpose = "Flu Vaccination",
                    status = "Approved",
                    ipAddress = "192.168.2.45"
                ),
                AccessLog(
                    id = "AL006",
                    accessorName = "AIA International",
                    accessorType = "Insurance Company",
                    accessedData = "Medical History, Treatment Records",
                    accessTime = "10:30",
                    accessDate = "Oct 3, 2025",
                    location = "Hong Kong",
                    purpose = "Policy Renewal Review",
                    status = "Approved",
                    ipAddress = "203.88.45.67"
                ),
                AccessLog(
                    id = "AL007",
                    accessorName = "Dr. Michael Chen",
                    accessorType = "Doctor",
                    accessedData = "Lab Results",
                    accessTime = "13:15",
                    accessDate = "Oct 1, 2025",
                    location = "Kowloon, Hong Kong",
                    purpose = "Second Opinion",
                    status = "Pending",
                    ipAddress = "192.168.10.88"
                )
            )
        )
        adapter.notifyDataSetChanged()
    }

    private fun updateStatistics() {
        val totalAccess = accessLogs.size
        val approved = accessLogs.count { it.status == "Approved" }
        val pending = accessLogs.count { it.status == "Pending" }

        binding.tvTotalAccess.text = totalAccess.toString()
        binding.tvApproved.text = approved.toString()
        binding.tvPending.text = pending.toString()
    }
}
