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
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Mengelola foto dan video laporan.
 * Foto dikompres menjadi JPEG dan disimpan di files/photos.
 * Video disimpan di files/videos.
 * Database hanya menyimpan path file.
 */
object ImageUtils {

    private const val MAX_SIDE = 2560
    private const val QUALITY = 92

    /**
     * Membuat URI sementara untuk hasil jepretan kamera.
     */
    fun createCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images")

        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Gagal membuat folder gambar sementara")
        }

        val file = File.createTempFile("camera_", ".jpg", dir)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Menghapus file sementara hasil kamera.
     */
    fun discardCameraUri(context: Context, uri: Uri?) {
        if (uri == null) return

        runCatching {
            context.contentResolver.delete(uri, null, null)
        }
    }

    /**
     * Membaca gambar, memperbaiki rotasi EXIF,
     * memperkecil ukuran, dan menyimpan sebagai JPEG.
     */
    suspend fun saveCompressed(
        context: Context,
        uri: Uri
    ): String? = withContext(Dispatchers.IO) {

        var bitmap: Bitmap? = null
        var outputFile: File? = null

        try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext null
            }

            var sample = 1

            while (
                bounds.outWidth / sample > MAX_SIDE * 2 ||
                bounds.outHeight / sample > MAX_SIDE * 2
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
            }

            bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            val originalBitmap = bitmap!!

            val rotation = readRotation(context, uri)

            val scale = min(
                1f,
                MAX_SIDE.toFloat() /
                        max(originalBitmap.width, originalBitmap.height)
            )

            if (rotation != 0f || scale < 1f) {
                val matrix = Matrix().apply {
                    postRotate(rotation)
                    postScale(scale, scale)
                }

                val correctedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )

                if (correctedBitmap !== originalBitmap) {
                    originalBitmap.recycle()
                    bitmap = correctedBitmap
                }
            }

            val dir = File(context.filesDir, "photos")

            if (!dir.exists() && !dir.mkdirs()) {
                return@withContext null
            }

            outputFile = File(
                dir,
                "report_${UUID.randomUUID()}.jpg"
            )

            val saved = FileOutputStream(outputFile!!).use { out ->
                bitmap!!.compress(
                    Bitmap.CompressFormat.JPEG,
                    QUALITY,
                    out
                )
            }

            if (!saved || outputFile!!.length() <= 0) {
                outputFile!!.delete()
                return@withContext null
            }

            outputFile!!.absolutePath

        } catch (e: Exception) {
            outputFile?.delete()
            null
        } finally {
            bitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
        }
    }

    /**
     * Menyalin video ke penyimpanan internal aplikasi.
     */
    suspend fun saveVideo(
        context: Context,
        uri: Uri
    ): String? = withContext(Dispatchers.IO) {

        var outputFile: File? = null

        try {
            val dir = File(context.filesDir, "videos")

            if (!dir.exists() && !dir.mkdirs()) {
                return@withContext null
            }

            outputFile = File(
                dir,
                "report_${UUID.randomUUID()}.mp4"
            )

            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext null

            input.use { source ->
                FileOutputStream(outputFile!!).use { out ->
                    source.copyTo(out)
                    out.flush()
                }
            }

            if (outputFile!!.length() > 0) {
                outputFile!!.absolutePath
            } else {
                outputFile!!.delete()
                null
            }

        } catch (e: Exception) {
            outputFile?.delete()
            null
        }
    }

    /**
     * Membaca orientasi gambar berdasarkan metadata EXIF.
     */
    private fun readRotation(
        context: Context,
        uri: Uri
    ): Float = try {

        context.contentResolver.openInputStream(uri)?.use { stream ->

            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

    } catch (e: Exception) {
        0f
    }

    /**
     * Mengambil file jika path tersedia dan file benar-benar ada.
     */
    fun fileOrNull(path: String?): File? =
        if (path.isNullOrBlank()) {
            null
        } else {
            File(path).takeIf { it.isFile }
        }

    /**
     * Menghapus file berdasarkan path.
     */
    fun deleteFile(path: String?) {
        if (!path.isNullOrBlank()) {
            runCatching {
                File(path).delete()
            }
        }
    }
}
