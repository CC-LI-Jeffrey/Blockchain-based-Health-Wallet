package com.fyp.blockchainhealthwallet

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fyp.blockchainhealthwallet.zkp.ZkpProofResult
import com.fyp.blockchainhealthwallet.zkp.ZkpService
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Calendar

class ZkpDemoActivity : AppCompatActivity() {

    private lateinit var zkpService: ZkpService
    private val gson = Gson()

    private lateinit var etYear: EditText
    private lateinit var etMonth: EditText
    private lateinit var etDay: EditText
    private lateinit var etMinAge: EditText
    private lateinit var btnGenerateZkp: Button
    private lateinit var etProofJson: EditText
    private lateinit var btnVerifyZkp: Button
    private lateinit var tvVerifyResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zkp_demo)
        supportActionBar?.title = "Interactive Age ZKP Demo"

        zkpService = ZkpService(this)

        etYear = findViewById(R.id.etYear)
        etMonth = findViewById(R.id.etMonth)
        etDay = findViewById(R.id.etDay)
        etMinAge = findViewById(R.id.etMinAge)
        btnGenerateZkp = findViewById(R.id.btnGenerateZkp)
        etProofJson = findViewById(R.id.etProofJson)
        btnVerifyZkp = findViewById(R.id.btnVerifyZkp)
        tvVerifyResult = findViewById(R.id.tvVerifyResult)

        // Set defaults
        etYear.setText("2000")
        etMonth.setText("1")
        etDay.setText("1")
        etMinAge.setText("18")

        btnGenerateZkp.setOnClickListener { generateProof() }
        btnVerifyZkp.setOnClickListener { verifyProof() }
    }

    private fun generateProof() {
        val y = etYear.text.toString().toIntOrNull() ?: 2000
        val m = etMonth.text.toString().toIntOrNull() ?: 1
        val d = etDay.text.toString().toIntOrNull() ?: 1
        val minAge = etMinAge.text.toString().toIntOrNull() ?: 18

        btnGenerateZkp.text = "Generating... Please Wait"
        btnGenerateZkp.isEnabled = false

        lifecycleScope.launch {
            try {
                // Call actual Circom WASM proof generation!
                val proof = zkpService.generateAgeProof(y, m, d, minAge)
                val proofJson = gson.toJson(proof)
                etProofJson.setText(proofJson)
                
                tvVerifyResult.text = "Proof Generated! Modify JSON to tamper."
                tvVerifyResult.setBackgroundColor(Color.parseColor("#EEEEEE"))
                tvVerifyResult.setTextColor(Color.BLACK)
            } catch (e: Exception) {
                Toast.makeText(this@ZkpDemoActivity, "Error generating: ${e.message}", Toast.LENGTH_LONG).show()
                tvVerifyResult.text = "Error generating proof: ${e.message}"
            } finally {
                btnGenerateZkp.text = "1. Generate ZKP Proof"
                btnGenerateZkp.isEnabled = true
            }
        }
    }

    private fun verifyProof() {
        val jsonStr = etProofJson.text.toString()
        if (jsonStr.isEmpty()) {
            Toast.makeText(this, "No proof to verify!", Toast.LENGTH_SHORT).show()
            return
        }

        btnVerifyZkp.text = "Verifying..."
        btnVerifyZkp.isEnabled = false

        lifecycleScope.launch {
            try {
                // Parse the possibly tampered JSON
                val proofToVerify = gson.fromJson(jsonStr, ZkpProofResult::class.java)

                // Extract the public signals exactly as the attacker altered them in the JSON!
                // publicSignals: [isAdult(1), currentYear, currentMonth, currentDay, minAge]
                val claimedYear = proofToVerify.publicSignals.getOrNull(1)?.toIntOrNull() ?: 2026
                val claimedMonth = proofToVerify.publicSignals.getOrNull(2)?.toIntOrNull() ?: 1
                val claimedDay = proofToVerify.publicSignals.getOrNull(3)?.toIntOrNull() ?: 1
                val claimedMinAge = proofToVerify.publicSignals.getOrNull(4)?.toIntOrNull() ?: 18

                // Call actual verification circuit!
                // It will bind the Math of pi_a, pi_b, pi_c against the claimed public signals.
                val isValid = zkpService.verifyAgeProof(proofToVerify, claimedYear, claimedMonth, claimedDay, claimedMinAge)

                if (isValid) {
                    tvVerifyResult.text = "VERIFICATION SUCCESS: VALID PROOF"
                    tvVerifyResult.setBackgroundColor(Color.parseColor("#4CAF50"))
                    tvVerifyResult.setTextColor(Color.WHITE)
                } else {
                    tvVerifyResult.text = "VERIFICATION FAILED: TAMPERED OR INVALID"
                    tvVerifyResult.setBackgroundColor(Color.parseColor("#F44336"))
                    tvVerifyResult.setTextColor(Color.WHITE)
                }
            } catch (e: Exception) {
                tvVerifyResult.text = "VERIFICATION FAILED: MALFORMED DATA"
                tvVerifyResult.setBackgroundColor(Color.parseColor("#F44336"))
                tvVerifyResult.setTextColor(Color.WHITE)
            } finally {
                btnVerifyZkp.text = "2. Verify Tampered/Untampered Proof"
                btnVerifyZkp.isEnabled = true
            }
        }
    }
}