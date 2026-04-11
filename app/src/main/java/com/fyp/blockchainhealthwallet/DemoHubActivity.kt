package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DemoHubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo_hub)
        supportActionBar?.title = "FYP Presentation Mode"

        findViewById<Button>(R.id.btnDemoTamperProof).setOnClickListener {
            val intent = Intent(this, TamperDemoActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnDemoZkp).setOnClickListener {
            val intent = Intent(this, ZkpDemoActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnDemoPartialShare).setOnClickListener {
            val intent = Intent(this, MerkleDemoActivity::class.java)
            startActivity(intent)
        }
    }
}