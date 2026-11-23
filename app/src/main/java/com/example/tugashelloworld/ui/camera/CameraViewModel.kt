package com.example.tugashelloworld.ui.camera

import android.app.Application
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
import java.io.File

/** Mode kamera: Potret (efek blur bila didukung), Foto biasa, atau Video. */
enum class CameraMode { PORTRAIT, PHOTO, VIDEO }

sealed interface CameraEvent {
    object SavedToGallery : CameraEvent
    object GalleryFailed : CameraEvent
}

/**
 * Menyimpan state kamera (mode, kamera depan/belakang, blitz, hasil jepretan/rekaman)
 * agar tidak hilang ketika layar diputar. Juga mengurus penyimpanan & penghapusan di galeri.
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    var mode: CameraMode = CameraMode.PHOTO
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    var flashMode: Int = ImageCapture.FLASH_MODE_AUTO
    var torchOn: Boolean = false

    /** Path file hasil jepretan/rekaman yang sedang ditinjau (null jika belum ada). */
    var capturedPath: String? = null
    var capturedIsVideo: Boolean = false

    /** Uri item di galeri untuk hasil yang sedang ditinjau (null jika belum/tidak tersimpan). */
    var galleryUri: String? = null

    /** True jika hasil sudah diserahkan ke form (berkas sementara jangan dihapus). */
    var handedOff: Boolean = false

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    /** Menyimpan hasil jepretan/rekaman (resolusi penuh) ke galeri, seperti kamera pada umumnya. */
    fun persistToGallery(path: String, isVideo: Boolean) {
        galleryUri = null
        viewModelScope.launch {
            val uri = GalleryUtils.saveToGallery(getApplication(), File(path), isVideo)
            if (uri == null) {
                _events.send(CameraEvent.GalleryFailed)
            } else if (capturedPath != path) {
                // Pengguna sudah menghapus hasil ini sebelum selesai tersimpan
                GalleryUtils.deleteFromGallery(getApplication(), uri.toString())
            } else {
                galleryUri = uri.toString()
                _events.send(CameraEvent.SavedToGallery)
            }
        }
    }

    /** Menghapus hasil yang sedang ditinjau, termasuk salinannya di galeri. */
    fun discardCapture() {
        val path = capturedPath
        val gallery = galleryUri
        capturedPath = null
        galleryUri = null
        capturedIsVideo = false
        viewModelScope.launch(Dispatchers.IO) {
            path?.let { runCatching { File(it).delete() } }
            gallery?.let { GalleryUtils.deleteFromGallery(getApplication(), it) }
        }
    }

    override fun onCleared() {
        // Berkas sementara dibersihkan; salinan di galeri tetap ada (seperti aplikasi kamera biasa)
        if (!handedOff) capturedPath?.let { runCatching { File(it).delete() } }
        super.onCleared()
    }
}
