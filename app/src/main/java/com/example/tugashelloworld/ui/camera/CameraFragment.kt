package com.example.tugashelloworld.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentCameraBinding
import com.example.tugashelloworld.util.GalleryUtils
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.snackbar
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kamera di dalam aplikasi (CameraX) dengan tiga mode:
 * POTRET, FOTO, dan VIDEO.
 */
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CameraViewModel by viewModels()

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var capturing = false
    private var audioAsked = false

    private var discardRecordingOnFinalize = false

    private var zoomLiveData: LiveData<ZoomState>? = null

    private val zoomObserver = Observer<ZoomState> { state ->
        _binding?.tvZoom?.text =
            getString(R.string.zoom_format, state.zoomRatio)
    }

    private val audioPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted && isAdded) {
                Toast.makeText(
                    requireContext(),
                    R.string.camera_no_audio,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val orientationListener by lazy {
        object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageCapture?.targetRotation = rotation
                videoCapture?.targetRotation = rotation
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (!hasPermission(Manifest.permission.CAMERA)) {
            Toast.makeText(
                requireContext(),
                R.string.permission_camera_denied,
                Toast.LENGTH_SHORT
            ).show()

            findNavController().navigateUp()
            return
        }

        binding.previewView.scaleType =
            PreviewView.ScaleType.FIT_CENTER

        setupGestures()
        setupButtons()
        updateModeUi()

        collectFlow(viewModel.events) { event ->
            when (event) {
                is CameraEvent.SavedToGallery -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.gallery_saved,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is CameraEvent.GalleryFailed -> {
                    snackbar(R.string.gallery_failed)
                }

                is CameraEvent.SnapshotSaved -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.snapshot_saved,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        if (viewModel.capturedPath != null) {
            showReview()
        } else {
            startCamera()
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------
    // Tombol dan mode
    // ---------------------------------------------------------

    private fun setupButtons() {
        binding.btnClose.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnShutter.setOnClickListener {
            if (viewModel.mode == CameraMode.VIDEO) {
                toggleRecording()
            } else {
                takePhoto()
            }
        }

        binding.btnSwitch.setOnClickListener {
            switchCamera()
        }

        // Foto saat merekam video (seperti aplikasi kamera pada umumnya).
        binding.btnSnapshot.setOnClickListener {
            takeSnapshotWhileRecording()
        }

        // Buka galeri (thumbnail hasil terakhir).
        binding.btnGalleryThumb.setOnClickListener {
            if (recording == null && !capturing) {
                findNavController().navigate(R.id.action_camera_to_gallery)
            }
        }

        binding.btnFlash.setOnClickListener {
            onFlashClick()
        }

        binding.btnRetake.setOnClickListener {
            retake()
        }

        binding.btnUse.setOnClickListener {
            useCapture()
        }

        binding.tabPortrait.setOnClickListener {
            selectMode(CameraMode.PORTRAIT)
        }

        binding.tabPhoto.setOnClickListener {
            selectMode(CameraMode.PHOTO)
        }

        binding.tabVideo.setOnClickListener {
            selectMode(CameraMode.VIDEO)
        }
    }

    private fun selectMode(mode: CameraMode) {
        if (recording != null || capturing || viewModel.mode == mode) {
            return
        }

        viewModel.mode = mode
        viewModel.torchOn = false

        updateModeUi()

        if (mode == CameraMode.VIDEO) {
            requestAudioIfNeeded()
        }

        bindUseCases()
    }

    private fun requestAudioIfNeeded() {
        if (
            !hasPermission(Manifest.permission.RECORD_AUDIO) &&
            !audioAsked
        ) {
            audioAsked = true
            audioPermission.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun updateModeUi() {
        styleTab(
            binding.tabPortrait,
            viewModel.mode == CameraMode.PORTRAIT
        )

        styleTab(
            binding.tabPhoto,
            viewModel.mode == CameraMode.PHOTO
        )

        styleTab(
            binding.tabVideo,
            viewModel.mode == CameraMode.VIDEO
        )

        updateShutterUi()
        updateFlashIcon()

        binding.tvModeHint.isVisible = false
    }

    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.setTextColor(
            if (selected) {
                0xFFFFD54F.toInt()
            } else {
                0xB3FFFFFF.toInt()
            }
        )

        tab.setTypeface(
            null,
            if (selected) {
                android.graphics.Typeface.BOLD
            } else {
                android.graphics.Typeface.NORMAL
            }
        )
    }

    private fun updateShutterUi() {
        binding.btnShutter.setBackgroundResource(
            when {
                recording != null ->
                    R.drawable.bg_shutter_recording

                viewModel.mode == CameraMode.VIDEO ->
                    R.drawable.bg_shutter_video

                else ->
                    R.drawable.bg_shutter
            }
        )
    }

    private fun onFlashClick() {
        if (viewModel.mode == CameraMode.VIDEO) {
            viewModel.torchOn = !viewModel.torchOn
            camera?.cameraControl?.enableTorch(viewModel.torchOn)
        } else {
            viewModel.flashMode = when (viewModel.flashMode) {
                ImageCapture.FLASH_MODE_AUTO ->
                    ImageCapture.FLASH_MODE_ON

                ImageCapture.FLASH_MODE_ON ->
                    ImageCapture.FLASH_MODE_OFF

                else ->
                    ImageCapture.FLASH_MODE_AUTO
            }

            imageCapture?.flashMode = viewModel.flashMode
        }

        updateFlashIcon()
    }

    private fun updateFlashIcon() {
        val icon = if (viewModel.mode == CameraMode.VIDEO) {
            if (viewModel.torchOn) {
                R.drawable.ic_flash_on
            } else {
                R.drawable.ic_flash_off
            }
        } else {
            when (viewModel.flashMode) {
                ImageCapture.FLASH_MODE_ON ->
                    R.drawable.ic_flash_on

                ImageCapture.FLASH_MODE_OFF ->
                    R.drawable.ic_flash_off

                else ->
                    R.drawable.ic_flash_auto
            }
        }

        binding.btnFlash.setImageResource(icon)
    }

    private fun switchCamera() {
        if (recording != null) return

        viewModel.lensFacing =
            if (
                viewModel.lensFacing ==
                CameraSelector.LENS_FACING_BACK
            ) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }

        viewModel.torchOn = false
        bindUseCases()
    }

    // ---------------------------------------------------------
    // Kamera
    // ---------------------------------------------------------

    private fun startCamera() {
        val context = requireContext()
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            if (_binding == null) return@addListener

            val provider = try {
                future.get()
            } catch (e: Exception) {
                fallbackToSystemCamera()
                return@addListener
            }

            cameraProvider = provider

            val extFuture =
                ExtensionsManager.getInstanceAsync(context, provider)

            extFuture.addListener({
                extensionsManager = runCatching {
                    extFuture.get()
                }.getOrNull()

                bindUseCases()
            }, ContextCompat.getMainExecutor(context))

        }, ContextCompat.getMainExecutor(context))
    }

    private fun hasLens(
        provider: ProcessCameraProvider,
        facing: Int
    ): Boolean {
        return try {
            provider.hasCamera(
                CameraSelector.Builder()
                    .requireLensFacing(facing)
                    .build()
            )
        } catch (e: Exception) {
            false
        }
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        if (_binding == null || recording != null) return

        val hasBack = hasLens(
            provider,
            CameraSelector.LENS_FACING_BACK
        )

        val hasFront = hasLens(
            provider,
            CameraSelector.LENS_FACING_FRONT
        )

        if (!hasBack && !hasFront) {
            fallbackToSystemCamera()
            return
        }

        if (
            viewModel.lensFacing == CameraSelector.LENS_FACING_BACK &&
            !hasBack
        ) {
            viewModel.lensFacing =
                CameraSelector.LENS_FACING_FRONT
        } else if (
            viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT &&
            !hasFront
        ) {
            viewModel.lensFacing =
                CameraSelector.LENS_FACING_BACK
        }

        binding.btnSwitch.isVisible = hasBack && hasFront

        val baseSelector = CameraSelector.Builder()
            .requireLensFacing(viewModel.lensFacing)
            .build()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        var selector = baseSelector
        var portraitEffect = false

        if (viewModel.mode == CameraMode.PORTRAIT) {
            val manager = extensionsManager

            val available = try {
                manager?.isExtensionAvailable(
                    baseSelector,
                    ExtensionMode.BOKEH
                ) == true
            } catch (e: Exception) {
                false
            }

            if (manager != null && available) {
                selector = manager.getExtensionEnabledCameraSelector(
                    baseSelector,
                    ExtensionMode.BOKEH
                )

                portraitEffect = true
            }
        }

        if (!tryBind(provider, selector, preview)) {
            if (
                portraitEffect &&
                tryBind(
                    provider,
                    baseSelector,
                    Preview.Builder().build().also {
                        it.setSurfaceProvider(
                            binding.previewView.surfaceProvider
                        )
                    }
                )
            ) {
                portraitEffect = false
            } else {
                fallbackToSystemCamera()
                return
            }
        }

        onCameraBound(portraitEffect)
    }

    private fun tryBind(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        preview: Preview
    ): Boolean {
        return try {
            provider.unbindAll()

            if (viewModel.mode == CameraMode.VIDEO) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(
                                Quality.FHD,
                                Quality.HD,
                                Quality.SD
                            ).drop(viewModel.videoQualityLevel.coerceIn(0, 2)),
                            FallbackStrategy.lowerQualityOrHigherThan(
                                Quality.SD
                            )
                        )
                    )
                    .build()

                val capture = VideoCapture.withOutput(recorder)

                // Coba pasang ImageCapture bersama video agar bisa foto saat merekam.
                // Jika kombinasi tidak didukung, pakai video saja (foto dari pratinjau).
                var snapshotCapture: ImageCapture? = null

                if (viewModel.snapshotViaImageCapture) {
                    val candidate = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(95)
                        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                        .build()

                    try {
                        camera = provider.bindToLifecycle(
                            viewLifecycleOwner,
                            selector,
                            preview,
                            capture,
                            candidate
                        )
                        snapshotCapture = candidate
                    } catch (e: Exception) {
                        provider.unbindAll()
                    }
                }

                if (snapshotCapture == null) {
                    camera = provider.bindToLifecycle(
                        viewLifecycleOwner,
                        selector,
                        preview,
                        capture
                    )
                }

                videoCapture = capture
                imageCapture = snapshotCapture

            } else {
                val capture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    )
                    .setJpegQuality(95)
                    .setFlashMode(viewModel.flashMode)
                    .build()

                camera = provider.bindToLifecycle(
                    viewLifecycleOwner,
                    selector,
                    preview,
                    capture
                )

                imageCapture = capture
                videoCapture = null
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun onCameraBound(portraitEffect: Boolean) {
        val cam = camera ?: return

        binding.btnFlash.isVisible =
            cam.cameraInfo.hasFlashUnit()

        if (viewModel.mode == CameraMode.VIDEO) {
            cam.cameraControl.enableTorch(viewModel.torchOn)
        }

        zoomLiveData?.removeObserver(zoomObserver)

        zoomLiveData = cam.cameraInfo.zoomState.also {
            it.observe(viewLifecycleOwner, zoomObserver)
        }

        if (viewModel.mode == CameraMode.PORTRAIT) {
            if (!portraitEffect) {
                cam.cameraInfo.zoomState.value?.let { zoom ->
                    cam.cameraControl.setZoomRatio(
                        1.5f.coerceIn(
                            zoom.minZoomRatio,
                            zoom.maxZoomRatio
                        )
                    )
                }
            }

            showHint(
                if (portraitEffect) {
                    R.string.portrait_effect_on
                } else {
                    R.string.portrait_effect_off
                }
            )
        }

        updateFlashIcon()
    }

    private fun showHint(resId: Int) {
        binding.tvModeHint.setText(resId)
        binding.tvModeHint.isVisible = true

        binding.tvModeHint.postDelayed({
            _binding?.tvModeHint?.isVisible = false
        }, 2800)
    }

    private fun fallbackToSystemCamera() {
        if (!isAdded || _binding == null) return

        Toast.makeText(
            requireContext(),
            R.string.camera_unavailable,
            Toast.LENGTH_SHORT
        ).show()

        findNavController()
            .previousBackStackEntry
            ?.savedStateHandle
            ?.set(RESULT_FALLBACK, true)

        findNavController().navigateUp()
    }

    // ---------------------------------------------------------
    // Gestur
    // ---------------------------------------------------------

    private fun setupGestures() {
        val scaleDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {
                    val cam = camera ?: return true
                    val zoom = cam.cameraInfo.zoomState.value ?: return true

                    val ratio = (
                            zoom.zoomRatio * detector.scaleFactor
                            ).coerceIn(
                            zoom.minZoomRatio,
                            zoom.maxZoomRatio
                        )

                    cam.cameraControl.setZoomRatio(ratio)
                    return true
                }
            }
        )

        val tapDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    focusAt(e.x, e.y)
                    return true
                }
            }
        )

        binding.previewView.setOnTouchListener { view, event ->
            scaleDetector.onTouchEvent(event)

            if (!scaleDetector.isInProgress) {
                tapDetector.onTouchEvent(event)
            }

            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
            }

            true
        }
    }

    private fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return

        val point = binding.previewView
            .meteringPointFactory
            .createPoint(x, y)

        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or
                    FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        cam.cameraControl.startFocusAndMetering(action)
        showFocusRing(x, y)
    }

    private fun showFocusRing(x: Float, y: Float) {
        binding.focusRing.apply {
            animate().cancel()

            translationX = x - width / 2f
            translationY = y - height / 2f

            alpha = 1f
            scaleX = 1.4f
            scaleY = 1.4f
            visibility = View.VISIBLE

            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .setStartDelay(600)
                        .setDuration(250)
                        .start()
                }
                .start()
        }
    }
    // ---------------------------------------------------------
    // Foto
    // ---------------------------------------------------------

    private fun takePhoto() {
        val capture = imageCapture ?: return

        if (capturing) return

        capturing = true
        binding.btnShutter.isEnabled = false

        val file = newTempFile("camera_", ".jpg")

        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal =
                viewModel.lensFacing ==
                        CameraSelector.LENS_FACING_FRONT
        }

        val options = ImageCapture.OutputFileOptions.Builder(file)
            .setMetadata(metadata)
            .build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    output: ImageCapture.OutputFileResults
                ) {
                    capturing = false

                    // Hapus file jika halaman kamera sudah ditutup.
                    if (_binding == null) {
                        file.delete()
                        return
                    }

                    viewModel.capturedPath = file.absolutePath
                    viewModel.capturedIsVideo = false

                    viewModel.persistToGallery(
                        file.absolutePath,
                        isVideo = false
                    )

                    playShutterFlash()
                    showReview()
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    capturing = false
                    file.delete()

                    if (_binding != null) {
                        binding.btnShutter.isEnabled = true
                        snackbar(R.string.camera_capture_failed)
                    }
                }
            }
        )
    }

    private var snapshotBusy = false

    /**
     * Mengambil foto saat video sedang direkam. Foto langsung masuk ke Galeri
     * (Pictures/LaporWarga) dan tidak mengganggu rekaman.
     */
    private fun takeSnapshotWhileRecording() {
        if (recording == null || stopping || snapshotBusy) return

        snapshotBusy = true
        val file = newTempFile("snapshot_", ".jpg")
        val capture = imageCapture

        if (capture != null) {
            val metadata = ImageCapture.Metadata().apply {
                isReversedHorizontal =
                    viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT
            }

            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build(),
                ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        snapshotBusy = false
                        if (_binding != null) playShutterFlash()
                        viewModel.saveSnapshotToGallery(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        snapshotBusy = false
                        file.delete()
                        if (_binding != null) snackbar(R.string.snapshot_failed)
                    }
                }
            )
            return
        }

        // Cadangan: ambil gambar dari pratinjau.
        val bitmap = binding.previewView.bitmap
        if (bitmap == null) {
            snapshotBusy = false
            file.delete()
            snackbar(R.string.snapshot_failed)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                }.getOrDefault(false)
            }
            snapshotBusy = false
            if (ok) {
                if (_binding != null) playShutterFlash()
                viewModel.saveSnapshotToGallery(file)
            } else {
                file.delete()
                if (_binding != null) snackbar(R.string.snapshot_failed)
            }
        }
    }

    private fun playShutterFlash() {
        binding.flashOverlay.apply {
            animate().cancel()
            alpha = 0.8f
            animate()
                .alpha(0f)
                .setDuration(250)
                .start()
        }
    }

    private fun newTempFile(
        prefix: String,
        suffix: String
    ): File {
        val dir = File(
            requireContext().cacheDir,
            "images"
        ).apply {
            mkdirs()
        }

        return File.createTempFile(prefix, suffix, dir)
    }

    // ---------------------------------------------------------
    // Video
    // ---------------------------------------------------------

    private fun toggleRecording() {
        val active = recording

        if (active != null) {
            stopRecording(active)
        } else {
            startRecording()
        }
    }

    private var recordStartedAt = 0L
    private var recordingHadAudio = false
    private var stopping = false

    /**
     * Berhenti merekam: tampilan langsung berubah (timer berhenti, tombol dinonaktifkan,
     * "Menyimpan…") tanpa menunggu CameraX menyelesaikan berkas. Ketukan berulang diabaikan.
     * Rekaman minimal 1 detik agar berkas tidak gagal karena belum ada data.
     */
    private fun stopRecording(active: Recording) {
        if (stopping) return
        stopping = true

        stopTimer()

        binding.btnShutter.isEnabled = false
        binding.btnShutter.alpha = 0.5f
        binding.btnSnapshot.isEnabled = false
        binding.btnSnapshot.alpha = 0.5f
        binding.viewRecDot.alpha = 0.25f
        binding.tvTimer.text = getString(R.string.camera_saving)

        val wait = (MIN_RECORD_MS - (SystemClock.elapsedRealtime() - recordStartedAt))
            .coerceAtLeast(0L)

        viewLifecycleOwner.lifecycleScope.launch {
            if (wait > 0) delay(wait)
            active.stop()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val capture = videoCapture ?: return

        val file = newTempFile("video_", ".mp4")

        val options = FileOutputOptions.Builder(file)
            .setDurationLimitMillis(MAX_VIDEO_MS)
            .setFileSizeLimit(MAX_VIDEO_BYTES)
            .build()

        var pending = capture.output.prepareRecording(
            requireContext(),
            options
        )

        recordingHadAudio = viewModel.videoAudioEnabled &&
                hasPermission(Manifest.permission.RECORD_AUDIO)

        if (recordingHadAudio) {
            pending = pending.withAudioEnabled()
        }

        discardRecordingOnFinalize = false
        stopping = false
        recordStartedAt = SystemClock.elapsedRealtime()

        recording = pending.start(
            ContextCompat.getMainExecutor(requireContext())
        ) { event ->
            onVideoEvent(event, file)
        }

        setRecordingUi(true)
        startTimer()
    }

    /**
     * Penghitung waktu rekaman berjalan sendiri (tiap 200 ms) berdasarkan jam sistem,
     * tidak bergantung pada event Status CameraX yang bisa jarang/terlambat.
     */
    private var timerJob: Job? = null

    private fun startTimer() {
        timerJob?.cancel()
        val startedAt = recordStartedAt

        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val b = _binding ?: break

                b.tvTimer.text = formatTimer(elapsed / 1000)
                // Titik merah berkedip tiap 500 ms seperti perekam video umumnya.
                b.viewRecDot.alpha = if ((elapsed / 500) % 2 == 0L) 1f else 0.25f

                delay(200)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun onVideoEvent(
        event: VideoRecordEvent,
        file: File
    ) {
        when (event) {

            is VideoRecordEvent.Finalize -> {
                recording = null
                stopping = false
                stopTimer()

                val shouldDiscard = discardRecordingOnFinalize
                discardRecordingOnFinalize = false

                if (shouldDiscard) {
                    file.delete()
                    return
                }

                val limitReached =
                    event.error ==
                            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                            event.error ==
                            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED

                // SOURCE_INACTIVE: kamera berhenti, tetapi data yang sudah terekam tetap valid.
                val success = !event.hasError() || limitReached ||
                        event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE

                if (
                    success &&
                    file.exists() &&
                    file.length() > 0
                ) {
                    viewModel.capturedPath = file.absolutePath
                    viewModel.capturedIsVideo = true

                    viewModel.persistToGallery(
                        file.absolutePath,
                        isVideo = true
                    )

                    if (_binding != null) {
                        setRecordingUi(false)
                        showReview()
                    }
                    viewModel.videoFailCount = 0
                } else {
                    android.util.Log.e(
                        "CameraFragment",
                        "Rekaman video gagal, kode=${event.error}",
                        event.cause
                    )
                    file.delete()

                    if (_binding != null) {
                        setRecordingUi(false)
                        handleVideoFailure(event.error)
                    }
                }
            }

            else -> Unit
        }
    }

    /**
     * Perekaman gagal (umum di emulator): turunkan kualitas atau matikan mikrofon
     * secara bergantian lalu pasang ulang kamera, sehingga percobaan berikutnya berhasil.
     */
    private fun handleVideoFailure(error: Int) {
        viewModel.videoFailCount++

        val canLower = viewModel.videoQualityLevel < 2
        val canDropAudio = recordingHadAudio && viewModel.videoAudioEnabled
        val dropAudioNow = canDropAudio && (viewModel.videoFailCount % 2 == 0 || !canLower)

        val message = when {
            viewModel.snapshotViaImageCapture && imageCapture != null -> {
                // Kombinasi video + foto bisa menjadi penyebab: lepas ImageCapture dulu.
                viewModel.snapshotViaImageCapture = false
                getString(R.string.camera_video_failed, error)
            }
            dropAudioNow -> {
                viewModel.videoAudioEnabled = false
                getString(R.string.camera_video_failed_no_audio, error)
            }
            canLower -> {
                viewModel.videoQualityLevel++
                getString(
                    R.string.camera_video_failed_lower,
                    error,
                    if (viewModel.videoQualityLevel == 1) "HD" else "SD"
                )
            }
            else -> getString(R.string.camera_video_failed, error)
        }

        snackbar(message)
        bindUseCases()
    }

    private fun formatTimer(seconds: Long): String {
        return String.format(
            java.util.Locale.US,
            "%02d:%02d",
            seconds / 60,
            seconds % 60
        )
    }

    private fun setRecordingUi(isRecording: Boolean) {
        binding.layoutRec.isVisible = isRecording
        binding.btnSnapshot.isVisible = isRecording

        if (isRecording) {
            binding.tvTimer.text = formatTimer(0)
            binding.viewRecDot.alpha = 1f
        } else {
            stopTimer()
            binding.btnShutter.isEnabled = true
            binding.btnShutter.alpha = 1f
            binding.btnSnapshot.isEnabled = true
            binding.btnSnapshot.alpha = 1f
        }

        binding.layoutModes.isInvisible = isRecording
        binding.btnSwitch.isInvisible = isRecording
        binding.btnGalleryThumb.isInvisible = isRecording

        updateShutterUi()
    }

    // ---------------------------------------------------------
    // Tinjau hasil
    // ---------------------------------------------------------

    private fun showReview() {
        val path = viewModel.capturedPath ?: return

        cameraProvider?.unbindAll()
        camera = null

        binding.previewView.isVisible = false
        binding.layoutCameraTools.isVisible = false
        binding.cameraControls.isVisible = false
        binding.tvModeHint.isVisible = false
        binding.reviewControls.isVisible = true

        if (viewModel.capturedIsVideo) {
            binding.ivReview.isVisible = false
            binding.vvReview.isVisible = true

            binding.vvReview.setVideoPath(path)

            binding.vvReview.setOnPreparedListener { player ->
                player.isLooping = true
                binding.vvReview.start()
            }

            binding.vvReview.setOnClickListener {
                if (binding.vvReview.isPlaying) {
                    binding.vvReview.pause()
                } else {
                    binding.vvReview.start()
                }
            }
        } else {
            binding.vvReview.isVisible = false
            binding.ivReview.isVisible = true

            Glide.with(this)
                .load(File(path))
                .into(binding.ivReview)
        }
    }

    private fun retake() {
        binding.vvReview.stopPlayback()

        viewModel.discardCapture()

        Toast.makeText(
            requireContext(),
            R.string.capture_deleted,
            Toast.LENGTH_SHORT
        ).show()

        binding.ivReview.isVisible = false
        binding.vvReview.isVisible = false
        binding.reviewControls.isVisible = false

        binding.previewView.isVisible = true
        binding.layoutCameraTools.isVisible = true
        binding.cameraControls.isVisible = true
        binding.btnShutter.isEnabled = true

        updateModeUi()
        startCamera()
    }

    private fun useCapture() {
        val path = viewModel.capturedPath ?: return

        // Cegah tombol Gunakan ditekan berulang kali selama proses berlangsung.
        if (!binding.btnUse.isEnabled) return
        binding.btnUse.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Tunggu salinan Galeri selesai dibuat sebelum URI diserahkan.
                val gallerySaved = viewModel.awaitGallerySave(path)

                // Pastikan hasil yang sedang diproses belum diganti atau dibuang.
                if (viewModel.capturedPath != path) {
                    return@launch
                }

                if (!gallerySaved || viewModel.galleryUri.isNullOrBlank()) {
                    snackbar(R.string.gallery_failed)
                    binding.btnUse.isEnabled = true
                    return@launch
                }

                val navController = findNavController()
                val handle = navController
                    .previousBackStackEntry
                    ?.savedStateHandle

                if (handle == null) {
                    snackbar(R.string.camera_capture_failed)
                    binding.btnUse.isEnabled = true
                    return@launch
                }

                val file = File(path)
                if (!file.isFile || !file.exists()) {
                    snackbar(R.string.camera_capture_failed)
                    binding.btnUse.isEnabled = true
                    return@launch
                }

                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )

                binding.vvReview.stopPlayback()

                // Serahkan hasil kamera kepada FormFragment.
                handle.set(RESULT_IS_VIDEO, viewModel.capturedIsVideo)
                handle.set(RESULT_GALLERY_URI, viewModel.galleryUri.orEmpty())
                handle.set(RESULT_URI, uri.toString())

                // File sementara dipertahankan karena sudah diserahkan.
                viewModel.handedOff = true
                navController.navigateUp()

            } catch (e: Exception) {
                if (_binding != null) {
                    snackbar(R.string.camera_capture_failed)
                    binding.btnUse.isEnabled = true
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Thumbnail galeri
    // ---------------------------------------------------------

    /** Menampilkan foto/video terbaru di album sebagai thumbnail. */
    private fun refreshGalleryThumb() {
        val appContext = context?.applicationContext ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val latest = withContext(Dispatchers.IO) {
                GalleryUtils.queryAlbum(appContext).firstOrNull()
            }

            val view = _binding?.btnGalleryThumb ?: return@launch

            if (latest == null) {
                Glide.with(this@CameraFragment).clear(view)
                view.setImageResource(R.drawable.ic_gallery)
            } else {
                Glide.with(this@CameraFragment)
                    .load(latest.uri)
                    .centerCrop()
                    .into(view)
            }
        }
    }

    // ---------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    override fun onResume() {
        super.onResume()
        // Dipanggil juga saat kembali dari galeri (item mungkin sudah dihapus).
        refreshGalleryThumb()
    }

    override fun onStop() {
        orientationListener.disable()
        super.onStop()
    }

    override fun onDestroyView() {
        stopTimer()

        // Buang rekaman aktif jika halaman kamera ditutup.
        if (recording != null) {
            discardRecordingOnFinalize = true
            recording?.stop()
        }

        recording = null

        zoomLiveData?.removeObserver(zoomObserver)
        zoomLiveData = null

        // Lepaskan seluruh use case CameraX.
        runCatching {
            cameraProvider?.unbindAll()
        }

        cameraProvider = null
        extensionsManager = null
        camera = null
        imageCapture = null
        videoCapture = null

        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_URI = "camera_result_uri"
        const val RESULT_IS_VIDEO = "camera_result_is_video"
        const val RESULT_GALLERY_URI = "camera_result_gallery_uri"
        const val RESULT_FALLBACK = "camera_result_fallback"

        private const val MAX_VIDEO_MS = 30_000L
        private const val MAX_VIDEO_BYTES = 500L * 1024 * 1024
        private const val MIN_RECORD_MS = 1_500L
    }
}