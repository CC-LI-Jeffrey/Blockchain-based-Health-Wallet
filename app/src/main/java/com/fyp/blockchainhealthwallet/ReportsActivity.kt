package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ReportsActivity : AppCompatActivity() {

    private lateinit var recyclerViewReports: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var fabAddReport: FloatingActionButton
    private lateinit var reportsAdapter: ReportsAdapter
    private val reportsList = mutableListOf<Report>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        setupViews()
        loadSampleData()
        updateEmptyState()
    }

    private fun setupViews() {
        recyclerViewReports = findViewById(R.id.recyclerViewReports)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        fabAddReport = findViewById(R.id.fabAddReport)

        // Setup RecyclerView
        reportsAdapter = ReportsAdapter(
            reports = reportsList,
            onItemClick = { report ->
                openReportDetails(report)
            },
            onDeleteClick = { report ->
                showDeleteConfirmation(report)
            }
        )

        recyclerViewReports.apply {
            layoutManager = LinearLayoutManager(this@ReportsActivity)
            adapter = reportsAdapter
        }

        // Setup FAB
        fabAddReport.setOnClickListener {
            openAddReport()
        }
    }

    private fun loadSampleData() {
        // Add sample data for demonstration
        reportsList.addAll(
            listOf(
                Report(
                    id = "1",
                    title = "Complete Blood Count",
                    reportType = ReportType.LAB_RESULT,
                    date = "Jan 15, 2025",
                    doctorName = "Dr. Sarah Johnson",
                    hospital = "City General Hospital",
                    description = "Regular blood work checkup. All values within normal range.",
                    filePath = null
                ),
                Report(
                    id = "2",
                    title = "Annual Checkup Notes",
                    reportType = ReportType.DOCTOR_NOTE,
                    date = "Jan 10, 2025",
                    doctorName = "Dr. Michael Chen",
                    hospital = "Health Plus Clinic",
                    description = "Patient is in good health. Continue current medication regimen.",
                    filePath = null
                ),
                Report(
                    id = "3",
                    title = "X-Ray - Chest",
                    reportType = ReportType.IMAGING,
                    date = "Dec 28, 2024",
                    doctorName = "Dr. Emily Rodriguez",
                    hospital = "Advanced Medical Center",
                    description = "Chest X-ray shows clear lungs, no abnormalities detected.",
                    filePath = null
                ),
                Report(
                    id = "4",
                    title = "Antibiotic Prescription",
                    reportType = ReportType.PRESCRIPTION,
                    date = "Dec 20, 2024",
                    doctorName = "Dr. James Wilson",
                    hospital = "Community Health Center",
                    description = "Prescribed Amoxicillin 500mg for respiratory infection.",
                    filePath = null
                )
            )
        )
        reportsAdapter.updateReports(reportsList)
    }

    private fun openReportDetails(report: Report) {
        val intent = Intent(this, ViewReportActivity::class.java).apply {
            putExtra("REPORT", report)
        }
        startActivity(intent)
    }

    private fun openAddReport() {
        val intent = Intent(this, AddReportActivity::class.java)
        startActivityForResult(intent, REQUEST_ADD_REPORT)
    }

    private fun showDeleteConfirmation(report: Report) {
        AlertDialog.Builder(this)
            .setTitle("Delete Report")
            .setMessage("Are you sure you want to delete '${report.title}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteReport(report)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteReport(report: Report) {
        reportsList.remove(report)
        reportsAdapter.updateReports(reportsList)
        updateEmptyState()
        Toast.makeText(this, "Report deleted", Toast.LENGTH_SHORT).show()
    }

    private fun updateEmptyState() {
        if (reportsList.isEmpty()) {
            recyclerViewReports.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            recyclerViewReports.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_REPORT && resultCode == RESULT_OK) {
            data?.getParcelableExtra<Report>("NEW_REPORT")?.let { newReport ->
                reportsList.add(0, newReport) // Add at the beginning
                reportsAdapter.updateReports(reportsList)
                updateEmptyState()
                Toast.makeText(this, "Report added successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_ADD_REPORT = 100
    }
}
