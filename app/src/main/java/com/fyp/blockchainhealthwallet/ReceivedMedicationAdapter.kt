package com.fyp.blockchainhealthwallet

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.math.BigInteger

class ReceivedMedicationAdapter(private val medications: List<ReceivedMedication>) :
    RecyclerView.Adapter<ReceivedMedicationAdapter.ReceivedMedicationViewHolder>() {

    class ReceivedMedicationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMedicationName: TextView = view.findViewById(R.id.tvMedicationName)
        val tvDosage: TextView = view.findViewById(R.id.tvDosage)
        val tvFrequency: TextView = view.findViewById(R.id.tvFrequency)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceivedMedicationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication, parent, false)
        return ReceivedMedicationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReceivedMedicationViewHolder, position: Int) {
        val medication = medications[position]
        holder.tvMedicationName.text = medication.name.ifEmpty { "N/A" }
        holder.tvDosage.text = "Dosage: ${medication.dosage.ifEmpty { "N/A" }}"
        holder.tvFrequency.text = medication.frequency.ifEmpty { "N/A" }
        holder.tvStatus.text = if (medication.isActive) "Active" else "Completed"

        // Update status color
        val context = holder.itemView.context
        if (medication.isActive) {
            holder.tvStatus.setTextColor(context.getColor(R.color.medication))
        } else {
            holder.tvStatus.setTextColor(context.getColor(R.color.text_hint))
        }

        // Add click listener to open details
        holder.itemView.setOnClickListener {
            val intent = Intent(context, ViewReceivedMedicationActivity::class.java).apply {
                putExtra("MEDICATION_NAME", medication.name)
                putExtra("DOSAGE", medication.dosage)
                putExtra("FREQUENCY", medication.frequency)
                putExtra("ROUTE", medication.route)
                putExtra("IS_ACTIVE", medication.isActive)
                putExtra("START_DATE", medication.startDate)
                putExtra("END_DATE", medication.endDate)
                putExtra("PURPOSE", medication.purpose)
                putExtra("DOCTOR", medication.prescribingDoctor)
                putExtra("PHARMACY", medication.pharmacy)
                putExtra("NOTES", medication.notes)
                putExtra("CREATED_AT", medication.createdAt)
                putExtra("SHARE_ID", medication.shareId.toString())
                putExtra("OWNER_ADDRESS", medication.ownerAddress)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = medications.size
}

data class ReceivedMedication(
    val shareId: BigInteger,
    val ownerAddress: String,
    val name: String,
    val dosage: String,
    val frequency: String,
    val route: String,
    val isActive: Boolean,
    val startDate: Long,
    val endDate: Long,
    val purpose: String,
    val prescribingDoctor: String,
    val pharmacy: String,
    val notes: String,
    val createdAt: Long
)
