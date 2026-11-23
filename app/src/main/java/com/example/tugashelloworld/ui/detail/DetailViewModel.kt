package com.example.tugashelloworld.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.tugashelloworld.data.ReportRepository
import com.example.tugashelloworld.model.Report
import com.example.tugashelloworld.model.ReportStatus
import com.example.tugashelloworld.util.ErrorMapper
import com.example.tugashelloworld.util.GalleryUtils
import com.example.tugashelloworld.util.ImageUtils
import com.example.tugashelloworld.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DetailEvent {
    data class Deleted(val galleryFailed: Boolean = false) : DetailEvent
    data class StatusUpdated(val title: String, val status: ReportStatus) : DetailEvent
    data class Error(val message: String) : DetailEvent
}

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ReportRepository(application)
    private val reportId: Long = savedStateHandle.get<Long>("reportId") ?: 0L

    val report: StateFlow<Resource<Report?>> = repository.observeReport(reportId)
        .map<Report?, Resource<Report?>> { Resource.Success(it) }
        .onStart { emit(Resource.Loading) }
        .catch { emit(Resource.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Resource.Loading)

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events: Flow<DetailEvent> = _events.receiveAsFlow()

    fun delete(report: Report, alsoGallery: Boolean) = perform {
        repository.delete(report.id)
        ImageUtils.deleteFile(report.photoPath)
        ImageUtils.deleteFile(report.videoPath)

        // Opsional: hapus juga salinan foto/video di galeri perangkat
        val context = getApplication<Application>()
        val galleryFailed = if (alsoGallery) {
            withContext(Dispatchers.IO) {
                listOf(report.photoGalleryUri, report.videoGalleryUri)
                    .filter { it.isNotBlank() }
                    .map { GalleryUtils.deleteFromGallery(context, it) }
                    .any { !it }
            }
        } else false
        DetailEvent.Deleted(galleryFailed)
    }

    fun updateStatus(report: Report, newStatus: ReportStatus) = perform {
        repository.updateStatus(report.id, newStatus.name)
        DetailEvent.StatusUpdated(report.title, newStatus)
    }

    private fun perform(block: suspend () -> DetailEvent) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                _events.send(block())
            } catch (e: Exception) {
                _events.send(DetailEvent.Error(ErrorMapper.message(context, e)))
            }
        }
    }
}
