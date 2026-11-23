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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menyimpan foto/video ke Galeri perangkat (MediaStore) seperti aplikasi kamera pada umumnya.
 * Foto masuk ke Pictures/LaporWarga, video ke Movies/LaporWarga.
 * Tidak memerlukan izin penyimpanan (Android 10+).
 */
object GalleryUtils {
    private const val ALBUM = "LaporWarga"

    /** Menyalin [source] ke galeri. Mengembalikan Uri item galeri, atau null jika gagal. */
    suspend fun saveToGallery(context: Context, source: File, isVideo: Boolean): Uri? =
        withContext(Dispatchers.IO) {
            if (!source.exists()) return@withContext null
            val resolver = context.contentResolver
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    if (isVideo) "VID_$stamp.mp4" else "IMG_$stamp.jpg"
                )
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/" + ALBUM
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val uri = resolver.insert(collection, values) ?: return@withContext null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IOException("Gagal membuka tujuan galeri")

                // Item baru terlihat di galeri setelah IS_PENDING = 0
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                null
            }
        }

    /**
     * Menghapus item galeri milik aplikasi ini. Mengembalikan false jika tidak diizinkan
     * (mis. aplikasi di-install ulang sehingga bukan lagi pemilik berkas).
     */
    fun deleteFromGallery(context: Context, uriString: String): Boolean = try {
        context.contentResolver.delete(Uri.parse(uriString), null, null)
        true
    } catch (e: Exception) {
        false
    }
}
