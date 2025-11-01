package com.fyp.blockchainhealthwallet

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class ViewReportActivity : AppCompatActivity() {

    private lateinit var tvReportTitle: TextView
    private lateinit var tvReportType: TextView
    private lateinit var tvReportDate: TextView
    private lateinit var tvDoctorName: TextView
    private lateinit var tvHospital: TextView
    private lateinit var tvDescription: TextView
    private lateinit var cardAttachedFile: CardView
    private lateinit var tvFilePath: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_report)

        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        setupToolbar()
        setupViews()
        loadReportData()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        tvReportTitle = findViewById(R.id.tvReportTitle)
        tvReportType = findViewById(R.id.tvReportType)
        tvReportDate = findViewById(R.id.tvReportDate)
        tvDoctorName = findViewById(R.id.tvDoctorName)
        tvHospital = findViewById(R.id.tvHospital)
        tvDescription = findViewById(R.id.tvDescription)
        cardAttachedFile = findViewById(R.id.cardAttachedFile)
        tvFilePath = findViewById(R.id.tvFilePath)
    }

    private fun loadReportData() {
        val report = intent.getParcelableExtra<Report>("REPORT")

        if (report != null) {
            tvReportTitle.text = report.title
            tvReportType.text = report.reportType.displayName
            tvReportDate.text = report.date
            tvDoctorName.text = report.doctorName
            tvHospital.text = report.hospital
            tvDescription.text = report.description

            // Show attached file if available
            if (report.filePath != null) {
                cardAttachedFile.visibility = View.VISIBLE
                tvFilePath.text = report.filePath
            } else {
                cardAttachedFile.visibility = View.GONE
            }
        } else {
            // If no report data, finish activity
            finish()
        }
    }
}
