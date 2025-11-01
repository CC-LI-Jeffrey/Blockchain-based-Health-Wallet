package com.fyp.blockchainhealthwallet.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.model.ShareRecord

class ShareRecordAdapter(
    private val shareRecords: List<ShareRecord>,
    private val onItemClick: (ShareRecord) -> Unit
) : RecyclerView.Adapter<ShareRecordAdapter.ShareRecordViewHolder>() {

    inner class ShareRecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRecipientName: TextView = itemView.findViewById(R.id.tvRecipientName)
        val tvRecipientType: TextView = itemView.findViewById(R.id.tvRecipientType)
        val tvSharedData: TextView = itemView.findViewById(R.id.tvSharedData)
        val tvShareDate: TextView = itemView.findViewById(R.id.tvShareDate)
        val tvExpiryDate: TextView = itemView.findViewById(R.id.tvExpiryDate)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(shareRecord: ShareRecord) {
            tvRecipientName.text = shareRecord.recipientName
            tvRecipientType.text = shareRecord.recipientType
            tvSharedData.text = shareRecord.sharedData
            tvShareDate.text = shareRecord.shareDate
            tvExpiryDate.text = shareRecord.expiryDate

            // Set status with color coding
            tvStatus.text = shareRecord.status
            when (shareRecord.status) {
                "Active" -> {
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    tvStatus.setBackgroundColor(Color.parseColor("#E8F5E9"))
                }
                "Expired" -> {
                    tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                    tvStatus.setBackgroundColor(Color.parseColor("#F5F5F5"))
                }
                "Revoked" -> {
                    tvStatus.setTextColor(Color.parseColor("#F44336"))
                    tvStatus.setBackgroundColor(Color.parseColor("#FFEBEE"))
                }
            }

            itemView.setOnClickListener {
                onItemClick(shareRecord)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShareRecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_share_record, parent, false)
        return ShareRecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShareRecordViewHolder, position: Int) {
        holder.bind(shareRecords[position])
    }

    override fun getItemCount(): Int = shareRecords.size
}
