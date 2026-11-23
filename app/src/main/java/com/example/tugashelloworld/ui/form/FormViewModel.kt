package com.example.tugashelloworld.ui.form

import android.app.Application
import android.net.Uri
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

/**
 * Menyimpan seluruh state form (foto, koordinat, kategori) agar tidak hilang saat layar diputar.
 * Dipakai untuk mode TAMBAH (reportId = 0) maupun EDIT (reportId terisi).
 */
class FormViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ReportRepository(application)
    private val authRepository = AuthRepository(application)

    val reportId: Long = savedStateHandle.get<Long>("reportId") ?: 0L
    val isEdit: Boolean get() = reportId != 0L

    private var existing: Report? = null
    private var originalPhotoPath: String = ""
    private var originalVideoPath: String = ""

    /** Uri salinan foto/video di galeri (hanya untuk hasil dari kamera aplikasi ini). */
    var photoGalleryUri: String = ""
        private set
    var videoGalleryUri: String = ""
        private set
    private var originalPhotoGalleryUri: String = ""
    private var originalVideoGalleryUri: String = ""
    private val pendingGalleryDeletes = mutableListOf<String>()
    private var saved = false

    var prefilled: Boolean = false
    var pendingCameraUri: Uri? = null
    var category: ReportCategory = ReportCategory.ROAD

    private val _editReport = MutableStateFlow<Report?>(null)
    val editReport: StateFlow<Report?> = _editReport.asStateFlow()

    private val _loadingReport = MutableStateFlow(false)
    val loadingReport: StateFlow<Boolean> = _loadingReport.asStateFlow()

    /** Path file foto yang sedang dipilih ("" jika belum ada). */
    private val _photoPath = MutableStateFlow("")
    val photoPath: StateFlow<String> = _photoPath.asStateFlow()

    /** Path file video lampiran ("" jika tidak ada). Video bersifat opsional. */
    private val _videoPath = MutableStateFlow("")
    val videoPath: StateFlow<String> = _videoPath.asStateFlow()

    private val _processingPhoto = MutableStateFlow(false)
    val processingPhoto: StateFlow<Boolean> = _processingPhoto.asStateFlow()

    private val _coordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val coordinates: StateFlow<Pair<Double, Double>?> = _coordinates.asStateFlow()

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
                val report = repository.getReport(reportId)
                if (report == null) {
                    _events.send(FormEvent.Error(context.getString(R.string.error_report_not_found)))
                } else {
                    existing = report
                    originalPhotoPath = report.photoPath
                    originalVideoPath = report.videoPath
                    photoGalleryUri = report.photoGalleryUri
                    videoGalleryUri = report.videoGalleryUri
                    originalPhotoGalleryUri = report.photoGalleryUri
                    originalVideoGalleryUri = report.videoGalleryUri
                    _videoPath.value = report.videoPath
                    category = report.categoryEnum()
                    _photoPath.value = report.photoPath
                    if (report.hasLocation()) _coordinates.value = report.latitude to report.longitude
                    _editReport.value = report
                }
            } catch (e: Exception) {
                _events.send(FormEvent.Error(ErrorMapper.message(context, e)))
            } finally {
                _loadingReport.value = false
            }
        }
    }

    /** Mengompres foto dari kamera/galeri lalu menyimpannya ke penyimpanan internal. */
    fun processPhoto(uri: Uri, fromCamera: Boolean = false, galleryUri: String? = null) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _processingPhoto.value = true
            val path = ImageUtils.saveCompressed(context, uri)
            if (fromCamera) ImageUtils.discardCameraUri(context, uri)
            if (path == null) {
                _events.send(FormEvent.Error(context.getString(R.string.error_photo_failed)))
            } else {
                discardUnsavedPhoto()
                _photoPath.value = path
                photoGalleryUri = galleryUri.orEmpty()
            }
            _processingPhoto.value = false
        }
    }

    /** Menyimpan video hasil rekaman kamera ke penyimpanan internal. */
    fun processVideo(uri: Uri, galleryUri: String? = null) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _processingPhoto.value = true
            val path = ImageUtils.saveVideo(context, uri)
            ImageUtils.discardCameraUri(context, uri)
            if (path == null) {
                _events.send(FormEvent.Error(context.getString(R.string.error_video_failed)))
            } else {
                discardUnsavedVideo()
                _videoPath.value = path
                videoGalleryUri = galleryUri.orEmpty()
            }
            _processingPhoto.value = false
        }
    }

    fun removeVideo(deleteFromGallery: Boolean = false) {
        discardUnsavedVideo()
        _videoPath.value = ""
        val uri = videoGalleryUri
        videoGalleryUri = ""
        if (deleteFromGallery) queueGalleryDelete(uri, uri == originalVideoGalleryUri)
    }

    /**
     * Menghapus item galeri. Jika item itu sudah tercatat di laporan lama (original),
     * penghapusan ditunda sampai laporan benar-benar disimpan agar tidak ada rujukan yang menggantung.
     */
    private fun queueGalleryDelete(uri: String, isOriginal: Boolean) {
        if (uri.isBlank()) return
        if (isOriginal) {
            pendingGalleryDeletes.add(uri)
        } else {
            val context = getApplication<Application>()
            viewModelScope.launch(Dispatchers.IO) { GalleryUtils.deleteFromGallery(context, uri) }
        }
    }

    private fun discardUnsavedVideo() {
        val current = _videoPath.value
        if (current.isNotBlank() && current != originalVideoPath) ImageUtils.deleteFile(current)
    }

    fun removePhoto(deleteFromGallery: Boolean = false) {
        discardUnsavedPhoto()
        _photoPath.value = ""
        val uri = photoGalleryUri
        photoGalleryUri = ""
        if (deleteFromGallery) queueGalleryDelete(uri, uri == originalPhotoGalleryUri)
    }

    /** Menghapus file foto yang baru dipilih tetapi belum disimpan (bukan foto asli laporan). */
    private fun discardUnsavedPhoto() {
        val current = _photoPath.value
        if (current.isNotBlank() && current != originalPhotoPath) ImageUtils.deleteFile(current)
    }

    /** Mengambil lokasi GPS saat ini, lalu mengubahnya menjadi alamat. */
    fun fetchCurrentLocation() {
        if (_locating.value) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            _locating.value = true
            val location = LocationHelper.currentLocation(context)
            if (location == null) {
                _events.send(FormEvent.Error(context.getString(R.string.error_location_failed)))
            } else {
                _coordinates.value = location.latitude to location.longitude
                val address = LocationHelper.addressFrom(context, location.latitude, location.longitude)
                    ?: String.format(java.util.Locale.US, "%.5f, %.5f", location.latitude, location.longitude)
                _events.send(FormEvent.AddressResolved(address))
            }
            _locating.value = false
        }
    }

    fun save(title: String, description: String, address: String) {
        if (_saving.value) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            _saving.value = true
            try {
                val user = authRepository.currentUser ?: throw NotLoggedInException()
                val now = System.currentTimeMillis()
                val old = existing
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
                if (old == null) repository.add(report) else repository.update(report)

                // Foto lama yang diganti tidak dipakai lagi
                if (old != null && old.photoPath.isNotBlank() && old.photoPath != report.photoPath) {
                    ImageUtils.deleteFile(old.photoPath)
                }
                if (old != null && old.videoPath.isNotBlank() && old.videoPath != report.videoPath) {
                    ImageUtils.deleteFile(old.videoPath)
                }
                if (pendingGalleryDeletes.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        pendingGalleryDeletes.forEach { GalleryUtils.deleteFromGallery(context, it) }
                    }
                    pendingGalleryDeletes.clear()
                }
                saved = true
                _events.send(FormEvent.Saved(old != null))
            } catch (e: Exception) {
                _events.send(FormEvent.Error(ErrorMapper.message(context, e)))
            } finally {
                _saving.value = false
            }
        }
    }

    override fun onCleared() {
        // Form ditutup tanpa menyimpan: buang foto sementara agar tidak menumpuk
        if (!saved) {
            discardUnsavedPhoto()
            discardUnsavedVideo()
        }
        super.onCleared()
    }
}
