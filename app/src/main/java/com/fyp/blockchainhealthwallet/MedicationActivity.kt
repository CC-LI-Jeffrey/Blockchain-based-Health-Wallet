package com.fyp.blockchainhealthwallet

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MedicationActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var emptyState: LinearLayout
    private lateinit var tvActiveCount: TextView
    private lateinit var tvTotalCount: TextView
    
    // Sample data
    private val sampleMedications = listOf(
        Medication("Paracetamol", "500mg", "3 times daily", true),
        Medication("Amoxicillin", "250mg", "2 times daily", true),
        Medication("Vitamin D3", "1000 IU", "Once daily", true),
        Medication("Ibuprofen", "400mg", "As needed", false),
        Medication("Omeprazole", "20mg", "Once daily before breakfast", true)
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medication)
        
        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Initialize views
        emptyState = findViewById(R.id.emptyState)
        tvActiveCount = findViewById(R.id.tvActiveCount)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        
        setupRecyclerView()
        setupFab()
        updateCounts()
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewMedication)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Set adapter with sample data
        val adapter = MedicationAdapter(sampleMedications)
        recyclerView.adapter = adapter
        
        // Hide empty state if we have data
        if (sampleMedications.isNotEmpty()) {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun updateCounts() {
        val activeCount = sampleMedications.count { it.isActive }
        val totalCount = sampleMedications.size
        
        tvActiveCount.text = activeCount.toString()
        tvTotalCount.text = totalCount.toString()
    }
    
    private fun setupFab() {
        fabAdd = findViewById(R.id.fabAddMedication)
        fabAdd.setOnClickListener {
            // TODO: Open dialog or new activity to add medication
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
