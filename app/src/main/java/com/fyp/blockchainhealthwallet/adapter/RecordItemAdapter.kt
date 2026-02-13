package com.fyp.blockchainhealthwallet.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.databinding.ItemRecordBinding
import com.fyp.blockchainhealthwallet.model.RecordItem

/**
 * Adapter for displaying records in a list
 */
class RecordItemAdapter(
    private val records: List<RecordItem>,
    private val onItemClick: (RecordItem) -> Unit
) : RecyclerView.Adapter<RecordItemAdapter.RecordViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    inner class RecordViewHolder(
        private val binding: ItemRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: RecordItem) {
            binding.tvTitle.text = record.title
            binding.tvSubtitle.text = record.subtitle
            binding.ivIcon.setImageResource(record.icon)

            binding.root.setOnClickListener {
                onItemClick(record)
            }
        }
    }
}
