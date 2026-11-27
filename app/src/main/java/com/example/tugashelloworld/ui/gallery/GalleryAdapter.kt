package com.example.tugashelloworld.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tugashelloworld.databinding.ItemGalleryBinding
import com.example.tugashelloworld.util.GalleryUtils.MediaItem
import java.util.Locale

class GalleryAdapter(
    private val onClick: (index: Int) -> Unit,
    private val onLongClick: (index: Int) -> Unit
) : ListAdapter<MediaItem, GalleryAdapter.Holder>(Diff) {

    private var selected: Set<String> = emptySet()
    private var selecting = false

    fun setSelection(uris: Set<String>, selectionMode: Boolean) {
        selected = uris
        selecting = selectionMode
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemGalleryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        Glide.with(b.ivThumb).load(item.uri).centerCrop().into(b.ivThumb)

        b.ivPlay.isVisible = item.isVideo
        b.tvDuration.isVisible = item.isVideo
        if (item.isVideo) {
            val sec = item.durationMs / 1000
            b.tvDuration.text = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
        }

        val isSelected = item.uri.toString() in selected
        b.overlaySelected.isVisible = isSelected
        b.ivCheck.isVisible = isSelected
        b.ivUnchecked.isVisible = selecting && !isSelected

        b.root.setOnClickListener {
            holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(onClick)
        }
        b.root.setOnLongClickListener {
            holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(onLongClick)
            true
        }
    }

    class Holder(val binding: ItemGalleryBinding) : RecyclerView.ViewHolder(binding.root)

    private object Diff : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(a: MediaItem, b: MediaItem) = a.uri == b.uri
        override fun areContentsTheSame(a: MediaItem, b: MediaItem) = a == b
    }
}
