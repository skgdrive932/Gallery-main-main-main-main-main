package com.sk.symbiangallery

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.app.RecoverableSecurityException
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var roundGallery: RoundGalleryView
    private lateinit var permissionText: TextView
    private lateinit var emptyText: TextView
    private lateinit var sectionTitle: TextView
    private lateinit var countText: TextView
    private lateinit var searchInput: EditText
    private lateinit var gridButton: TextView
    private lateinit var roundButton: TextView
    private lateinit var tabPhotos: TextView
    private lateinit var tabVideos: TextView
    private lateinit var tabAlbums: TextView
    private lateinit var tabFavorites: TextView
    private lateinit var selectionBar: View
    private lateinit var selectionCount: TextView
    private lateinit var btnSelect: TextView
    private var selectionMode = false
    private val selectedItems = linkedMapOf<String, MediaItem>()
    private var pendingDeleteUris: List<Uri> = emptyList()
    private var pendingDeleteIndex = 0
    private var pendingMove = false

    private val deletePermissionLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            if (Build.VERSION.SDK_INT >= 30) finishDeleteOperation()
            else deleteLegacyNext()
        } else {
            pendingMove = false
            Toast.makeText(this, "Permission not granted; files were not deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private val moveFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) moveSelectedTo(treeUri)
    }

    private enum class Tab { PHOTOS, VIDEOS, ALBUMS, FAVORITES }
    private var currentTab = Tab.PHOTOS
    private var roundMode = false
    private var currentItems: List<MediaItem> = emptyList()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasAnyMediaPermission()) {
                permissionText.visibility = View.GONE
                if (Build.VERSION.SDK_INT <= 28 && pendingDeleteUris.isNotEmpty() && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    deleteLegacyNext()
                } else loadCurrentTab()
            } else permissionText.visibility = View.VISIBLE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.mediaRecycler)
        roundGallery = findViewById(R.id.roundGallery)
        permissionText = findViewById(R.id.permissionText)
        emptyText = findViewById(R.id.emptyText)
        sectionTitle = findViewById(R.id.sectionTitle)
        countText = findViewById(R.id.countText)
        searchInput = findViewById(R.id.searchInput)
        gridButton = findViewById(R.id.gridButton)
        roundButton = findViewById(R.id.roundButton)
        tabPhotos = findViewById(R.id.tabPhotos)
        tabVideos = findViewById(R.id.tabVideos)
        tabAlbums = findViewById(R.id.tabAlbums)
        tabFavorites = findViewById(R.id.tabFavorites)
        selectionBar = findViewById(R.id.selectionBar)
        selectionCount = findViewById(R.id.selectionCount)
        btnSelect = findViewById(R.id.btnSelect)

        btnSelect.setOnClickListener { enterSelectionMode() }
        findViewById<TextView>(R.id.btnSelectAll).setOnClickListener { selectAllVisible() }
        findViewById<TextView>(R.id.btnShareSelected).setOnClickListener { shareSelected() }
        findViewById<TextView>(R.id.btnMoveSelected).setOnClickListener { beginMoveSelected() }
        findViewById<TextView>(R.id.btnDeleteSelected).setOnClickListener { confirmDeleteSelected() }
        findViewById<TextView>(R.id.btnCancelSelection).setOnClickListener { exitSelectionMode() }

        findViewById<TextView>(R.id.aboutButton).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        tabPhotos.setOnClickListener { switchTab(Tab.PHOTOS) }
        tabVideos.setOnClickListener { switchTab(Tab.VIDEOS) }
        tabAlbums.setOnClickListener { switchTab(Tab.ALBUMS) }
        tabFavorites.setOnClickListener { switchTab(Tab.FAVORITES) }
        permissionText.setOnClickListener { requestMediaPermission() }
        gridButton.setOnClickListener { setRoundMode(false) }
        roundButton.setOnClickListener { setRoundMode(true) }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applySearch() }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        updateTabVisuals()
        if (hasRequiredMediaPermissionForCurrentTab()) loadCurrentTab() else requestMediaPermission()
    }

    override fun onResume() {
        super.onResume()
        if (::recycler.isInitialized && hasAnyMediaPermission()) loadCurrentTab()
    }

    private fun switchTab(tab: Tab) {
        if (tab != currentTab) exitSelectionMode()
        currentTab = tab
        if (tab == Tab.ALBUMS || tab == Tab.FAVORITES) setRoundMode(false)
        updateTabVisuals()
        if (hasRequiredMediaPermissionForCurrentTab()) loadCurrentTab() else requestMediaPermission()
    }

    private fun updateTabVisuals() {
        val tabs = listOf(tabPhotos, tabVideos, tabAlbums, tabFavorites)
        tabs.forEach {
            it.setTextColor(ContextCompat.getColor(this, R.color.symbian_text))
            it.setBackgroundResource(R.drawable.bg_tab)
        }
        val active = when (currentTab) {
            Tab.PHOTOS -> tabPhotos
            Tab.VIDEOS -> tabVideos
            Tab.ALBUMS -> tabAlbums
            Tab.FAVORITES -> tabFavorites
        }
        active.setTextColor(ContextCompat.getColor(this, R.color.symbian_white))
        active.setBackgroundResource(R.drawable.bg_tab_selected)
        sectionTitle.text = when (currentTab) {
            Tab.PHOTOS -> "Photos"
            Tab.VIDEOS -> "Videos"
            Tab.ALBUMS -> "Albums"
            Tab.FAVORITES -> "Favorites"
        }
    }

    private fun loadCurrentTab() {
        permissionText.visibility = View.GONE
        emptyText.visibility = View.GONE
        if (currentTab == Tab.ALBUMS || currentTab == Tab.FAVORITES) {
            if (selectionMode) exitSelectionMode()
            if (currentTab == Tab.ALBUMS) loadAlbums() else loadFavorites()
        } else {
            val images = currentTab == Tab.PHOTOS
            currentItems = MediaRepository.loadMedia(contentResolver, images, !images)
            countText.text = currentItems.size.toString()
            updateSelectionUi()
            applySearch()
        }
    }

    private fun applySearch() {
        if (!::searchInput.isInitialized || currentTab == Tab.ALBUMS || currentTab == Tab.FAVORITES) return
        val query = searchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val filtered = if (query.isBlank()) currentItems else currentItems.filter { it.name.lowercase().contains(query) }
        countText.text = filtered.size.toString()
        showEmpty(filtered.isEmpty(), if (query.isBlank()) "No media found" else "No matching media")
        if (roundMode) {
            recycler.visibility = View.GONE
            roundGallery.visibility = View.VISIBLE
            roundGallery.submitItems(filtered) { openMedia(it, filtered) }
        } else {
            recycler.visibility = View.VISIBLE
            roundGallery.visibility = View.GONE
            recycler.layoutManager = GridLayoutManager(this, 3)
            recycler.adapter = MediaAdapter(
                filtered,
                selectedItems.keys,
                selectionMode,
                onClick = { item ->
                    if (selectionMode) toggleSelected(item) else openMedia(item, filtered)
                },
                onLongClick = { item ->
                    if (!selectionMode) enterSelectionMode()
                    toggleSelected(item)
                }
            )
        }
    }

    private fun enterSelectionMode() {
        if (currentTab == Tab.ALBUMS || currentTab == Tab.FAVORITES) {
            Toast.makeText(this, "Selection is available in Photos and Videos", Toast.LENGTH_SHORT).show()
            return
        }
        if (roundMode) setRoundMode(false)
        selectionMode = true
        selectionBar.visibility = View.VISIBLE
        btnSelect.text = "Selecting"
        updateSelectionUi()
        applySearch()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedItems.clear()
        selectionBar.visibility = View.GONE
        btnSelect.text = "Select"
        applySearch()
    }

    private fun toggleSelected(item: MediaItem) {
        val key = item.uri.toString()
        if (selectedItems.containsKey(key)) selectedItems.remove(key) else selectedItems[key] = item
        updateSelectionUi()
        recycler.adapter?.notifyDataSetChanged()
    }

    private fun selectAllVisible() {
        val query = searchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val visible = if (query.isBlank()) currentItems else currentItems.filter { it.name.lowercase().contains(query) }
        if (visible.isNotEmpty() && visible.all { selectedItems.containsKey(it.uri.toString()) }) {
            visible.forEach { selectedItems.remove(it.uri.toString()) }
        } else visible.forEach { selectedItems[it.uri.toString()] = it }
        updateSelectionUi()
        recycler.adapter?.notifyDataSetChanged()
    }

    private fun updateSelectionUi() {
        selectionCount.text = "${selectedItems.size} selected"
        findViewById<TextView>(R.id.btnSelectAll).text = if (currentItems.isNotEmpty() && currentItems.all { selectedItems.containsKey(it.uri.toString()) }) "None" else "All"
        findViewById<View>(R.id.btnShareSelected).alpha = if (selectedItems.isEmpty()) .45f else 1f
        findViewById<View>(R.id.btnMoveSelected).alpha = if (selectedItems.isEmpty()) .45f else 1f
        findViewById<View>(R.id.btnDeleteSelected).alpha = if (selectedItems.isEmpty()) .45f else 1f
    }

    private fun shareSelected() {
        val uris = selectedItems.values.map { it.uri }
        if (uris.isEmpty()) { Toast.makeText(this, "Pehle files select karein", Toast.LENGTH_SHORT).show(); return }
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uris.first()) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
            clipData = ClipData.newUri(contentResolver, "Shared media", uris.first())
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            var clips: ClipData? = null
            uris.forEach { uri ->
                if (clips == null) clips = ClipData.newUri(contentResolver, "Shared media", uri)
                else clips?.addItem(ClipData.Item(uri))
            }
            clipData = clips
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share selected media"))
    }

    private fun beginMoveSelected() {
        if (selectedItems.isEmpty()) { Toast.makeText(this, "Pehle files select karein", Toast.LENGTH_SHORT).show(); return }
        moveFolderLauncher.launch(null)
    }

    private fun moveSelectedTo(treeUri: Uri) {
        val destination = DocumentFile.fromTreeUri(this, treeUri)
        if (destination == null || !destination.canWrite()) {
            Toast.makeText(this, "Selected folder mein write access nahi hai", Toast.LENGTH_LONG).show()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) { }
        val uris = selectedItems.values.map { it.uri }
        var copied = 0
        try {
            uris.forEach { source ->
                val name = selectedItems[source.toString()]?.name ?: "media_${System.currentTimeMillis()}"
                val mime = contentResolver.getType(source) ?: "application/octet-stream"
                val target = destination.createFile(mime, name) ?: throw IllegalStateException("Cannot create $name")
                contentResolver.openInputStream(source).use { input ->
                    if (input == null) throw IllegalStateException("Cannot read $name")
                    contentResolver.openOutputStream(target.uri, "w").use { output ->
                        if (output == null) throw IllegalStateException("Cannot write $name")
                        input.copyTo(output)
                    }
                }
                copied++
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Copied $copied file(s); move stopped: ${e.message ?: "error"}", Toast.LENGTH_LONG).show()
            loadCurrentTab()
            return
        }
        pendingMove = true
        requestDeleteUris(uris)
    }

    private fun confirmDeleteSelected() {
        if (selectedItems.isEmpty()) { Toast.makeText(this, "Pehle files select karein", Toast.LENGTH_SHORT).show(); return }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete selected files?")
            .setMessage("${selectedItems.size} photo/video file(s) delete ki jayengi.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                pendingMove = false
                requestDeleteUris(selectedItems.values.map { it.uri })
            }.show()
    }

    private fun requestDeleteUris(uris: List<Uri>) {
        pendingDeleteUris = uris
        pendingDeleteIndex = 0
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val request = MediaStore.createDeleteRequest(contentResolver, uris)
                deletePermissionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                Toast.makeText(this, "Delete request failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else deleteLegacyNext()
    }

    private fun deleteLegacyNext() {
        while (pendingDeleteIndex < pendingDeleteUris.size) {
            val uri = pendingDeleteUris[pendingDeleteIndex]
            try {
                contentResolver.delete(uri, null, null)
                pendingDeleteIndex++
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                    val request = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                    deletePermissionLauncher.launch(request)
                    return
                }
                Toast.makeText(this, "Could not delete file: ${e.message ?: "permission denied"}", Toast.LENGTH_LONG).show()
                pendingDeleteIndex++
            }
        }
        finishDeleteOperation()
    }

    private fun finishDeleteOperation() {
        val count = pendingDeleteUris.size
        selectedItems.clear()
        selectionMode = false
        selectionBar.visibility = View.GONE
        btnSelect.text = "Select"
        pendingDeleteUris = emptyList()
        pendingDeleteIndex = 0
        val wasMove = pendingMove
        pendingMove = false
        loadCurrentTab()
        Toast.makeText(this, if (wasMove) "Moved $count file(s)" else "Deleted $count file(s)", Toast.LENGTH_SHORT).show()
    }

    private fun loadAlbums() {
        recycler.visibility = View.VISIBLE
        roundGallery.visibility = View.GONE
        recycler.layoutManager = LinearLayoutManager(this)
        val all = MediaRepository.loadMedia(contentResolver, true, true)
        val folders = all.groupBy { it.bucketId.ifBlank { "all" } }.map { (id, list) ->
            FolderItem(id, list.firstOrNull()?.bucketName?.ifBlank { "Pictures" } ?: "Pictures", list.size)
        }.sortedBy { it.name.lowercase() }
        countText.text = folders.size.toString()
        showEmpty(folders.isEmpty(), "No albums found")
        recycler.adapter = FolderAdapter(folders) { folder ->
            startActivity(Intent(this, FolderActivity::class.java).putExtra("folder_id", folder.id).putExtra("folder_name", folder.name))
        }
    }

    private fun loadFavorites() {
        recycler.visibility = View.VISIBLE
        roundGallery.visibility = View.GONE
        recycler.layoutManager = GridLayoutManager(this, 3)
        val favorites = getSharedPreferences("mani_gallery_favorites", MODE_PRIVATE).getStringSet("uris", emptySet()) ?: emptySet()
        val allMedia = MediaRepository.loadMedia(contentResolver, true, true)
        val favoriteItems = allMedia.filter { favorites.contains(it.uri.toString()) }
        countText.text = favoriteItems.size.toString()
        showEmpty(favoriteItems.isEmpty(), "No favorites yet. Open a photo and use More → Add / Remove Favorite.")
        recycler.adapter = MediaAdapter(favoriteItems, onClick = { openMedia(it, favoriteItems) }, onLongClick = { item ->
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Remove favorite?")
                .setMessage(item.name).setNegativeButton("Cancel", null)
                .setPositiveButton("Remove") { _, _ ->
                    val prefs = getSharedPreferences("mani_gallery_favorites", MODE_PRIVATE)
                    val updated = prefs.getStringSet("uris", emptySet())?.toMutableSet() ?: mutableSetOf()
                    updated.remove(item.uri.toString())
                    prefs.edit().putStringSet("uris", updated).apply()
                    loadFavorites()
                }.show()
        })
    }

    private fun setRoundMode(enabled: Boolean) {
        roundMode = enabled
        if (currentTab == Tab.ALBUMS || currentTab == Tab.FAVORITES) roundMode = false
        gridButton.setBackgroundResource(if (!roundMode) R.drawable.bg_toggle_selected else R.drawable.bg_toggle)
        roundButton.setBackgroundResource(if (roundMode) R.drawable.bg_toggle_selected else R.drawable.bg_toggle)
        gridButton.setTextColor(ContextCompat.getColor(this, if (!roundMode) R.color.symbian_white else R.color.symbian_text))
        roundButton.setTextColor(ContextCompat.getColor(this, if (roundMode) R.color.symbian_white else R.color.symbian_text))
        applySearch()
    }

    private fun openMedia(item: MediaItem, visibleItems: List<MediaItem> = currentItems) {
        if (item.isVideo) {
            val videos = visibleItems.filter { it.isVideo }
            val videoPosition = videos.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
            startActivity(Intent(this, VideoPlayerActivity::class.java)
                .putExtra("uri", item.uri.toString())
                .putExtra("name", item.name)
                .putStringArrayListExtra("video_list", ArrayList(videos.map { it.uri.toString() }))
                .putStringArrayListExtra("video_names", ArrayList(videos.map { it.name }))
                .putExtra("video_position", videoPosition))
            return
        }

        // Photo viewer ko current visible Photos ki poori list bhejo,
        // taaki ek photo open karne ke baad left/right swipe se next/previous photo khule.
        val photos = visibleItems.filter { !it.isVideo }
        val position = photos.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
        val intent = Intent(this, PhotoViewerActivity::class.java).apply {
            putStringArrayListExtra("image_list", ArrayList(photos.map { it.uri.toString() }))
            putExtra("position", position)
            putExtra("uri", item.uri.toString())
            putExtra("name", item.name)
        }
        startActivity(intent)
    }

    private fun showEmpty(isEmpty: Boolean, message: String) {
        emptyText.text = message
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun hasAnyMediaPermission(): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun hasRequiredMediaPermissionForCurrentTab(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return hasAnyMediaPermission()
        val permission = when (currentTab) {
            Tab.PHOTOS, Tab.ALBUMS, Tab.FAVORITES -> Manifest.permission.READ_MEDIA_IMAGES
            Tab.VIDEOS -> Manifest.permission.READ_MEDIA_VIDEO
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
        else if (Build.VERSION.SDK_INT <= 28) permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        else permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
    }
}
