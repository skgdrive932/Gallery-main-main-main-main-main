package com.sk.symbiangallery

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GalleryAdapter(
    private val context: Context,
    private val mediaList: List<MediaItem>
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_gallery, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mediaList[position]

        // Glide se thumbnail load karein
        Glide.with(context)
            .load(item.uri)
            .centerCrop()
            .into(holder.imageView)

        // Photo Click Event: ViewPager me swipe karwane ke liye saare URIs bhein
        holder.itemView.setOnClickListener {
            val intent = Intent(context, PhotoViewerActivity::class.java).apply {
                val uriList = ArrayList(mediaList.map { it.uri.toString() })
                putStringArrayListExtra("image_list", uriList)
                putExtra("position", holder.adapterPosition)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = mediaList.size
}
