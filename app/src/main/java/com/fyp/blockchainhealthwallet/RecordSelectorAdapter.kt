package com.fyp.blockchainhealthwallet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.R

class RecordSelectorAdapter(
    private val records: List<RecordSelectorActivity.SelectableRecord>,
    private val onRecordClick: (RecordSelectorActivity.SelectableRecord) -> Unit
) : RecyclerView.Adapter<RecordSelectorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvRecordTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvRecordSubtitle)
        val tvSelectButton: TextView = view.findViewById(R.id.tvSelectButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selectable_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.tvTitle.text = record.title
        holder.tvSubtitle.text = record.subtitle
        
        holder.itemView.setOnClickListener {
            onRecordClick(record)
        }
        
        holder.tvSelectButton.setOnClickListener {
            onRecordClick(record)
        }
    }

    override fun getItemCount() = records.size
}
