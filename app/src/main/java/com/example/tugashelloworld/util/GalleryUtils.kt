
package com.example.tugashelloworld.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * Menyimpan foto/video ke Galeri perangkat (MediaStore).
 *
 * Foto: Pictures/LaporWarga
 * Video: Movies/LaporWarga
 *
 * Tidak memerlukan izin penyimpanan untuk Android 10+.
 */
object GalleryUtils {

    private const val ALBUM = "LaporWarga"

    /**
     * Menyalin file sumber ke galeri.
     * Mengembalikan Uri item galeri atau null jika gagal.
     */
    suspend fun saveToGallery(
        context: Context,
        source: File,
        isVideo: Boolean
    ): Uri? = withContext(Dispatchers.IO) {

        if (!source.exists() || !source.isFile) {
            return@withContext null
        }

        val resolver = context.applicationContext.contentResolver
        val uniqueId = UUID.randomUUID().toString()
            .replace("-", "")
            .take(8)

        val fileName = if (isVideo) {
            "VID_${System.currentTimeMillis()}_$uniqueId.mp4"
        } else {
            "IMG_${System.currentTimeMillis()}_$uniqueId.jpg"
        }

        val relativePath = (
                if (isVideo) {
                    Environment.DIRECTORY_MOVIES
                } else {
                    Environment.DIRECTORY_PICTURES
                }
                ) + "/$ALBUM"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                if (isVideo) "video/mp4" else "image/jpeg"
            )
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        } else {
            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        }

        val uri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            null
        } ?: return@withContext null

        try {
            val output = resolver.openOutputStream(uri)
                ?: throw IOException("Gagal membuka tujuan galeri")

            output.use { out ->
                source.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            // Publikasikan file agar terlihat di galeri.
            val completedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }

            val updatedRows = resolver.update(
                uri,
                completedValues,
                null,
                null
            )

            if (updatedRows <= 0) {
                throw IOException("Gagal menyelesaikan penyimpanan galeri")
            }

            uri

        } catch (e: Exception) {
            runCatching {
                resolver.delete(uri, null, null)
            }

            null
        }
    }

    /**
     * Menghapus item galeri berdasarkan Uri.
     *
     * true  = berhasil menghapus minimal satu item.
     * false = gagal atau item tidak ditemukan.
     */
    fun deleteFromGallery(
        context: Context,
        uriString: String
    ): Boolean {

        if (uriString.isBlank()) {
            return false
        }

        return try {
            val uri = Uri.parse(uriString)

            context.applicationContext.contentResolver.delete(
                uri,
                null,
                null
            ) > 0

        } catch (e: Exception) {
            false
        }
    }
}
