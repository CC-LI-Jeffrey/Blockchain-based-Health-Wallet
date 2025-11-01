package com.fyp.blockchainhealthwallet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MedicationAdapter(private val medications: List<Medication>) : 
    RecyclerView.Adapter<MedicationAdapter.MedicationViewHolder>() {

    class MedicationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMedicationName: TextView = view.findViewById(R.id.tvMedicationName)
        val tvDosage: TextView = view.findViewById(R.id.tvDosage)
        val tvFrequency: TextView = view.findViewById(R.id.tvFrequency)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication, parent, false)
        return MedicationViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicationViewHolder, position: Int) {
        val medication = medications[position]
        holder.tvMedicationName.text = medication.name
        holder.tvDosage.text = "Dosage: ${medication.dosage}"
        holder.tvFrequency.text = medication.frequency
        holder.tvStatus.text = if (medication.isActive) "Active" else "Completed"
        
        // Update status color
        val context = holder.itemView.context
        if (medication.isActive) {
            holder.tvStatus.setTextColor(context.getColor(R.color.medication))
        } else {
            holder.tvStatus.setTextColor(context.getColor(R.color.text_hint))
        }
    }

    override fun getItemCount() = medications.size
}
