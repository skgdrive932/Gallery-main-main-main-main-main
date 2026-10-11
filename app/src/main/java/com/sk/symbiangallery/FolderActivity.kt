package com.sk.symbiangallery

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FolderActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private var folderId: String = "ALL_MEDIA"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder)

        folderId = intent.getStringExtra("folder_id") ?: "ALL_MEDIA"
        val folderName = intent.getStringExtra("folder_name") ?: "Gallery"

        findViewById<TextView>(R.id.folderTitle).text = folderName
        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        recycler = findViewById(R.id.mediaRecycler)
        recycler.layoutManager = GridLayoutManager(this, 3)

        loadMedia()
    }

    override fun onResume() {
        super.onResume()
        if (::recycler.isInitialized) loadMedia()
    }

    private fun loadMedia() {
        val all = MediaRepository.loadMedia(contentResolver)
        val items = if (folderId == "ALL_MEDIA") {
            all
        } else {
            all.filter { it.bucketId == folderId }
        }

        findViewById<TextView>(R.id.folderCount).text = "${items.size}"

        recycler.adapter = MediaAdapter(items, onClick = { item ->
            if (item.isVideo) {
                val intent = Intent(this, VideoPlayerActivity::class.java)
                intent.putExtra("uri", item.uri.toString())
                intent.putExtra("name", item.name)
                startActivity(intent)
            } else {
                val photos = items.filter { !it.isVideo }
                val position = photos.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
                val intent = Intent(this, PhotoViewerActivity::class.java).apply {
                    putStringArrayListExtra("image_list", ArrayList(photos.map { it.uri.toString() }))
                    putExtra("position", position)
                    putExtra("uri", item.uri.toString())
                    putExtra("name", item.name)
                }
                startActivity(intent)
            }
        })
    }
}