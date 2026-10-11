package com.sk.symbiangallery

import android.net.Uri

data class MediaItem(
    val id: Long,
    val name: String,
    val uri: Uri,
    val isVideo: Boolean,
    val bucketId: String,
    val bucketName: String,
    val dateAdded: Long
)