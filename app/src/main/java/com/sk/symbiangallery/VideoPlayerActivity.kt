package com.sk.symbiangallery

import android.content.Context
import android.media.AudioManager
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Environment
import java.io.OutputStream
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import android.widget.SeekBar
import android.widget.ProgressBar
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale

class VideoPlayerActivity : AppCompatActivity(), GesturePlayerView.Listener {
    private var player: ExoPlayer? = null
    private lateinit var playerView: GesturePlayerView
    private lateinit var videoTitle: TextView
    private lateinit var topBar: View
    private lateinit var gestureOverlay: TextView
    private lateinit var audioManager: AudioManager
    private var baseBrightness = .5f
    private var longPress2x = false
    private var controlsLocked = false
    private var currentVideoUri: Uri? = null
    private var currentVideoName = "Video"
    private var currentSpeed = 1f
    private var details = VideoDetails()
    private var controlsHidden = false
    private var uiHideRunnable: Runnable? = null
    private var zoomScale = 1f
    private var volumeFraction = 0f
    private var videoUris: ArrayList<String> = arrayListOf()
    private var videoNames: ArrayList<String> = arrayListOf()
    private var videoPosition = 0
    private var repeatStartMs: Long? = null
    private var repeatEndMs: Long? = null
    private var audioOnly = false

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addSubtitle(uri)
    }

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            currentVideoUri = uri
            currentVideoName = uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
            videoTitle.text = currentVideoName
            details = readVideoDetails(uri, currentVideoName)
            createPlayer(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.playerView)
        playerView.listener = this
        playerView.useController = false
        videoTitle = findViewById(R.id.videoTitle)
        topBar = findViewById(R.id.topBar)
        gestureOverlay = findViewById(R.id.gestureOverlay)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        baseBrightness = window.attributes.screenBrightness.takeIf { it > 0f } ?: .5f

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPlay).setOnClickListener { player?.let { it.playWhenReady = !it.isPlaying }; scheduleUiHide() }
        findViewById<View>(R.id.btnCenterBack10).setOnClickListener { onSeek(-10_000L); scheduleUiHide() }
        findViewById<View>(R.id.btnCenterForward10).setOnClickListener { onSeek(10_000L); scheduleUiHide() }
        findViewById<View>(R.id.btnRadial).setOnClickListener { toggleRadial() }
        findViewById<View>(R.id.btnPrev).setOnClickListener { playAdjacentVideo(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { playAdjacentVideo(1) }
        findViewById<View>(R.id.btnBack10).setOnClickListener { onSeek(-10_000L) }
        findViewById<View>(R.id.btnForward10).setOnClickListener { onSeek(10_000L) }
        findViewById<View>(R.id.btnSpeed).setOnClickListener { showSpeedDialog() }
        findViewById<View>(R.id.btnAudio).setOnClickListener { showAudioTracks() }
        findViewById<View>(R.id.btnSubtitle).setOnClickListener { subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/*")) }
        findViewById<View>(R.id.btnAspect).setOnClickListener { showAspectDialog() }
        findViewById<View>(R.id.btnRepeat).setOnClickListener { toggleAbRepeat() }
        findViewById<View>(R.id.btnFrame).setOnClickListener { saveFrameScreenshot() }
        findViewById<View>(R.id.btnInfo).setOnClickListener { showInfoDialog() }
        findViewById<View>(R.id.btnLock).setOnClickListener { toggleLock() }
        findViewById<View>(R.id.btnFullscreen).setOnClickListener { toggleFullscreen() }
        findViewById<View>(R.id.btnFile).setOnClickListener { videoPicker.launch(arrayOf("video/*")) }
        findViewById<View>(R.id.btnSubtitleTop).setOnClickListener { subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "text/vtt", "application/*")) }
        findViewById<View>(R.id.btnDecoder).setOnClickListener { toast("Hardware decoder: Media3 default") }
        findViewById<View>(R.id.btnMore).setOnClickListener { showVideoMoreOptions() }
        findViewById<View>(R.id.btnSpeedTop).setOnClickListener { showSpeedDialog() }
        findViewById<View>(R.id.btnScreenshotTop).setOnClickListener { saveFrameScreenshot() }
        findViewById<View>(R.id.btnAudioOnlyTop).setOnClickListener { toggleAudioOnly() }
        findViewById<View>(R.id.btnFitTop).setOnClickListener { showAspectDialog() }
        findViewById<View>(R.id.btnPipTop).setOnClickListener { enterPipMode() }

        findViewById<SeekBar>(R.id.seekBar).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) player?.let { if (it.duration > 0) it.seekTo(it.duration * progress / 1000L) }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        videoUris = intent.getStringArrayListExtra("video_list") ?: arrayListOf()
        videoNames = intent.getStringArrayListExtra("video_names") ?: arrayListOf()
        videoPosition = intent.getIntExtra("video_position", 0)
        val uri = intent.getStringExtra("uri")?.let(Uri::parse)
        currentVideoUri = uri
        currentVideoName = intent.getStringExtra("name") ?: "Video"
        videoTitle.text = currentVideoName
        if (uri != null) {
            details = readVideoDetails(uri, currentVideoName)
            createPlayer(uri)
        }
        playerView.setOnClickListener {
            if (!controlsLocked) {
                if (controlsHidden) showControls() else scheduleUiHide()
            }
        }
        window.decorView.post(uiTicker)
        scheduleUiHide()
    }

    private fun createPlayer(uri: Uri, startPosition: Long = 0L, subtitle: MediaItem.SubtitleConfiguration? = null) {
        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            playerView.useController = false
            val builder = MediaItem.Builder().setUri(uri)
            if (subtitle != null) builder.setSubtitleConfigurations(listOf(subtitle))
            exo.setMediaItem(builder.build(), startPosition)
            exo.prepare()
            exo.playWhenReady = true
            exo.setPlaybackSpeed(currentSpeed)
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && details.durationMs <= 0) {
                        details = details.copy(durationMs = exo.duration)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    findViewById<TextView>(R.id.btnPlay).text = if (isPlaying) "Ⅱ" else "▶"
                }
            })
        }
    }

    override fun onSeek(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0 } ?: return
        val target = (p.currentPosition + deltaMs).coerceIn(0L, duration)
        p.seekTo(target)
        showGesture(if (deltaMs >= 0) "⏩ +${formatDuration(kotlin.math.abs(deltaMs))}" else "⏪ -${formatDuration(kotlin.math.abs(deltaMs))}")
    }

    override fun onBrightness(delta: Float) {
        baseBrightness = (baseBrightness + delta).coerceIn(.03f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = baseBrightness }
        val pct = (baseBrightness * 100).toInt()
        findViewById<View>(R.id.brightnessHud).visibility = View.VISIBLE
        findViewById<ProgressBar>(R.id.brightnessProgress).progress = pct
        findViewById<TextView>(R.id.brightnessValue).text = "$pct%"
        showGesture("☀ $pct%")
    }

    override fun onVolume(delta: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volumeFraction += delta
        val steps = if (volumeFraction >= 0f) kotlin.math.floor(volumeFraction).toInt() else kotlin.math.ceil(volumeFraction).toInt()
        if (steps != 0) {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val value = (current + steps).coerceIn(0, max)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
            volumeFraction -= steps
        }
        val value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pct = ((value * 100f) / max).toInt()
        findViewById<View>(R.id.volumeHud).visibility = View.VISIBLE
        findViewById<ProgressBar>(R.id.volumeProgress).progress = pct
        findViewById<TextView>(R.id.volumeValue).text = "$pct%"
        findViewById<TextView>(R.id.volumeIcon).text = if (value == 0) "🔇" else "🔊"
        showGesture("🔊 $pct%")
    }

    override fun onZoom(delta: Float) {
        zoomScale = (zoomScale + delta).coerceIn(1f, 2.5f)
        playerView.scaleX = zoomScale
        playerView.scaleY = zoomScale
        showGesture("Zoom ${String.format(Locale.US, "%.1f", zoomScale)}×")
    }

    override fun onDoubleTapSeek(forward: Boolean) {
        onSeek(if (forward) 10_000L else -10_000L)
    }

    override fun onLongPressStart() {
        val p = player ?: return
        if (!p.isPlaying) return
        longPress2x = true
        p.setPlaybackSpeed(2f)
        showGesture("⚡ 2×")
    }

    override fun onLongPressEnd() {
        if (longPress2x) {
            player?.setPlaybackSpeed(currentSpeed)
            showGesture("▶ ${currentSpeed}×")
        }
        longPress2x = false
    }

    override fun onGestureFinished() {
        gestureOverlay.removeCallbacks(hideRunnable)
        gestureOverlay.postDelayed(hideRunnable, 350)
        gestureOverlay.postDelayed({
            findViewById<View>(R.id.brightnessHud).visibility = View.GONE
            findViewById<View>(R.id.volumeHud).visibility = View.GONE
        }, 350)
    }

    private fun showGesture(text: String) {
        gestureOverlay.animate().cancel()
        gestureOverlay.text = text
        gestureOverlay.visibility = View.VISIBLE
        gestureOverlay.alpha = 1f
        gestureOverlay.removeCallbacks(hideRunnable)
        gestureOverlay.postDelayed(hideRunnable, 850)
    }
    private val hideRunnable = Runnable { gestureOverlay.animate().alpha(0f).setDuration(180).withEndAction { gestureOverlay.visibility = View.GONE }.start() }
    private fun hideGesture() { gestureOverlay.removeCallbacks(hideRunnable); hideRunnable.run() }

    private fun toggleRadial() {
        val menu = findViewById<View>(R.id.radialMenu)
        menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun scheduleUiHide() {
        if (controlsLocked) return
        showControls()
        uiHideRunnable?.let { window.decorView.removeCallbacks(it) }
        val r = Runnable { hideControls() }
        uiHideRunnable = r
        window.decorView.postDelayed(r, 3500)
    }

    private fun showControls() {
        controlsHidden = false
        findViewById<View>(R.id.topBar).animate().alpha(1f).setDuration(150).start()
        findViewById<View>(R.id.bottomBar).animate().alpha(1f).setDuration(150).start()
        findViewById<View>(R.id.btnPlay).animate().alpha(1f).setDuration(150).start()
    }

    private fun hideControls() {
        if (controlsLocked) return
        controlsHidden = true
        findViewById<View>(R.id.topBar).animate().alpha(0f).setDuration(220).start()
        findViewById<View>(R.id.bottomBar).animate().alpha(0f).setDuration(220).start()
        findViewById<View>(R.id.btnPlay).animate().alpha(0f).setDuration(220).start()
        findViewById<View>(R.id.radialMenu).visibility = View.GONE
    }

    private fun toggleFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController
            if (controller != null) {
                if (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0) {
                    controller.show(WindowInsets.Type.systemBars())
                } else {
                    controller.hide(WindowInsets.Type.systemBars())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0) 0 else (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
        scheduleUiHide()
    }

    private fun enterPipMode() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try { enterPictureInPictureMode() } catch (_: Exception) { toast("Picture-in-picture unavailable") }
        } else toast("Picture-in-picture requires Android 8+")
    }

    private fun playAdjacentVideo(direction: Int) {
        if (videoUris.isEmpty()) { toast("Open videos from the Gallery to use Previous/Next"); return }
        val next = videoPosition + direction
        if (next !in videoUris.indices) { toast(if (direction < 0) "This is the first video" else "This is the last video"); return }
        videoPosition = next
        val uri = Uri.parse(videoUris[next])
        currentVideoUri = uri
        currentVideoName = videoNames.getOrNull(next) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
        videoTitle.text = currentVideoName
        details = readVideoDetails(uri, currentVideoName)
        repeatStartMs = null; repeatEndMs = null
        createPlayer(uri)
        scheduleUiHide()
    }

    private fun toggleAbRepeat() {
        val p = player ?: return
        if (repeatStartMs != null && repeatEndMs != null) {
            repeatStartMs = null; repeatEndMs = null; toast("A-B repeat reset"); return
        }
        if (repeatStartMs == null) {
            repeatStartMs = p.currentPosition; repeatEndMs = null
            toast("A point set at ${formatDuration(p.currentPosition)}. Tap A-B again to set B.")
        } else {
            val end = p.currentPosition
            val start = repeatStartMs ?: 0L
            if (end <= start + 500) { toast("B point must be after A"); return }
            repeatEndMs = end
            toast("A-B repeat enabled: ${formatDuration(start)} – ${formatDuration(end)}. Tap A-B to reset.")
        }
    }

    private fun toggleAudioOnly() {
        audioOnly = !audioOnly
        playerView.visibility = if (audioOnly) View.INVISIBLE else View.VISIBLE
        toast(if (audioOnly) "Audio-only mode enabled" else "Video display restored")
    }

    private fun saveFrameScreenshot() {
        val uri = currentVideoUri ?: run { toast("No video selected"); return }
        val positionUs = (player?.currentPosition ?: 0L) * 1000L
        val bitmap = try {
            MediaMetadataRetriever().let { retriever ->
                retriever.setDataSource(this, uri)
                val frame = retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                frame
            }
        } catch (_: Exception) { null }
        if (bitmap == null) { toast("Could not capture a frame from this video"); return }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "MANI_Frame_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MANI Gallery")
            }
            val outUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Cannot create image")
            contentResolver.openOutputStream(outUri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
            toast("Frame saved to Pictures/MANI Gallery")
        } catch (e: Exception) { toast("Frame save failed: ${e.message ?: "error"}") }
        finally { bitmap.recycle() }
    }

    private fun showVideoMoreOptions() {
        AlertDialog.Builder(this).setTitle("Video options")
            .setItems(arrayOf("Video information", "Add / Remove Favorite")) { _, which ->
                if (which == 0) showInfoDialog() else toggleVideoFavorite()
            }.show()
    }

    private fun toggleVideoFavorite() {
        val uri = currentVideoUri ?: return
        val prefs = getSharedPreferences("mani_gallery_favorites", MODE_PRIVATE)
        val favorites = prefs.getStringSet("uris", emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = uri.toString()
        val added = if (favorites.contains(key)) { favorites.remove(key); false } else { favorites.add(key); true }
        prefs.edit().putStringSet("uris", favorites).apply()
        toast(if (added) "Added to Favorites" else "Removed from Favorites")
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Video Information")
            .setMessage("Name: ${details.name}\nSize: ${details.size}\nDuration: ${formatDuration(details.durationMs)}\nResolution: ${details.resolution}\nFPS: ${if (details.fps > 0) String.format(Locale.US, "%.2f", details.fps) else "Unknown"}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = floatArrayOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f)
        val labels = speeds.map { "${it}×" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Playback speed").setItems(labels) { _, which ->
            currentSpeed = speeds[which]
            player?.setPlaybackSpeed(currentSpeed)
        }.show()
    }

    private fun showAspectDialog() {
        val names = arrayOf("Fit", "Fill", "16:9", "4:3", "Zoom")
        val modes = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
        AlertDialog.Builder(this).setTitle("Screen / Aspect ratio").setItems(names) { _, which -> playerView.resizeMode = modes[which] }.show()
    }

    private fun showAudioTracks() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) { Toast.makeText(this, "No alternate audio track", Toast.LENGTH_SHORT).show(); return }
        val labels = mutableListOf("Auto")
        val choices = mutableListOf<(() -> Unit)?>(null)
        groups.forEachIndexed { gi, group ->
            for (ti in 0 until group.length) {
                val format = group.getTrackFormat(ti)
                val lang = format.language ?: "und"
                val label = format.label ?: "Audio ${ti + 1}"
                labels += "$label ($lang)"
                choices += { p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(ti))).build() }
            }
        }
        AlertDialog.Builder(this).setTitle("Audio track").setItems(labels.toTypedArray()) { _, which -> choices[which]?.invoke() }.show()
    }

    private fun addSubtitle(uri: Uri) {
        val mime = when {
            uri.toString().lowercase(Locale.US).endsWith(".srt") -> "application/x-subrip"
            uri.toString().lowercase(Locale.US).endsWith(".vtt") -> "text/vtt"
            else -> "text/x-ssa"
        }
        val sub = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val pos = player?.currentPosition ?: 0L
        createPlayer(currentVideoUri ?: return, pos, sub)
        Toast.makeText(this, "Subtitle loaded", Toast.LENGTH_SHORT).show()
    }

    private fun toggleLock() {
        controlsLocked = !controlsLocked
        playerView.gesturesLocked = controlsLocked
        // This screen uses its own overlay controls; never enable Media3's default controller.
        playerView.useController = false
        topBar.visibility = if (controlsLocked) View.GONE else View.VISIBLE
        if (controlsLocked) Toast.makeText(this, "Controls locked • long-press to unlock", Toast.LENGTH_SHORT).show()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (controlsLocked && ev.actionMasked == android.view.MotionEvent.ACTION_UP) {
            // A tap while locked unlocks the player; this prevents getting stuck in lock mode.
            controlsLocked = false
            playerView.gesturesLocked = false
            playerView.useController = false
            topBar.visibility = View.VISIBLE
            Toast.makeText(this, "Controls unlocked", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun readVideoDetails(uri: Uri, fallbackName: String): VideoDetails {
        var name = fallbackName; var sizeBytes = -1L; var duration = 0L; var width = 0; var height = 0; var fps = 0f
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                fun idx(s: String) = c.getColumnIndex(s)
                idx(MediaStore.MediaColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                idx(MediaStore.MediaColumns.SIZE).takeIf { it >= 0 }?.let { sizeBytes = c.getLong(it) }
                idx(MediaStore.MediaColumns.DURATION).takeIf { it >= 0 }?.let { duration = c.getLong(it) }
                idx(MediaStore.MediaColumns.WIDTH).takeIf { it >= 0 }?.let { width = c.getInt(it) }
                idx(MediaStore.MediaColumns.HEIGHT).takeIf { it >= 0 }?.let { height = c.getInt(it) }
            }
        }
        try {
            MediaMetadataRetriever().also { r ->
                r.setDataSource(this, uri)
                duration = duration.takeIf { it > 0 } ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = width.takeIf { it > 0 } ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = height.takeIf { it > 0 } ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
                r.release()
            }
        } catch (_: Exception) { }
        return VideoDetails(uri.toString(), name, formatBytes(sizeBytes), duration, if (width > 0 && height > 0) "${width}×${height}" else "Unknown", fps)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 0 -> "Unknown"; bytes < 1024 -> "$bytes B"; bytes < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0); bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0); else -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    }
    private fun formatDuration(ms: Long): String { val s = ms / 1000; return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s/3600, (s/60)%60, s%60) else String.format(Locale.US, "%02d:%02d", s/60, s%60) }
    private val uiTicker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                val duration = p.duration.coerceAtLeast(0L)
                val position = p.currentPosition.coerceAtLeast(0L)
                findViewById<TextView>(R.id.positionText).text = formatDuration(position)
                findViewById<TextView>(R.id.remainingText).text = "-${formatDuration((duration - position).coerceAtLeast(0L))}"
                if (duration > 0) findViewById<SeekBar>(R.id.seekBar).progress = ((position * 1000L) / duration).toInt()
                val a = repeatStartMs; val b = repeatEndMs
                if (a != null && b != null && position >= b) p.seekTo(a)
            }
            window.decorView.postDelayed(this, 500)
        }
    }

    override fun onStop() {
        window.decorView.removeCallbacks(uiTicker)
        uiHideRunnable?.let { window.decorView.removeCallbacks(it) }
        playerView.player = null
        player?.release()
        player = null
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (player == null && currentVideoUri != null) createPlayer(currentVideoUri!!)
        window.decorView.removeCallbacks(uiTicker)
        window.decorView.post(uiTicker)
    }

    private data class VideoDetails(
        val uri: String = "",
        val name: String = "Video",
        val size: String = "Unknown",
        val durationMs: Long = 0L,
        val resolution: String = "Unknown",
        val fps: Float = 0f
    )
}
