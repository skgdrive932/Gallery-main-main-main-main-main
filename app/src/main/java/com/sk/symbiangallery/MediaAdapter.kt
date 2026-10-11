package com.sk.symbiangallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MediaAdapter(
    private val items: List<MediaItem>,
    private val selectedUris: Set<String> = emptySet(),
    private val selectionMode: Boolean = false,
    private val onClick: (MediaItem) -> Unit,
    private val onLongClick: (MediaItem) -> Unit = {}
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val name: TextView = view.findViewById(R.id.fileName)
        val badge: TextView = view.findViewById(R.id.videoBadge)
        val selectionMark: TextView = view.findViewById(R.id.selectionMark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val selected = selectedUris.contains(item.uri.toString())
        Glide.with(holder.thumb).load(item.uri).centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image).into(holder.thumb)
        holder.name.text = item.name
        holder.badge.visibility = if (item.isVideo) View.VISIBLE else View.GONE
        holder.selectionMark.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.selectionMark.text = if (selected) "✓" else "○"
        holder.selectionMark.alpha = if (selected) 1f else .65f
        holder.itemView.alpha = if (selectionMode && selected) .78f else 1f
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    override fun getItemCount(): Int = items.size
}
