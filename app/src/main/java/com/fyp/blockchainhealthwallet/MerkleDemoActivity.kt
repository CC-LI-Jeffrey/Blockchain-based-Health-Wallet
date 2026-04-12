package com.fyp.blockchainhealthwallet

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fyp.blockchainhealthwallet.blockchain.MerkleTreeHelper
import com.fyp.blockchainhealthwallet.models.RecordSchemas
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MerkleDemoActivity : AppCompatActivity() {

    private val merkleHelper = MerkleTreeHelper()
    private val gson = Gson()

    private lateinit var cbMedicine: CheckBox
    private lateinit var etMedicine: EditText
    private lateinit var cbDosage: CheckBox
    private lateinit var etDosage: EditText
    private lateinit var cbDoctor: CheckBox
    private lateinit var etDoctor: EditText

    private lateinit var btnGenerateMerkle: Button
    private lateinit var etMerkleJson: EditText
    private lateinit var btnVerifyMerkle: Button
    private lateinit var tvVerifyResult: TextView

    // We store the data class representation to deserialize back
    data class PartialSharePayload(
        val expectedRoot: String,
        val attributesToShare: Map<String, String>,
        val proofs: Map<String, List<MerkleTreeHelper.ProofNode>>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_merkle_demo)
        supportActionBar?.title = "Interactive Merkle Demo"

        cbMedicine = findViewById(R.id.cbMedicine)
        etMedicine = findViewById(R.id.etMedicine)
        cbDosage = findViewById(R.id.cbDosage)
        etDosage = findViewById(R.id.etDosage)
        cbDoctor = findViewById(R.id.cbDoctor)
        etDoctor = findViewById(R.id.etDoctor)

        btnGenerateMerkle = findViewById(R.id.btnGenerateMerkle)
        etMerkleJson = findViewById(R.id.etMerkleJson)
        btnVerifyMerkle = findViewById(R.id.btnVerifyMerkle)
        tvVerifyResult = findViewById(R.id.tvVerifyResult)

        btnGenerateMerkle.setOnClickListener { generateProof() }
        btnVerifyMerkle.setOnClickListener { verifyProof() }
    }

    private fun generateProof() {
        try {
            // Build the full record dictionary
            val fullRecord = mutableMapOf(
                "medicineName" to etMedicine.text.toString(),
                "dosage" to etDosage.text.toString(),
                "prescribedBy" to etDoctor.text.toString()
            )
            // Add some dummies to fill up schema
            fullRecord["frequency"] = "Once daily"
            fullRecord["route"] = "Oral"
            fullRecord["startDate"] = "2026-01-01"
            fullRecord["endDate"] = "2026-12-31"
            fullRecord["purpose"] = "Pain relief"
            fullRecord["pharmacy"] = "Local"
            fullRecord["notes"] = "After meal"

            // 1. Build Merkle Tree
            val tree = merkleHelper.buildMerkleTree(RecordSchemas.RecordType.MEDICATION, fullRecord)

            // 2. Select only what we want to share
            val sharedAttributes = mutableMapOf<String, String>()
            if (cbMedicine.isChecked) sharedAttributes["medicineName"] = fullRecord["medicineName"]!!
            if (cbDosage.isChecked) sharedAttributes["dosage"] = fullRecord["dosage"]!!
            if (cbDoctor.isChecked) sharedAttributes["prescribedBy"] = fullRecord["prescribedBy"]!!

            if (sharedAttributes.isEmpty()) {
                Toast.makeText(this, "Select at least 1 attribute to share", Toast.LENGTH_SHORT).show()
                return
            }

            // 3. Generate proofs
            val proofs = merkleHelper.generateProofs(tree, sharedAttributes)

            // 4. Bundle Payload
            val payload = PartialSharePayload(
                expectedRoot = tree.root,
                attributesToShare = sharedAttributes,
                proofs = proofs
            )

            // Show JSON
            etMerkleJson.setText(gson.toJson(payload))
            tvVerifyResult.text = "Partial Proof Generated! Try editing a shared value or root."
            tvVerifyResult.setBackgroundColor(Color.parseColor("#EEEEEE"))
            tvVerifyResult.setTextColor(Color.BLACK)

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun verifyProof() {
        val jsonStr = etMerkleJson.text.toString()
        if (jsonStr.isEmpty()) return

        try {
            // 1. Parse potentially tampered JSON
            val payloadType = object : TypeToken<PartialSharePayload>() {}.type
            val payload: PartialSharePayload = gson.fromJson(jsonStr, payloadType)

            // 2. Cryptographically Verify
            val verificationResult = merkleHelper.verifyProofs(
                attributes = payload.attributesToShare,
                proofs = payload.proofs,
                expectedRoot = payload.expectedRoot
            )

            // 3. Check if ALL passed
            val allPassed = verificationResult.values.all { it }

            if (allPassed) {
                tvVerifyResult.text = "VERIFICATION SUCCESS: VALID PARTIAL PROOF"
                tvVerifyResult.setBackgroundColor(Color.parseColor("#4CAF50"))
                tvVerifyResult.setTextColor(Color.WHITE)
            } else {
                val failedAttrs = verificationResult.filter { !it.value }.keys.joinToString()
                tvVerifyResult.text = "VERIFICATION FAILED FOR: $failedAttrs"
                tvVerifyResult.setBackgroundColor(Color.parseColor("#F44336"))
                tvVerifyResult.setTextColor(Color.WHITE)
            }
        } catch (e: Exception) {
            tvVerifyResult.text = "VERIFICATION FAILED: MALFORMED DATA"
            tvVerifyResult.setBackgroundColor(Color.parseColor("#F44336"))
            tvVerifyResult.setTextColor(Color.WHITE)
        }
    }
}