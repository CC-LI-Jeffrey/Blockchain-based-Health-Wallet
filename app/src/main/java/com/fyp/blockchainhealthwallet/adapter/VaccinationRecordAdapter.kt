package com.fyp.blockchainhealthwallet.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.model.VaccinationRecord

class VaccinationRecordAdapter(
    private val records: List<VaccinationRecord>,
    private val onItemClick: (VaccinationRecord) -> Unit
) : RecyclerView.Adapter<VaccinationRecordAdapter.VaccinationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaccinationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vaccination_record, parent, false)
        return VaccinationViewHolder(view)
    }

    override fun onBindViewHolder(holder: VaccinationViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    inner class VaccinationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvVaccineName: TextView = itemView.findViewById(R.id.tvVaccineName)

        fun bind(record: VaccinationRecord) {
            tvDate.text = record.date
            tvVaccineName.text = record.vaccineName

            itemView.setOnClickListener {
                onItemClick(record)
            }
        }
    }
}
