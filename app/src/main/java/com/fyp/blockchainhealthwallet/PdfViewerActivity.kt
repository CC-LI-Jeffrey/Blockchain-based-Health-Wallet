package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private var downloadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        setupUI()
        loadPdf()
    }

    private fun setupUI() {
        pdfView = findViewById(R.id.pdfView)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)
        
        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Setup share button
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            sharePdf()
        }

        // Set title
        val vaccineName = intent.getStringExtra("VACCINE_NAME") ?: "Vaccination Certificate"
        findViewById<TextView>(R.id.tvTitle).text = vaccineName
    }

    private fun loadPdf() {
        val pdfUrl = intent.getStringExtra("PDF_URL")
        
        if (pdfUrl.isNullOrEmpty()) {
            showError("No PDF URL provided")
            return
        }
        
        // Convert Google Drive share link to direct download link if needed
        val directPdfUrl = convertGoogleDriveUrl(pdfUrl)
        
        // Download and display PDF
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val pdfFile = downloadPdf(directPdfUrl)
                withContext(Dispatchers.Main) {
                    displayPdf(pdfFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Failed to load PDF: ${e.message}")
                }
            }
        }
    }

    private fun downloadPdf(urlString: String): File {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.doInput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.connect()

        val inputStream = connection.inputStream
        val pdfFile = File(cacheDir, "temp_vaccination_certificate.pdf")
        val outputStream = FileOutputStream(pdfFile)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }

        outputStream.close()
        inputStream.close()
        connection.disconnect()

        return pdfFile
    }

    private fun displayPdf(pdfFile: File) {
        progressBar.visibility = View.GONE
        tvError.visibility = View.GONE
        
        pdfView.fromFile(pdfFile)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .defaultPage(0)
            .enableAnnotationRendering(false)
            .password(null)
            .scrollHandle(null)
            .enableAntialiasing(true)
            .spacing(0)
            .onError { throwable ->
                showError("Error displaying PDF: ${throwable.message}")
            }
            .onPageError { page, throwable ->
                Toast.makeText(this, "Error on page $page: ${throwable.message}", Toast.LENGTH_SHORT).show()
            }
            .load()
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        tvError.visibility = View.VISIBLE
        tvError.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun convertGoogleDriveUrl(url: String?): String {
        // Convert Google Drive share link to direct download link
        // Format: https://drive.google.com/file/d/FILE_ID/view?usp=drive_link
        // To: https://drive.google.com/uc?export=download&id=FILE_ID
        if (url == null) return ""
        
        val fileIdPattern = "/d/([^/]+)/".toRegex()
        val matchResult = fileIdPattern.find(url)
        
        return if (matchResult != null) {
            val fileId = matchResult.groupValues[1]
            "https://drive.google.com/uc?export=download&id=$fileId"
        } else {
            url
        }
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

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
    }
}
