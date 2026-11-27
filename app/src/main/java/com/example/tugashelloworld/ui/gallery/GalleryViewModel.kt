package com.example.tugashelloworld.ui.gallery

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tugashelloworld.util.GalleryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface GalleryEvent {
    /** Penghapusan selesai. [count] = jumlah item yang diminta dihapus. */
    class Deleted(val count: Int) : GalleryEvent

    /** Sistem meminta persetujuan pengguna (dialog bawaan Android). */
    class ConfirmDelete(val sender: IntentSender) : GalleryEvent

    object DeleteFailed : GalleryEvent
}

/**
 * Dipakai bersama oleh GalleryFragment dan MediaViewerFragment
 * (lewat activityViewModels) agar daftar item selalu sinkron.
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _items = MutableStateFlow<List<GalleryUtils.MediaItem>>(emptyList())
    val items: StateFlow<List<GalleryUtils.MediaItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<GalleryEvent>(Channel.BUFFERED)
    val events: Flow<GalleryEvent> = _events.receiveAsFlow()

    private var pendingCount = 0

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _items.value = withContext(Dispatchers.IO) {
                    GalleryUtils.queryAlbum(getApplication())
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun delete(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            pendingCount = uris.size

            val result = withContext(Dispatchers.IO) {
                GalleryUtils.deleteItems(getApplication(), uris)
            }

            when (result) {
                is GalleryUtils.DeleteResult.Deleted -> {
                    refresh()
                    _events.send(GalleryEvent.Deleted(uris.size))
                }

                is GalleryUtils.DeleteResult.NeedsConfirmation ->
                    _events.send(GalleryEvent.ConfirmDelete(result.sender))

                is GalleryUtils.DeleteResult.Failed ->
                    _events.send(GalleryEvent.DeleteFailed)
            }
        }
    }

    /** Dipanggil setelah dialog persetujuan sistem ditutup. */
    fun onConfirmResult(approved: Boolean) {
        refresh()
        viewModelScope.launch {
            _events.send(
                if (approved) GalleryEvent.Deleted(pendingCount)
                else GalleryEvent.DeleteFailed
            )
        }
    }
}
