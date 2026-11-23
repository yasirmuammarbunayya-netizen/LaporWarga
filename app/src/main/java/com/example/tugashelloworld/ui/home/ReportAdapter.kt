package com.example.tugashelloworld.ui.home

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.ItemReportBinding
import com.example.tugashelloworld.model.Report
import com.example.tugashelloworld.model.categoryEnum
import com.example.tugashelloworld.model.statusEnum
import com.example.tugashelloworld.util.ImageUtils
import com.example.tugashelloworld.util.TimeFormat

/** Adapter RecyclerView untuk kartu laporan (dipakai di Beranda dan Laporan Saya). */
class ReportAdapter(
    private val onItemClick: (Report) -> Unit
) : ListAdapter<Report, ReportAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemReportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = getItem(position)
        val context = holder.itemView.context
        with(holder.binding) {
            tvTitle.text = report.title
            tvCategory.text = context.getString(report.categoryEnum().label)
            tvAddress.text = report.address.ifBlank { context.getString(R.string.no_address) }
            tvDate.text = TimeFormat.relative(report.createdAt)

            val status = report.statusEnum()
            tvStatus.text = context.getString(status.label)
            tvStatus.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, status.color))

            val photo = ImageUtils.fileOrNull(report.photoPath)
            if (photo != null) {
                ivPhoto.imageTintList = null
                ivPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(ivPhoto).load(photo).override(240, 240).centerCrop().into(ivPhoto)
            } else {
                // Tidak ada foto: tampilkan ikon placeholder
                Glide.with(ivPhoto).clear(ivPhoto)
                ivPhoto.scaleType = ImageView.ScaleType.CENTER
                ivPhoto.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.md_on_surface_variant)
                )
                ivPhoto.setImageResource(R.drawable.ic_gallery)
            }

            root.setOnClickListener { onItemClick(report) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Report>() {
        override fun areItemsTheSame(oldItem: Report, newItem: Report) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Report, newItem: Report) = oldItem == newItem
    }
}
