package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class VaccinationDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vaccination_detail)

        setupUI()
        loadRecordDetails()
    }

    private fun setupUI() {
        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Setup view related records button (will open PDF)
        findViewById<androidx.cardview.widget.CardView>(R.id.btnViewRelatedRecords).setOnClickListener {
            val pdfUrl = intent.getStringExtra("PDF_URL")
            if (!pdfUrl.isNullOrEmpty()) {
                openPdfViewer(pdfUrl)
            } else {
                Toast.makeText(this, "PDF not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadRecordDetails() {
        // Get data from intent
        val date = intent.getStringExtra("DATE") ?: ""
        val vaccineName = intent.getStringExtra("VACCINE_NAME") ?: ""
        val vaccineNameEn = intent.getStringExtra("VACCINE_NAME_EN") ?: ""
        val vaccineFullName = intent.getStringExtra("VACCINE_FULL_NAME") ?: ""
        val manufacturer = intent.getStringExtra("MANUFACTURER") ?: ""
        val country = intent.getStringExtra("COUNTRY") ?: ""
        val provider = intent.getStringExtra("PROVIDER") ?: ""
        val location = intent.getStringExtra("LOCATION") ?: ""
        val batchNumber = intent.getStringExtra("BATCH_NUMBER") ?: ""

        // Set data to views
        findViewById<TextView>(R.id.tvDate).text = date
        findViewById<TextView>(R.id.tvVaccineName).text = vaccineName
        findViewById<TextView>(R.id.tvVaccineNameEn).text = vaccineNameEn
        findViewById<TextView>(R.id.tvVaccineFullName).text = vaccineFullName
        findViewById<TextView>(R.id.tvManufacturer).text = manufacturer
        findViewById<TextView>(R.id.tvCountry).text = country
        findViewById<TextView>(R.id.tvProvider).text = provider
        findViewById<TextView>(R.id.tvLocation).text = location
        findViewById<TextView>(R.id.tvBatchNumber).text = batchNumber
    }

    private fun openPdfViewer(pdfUrl: String) {
        val intent = Intent(this, PdfViewerActivity::class.java).apply {
            putExtra("PDF_URL", pdfUrl)
            putExtra("VACCINE_NAME", intent.getStringExtra("VACCINE_NAME"))
        }
        startActivity(intent)
    }
}
