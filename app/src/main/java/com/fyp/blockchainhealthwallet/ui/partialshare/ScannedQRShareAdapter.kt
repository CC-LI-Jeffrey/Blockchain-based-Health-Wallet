package com.fyp.blockchainhealthwallet.ui.partialshare

import android.graphics.Color
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

class ScannedQRShareAdapter(
    private val onDelete: (SavedQRShare) -> Unit,
    private val onView: (SavedQRShare) -> Unit
) : ListAdapter<SavedQRShare, ScannedQRShareAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scanned_qr_share, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvValidity: TextView = itemView.findViewById(R.id.tvValidity)
        private val tvRecordType: TextView = itemView.findViewById(R.id.tvRecordType)
        private val tvAttributeCount: TextView = itemView.findViewById(R.id.tvAttributeCount)
        private val tvScanTime: TextView = itemView.findViewById(R.id.tvScanTime)
        private val tvExpiry: TextView = itemView.findViewById(R.id.tvExpiry)
        private val btnDelete: com.google.android.material.button.MaterialButton =
            itemView.findViewById(R.id.btnDelete)
        private val btnView: com.google.android.material.button.MaterialButton =
            itemView.findViewById(R.id.btnView)

        private val formatter = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

        fun bind(share: SavedQRShare, displayNum: Int) {
            val pkg = share.sharePackage

            tvTitle.text = "QR Scan #$displayNum"

            if (share.isValid) {
                tvValidity.text = "VALID"
                tvValidity.setBackgroundColor(0xFF2E7D32.toInt()) // dark green
            } else {
                tvValidity.text = "FAILED"
                tvValidity.setBackgroundColor(0xFFC62828.toInt()) // dark red
            }

            tvRecordType.text = "Record type: ${pkg.recordType}"
            tvAttributeCount.text = "${pkg.attributes.size} attribute(s) shared"
            tvScanTime.text = "Scanned: ${formatter.format(Date(share.scanTime))}"
            tvExpiry.text = "Expires: ${formatter.format(Date(pkg.expiryTime))}"

            btnDelete.setOnClickListener { onDelete(share) }
            btnView.setOnClickListener { onView(share) }
            itemView.setOnClickListener { onView(share) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SavedQRShare>() {
        override fun areItemsTheSame(old: SavedQRShare, new: SavedQRShare) = old.id == new.id
        override fun areContentsTheSame(old: SavedQRShare, new: SavedQRShare) = old == new
    }
}
