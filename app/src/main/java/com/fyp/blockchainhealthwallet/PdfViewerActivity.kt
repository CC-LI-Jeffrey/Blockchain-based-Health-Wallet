package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PdfViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        setupUI()
        loadPdf()
    }

    private fun setupUI() {
        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Setup share button
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            sharePdf()
        }

        // Set title
        val vaccineName = intent.getStringExtra("VACCINE_NAME") ?: "疫苗證書"
        findViewById<TextView>(R.id.tvTitle).text = vaccineName
    }

    private fun loadPdf() {
        val pdfUrl = intent.getStringExtra("PDF_URL")
        
        // Display URL in placeholder
        findViewById<TextView>(R.id.tvPdfUrl).text = pdfUrl ?: "No PDF URL provided"
        
        // In a production app, you would:
        // 1. Download the PDF from the URL
        // 2. Use a PDF rendering library (like AndroidPdfViewer or PDFView)
        // 3. Display the PDF in the pdfContainer
        
        // For now, we'll just show a placeholder message
        Toast.makeText(this, "PDF URL: $pdfUrl", Toast.LENGTH_LONG).show()
    }

    private fun sharePdf() {
        val pdfUrl = intent.getStringExtra("PDF_URL")
        val vaccineName = intent.getStringExtra("VACCINE_NAME") ?: "Vaccination Record"
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, vaccineName)
            putExtra(Intent.EXTRA_TEXT, "Check out my vaccination record: $pdfUrl")
        }
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to share", Toast.LENGTH_SHORT).show()
        }
    }
}
