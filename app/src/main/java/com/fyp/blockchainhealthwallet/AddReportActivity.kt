package com.fyp.blockchainhealthwallet

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class AddReportActivity : AppCompatActivity() {

    private lateinit var etReportTitle: TextInputEditText
    private lateinit var actvReportType: AutoCompleteTextView
    private lateinit var etDate: TextInputEditText
    private lateinit var etDoctorName: TextInputEditText
    private lateinit var etHospital: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var btnAttachFile: MaterialButton
    private lateinit var tvAttachedFile: TextView
    private lateinit var btnSaveReport: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private var selectedDate: Calendar = Calendar.getInstance()
    private var attachedFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_report)

        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        setupToolbar()
        setupViews()
        setupReportTypeDropdown()
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
        etReportTitle = findViewById(R.id.etReportTitle)
        actvReportType = findViewById(R.id.actvReportType)
        etDate = findViewById(R.id.etDate)
        etDoctorName = findViewById(R.id.etDoctorName)
        etHospital = findViewById(R.id.etHospital)
        etDescription = findViewById(R.id.etDescription)
        btnAttachFile = findViewById(R.id.btnAttachFile)
        tvAttachedFile = findViewById(R.id.tvAttachedFile)
        btnSaveReport = findViewById(R.id.btnSaveReport)
        btnCancel = findViewById(R.id.btnCancel)

        // Set default date
        updateDateField()

        // Date picker
        etDate.setOnClickListener {
            showDatePicker()
        }

        // Attach file
        btnAttachFile.setOnClickListener {
            // TODO: Implement file picker
            Toast.makeText(this, "File attachment feature coming soon", Toast.LENGTH_SHORT).show()
        }

        // Save button
        btnSaveReport.setOnClickListener {
            saveReport()
        }

        // Cancel button
        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupReportTypeDropdown() {
        val reportTypes = ReportType.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, reportTypes)
        actvReportType.setAdapter(adapter)
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                updateDateField()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateField() {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        etDate.setText(dateFormat.format(selectedDate.time))
    }

    private fun saveReport() {
        // Validate inputs
        val title = etReportTitle.text.toString().trim()
        val typeString = actvReportType.text.toString().trim()
        val date = etDate.text.toString().trim()
        val doctorName = etDoctorName.text.toString().trim()
        val hospital = etHospital.text.toString().trim()
        val description = etDescription.text.toString().trim()

        if (title.isEmpty()) {
            etReportTitle.error = "Please enter report title"
            etReportTitle.requestFocus()
            return
        }

        if (typeString.isEmpty()) {
            Toast.makeText(this, "Please select report type", Toast.LENGTH_SHORT).show()
            actvReportType.requestFocus()
            return
        }

        if (doctorName.isEmpty()) {
            etDoctorName.error = "Please enter doctor name"
            etDoctorName.requestFocus()
            return
        }

        if (hospital.isEmpty()) {
            etHospital.error = "Please enter hospital/clinic name"
            etHospital.requestFocus()
            return
        }

        if (description.isEmpty()) {
            etDescription.error = "Please enter description"
            etDescription.requestFocus()
            return
        }

        // Find the report type
        val reportType = ReportType.values().find { it.displayName == typeString } 
            ?: ReportType.OTHER

        // Create new report
        val newReport = Report(
            id = UUID.randomUUID().toString(),
            title = title,
            reportType = reportType,
            date = date,
            doctorName = doctorName,
            hospital = hospital,
            description = description,
            filePath = attachedFilePath,
            timestamp = System.currentTimeMillis()
        )

        // Return result
        val resultIntent = Intent().apply {
            putExtra("NEW_REPORT", newReport)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
