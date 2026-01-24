package com.fyp.blockchainhealthwallet

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class ViewReceivedMedicationActivity : AppCompatActivity() {

    private lateinit var etMedicationName: TextInputEditText
    private lateinit var etDosage: TextInputEditText
    private lateinit var etFrequency: TextInputEditText
    private lateinit var etRoute: TextInputEditText
    private lateinit var etStartDate: TextInputEditText
    private lateinit var etEndDate: TextInputEditText
    private lateinit var switchIsActive: SwitchMaterial
    private lateinit var etPurpose: TextInputEditText
    private lateinit var etDoctor: TextInputEditText
    private lateinit var etPharmacy: TextInputEditText
    private lateinit var etNotes: TextInputEditText
    private lateinit var tvCreatedAt: TextView
    private lateinit var tvShareInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_received_medication)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        setupToolbar()
        setupViews()
        loadDataFromIntent()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Shared Medication"
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        etMedicationName = findViewById(R.id.etMedicationName)
        etDosage = findViewById(R.id.etDosage)
        etFrequency = findViewById(R.id.etFrequency)
        etRoute = findViewById(R.id.etRoute)
        etStartDate = findViewById(R.id.etStartDate)
        etEndDate = findViewById(R.id.etEndDate)
        switchIsActive = findViewById(R.id.switchIsActive)
        etPurpose = findViewById(R.id.etPurpose)
        etDoctor = findViewById(R.id.etDoctor)
        etPharmacy = findViewById(R.id.etPharmacy)
        etNotes = findViewById(R.id.etNotes)
        tvCreatedAt = findViewById(R.id.tvCreatedAt)
        tvShareInfo = findViewById(R.id.tvShareInfo)

        // All fields are read-only for received medications
        etMedicationName.isEnabled = false
        etDosage.isEnabled = false
        etFrequency.isEnabled = false
        etRoute.isEnabled = false
        etStartDate.isEnabled = false
        etEndDate.isEnabled = false
        switchIsActive.isEnabled = false
        etPurpose.isEnabled = false
        etDoctor.isEnabled = false
        etPharmacy.isEnabled = false
        etNotes.isEnabled = false
    }

    private fun loadDataFromIntent() {
        val name = intent.getStringExtra("MEDICATION_NAME") ?: ""
        val dosage = intent.getStringExtra("DOSAGE") ?: ""
        val frequency = intent.getStringExtra("FREQUENCY") ?: ""
        val route = intent.getStringExtra("ROUTE") ?: ""
        val purpose = intent.getStringExtra("PURPOSE") ?: ""
        val doctor = intent.getStringExtra("DOCTOR") ?: ""
        val pharmacy = intent.getStringExtra("PHARMACY") ?: ""
        val notes = intent.getStringExtra("NOTES") ?: ""

        etMedicationName.setText(name.ifEmpty { "N/A" })
        etDosage.setText(dosage.ifEmpty { "N/A" })
        etFrequency.setText(frequency.ifEmpty { "N/A" })
        etRoute.setText(route.ifEmpty { "N/A" })
        etPurpose.setText(purpose.ifEmpty { "N/A" })
        etDoctor.setText(doctor.ifEmpty { "N/A" })
        etPharmacy.setText(pharmacy.ifEmpty { "N/A" })
        etNotes.setText(notes.ifEmpty { "N/A" })

        switchIsActive.isChecked = intent.getBooleanExtra("IS_ACTIVE", true)

        val startDateMs = intent.getLongExtra("START_DATE", 0L)
        if (startDateMs > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            etStartDate.setText(dateFormat.format(Date(startDateMs)))
        } else {
            etStartDate.setText("N/A")
        }

        val endDateMs = intent.getLongExtra("END_DATE", 0L)
        if (endDateMs > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            etEndDate.setText(dateFormat.format(Date(endDateMs)))
        } else {
            etEndDate.setText("N/A")
        }

        val createdAtMs = intent.getLongExtra("CREATED_AT", 0L)
        if (createdAtMs > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            tvCreatedAt.text = "Created: ${dateFormat.format(Date(createdAtMs))}"
        } else {
            tvCreatedAt.text = "Created: N/A"
        }

        val shareId = intent.getStringExtra("SHARE_ID") ?: ""
        val ownerAddress = intent.getStringExtra("OWNER_ADDRESS") ?: ""
        tvShareInfo.text = "Shared by: ${ownerAddress.take(10)}...\nShare ID: $shareId"
    }
}
