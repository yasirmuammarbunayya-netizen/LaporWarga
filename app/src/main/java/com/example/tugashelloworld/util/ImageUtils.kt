package com.example.tugashelloworld.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Foto laporan dikompres lalu disimpan sebagai file JPEG di penyimpanan internal aplikasi
 * (folder files/photos). Database hanya menyimpan path file-nya.
 */
object ImageUtils {
    private const val MAX_SIDE = 2560
    private const val QUALITY = 92

    /** Uri sementara untuk hasil jepretan kamera (via FileProvider). */
    fun createCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File.createTempFile("camera_", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun discardCameraUri(context: Context, uri: Uri?) {
        if (uri == null) return
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /**
     * Membaca gambar dari [uri], memperbaiki rotasi, memperkecil, lalu menyimpannya
     * sebagai file JPEG. Mengembalikan path absolut file atau null jika gagal.
     */
    suspend fun saveCompressed(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null

            val rotation = readRotation(context, uri)
            val scale = min(1f, MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height))
            if (rotation != 0f || scale < 1f) {
                val matrix = Matrix().apply {
                    postRotate(rotation)
                    postScale(scale, scale)
                }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "report_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Menyalin video hasil rekaman ke penyimpanan internal (folder files/videos). */
    suspend fun saveVideo(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "videos").apply { mkdirs() }
            val file = File(dir, "report_${System.currentTimeMillis()}.mp4")
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { source -> FileOutputStream(file).use { out -> source.copyTo(out) } }
            if (file.length() > 0) file.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    private fun readRotation(context: Context, uri: Uri): Float = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (e: Exception) {
        0f
    }

    fun fileOrNull(path: String?): File? =
        if (path.isNullOrBlank()) null else File(path).takeIf { it.exists() }

    fun deleteFile(path: String?) {
        if (!path.isNullOrBlank()) runCatching { File(path).delete() }
    }
}
