package com.fyp.blockchainhealthwallet.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fyp.blockchainhealthwallet.R
import com.fyp.blockchainhealthwallet.model.AccessLog

class AccessLogAdapter(
    private val accessLogs: List<AccessLog>,
    private val onItemClick: (AccessLog) -> Unit
) : RecyclerView.Adapter<AccessLogAdapter.AccessLogViewHolder>() {

    inner class AccessLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAccessorName: TextView = itemView.findViewById(R.id.tvAccessorName)
        val tvAccessorType: TextView = itemView.findViewById(R.id.tvAccessorType)
        val tvAccessedData: TextView = itemView.findViewById(R.id.tvAccessedData)
        val tvAccessDate: TextView = itemView.findViewById(R.id.tvAccessDate)
        val tvAccessTime: TextView = itemView.findViewById(R.id.tvAccessTime)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(accessLog: AccessLog) {
            tvAccessorName.text = accessLog.accessorName
            tvAccessorType.text = accessLog.accessorType
            tvAccessedData.text = accessLog.accessedData
            tvAccessDate.text = accessLog.accessDate
            tvAccessTime.text = accessLog.accessTime
            tvStatus.text = accessLog.status

            // Set status color
            val statusColor = when (accessLog.status) {
                "Approved" -> itemView.context.getColor(android.R.color.holo_green_dark)
                "Denied" -> itemView.context.getColor(android.R.color.holo_red_dark)
                "Pending" -> itemView.context.getColor(android.R.color.holo_orange_dark)
                else -> itemView.context.getColor(android.R.color.darker_gray)
            }
            tvStatus.setTextColor(statusColor)

            itemView.setOnClickListener {
                onItemClick(accessLog)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccessLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_access_log, parent, false)
        return AccessLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccessLogViewHolder, position: Int) {
        holder.bind(accessLogs[position])
    }

    override fun getItemCount(): Int = accessLogs.size
}
