
package com.example.tugashelloworld.ui.camera

import android.app.Application
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tugashelloworld.util.GalleryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mode kamera: Potret, Foto biasa, atau Video.
 */
enum class CameraMode {
    PORTRAIT,
    PHOTO,
    VIDEO
}

sealed interface CameraEvent {
    object SavedToGallery : CameraEvent
    object GalleryFailed : CameraEvent
}

/**
 * Mengelola state kamera, hasil foto/video,
 * dan penyimpanan media ke galeri perangkat.
 */
class CameraViewModel(
    application: Application
) : AndroidViewModel(application) {

    var mode: CameraMode = CameraMode.PHOTO

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    var flashMode: Int = ImageCapture.FLASH_MODE_AUTO

    var torchOn: Boolean = false

    /**
     * Path file hasil jepretan atau rekaman.
     * Setter tetap public karena digunakan CameraFragment.
     */
    var capturedPath: String? = null

    var capturedIsVideo: Boolean = false

    /**
     * URI item galeri untuk hasil yang sedang ditinjau.
     */
    var galleryUri: String? = null
        private set

    /**
     * Menandakan hasil sudah diserahkan ke FormFragment.
     */
    var handedOff: Boolean = false

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)

    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    /**
     * Menyimpan hasil foto/video ke galeri.
     */
    fun persistToGallery(
        path: String,
        isVideo: Boolean
    ) {
        galleryUri = null

        viewModelScope.launch {
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

                // Hasil sudah diganti atau dibatalkan.
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
                    _events.send(CameraEvent.SavedToGallery)
                }

            } catch (_: Exception) {
                Log.e(TAG, "Gagal menyimpan hasil kamera ke galeri")

                if (capturedPath == path) {
                    _events.send(CameraEvent.GalleryFailed)
                }
            }
        }
    }

    /**
     * Menghapus hasil yang sedang ditinjau,
     * termasuk salinannya di galeri jika sudah tersedia.
     */
    fun discardCapture() {
        val path = capturedPath
        val gallery = galleryUri

        capturedPath = null
        galleryUri = null
        capturedIsVideo = false
        handedOff = false

        viewModelScope.launch(Dispatchers.IO) {

            path?.let {
                val deleted = runCatching {
                    File(it).delete()
                }.getOrDefault(false)

                if (!deleted) {
                    Log.w(TAG, "Gagal menghapus file sementara: $it")
                }
            }

            gallery?.let {
                val deleted = GalleryUtils.deleteFromGallery(
                    getApplication(),
                    it
                )

                if (!deleted) {
                    Log.w(TAG, "Gagal menghapus media galeri: $it")
                }
            }
        }
    }

    /**
     * Membersihkan file sementara ketika ViewModel dihancurkan.
     * Salinan galeri tetap dipertahankan.
     */
    override fun onCleared() {
        if (!handedOff) {
            capturedPath?.let { path ->
                runCatching {
                    File(path).delete()
                }.onFailure {
                    Log.w(TAG, "Gagal membersihkan file kamera", it)
                }
            }
        }

        super.onCleared()
    }

    private companion object {
        const val TAG = "CameraViewModel"
    }
}
