package com.example.tugashelloworld.ui.camera

import android.app.Application
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tugashelloworld.util.GalleryUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** true jika berjalan di emulator Android. */
fun isEmulator(): Boolean =
    android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.FINGERPRINT.contains("emulator") ||
            android.os.Build.HARDWARE.let { it.contains("goldfish") || it.contains("ranchu") } ||
            android.os.Build.MODEL.contains("Emulator") ||
            android.os.Build.MODEL.contains("Android SDK built for") ||
            android.os.Build.PRODUCT.contains("sdk_gphone") ||
            android.os.Build.PRODUCT.contains("emu")

enum class CameraMode {
    PORTRAIT,
    PHOTO,
    VIDEO
}

sealed interface CameraEvent {
    object SavedToGallery : CameraEvent
    object GalleryFailed : CameraEvent
    object SnapshotSaved : CameraEvent
}

class CameraViewModel(
    application: Application
) : AndroidViewModel(application) {

    var mode: CameraMode = CameraMode.PHOTO

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    var flashMode: Int = ImageCapture.FLASH_MODE_AUTO

    var torchOn: Boolean = false

    /** Indeks kualitas video: 0 = FHD, 1 = HD, 2 = SD. Emulator mulai dari HD (lebih stabil). */
    var videoQualityLevel: Int = if (isEmulator()) 1 else 0

    /** Dimatikan otomatis jika perekaman gagal karena mikrofon (mis. emulator). */
    var videoAudioEnabled: Boolean = true

    var videoFailCount: Int = 0

    /** Foto saat merekam memakai ImageCapture; dimatikan otomatis jika membuat rekaman gagal. */
    var snapshotViaImageCapture: Boolean = true

    var capturedPath: String? = null

    var capturedIsVideo: Boolean = false

    var galleryUri: String? = null
        private set

    var handedOff: Boolean = false

    private var gallerySaveJob: Job? = null
    private var gallerySavePath: String? = null
    private var gallerySaveSucceeded = false

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    /**
     * Foto yang diambil saat merekam video: langsung disimpan ke Galeri
     * tanpa mengganti video yang sedang direkam.
     */
    fun saveSnapshotToGallery(file: File) {
        viewModelScope.launch {
            try {
                val uri = GalleryUtils.saveToGallery(getApplication(), file, false)
                _events.send(
                    if (uri != null) CameraEvent.SnapshotSaved else CameraEvent.GalleryFailed
                )
            } finally {
                file.delete()
            }
        }
    }

    /**
     * Menyalin hasil kamera ke Galeri.
     */
    fun persistToGallery(
        path: String,
        isVideo: Boolean
    ) {
        gallerySaveJob?.cancel()

        galleryUri = null
        gallerySavePath = path
        gallerySaveSucceeded = false

        gallerySaveJob = viewModelScope.launch {
            try {
                val uri = GalleryUtils.saveToGallery(
                    getApplication(),
                    File(path),
                    isVideo
                )

                if (uri == null) {
                    if (capturedPath == path) {
                        _events.send(CameraEvent.GalleryFailed)
                    }
                    return@launch
                }

                // Jika hasil kamera sudah diganti atau dibatalkan,
                // hapus salinan Galeri yang baru dibuat.
                if (capturedPath != path) {
                    withContext(Dispatchers.IO) {
                        val deleted = GalleryUtils.deleteFromGallery(
                            getApplication(),
                            uri.toString()
                        )

                        if (!deleted) {
                            Log.w(
                                TAG,
                                "Gagal membersihkan hasil galeri lama: $uri"
                            )
                        }
                    }
                } else {
                    galleryUri = uri.toString()
                    gallerySaveSucceeded = true
                    _events.send(CameraEvent.SavedToGallery)
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Gagal menyimpan hasil kamera ke galeri",
                    e
                )

                if (capturedPath == path) {
                    _events.send(CameraEvent.GalleryFailed)
                }
            }
        }
    }

    /**
     * Menunggu proses penyimpanan Galeri untuk hasil kamera tertentu.
     */
    suspend fun awaitGallerySave(path: String): Boolean {
        if (capturedPath != path) return false

        if (gallerySavePath == path) {
            gallerySaveJob?.join()
        }

        return capturedPath == path &&
                gallerySavePath == path &&
                gallerySaveSucceeded &&
                !galleryUri.isNullOrBlank()
    }

    /**
     * Menghapus hasil yang sedang ditinjau,
     * termasuk salinannya di Galeri.
     */
    fun discardCapture() {
        val path = capturedPath
        val gallery = galleryUri

        // Batalkan proses penyimpanan yang masih berlangsung.
        gallerySaveJob?.cancel()
        gallerySaveJob = null

        capturedPath = null
        galleryUri = null
        capturedIsVideo = false
        handedOff = false

        gallerySaveSucceeded = false
        gallerySavePath = null

        viewModelScope.launch(Dispatchers.IO) {
            path?.let { filePath ->
                val deleted = runCatching {
                    File(filePath).delete()
                }.getOrDefault(false)

                if (!deleted) {
                    Log.w(
                        TAG,
                        "Gagal menghapus file sementara: $filePath"
                    )
                }
            }

            gallery?.let { uri ->
                val deleted = GalleryUtils.deleteFromGallery(
                    getApplication(),
                    uri
                )

                if (!deleted) {
                    Log.w(
                        TAG,
                        "Gagal menghapus media galeri: $uri"
                    )
                }
            }
        }
    }

    /**
     * Membersihkan file sementara ketika ViewModel dihancurkan.
     * Salinan Galeri tetap dipertahankan.
     */
    override fun onCleared() {
        if (!handedOff) {
            capturedPath?.let { path ->
                runCatching {
                    File(path).delete()
                }.onFailure { error ->
                    Log.w(
                        TAG,
                        "Gagal membersihkan file kamera",
                        error
                    )
                }
            }
        }

        super.onCleared()
    }

    private companion object {
        const val TAG = "CameraViewModel"
    }
}
