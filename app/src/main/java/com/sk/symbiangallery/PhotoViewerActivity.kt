package com.sk.symbiangallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import android.app.WallpaperManager
import android.os.Build
import android.content.ContentValues
import android.app.RecoverableSecurityException
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.EditText
import android.widget.Toast
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.viewpager2.widget.ViewPager2
import java.util.Locale

class PhotoViewerActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var photoTitle: TextView
    private lateinit var photoName: TextView
    private var imageUriList: ArrayList<String> = arrayListOf()
    private var currentPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_viewer)
        viewPager = findViewById(R.id.viewPager)
        photoTitle = findViewById(R.id.photoTitle)
        photoName = findViewById(R.id.photoName)

        imageUriList = intent.getStringArrayListExtra("image_list") ?: arrayListOf()
        currentPosition = intent.getIntExtra("position", 0)
        if (imageUriList.isEmpty()) intent.getStringExtra("uri")?.let { imageUriList.add(it) }
        if (imageUriList.isEmpty()) { finish(); return }
        currentPosition = currentPosition.coerceIn(0, imageUriList.lastIndex)

        viewPager.adapter = PhotoPagerAdapter(imageUriList)
        viewPager.setCurrentItem(currentPosition, false)
        updateTitle(currentPosition)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updateTitle(position)
            }
        })

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnMore).setOnClickListener { showMoreOptions() }
    }

    private val mediaActionLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) { Toast.makeText(this, "Action completed", Toast.LENGTH_SHORT).show(); finish() } 
    }

    private fun showMoreOptions() {
        val options = arrayOf("Share", "Delete", "Rename", "Set as Wallpaper", "Move to Bin", "Details", "Add / Remove Favorite")
        AlertDialog.Builder(this).setTitle("Image options").setItems(options) { _, which ->
            when (which) {
                0 -> shareCurrentImage()
                1 -> confirmDelete(false)
                2 -> renameCurrentImage()
                3 -> setAsWallpaper()
                4 -> confirmDelete(true)
                5 -> showImageDetails()
                6 -> toggleFavorite()
            }
        }.show()
    }

    private fun toggleFavorite() {
        val uri = currentUri() ?: return
        val prefs = getSharedPreferences("mani_gallery_favorites", MODE_PRIVATE)
        val favorites = prefs.getStringSet("uris", emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = uri.toString()
        val added = if (favorites.contains(key)) { favorites.remove(key); false } else { favorites.add(key); true }
        prefs.edit().putStringSet("uris", favorites).apply()
        Toast.makeText(this, if (added) "Added to Favorites" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(toBin: Boolean) {
        val uri = currentUri() ?: return
        AlertDialog.Builder(this).setTitle(if (toBin) "Move to Bin?" else "Delete image?")
            .setMessage(if (toBin) "This image will be moved to the system trash when supported." else "This image will be deleted.")
            .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ ->
                try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val request = if (toBin) MediaStore.createTrashRequest(contentResolver, listOf(uri), true)
                                      else MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                        mediaActionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    } else {
                        val deleted = contentResolver.delete(uri, null, null)
                        Toast.makeText(this, if (deleted > 0) "Image deleted" else "Could not delete image", Toast.LENGTH_SHORT).show()
                        if (deleted > 0) finish()
                    }
                } catch (e: Exception) { Toast.makeText(this, "Action needs file permission: ${e.message ?: "not allowed"}", Toast.LENGTH_LONG).show() }
            }.show()
    }

    private fun renameCurrentImage() {
        val uri = currentUri() ?: return
        val input = EditText(this).apply { setText(readImageDetails(uri).name); setSelection(text.length) }
        AlertDialog.Builder(this).setTitle("Rename image").setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank()) { Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                try {
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
                    val count = contentResolver.update(uri, values, null, null)
                    Toast.makeText(this, if (count > 0) "Renamed" else "Rename not supported for this file", Toast.LENGTH_SHORT).show()
                    if (count > 0) updateTitle(currentPosition)
                } catch (e: Exception) { Toast.makeText(this, "Rename needs permission: ${e.message ?: "not allowed"}", Toast.LENGTH_LONG).show() }
            }.show()
    }

    private fun setAsWallpaper() {
        val uri = currentUri() ?: return
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: throw IllegalStateException("Unsupported image")
                WallpaperManager.getInstance(this).setBitmap(bitmap)
                Toast.makeText(this, "Wallpaper set", Toast.LENGTH_SHORT).show()
                bitmap.recycle()
            }
        } catch (e: Exception) { Toast.makeText(this, "Could not set wallpaper: ${e.message ?: "error"}", Toast.LENGTH_LONG).show() }
    }

    private fun currentUri(): Uri? = imageUriList.getOrNull(currentPosition)?.let(Uri::parse)

    private fun shareCurrentImage() {
        val uri = currentUri() ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uri) ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, "Shared image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Image"))
    }

    private fun showImageDetails() {
        val uri = currentUri() ?: return
        val info = readImageDetails(uri)
        AlertDialog.Builder(this)
            .setTitle("Full Image Details")
            .setMessage("Image name: ${info.name}\nSize: ${info.size}\nResolution: ${info.resolution}\nMegapixel: ${info.megapixels}\nLocation: ${info.location}\nPath: ${info.path}")
            .setPositiveButton("OK", null)
            .show()
    }

    private data class ImageDetails(
        val name: String, val size: String, val resolution: String,
        val megapixels: String, val location: String, val path: String
    )

    private fun readImageDetails(uri: Uri): ImageDetails {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Image"
        var sizeBytes = -1L
        var width = 0
        var height = 0
        var path = uri.toString()
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE,
                MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    fun value(column: String): Int = c.getColumnIndex(column)
                    value(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    value(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { sizeBytes = c.getLong(it) }
                    value(MediaStore.Images.Media.WIDTH).takeIf { it >= 0 }?.let { width = c.getInt(it) }
                    value(MediaStore.Images.Media.HEIGHT).takeIf { it >= 0 }?.let { height = c.getInt(it) }
                    value(MediaStore.Images.Media.DATA).takeIf { it >= 0 }?.let { c.getString(it)?.takeIf(String::isNotBlank)?.let { filePath -> path = filePath } }
                }
            }
        } catch (_: Exception) { }

        if (width <= 0 || height <= 0) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                if (width <= 0) width = options.outWidth.coerceAtLeast(0)
                if (height <= 0) height = options.outHeight.coerceAtLeast(0)
            } catch (_: Exception) { }
        }

        var location = "Not available"
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = exif.latLong
                if (latLong != null && latLong.size >= 2) {
                    location = String.format(Locale.US, "%.6f, %.6f", latLong[0], latLong[1])
                }
            }
        } catch (_: Exception) { }

        if (path.startsWith("file://")) path = Uri.parse(path).path ?: path
        val resolution = if (width > 0 && height > 0) "${width} × ${height} px" else "Unknown"
        val mp = if (width > 0 && height > 0) String.format(Locale.US, "%.2f MP", width.toLong() * height / 1_000_000.0) else "Unknown"
        return ImageDetails(name, formatBytes(sizeBytes), resolution, mp, location, path)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 0 -> "Unknown"
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)
        else -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    }

    private fun updateTitle(position: Int) {
        val uri = imageUriList.getOrNull(position)?.let(Uri::parse)
        var name = uri?.lastPathSegment?.substringAfterLast('/') ?: "Image"
        try {
            uri?.let { contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf(String::isNotBlank)?.let { name = it }
            } }
        } catch (_: Exception) { }
        photoName.text = name
        photoTitle.text = "${position + 1} / ${imageUriList.size}"
    }
}
