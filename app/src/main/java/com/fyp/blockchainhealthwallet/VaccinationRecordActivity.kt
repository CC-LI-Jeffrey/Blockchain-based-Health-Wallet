package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.adapter.VaccinationRecordAdapter
import com.fyp.blockchainhealthwallet.model.VaccinationRecord
import com.google.android.material.card.MaterialCardView

class VaccinationRecordActivity : AppCompatActivity() {

    private lateinit var rvVaccinationRecords: RecyclerView
    private lateinit var adapter: VaccinationRecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vaccination_record)

        setupUI()
        loadVaccinationRecords()
    }

    private fun setupUI() {
        // Setup RecyclerView
        rvVaccinationRecords = findViewById(R.id.rvVaccinationRecords)
        rvVaccinationRecords.layoutManager = LinearLayoutManager(this)

        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Setup upload button
        findViewById<MaterialCardView>(R.id.btnUploadRecord).setOnClickListener {
            // TODO: Implement file picker to upload vaccination record
            Toast.makeText(this, "Upload vaccination record feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadVaccinationRecords() {
        // Mock data - replace with actual data from your blockchain/database
        val records = listOf(
            VaccinationRecord(
                id = "1",
                date = "June 4, 2022",
                vaccineName = "COVID-19 Vaccine",
                vaccineNameEn = "COVID-19 vaccine",
                vaccineFullName = "Comirnaty COVID-19 mRNA Vaccine (BNT162b2) Concentrate for Dispersion for Injection (3rd Dose)",
                manufacturer = "BioNTech",
                country = "Hong Kong",
                provider = "Department of Health COVID-19 Vaccination Programme (On-Site)",
                location = "Community Vaccination Centre, Hiu Kwong Street Sports Centre",
                batchNumber = "2A088A",
                pdfUrl = "https://drive.google.com/file/d/1UXrKDX2Urj8CaOHb674zZDj82naFt7aY/view?usp=drive_link"
            ),
            VaccinationRecord(
                id = "2",
                date = "August 12, 2021",
                vaccineName = "COVID-19 Vaccine",
                vaccineNameEn = "COVID-19 vaccine",
                vaccineFullName = "Comirnaty COVID-19 mRNA Vaccine (BNT162b2) Concentrate for Dispersion for Injection (2nd Dose)",
                manufacturer = "BioNTech",
                country = "Hong Kong",
                provider = "Department of Health COVID-19 Vaccination Programme (On-Site)",
                location = "Community Vaccination Centre, Hiu Kwong Street Sports Centre",
                batchNumber = "2A088B",
                pdfUrl = "https://drive.google.com/file/d/1UXrKDX2Urj8CaOHb674zZDj82naFt7aY/view?usp=drive_link"
            ),
            VaccinationRecord(
                id = "3",
                date = "July 19, 2021",
                vaccineName = "COVID-19 Vaccine",
                vaccineNameEn = "COVID-19 vaccine",
                vaccineFullName = "Comirnaty COVID-19 mRNA Vaccine (BNT162b2) Concentrate for Dispersion for Injection (1st Dose)",
                manufacturer = "BioNTech",
                country = "Hong Kong",
                provider = "Department of Health COVID-19 Vaccination Programme (On-Site)",
                location = "Community Vaccination Centre, Hiu Kwong Street Sports Centre",
                batchNumber = "2A088C",
                pdfUrl = "https://drive.google.com/file/d/1UXrKDX2Urj8CaOHb674zZDj82naFt7aY/view?usp=drive_link"
            )
        )

        adapter = VaccinationRecordAdapter(records) { record ->
            openVaccinationDetail(record)
        }
        rvVaccinationRecords.adapter = adapter
    }

    private fun openVaccinationDetail(record: VaccinationRecord) {
        val intent = Intent(this, VaccinationDetailActivity::class.java).apply {
            putExtra("RECORD_ID", record.id)
            putExtra("DATE", record.date)
            putExtra("VACCINE_NAME", record.vaccineName)
            putExtra("VACCINE_NAME_EN", record.vaccineNameEn)
            putExtra("VACCINE_FULL_NAME", record.vaccineFullName)
            putExtra("MANUFACTURER", record.manufacturer)
            putExtra("COUNTRY", record.country)
            putExtra("PROVIDER", record.provider)
            putExtra("LOCATION", record.location)
            putExtra("BATCH_NUMBER", record.batchNumber)
            putExtra("PDF_URL", record.pdfUrl)
        }
        startActivity(intent)
    }
}
