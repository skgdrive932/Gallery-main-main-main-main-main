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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.FullScreenContentCallback

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

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var loadTime: Long = 0

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

        MobileAds.initialize(this) {}
        loadAd()

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
            override fun onTextChanged(s: CharSequence?, start
