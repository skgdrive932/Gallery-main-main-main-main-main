package com.sk.symbiangallery

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object MediaRepository {

    fun loadMedia(
        contentResolver: ContentResolver,
        includeImages: Boolean = true,
        includeVideos: Boolean = true
    ): List<MediaItem> {
        val result = mutableListOf<MediaItem>()

        if (includeImages) {
            queryCollection(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                isVideo = false,
                result = result
            )
        }

        if (includeVideos) {
            queryCollection(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                isVideo = true,
                result = result
            )
        }

        return result.sortedByDescending { it.dateAdded }
    }

    private fun queryCollection(
        resolver: ContentResolver,
        collection: Uri,
        isVideo: Boolean,
        result: MutableList<MediaItem>
    ) {
        try {
            val projection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED
            )

            if (Build.VERSION.SDK_INT >= 29) {
                projection += MediaStore.MediaColumns.RELATIVE_PATH
            }

            resolver.query(
                collection,
                projection.toTypedArray(),
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val bucketIdIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val dateIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Unknown"
                    val bucketId = if (bucketIdIndex >= 0) {
                        cursor.getString(bucketIdIndex) ?: ""
                    } else {
                        ""
                    }
                    val bucketName = if (bucketNameIndex >= 0) {
                        cursor.getString(bucketNameIndex)?.ifBlank { "Pictures" } ?: "Pictures"
                    } else {
                        "Pictures"
                    }
                    val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L

                    result += MediaItem(
                        id = id,
                        name = name,
                        uri = ContentUris.withAppendedId(collection, id),
                        isVideo = isVideo,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        dateAdded = date
                    )
                }
            }
        } catch (_: SecurityException) {
            // The user may grant only photos or only videos on newer Android versions.
            // Skip the collection that is not currently accessible.
        }
    }
}
