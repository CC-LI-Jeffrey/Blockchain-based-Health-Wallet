package com.fyp.blockchainhealthwallet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReportsAdapter(
    private var reports: List<Report>,
    private val onItemClick: (Report) -> Unit,
    private val onDeleteClick: (Report) -> Unit
) : RecyclerView.Adapter<ReportsAdapter.ReportViewHolder>() {

    inner class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvReportTitle: TextView = itemView.findViewById(R.id.tvReportTitle)
        val tvReportType: TextView = itemView.findViewById(R.id.tvReportType)
        val tvReportDate: TextView = itemView.findViewById(R.id.tvReportDate)
        val tvDoctorName: TextView = itemView.findViewById(R.id.tvDoctorName)
        val tvHospital: TextView = itemView.findViewById(R.id.tvHospital)
        val btnDeleteReport: ImageView = itemView.findViewById(R.id.btnDeleteReport)

        fun bind(report: Report) {
            tvReportTitle.text = report.title
            tvReportType.text = report.reportType.displayName
            tvReportDate.text = report.date
            tvDoctorName.text = report.doctorName
            tvHospital.text = report.hospital

            itemView.setOnClickListener {
                onItemClick(report)
            }

            btnDeleteReport.setOnClickListener {
                onDeleteClick(report)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(reports[position])
    }

    override fun getItemCount(): Int = reports.size

    fun updateReports(newReports: List<Report>) {
        reports = newReports
        notifyDataSetChanged()
    }
}
