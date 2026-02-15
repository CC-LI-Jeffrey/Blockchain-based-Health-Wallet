package com.fyp.blockchainhealthwallet.ui.partialshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import java.text.SimpleDateFormat
import java.util.*

class SharedRecordAdapter(
    private val onItemClick: (BlockchainService.PartialShareInfo) -> Unit
) : ListAdapter<BlockchainService.PartialShareInfo, SharedRecordAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shared_record, parent, false)
        return ViewHolder(view, onItemClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }
    
    class ViewHolder(
        itemView: View,
        private val onItemClick: (BlockchainService.PartialShareInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val recordIdText: TextView = itemView.findViewById(R.id.recordIdText)
        private val ownerText: TextView = itemView.findViewById(R.id.ownerText)
        private val expiryText: TextView = itemView.findViewById(R.id.expiryText)
        private val ipfsHashText: TextView = itemView.findViewById(R.id.ipfsHashText)
        
        fun bind(shareInfo: BlockchainService.PartialShareInfo, displayNumber: Int) {
            // Display sequential number for cleaner UI
            recordIdText.text = "Shared Record #$displayNumber"
            
            ownerText.text = "From: ${shareInfo.owner.take(10)}...${shareInfo.owner.takeLast(8)}"
            
            val expiryDate = Date(shareInfo.expiryTime.toLong() * 1000)
            val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            expiryText.text = "Expires: ${formatter.format(expiryDate)}"
            
            ipfsHashText.text = "IPFS: ${shareInfo.ipfsHash.take(15)}...${shareInfo.ipfsHash.takeLast(10)}"
            
            itemView.setOnClickListener {
                onItemClick(shareInfo)
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<BlockchainService.PartialShareInfo>() {
        override fun areItemsTheSame(
            oldItem: BlockchainService.PartialShareInfo,
            newItem: BlockchainService.PartialShareInfo
        ): Boolean {
            return oldItem.recordId == newItem.recordId
        }
        
        override fun areContentsTheSame(
            oldItem: BlockchainService.PartialShareInfo,
            newItem: BlockchainService.PartialShareInfo
        ): Boolean {
            return oldItem == newItem
        }
    }
}
