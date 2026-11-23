
package com.example.tugashelloworld.ui.form

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.tugashelloworld.R
import com.example.tugashelloworld.data.AuthRepository
import com.example.tugashelloworld.data.NotLoggedInException
import com.example.tugashelloworld.data.ReportRepository
import com.example.tugashelloworld.model.Report
import com.example.tugashelloworld.model.ReportCategory
import com.example.tugashelloworld.model.ReportStatus
import com.example.tugashelloworld.model.categoryEnum
import com.example.tugashelloworld.model.hasLocation
import com.example.tugashelloworld.util.ErrorMapper
import com.example.tugashelloworld.util.GalleryUtils
import com.example.tugashelloworld.util.ImageUtils
import com.example.tugashelloworld.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FormEvent {
    data class AddressResolved(val address: String) : FormEvent
    data class Saved(val wasEdit: Boolean) : FormEvent
    data class Error(val message: String) : FormEvent
}

class FormViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ReportRepository(application)
    private val authRepository = AuthRepository(application)

    val reportId: Long = savedStateHandle.get<Long>("reportId") ?: 0L
    val isEdit: Boolean get() = reportId != 0L

    private var existing: Report? = null
    private var originalPhotoPath = ""
    private var originalVideoPath = ""

    var photoGalleryUri: String = ""
        private set

    var videoGalleryUri: String = ""
        private set

    private var originalPhotoGalleryUri = ""
    private var originalVideoGalleryUri = ""

    private val pendingGalleryDeletes = mutableListOf<String>()

    private var saved = false

    var prefilled = false
    var pendingCameraUri: Uri? = null
    var category: ReportCategory = ReportCategory.ROAD

    private val _editReport = MutableStateFlow<Report?>(null)
    val editReport: StateFlow<Report?> = _editReport.asStateFlow()

    private val _loadingReport = MutableStateFlow(false)
    val loadingReport: StateFlow<Boolean> = _loadingReport.asStateFlow()

    private val _photoPath = MutableStateFlow("")
    val photoPath: StateFlow<String> = _photoPath.asStateFlow()

    private val _videoPath = MutableStateFlow("")
    val videoPath: StateFlow<String> = _videoPath.asStateFlow()

    private val _processingPhoto = MutableStateFlow(false)
    val processingPhoto: StateFlow<Boolean> = _processingPhoto.asStateFlow()

    private val _coordinates =
        MutableStateFlow<Pair<Double, Double>?>(null)

    val coordinates: StateFlow<Pair<Double, Double>?> =
        _coordinates.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _events = Channel<FormEvent>(Channel.BUFFERED)
    val events: Flow<FormEvent> = _events.receiveAsFlow()

    init {
        if (isEdit) loadExisting()
    }

    private fun loadExisting() {
        val context = getApplication<Application>()

        viewModelScope.launch {
            _loadingReport.value = true

            try {
                val user = authRepository.currentUser
                    ?: throw NotLoggedInException()

                val report = repository.getReport(reportId)
                    ?: throw IllegalStateException(
                        context.getString(R.string.error_report_not_found)
                    )

                if (report.userId != user.id) {
                    throw SecurityException(
                        "Anda tidak memiliki izin untuk mengedit laporan ini."
                    )
                }

                existing = report

                originalPhotoPath = report.photoPath
                originalVideoPath = report.videoPath

                photoGalleryUri = report.photoGalleryUri
                videoGalleryUri = report.videoGalleryUri

                originalPhotoGalleryUri = report.photoGalleryUri
                originalVideoGalleryUri = report.videoGalleryUri

                _videoPath.value = report.videoPath
                _photoPath.value = report.photoPath

                category = report.categoryEnum()

                if (report.hasLocation()) {
                    _coordinates.value =
                        report.latitude to report.longitude
                }

                _editReport.value = report

            } catch (e: Exception) {
                val message = if (e is SecurityException) {
                    e.message
                        ?: "Anda tidak memiliki izin mengedit laporan ini."
                } else {
                    ErrorMapper.message(context, e)
                }

                _events.send(FormEvent.Error(message))

            } finally {
                _loadingReport.value = false
            }
        }
    }

    /**
     * Memastikan hanya satu proses media berjalan pada satu waktu.
     * Fungsi dipanggil dari Main Thread oleh Fragment.
     */
    private fun beginMediaProcessing(): Boolean {
        if (_processingPhoto.value || _saving.value) {
            return false
        }

        _processingPhoto.value = true
        return true
    }

    /**
     * Mengompres foto dari kamera atau galeri.
     */
    fun processPhoto(
        uri: Uri,
        fromCamera: Boolean = false,
        galleryUri: String? = null
    ) {
        if (!beginMediaProcessing()) return

        val context = getApplication<Application>()

        viewModelScope.launch {
            var newPath: String? = null

            try {
                newPath = ImageUtils.saveCompressed(context, uri)

                if (newPath == null) {
                    _events.send(
                        FormEvent.Error(
                            context.getString(R.string.error_photo_failed)
                        )
                    )
                } else {
                    discardUnsavedPhoto()
                    _photoPath.value = newPath
                    photoGalleryUri = galleryUri.orEmpty()
                }

            } catch (e: Exception) {
                newPath?.let {
                    ImageUtils.deleteFile(it)
                }

                _events.send(
                    FormEvent.Error(ErrorMapper.message(context, e))
                )

            } finally {
                if (fromCamera) {
                    ImageUtils.discardCameraUri(context, uri)
                }

                _processingPhoto.value = false
            }
        }
    }

    /**
     * Menyimpan video hasil rekaman ke penyimpanan internal.
     */
    fun processVideo(
        uri: Uri,
        galleryUri: String? = null
    ) {
        if (!beginMediaProcessing()) return

        val context = getApplication<Application>()

        viewModelScope.launch {
            var newPath: String? = null

            try {
                newPath = ImageUtils.saveVideo(context, uri)

                if (newPath == null) {
                    _events.send(
                        FormEvent.Error(
                            context.getString(R.string.error_video_failed)
                        )
                    )
                } else {
                    discardUnsavedVideo()
                    _videoPath.value = newPath
                    videoGalleryUri = galleryUri.orEmpty()
                }

            } catch (e: Exception) {
                newPath?.let {
                    ImageUtils.deleteFile(it)
                }

                _events.send(
                    FormEvent.Error(ErrorMapper.message(context, e))
                )

            } finally {
                ImageUtils.discardCameraUri(context, uri)
                _processingPhoto.value = false
            }
        }
    }

    /**
     * Menghapus video dari form.
     */
    fun removeVideo(deleteFromGallery: Boolean = false) {
        if (_processingPhoto.value || _saving.value) return

        discardUnsavedVideo()

        _videoPath.value = ""

        val uri = videoGalleryUri
        videoGalleryUri = ""

        if (deleteFromGallery) {
            queueGalleryDelete(
                uri,
                uri == originalVideoGalleryUri
            )
        }
    }

    /**
     * Menghapus foto dari form.
     */
    fun removePhoto(deleteFromGallery: Boolean = false) {
        if (_processingPhoto.value || _saving.value) return

        discardUnsavedPhoto()

        _photoPath.value = ""

        val uri = photoGalleryUri
        photoGalleryUri = ""

        if (deleteFromGallery) {
            queueGalleryDelete(
                uri,
                uri == originalPhotoGalleryUri
            )
        }
    }

    /**
     * Menunda penghapusan media lama sampai laporan berhasil disimpan.
     */
    private fun queueGalleryDelete(
        uri: String,
        isOriginal: Boolean
    ) {
        if (uri.isBlank()) return

        if (isOriginal) {
            if (!pendingGalleryDeletes.contains(uri)) {
                pendingGalleryDeletes.add(uri)
            }
        } else {
            val context = getApplication<Application>()

            viewModelScope.launch(Dispatchers.IO) {
                deleteGalleryItem(context, uri)
            }
        }
    }

    /**
     * Menghapus item galeri dan mencatat hasilnya di Logcat.
     */
    private fun deleteGalleryItem(
        context: Application,
        uri: String
    ) {
        val deleted = GalleryUtils.deleteFromGallery(context, uri)

        if (!deleted) {
            Log.w(
                TAG,
                "Gagal menghapus media dari galeri: $uri"
            )
        }
    }

    /**
     * Menghapus foto sementara yang bukan foto asli laporan.
     */
    private fun discardUnsavedPhoto() {
        val current = _photoPath.value

        if (current.isNotBlank() && current != originalPhotoPath) {
            ImageUtils.deleteFile(current)
        }
    }

    /**
     * Menghapus video sementara yang bukan video asli laporan.
     */
    private fun discardUnsavedVideo() {
        val current = _videoPath.value

        if (current.isNotBlank() && current != originalVideoPath) {
            ImageUtils.deleteFile(current)
        }
    }

    /**
     * Mengambil lokasi GPS dan mengubahnya menjadi alamat.
     */
    fun fetchCurrentLocation() {
        if (_locating.value) return

        val context = getApplication<Application>()

        viewModelScope.launch {
            _locating.value = true

            try {
                val location = LocationHelper.currentLocation(context)

                if (location == null) {
                    _events.send(
                        FormEvent.Error(
                            context.getString(R.string.error_location_failed)
                        )
                    )
                } else {
                    _coordinates.value =
                        location.latitude to location.longitude

                    val address = LocationHelper.addressFrom(
                        context,
                        location.latitude,
                        location.longitude
                    ) ?: String.format(
                        java.util.Locale.US,
                        "%.5f, %.5f",
                        location.latitude,
                        location.longitude
                    )

                    _events.send(FormEvent.AddressResolved(address))
                }

            } catch (e: Exception) {
                _events.send(
                    FormEvent.Error(ErrorMapper.message(context, e))
                )
            } finally {
                _locating.value = false
            }
        }
    }

    /**
     * Menyimpan laporan baru atau memperbarui laporan milik pengguna.
     */
    fun save(
        title: String,
        description: String,
        address: String
    ) {
        if (_saving.value || _processingPhoto.value) return

        val context = getApplication<Application>()

        viewModelScope.launch {
            _saving.value = true

            try {
                val user = authRepository.currentUser
                    ?: throw NotLoggedInException()

                /*
                 * Jika mode edit, data lama wajib sudah berhasil dimuat.
                 */
                val old = if (isEdit) {
                    if (existing == null) {
                        throw IllegalStateException(
                            "Data laporan belum berhasil dimuat. " +
                                    "Silakan kembali dan coba lagi."
                        )
                    }

                    val latest = repository.getReport(reportId)
                        ?: throw IllegalStateException(
                            context.getString(R.string.error_report_not_found)
                        )

                    if (latest.userId != user.id) {
                        throw SecurityException(
                            "Anda tidak memiliki izin mengedit laporan ini."
                        )
                    }

                    latest
                } else {
                    null
                }

                val now = System.currentTimeMillis()
                val coords = _coordinates.value

                val report = Report(
                    id = old?.id ?: 0L,
                    title = title,
                    description = description,
                    category = category.name,
                    address = address,
                    latitude = coords?.first ?: 0.0,
                    longitude = coords?.second ?: 0.0,
                    photoPath = _photoPath.value,
                    videoPath = _videoPath.value,
                    photoGalleryUri = photoGalleryUri,
                    videoGalleryUri = videoGalleryUri,
                    status = old?.status ?: ReportStatus.SENT.name,
                    userId = old?.userId ?: user.id,
                    userName = old?.userName ?: user.name,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now
                )

                if (old == null) {
                    repository.add(report)
                } else {
                    repository.update(report)
                }

                // Database sudah berhasil disimpan.
                saved = true

                // Hapus foto lama jika sudah diganti.
                if (
                    old != null &&
                    old.photoPath.isNotBlank() &&
                    old.photoPath != report.photoPath
                ) {
                    ImageUtils.deleteFile(old.photoPath)
                }

                // Hapus video lama jika sudah diganti.
                if (
                    old != null &&
                    old.videoPath.isNotBlank() &&
                    old.videoPath != report.videoPath
                ) {
                    ImageUtils.deleteFile(old.videoPath)
                }

                /*
                 * Penghapusan galeri dilakukan setelah database berhasil.
                 */
                if (pendingGalleryDeletes.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        pendingGalleryDeletes.forEach { uri ->
                            deleteGalleryItem(context, uri)
                        }
                    }

                    pendingGalleryDeletes.clear()
                }

                _events.send(FormEvent.Saved(old != null))

            } catch (e: Exception) {
                val message = if (e is SecurityException) {
                    e.message
                        ?: "Anda tidak memiliki izin mengedit laporan ini."
                } else {
                    ErrorMapper.message(context, e)
                }

                _events.send(FormEvent.Error(message))

            } finally {
                _saving.value = false
            }
        }
    }

    /**
     * Membersihkan file media sementara ketika ViewModel dihancurkan.
     */
    override fun onCleared() {
        if (!saved) {
            discardUnsavedPhoto()
            discardUnsavedVideo()
        }

        super.onCleared()
    }

    private companion object {
        const val TAG = "FormViewModel"
    }
}
