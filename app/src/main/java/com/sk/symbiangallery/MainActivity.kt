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
// AdMob imports
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

    // AdMob App Open Ad Variables
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

        // Initialize Mobile Ads SDK and Load App Open Ad
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
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applySearch() }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        updateTabVisuals()
        if (hasRequiredMediaPermissionForCurrentTab()) loadCurrentTab() else requestMediaPermission()
    }

    // App Open Ad Loading Logic
    private fun loadAd() {
        if (isLoadingAd || isAdAvailable()) {
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            this,
            "ca-app-pub-1692534879845424/5208240350",
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = System.currentTimeMillis()
                    showAdIfAvailable()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && (System.currentTimeMillis() - loadTime < 4 * 3600 * 1000)
    }

    private fun showAdIfAvailable() {
        if (isAdAvailable()) {
            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    loadAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    appOpenAd = null
                    loadAd()
                }

                override fun onAdShowedFullScreenContent() {}
            }
            appOpenAd?.show(this)
        } else {
            loadAd()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::recycler.isInitialized && hasAnyMediaPermission()) loadCurrentTab()
        showAdIfAvailable()
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
        btnSelect.text
