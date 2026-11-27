package com.example.tugashelloworld.util

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
        val uniqueId = UUID.randomUUID().toString().replace("-", "").take(8)

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
        } catch (e: CancellationException) {
            throw e
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

        } catch (e: CancellationException) {
            runCatching {
                resolver.delete(uri, null, null)
            }
            throw e
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

    // ---------------------------------------------------------
    // Membaca & menghapus isi album LaporWarga
    // ---------------------------------------------------------

    /** Satu foto/video di galeri. */
    data class MediaItem(
        val uri: Uri,
        val isVideo: Boolean,
        val dateAdded: Long,
        val durationMs: Long = 0L
    )

    /** Hasil upaya penghapusan. */
    sealed interface DeleteResult {
        /** Semua item berhasil dihapus. */
        object Deleted : DeleteResult

        /** Sistem perlu meminta izin pengguna lewat dialog bawaan Android. */
        class NeedsConfirmation(val sender: IntentSender) : DeleteResult

        object Failed : DeleteResult
    }

    /**
     * Membaca seluruh foto & video album LaporWarga (terbaru di atas).
     * Tidak membutuhkan izin penyimpanan karena item dibuat oleh aplikasi ini.
     */
    fun queryAlbum(context: Context): List<MediaItem> {
        val resolver = context.applicationContext.contentResolver
        val result = mutableListOf<MediaItem>()

        val photos = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val videos = MediaStore.Video.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )

        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                    "${MediaStore.MediaColumns.IS_PENDING} = 0"

        runCatching {
            resolver.query(
                photos,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATE_ADDED
                ),
                selection,
                arrayOf("${Environment.DIRECTORY_PICTURES}/$ALBUM%"),
                null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (c.moveToNext()) {
                    result += MediaItem(
                        uri = ContentUris.withAppendedId(photos, c.getLong(idCol)),
                        isVideo = false,
                        dateAdded = c.getLong(dateCol)
                    )
                }
            }
        }

        runCatching {
            resolver.query(
                videos,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.Video.VideoColumns.DURATION
                ),
                selection,
                arrayOf("${Environment.DIRECTORY_MOVIES}/$ALBUM%"),
                null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                while (c.moveToNext()) {
                    result += MediaItem(
                        uri = ContentUris.withAppendedId(videos, c.getLong(idCol)),
                        isVideo = true,
                        dateAdded = c.getLong(dateCol),
                        durationMs = c.getLong(durCol)
                    )
                }
            }
        }

        return result.sortedByDescending { it.dateAdded }
    }

    /**
     * Menghapus beberapa item sekaligus.
     *
     * Item buatan aplikasi ini terhapus langsung. Jika sistem menolak
     * (mis. aplikasi di-install ulang sehingga bukan lagi pemilik berkas),
     * dikembalikan [DeleteResult.NeedsConfirmation] agar pengguna
     * dapat menyetujui lewat dialog sistem seperti di aplikasi Galeri.
     */
    fun deleteItems(context: Context, uris: List<Uri>): DeleteResult {
        if (uris.isEmpty()) return DeleteResult.Deleted

        val resolver = context.applicationContext.contentResolver
        val denied = mutableListOf<Uri>()
        var recoverable: IntentSender? = null

        for (uri in uris) {
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                denied += uri
                if (
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                    e is RecoverableSecurityException &&
                    recoverable == null
                ) {
                    recoverable = e.userAction.actionIntent.intentSender
                }
            } catch (e: Exception) {
                return DeleteResult.Failed
            }
        }

        if (denied.isEmpty()) return DeleteResult.Deleted

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                DeleteResult.NeedsConfirmation(
                    MediaStore.createDeleteRequest(resolver, denied).intentSender
                )
            } else {
                recoverable?.let { DeleteResult.NeedsConfirmation(it) }
                    ?: DeleteResult.Failed
            }
        } catch (e: Exception) {
            DeleteResult.Failed
        }
    }
}
