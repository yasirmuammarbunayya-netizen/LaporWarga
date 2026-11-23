package com.example.tugashelloworld.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentCameraBinding
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.snackbar
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Kamera di dalam aplikasi (CameraX) dengan tiga mode seperti kamera pada umumnya:
 *  - POTRET : efek blur latar (CameraX Extensions BOKEH) jika didukung perangkat
 *  - FOTO   : foto kualitas maksimum
 *  - VIDEO  : rekam video Full HD (maks. 30 detik), dengan suara jika izin mikrofon diberikan
 * Fitur lain: kamera depan/belakang, blitz/senter, ketuk untuk fokus, cubit untuk zoom,
 * dan layar tinjau (Ulangi / Gunakan).
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

    private var zoomLiveData: LiveData<ZoomState>? = null
    private val zoomObserver = Observer<ZoomState> { state ->
        _binding?.tvZoom?.text = getString(R.string.zoom_format, state.zoomRatio)
    }

    private val audioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && isAdded) {
                Toast.makeText(requireContext(), R.string.camera_no_audio, Toast.LENGTH_SHORT).show()
            }
        }

    /** Memantau orientasi HP agar EXIF foto dan orientasi video benar. */
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!hasPermission(Manifest.permission.CAMERA)) {
            Toast.makeText(requireContext(), R.string.permission_camera_denied, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        binding.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        setupGestures()
        setupButtons()
        updateModeUi()

        collectFlow(viewModel.events) { event ->
            when (event) {
                is CameraEvent.SavedToGallery ->
                    Toast.makeText(requireContext(), R.string.gallery_saved, Toast.LENGTH_SHORT).show()
                is CameraEvent.GalleryFailed -> snackbar(R.string.gallery_failed)
            }
        }

        if (viewModel.capturedPath != null) showReview() else startCamera()
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------ Tombol & mode

    private fun setupButtons() {
        binding.btnClose.setOnClickListener { findNavController().navigateUp() }
        binding.btnShutter.setOnClickListener {
            if (viewModel.mode == CameraMode.VIDEO) toggleRecording() else takePhoto()
        }
        binding.btnSwitch.setOnClickListener { switchCamera() }
        binding.btnFlash.setOnClickListener { onFlashClick() }
        binding.btnRetake.setOnClickListener { retake() }
        binding.btnUse.setOnClickListener { useCapture() }

        binding.tabPortrait.setOnClickListener { selectMode(CameraMode.PORTRAIT) }
        binding.tabPhoto.setOnClickListener { selectMode(CameraMode.PHOTO) }
        binding.tabVideo.setOnClickListener { selectMode(CameraMode.VIDEO) }
    }

    private fun selectMode(mode: CameraMode) {
        if (recording != null || capturing || viewModel.mode == mode) return
        viewModel.mode = mode
        viewModel.torchOn = false
        updateModeUi()
        if (mode == CameraMode.VIDEO) requestAudioIfNeeded()
        bindUseCases()
    }

    private fun requestAudioIfNeeded() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO) && !audioAsked) {
            audioAsked = true
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun updateModeUi() {
        styleTab(binding.tabPortrait, viewModel.mode == CameraMode.PORTRAIT)
        styleTab(binding.tabPhoto, viewModel.mode == CameraMode.PHOTO)
        styleTab(binding.tabVideo, viewModel.mode == CameraMode.VIDEO)
        updateShutterUi()
        updateFlashIcon()
        binding.tvModeHint.isVisible = false
    }

    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.setTextColor(if (selected) 0xFFFFD54F.toInt() else 0xB3FFFFFF.toInt())
        tab.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun updateShutterUi() {
        binding.btnShutter.setBackgroundResource(
            when {
                recording != null -> R.drawable.bg_shutter_recording
                viewModel.mode == CameraMode.VIDEO -> R.drawable.bg_shutter_video
                else -> R.drawable.bg_shutter
            }
        )
    }

    private fun onFlashClick() {
        if (viewModel.mode == CameraMode.VIDEO) {
            viewModel.torchOn = !viewModel.torchOn
            camera?.cameraControl?.enableTorch(viewModel.torchOn)
        } else {
            viewModel.flashMode = when (viewModel.flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                else -> ImageCapture.FLASH_MODE_AUTO
            }
            imageCapture?.flashMode = viewModel.flashMode
        }
        updateFlashIcon()
    }

    private fun updateFlashIcon() {
        val icon = if (viewModel.mode == CameraMode.VIDEO) {
            if (viewModel.torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        } else {
            when (viewModel.flashMode) {
                ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
                ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_flash_off
                else -> R.drawable.ic_flash_auto
            }
        }
        binding.btnFlash.setImageResource(icon)
    }

    private fun switchCamera() {
        if (recording != null) return
        viewModel.lensFacing =
            if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
        viewModel.torchOn = false
        bindUseCases()
    }

    // ------------------------------------------------------------------ Kamera

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

            // Extensions (efek potret/bokeh) bersifat opsional: jika gagal, mode potret tetap jalan tanpa blur.
            val extFuture = ExtensionsManager.getInstanceAsync(context, provider)
            extFuture.addListener({
                if (_binding == null) return@addListener
                extensionsManager = runCatching { extFuture.get() }.getOrNull()
                bindUseCases()
            }, ContextCompat.getMainExecutor(context))
        }, ContextCompat.getMainExecutor(context))
    }

    private fun hasLens(provider: ProcessCameraProvider, facing: Int): Boolean = try {
        provider.hasCamera(CameraSelector.Builder().requireLensFacing(facing).build())
    } catch (e: Exception) {
        false
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        if (_binding == null || recording != null) return

        val hasBack = hasLens(provider, CameraSelector.LENS_FACING_BACK)
        val hasFront = hasLens(provider, CameraSelector.LENS_FACING_FRONT)
        if (!hasBack && !hasFront) {
            fallbackToSystemCamera()
            return
        }
        if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK && !hasBack) {
            viewModel.lensFacing = CameraSelector.LENS_FACING_FRONT
        } else if (viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT && !hasFront) {
            viewModel.lensFacing = CameraSelector.LENS_FACING_BACK
        }
        binding.btnSwitch.isVisible = hasBack && hasFront

        val baseSelector = CameraSelector.Builder().requireLensFacing(viewModel.lensFacing).build()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        // Mode potret: coba efek blur (BOKEH) bila perangkat mendukung
        var selector = baseSelector
        var portraitEffect = false
        if (viewModel.mode == CameraMode.PORTRAIT) {
            val manager = extensionsManager
            val available = try {
                manager?.isExtensionAvailable(baseSelector, ExtensionMode.BOKEH) == true
            } catch (e: Exception) {
                false
            }
            if (manager != null && available) {
                selector = manager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.BOKEH)
                portraitEffect = true
            }
        }

        if (!tryBind(provider, selector, preview)) {
            // Gagal dengan efek? ulangi tanpa efek. Gagal tanpa efek? pakai kamera bawaan HP.
            if (portraitEffect && tryBind(provider, baseSelector, Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                })
            ) {
                portraitEffect = false
            } else {
                fallbackToSystemCamera()
                return
            }
        }
        onCameraBound(portraitEffect)
    }

    /** Mengikat use case sesuai mode. Mengembalikan false jika gagal. */
    private fun tryBind(provider: ProcessCameraProvider, selector: CameraSelector, preview: Preview): Boolean {
        return try {
            provider.unbindAll()
            if (viewModel.mode == CameraMode.VIDEO) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.FHD, Quality.HD, Quality.SD),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                camera = provider.bindToLifecycle(viewLifecycleOwner, selector, preview, capture)
                videoCapture = capture
                imageCapture = null
            } else {
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(95)
                    .setFlashMode(viewModel.flashMode)
                    .build()
                camera = provider.bindToLifecycle(viewLifecycleOwner, selector, preview, capture)
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
        binding.btnFlash.isVisible = cam.cameraInfo.hasFlashUnit()
        if (viewModel.mode == CameraMode.VIDEO) cam.cameraControl.enableTorch(viewModel.torchOn)

        zoomLiveData?.removeObserver(zoomObserver)
        zoomLiveData = cam.cameraInfo.zoomState.also { it.observe(viewLifecycleOwner, zoomObserver) }

        if (viewModel.mode == CameraMode.PORTRAIT) {
            if (!portraitEffect) {
                // Tanpa efek blur: bingkai potret dengan zoom ringan (seperti lensa potret)
                cam.cameraInfo.zoomState.value?.let { z ->
                    cam.cameraControl.setZoomRatio(1.5f.coerceIn(z.minZoomRatio, z.maxZoomRatio))
                }
            }
            showHint(if (portraitEffect) R.string.portrait_effect_on else R.string.portrait_effect_off)
        }
        updateFlashIcon()
    }

    private fun showHint(resId: Int) {
        binding.tvModeHint.setText(resId)
        binding.tvModeHint.isVisible = true
        binding.tvModeHint.postDelayed({ _binding?.tvModeHint?.isVisible = false }, 2800)
    }

    /** Jika CameraX gagal di perangkat ini, minta Form memakai kamera bawaan HP. */
    private fun fallbackToSystemCamera() {
        if (!isAdded || _binding == null) return
        Toast.makeText(requireContext(), R.string.camera_unavailable, Toast.LENGTH_SHORT).show()
        findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_FALLBACK, true)
        findNavController().navigateUp()
    }

    // ------------------------------------------------------------------ Gestur

    private fun setupGestures() {
        val scaleDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val cam = camera ?: return true
                    val zoom = cam.cameraInfo.zoomState.value ?: return true
                    val ratio = (zoom.zoomRatio * detector.scaleFactor)
                        .coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
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
            if (!scaleDetector.isInProgress) tapDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) view.performClick()
            true
        }
    }

    private fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        val point = binding.previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
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
            animate().scaleX(1f).scaleY(1f).setDuration(200).withEndAction {
                animate().alpha(0f).setStartDelay(600).setDuration(250).start()
            }.start()
        }
    }

    // ------------------------------------------------------------------ Foto

    private fun takePhoto() {
        val capture = imageCapture ?: return
        if (capturing) return
        capturing = true
        binding.btnShutter.isEnabled = false

        val file = newTempFile("camera_", ".jpg")
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    viewModel.capturedPath = file.absolutePath
                    viewModel.capturedIsVideo = false
                    viewModel.persistToGallery(file.absolutePath, isVideo = false)
                    if (_binding != null) {
                        playShutterFlash()
                        showReview()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
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

    private fun playShutterFlash() {
        binding.flashOverlay.apply {
            animate().cancel()
            alpha = 0.8f
            animate().alpha(0f).setDuration(250).start()
        }
    }

    private fun newTempFile(prefix: String, suffix: String): File {
        val dir = File(requireContext().cacheDir, "images").apply { mkdirs() }
        return File.createTempFile(prefix, suffix, dir)
    }

    // ------------------------------------------------------------------ Video

    private fun toggleRecording() {
        val active = recording
        if (active != null) {
            active.stop()
        } else {
            startRecording()
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

        var pending = capture.output.prepareRecording(requireContext(), options)
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) pending = pending.withAudioEnabled()

        recording = pending.start(ContextCompat.getMainExecutor(requireContext())) { event ->
            onVideoEvent(event, file)
        }
        setRecordingUi(true)
    }

    private fun onVideoEvent(event: VideoRecordEvent, file: File) {
        when (event) {
            is VideoRecordEvent.Status -> {
                val seconds = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                _binding?.tvTimer?.text = formatTimer(seconds)
            }
            is VideoRecordEvent.Finalize -> {
                recording = null
                val limitReached = event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                    event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                val success = !event.hasError() || limitReached
                if (success && file.exists() && file.length() > 0) {
                    viewModel.capturedPath = file.absolutePath
                    viewModel.capturedIsVideo = true
                    viewModel.persistToGallery(file.absolutePath, isVideo = true)
                    if (_binding != null) {
                        setRecordingUi(false)
                        showReview()
                    }
                } else {
                    file.delete()
                    if (_binding != null) {
                        setRecordingUi(false)
                        snackbar(R.string.camera_capture_failed)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun formatTimer(seconds: Long): String =
        String.format(java.util.Locale.US, "%02d:%02d / %02d:%02d", seconds / 60, seconds % 60,
            MAX_VIDEO_MS / 60_000, (MAX_VIDEO_MS / 1000) % 60)

    private fun setRecordingUi(isRecording: Boolean) {
        binding.tvTimer.isVisible = isRecording
        if (isRecording) binding.tvTimer.text = formatTimer(0)
        binding.layoutModes.isInvisible = isRecording
        binding.btnSwitch.isInvisible = isRecording
        updateShutterUi()
    }

    // ------------------------------------------------------------------ Tinjau hasil

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
                if (binding.vvReview.isPlaying) binding.vvReview.pause() else binding.vvReview.start()
            }
        } else {
            binding.vvReview.isVisible = false
            binding.ivReview.isVisible = true
            Glide.with(this).load(File(path)).into(binding.ivReview)
        }
    }

    private fun retake() {
        binding.vvReview.stopPlayback()
        // Hapus hasil ini, termasuk salinannya di galeri
        viewModel.discardCapture()
        Toast.makeText(requireContext(), R.string.capture_deleted, Toast.LENGTH_SHORT).show()
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
        binding.vvReview.stopPlayback()
        val uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", File(path)
        )
        viewModel.handedOff = true
        val handle = findNavController().previousBackStackEntry?.savedStateHandle
        handle?.set(RESULT_IS_VIDEO, viewModel.capturedIsVideo)
        handle?.set(RESULT_GALLERY_URI, viewModel.galleryUri.orEmpty())
        handle?.set(RESULT_URI, uri.toString())
        findNavController().navigateUp()
    }

    // ------------------------------------------------------------------ Lifecycle

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    override fun onStop() {
        orientationListener.disable()
        super.onStop()
    }

    override fun onDestroyView() {
        recording?.stop()
        recording = null
        zoomLiveData?.removeObserver(zoomObserver)
        zoomLiveData = null
        camera = null
        imageCapture = null
        videoCapture = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Kunci hasil yang dibaca FormFragment melalui savedStateHandle. */
        const val RESULT_URI = "camera_result_uri"
        const val RESULT_IS_VIDEO = "camera_result_is_video"
        const val RESULT_GALLERY_URI = "camera_result_gallery_uri"
        const val RESULT_FALLBACK = "camera_result_fallback"

        private const val MAX_VIDEO_MS = 30_000L
        private const val MAX_VIDEO_BYTES = 100L * 1024 * 1024
    }
}
