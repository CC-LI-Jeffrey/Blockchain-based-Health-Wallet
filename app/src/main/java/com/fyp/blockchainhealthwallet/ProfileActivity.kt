package com.fyp.blockchainhealthwallet

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        // Setup back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}
