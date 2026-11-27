
package com.example.tugashelloworld.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.tugashelloworld.data.AuthRepository
import com.example.tugashelloworld.data.NotLoggedInException
import com.example.tugashelloworld.data.ReportRepository
import com.example.tugashelloworld.model.Report
import com.example.tugashelloworld.model.ReportStatus
import com.example.tugashelloworld.util.ErrorMapper
import com.example.tugashelloworld.util.GalleryUtils
import com.example.tugashelloworld.util.ImageUtils
import com.example.tugashelloworld.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DetailEvent {

    data class Deleted(
        val galleryFailed: Boolean = false
    ) : DetailEvent

    data class StatusUpdated(
        val title: String,
        val status: ReportStatus
    ) : DetailEvent

    data class Error(
        val message: String
    ) : DetailEvent
}

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ReportRepository(application)
    private val authRepository = AuthRepository(application)

    private val reportId: Long =
        savedStateHandle.get<Long>("reportId") ?: 0L

    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val report: StateFlow<Resource<Report?>> =
        refreshTrigger.flatMapLatest {
            repository.observeReport(reportId)
                .map<Report?, Resource<Report?>> {
                    Resource.Success(it)
                }
                .onStart {
                    emit(Resource.Loading)
                }
                .catch {
                    emit(Resource.Error(it))
                }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Resource.Loading
        )

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events: Flow<DetailEvent> = _events.receiveAsFlow()

    /**
     * Memuat ulang laporan.
     */
    fun retry() {
        refreshTrigger.value++
    }

    /**
     * Mengambil laporan terbaru dan memeriksa kepemilikan.
     */
    private suspend fun getOwnedReport(id: Long): Report {
        val user = authRepository.currentUser
            ?: throw NotLoggedInException()

        val currentReport = repository.getReport(id)
            ?: throw IllegalStateException("Laporan tidak ditemukan.")

        if (currentReport.userId != user.id) {
            throw SecurityException(
                "Anda tidak memiliki izin untuk mengubah laporan ini."
            )
        }

        return currentReport
    }

    /**
     * Menghapus laporan milik pengguna yang sedang login.
     */
    fun delete(report: Report, alsoGallery: Boolean) {
        perform {
            val current = getOwnedReport(report.id)

            repository.delete(current.id)

            ImageUtils.deleteFile(current.photoPath)
            ImageUtils.deleteFile(current.videoPath)

            val context = getApplication<Application>()

            val galleryFailed = if (alsoGallery) {
                withContext(Dispatchers.IO) {
                    listOf(
                        current.photoGalleryUri,
                        current.videoGalleryUri
                    )
                        .filter { it.isNotBlank() }
                        .map { uri ->
                            runCatching {
                                GalleryUtils.deleteFromGallery(
                                    context,
                                    uri
                                )
                            }.getOrDefault(false)
                        }
                        .any { success -> !success }
                }
            } else {
                false
            }

            DetailEvent.Deleted(galleryFailed)
        }
    }

    /**
     * Mengubah status laporan milik pengguna yang sedang login.
     */
    fun updateStatus(
        report: Report,
        newStatus: ReportStatus
    ) {
        perform {
            val current = getOwnedReport(report.id)

            repository.updateStatus(
                current.id,
                newStatus.name
            )

            DetailEvent.StatusUpdated(
                current.title,
                newStatus
            )
        }
    }

    /**
     * Menjalankan operasi dengan penanganan error.
     */
    private fun perform(
        block: suspend () -> DetailEvent
    ) {
        val context = getApplication<Application>()

        viewModelScope.launch {
            try {
                val event = block()
                _events.send(event)

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                val message = when (e) {
                    is SecurityException ->
                        e.message
                            ?: "Anda tidak memiliki izin untuk melakukan tindakan ini."

                    else ->
                        ErrorMapper.message(context, e)
                }

                _events.send(DetailEvent.Error(message))
            }
        }
    }
}
