package com.fyp.blockchainhealthwallet.ui.partialshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.R
import java.text.SimpleDateFormat
import java.util.*

class SentPartialShareAdapter(
    private val onItemClick: (SentPartialShareInfo) -> Unit
) : ListAdapter<SentPartialShareInfo, SentPartialShareAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sent_partial_share, parent, false)
        return ViewHolder(view, onItemClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }
    
    class ViewHolder(
        itemView: View,
        private val onItemClick: (SentPartialShareInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val tvRecordId: TextView = itemView.findViewById(R.id.tvRecordId)
        private val tvReceiver: TextView = itemView.findViewById(R.id.tvReceiver)
        private val tvIpfsHash: TextView = itemView.findViewById(R.id.tvIpfsHash)
        private val tvExpiry: TextView = itemView.findViewById(R.id.tvExpiry)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        
        fun bind(shareInfo: SentPartialShareInfo, displayNumber: Int) {
            // Display sequential number for cleaner UI
            tvRecordId.text = "Shared Record #$displayNumber"
            
            tvReceiver.text = "To: ${shareInfo.receiver.take(10)}...${shareInfo.receiver.takeLast(8)}"
            tvIpfsHash.text = "IPFS: ${shareInfo.ipfsHash.take(15)}..."
            
            val date = Date(shareInfo.expiryTime * 1000)
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvExpiry.text = "Expires: ${formatter.format(date)}"
            
            tvStatus.text = if (shareInfo.isActive) "Active" else "Revoked"
            tvStatus.setTextColor(
                itemView.context.getColor(
                    if (shareInfo.isActive) R.color.share else R.color.revoked
                )
            )
            
            itemView.setOnClickListener {
                onItemClick(shareInfo)
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<SentPartialShareInfo>() {
        override fun areItemsTheSame(
            oldItem: SentPartialShareInfo,
            newItem: SentPartialShareInfo
        ): Boolean {
            return oldItem.recordId == newItem.recordId && 
                   oldItem.receiver == newItem.receiver
        }
        
        override fun areContentsTheSame(
            oldItem: SentPartialShareInfo,
            newItem: SentPartialShareInfo
        ): Boolean {
            return oldItem == newItem
        }
    }
}
