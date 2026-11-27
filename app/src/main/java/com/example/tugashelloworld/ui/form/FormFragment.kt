package com.example.tugashelloworld.ui.form

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentFormBinding
import com.example.tugashelloworld.model.ReportCategory
import com.example.tugashelloworld.ui.camera.CameraFragment
import com.example.tugashelloworld.ui.common.VideoPlayerDialog
import com.example.tugashelloworld.ui.gallery.GalleryPick
import com.example.tugashelloworld.ui.map.LocationPickerFragment
import com.example.tugashelloworld.util.ImageUtils
import com.example.tugashelloworld.util.LocationHelper
import com.example.tugashelloworld.util.Validators
import com.example.tugashelloworld.util.animateWallpaper
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.hideKeyboard
import com.example.tugashelloworld.util.showError
import com.example.tugashelloworld.util.snackbar
import com.example.tugashelloworld.util.value

class FormFragment : Fragment() {

    private var _binding: FragmentFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormViewModel by viewModels()

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                openCamera()
            } else {
                snackbar(R.string.permission_camera_denied)
            }
        }

    private val takePicture =
        registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->

            val uri = viewModel.pendingCameraUri
            viewModel.pendingCameraUri = null

            if (success && uri != null) {
                viewModel.processPhoto(
                    uri,
                    fromCamera = true
                )
            } else if (uri != null) {
                context?.let { ctx ->
                    ImageUtils.discardCameraUri(ctx, uri)
                }
            }
        }

    private val locationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            if (result.values.any { it }) {
                viewModel.fetchCurrentLocation()
            } else {
                snackbar(R.string.permission_location_denied)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFormBinding.inflate(
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

        animateWallpaper(binding.toolbar)

        binding.toolbar.setTitle(
            if (viewModel.isEdit) {
                R.string.form_title_edit
            } else {
                R.string.form_title_new
            }
        )

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSave.setText(
            if (viewModel.isEdit) {
                R.string.save_changes
            } else {
                R.string.submit_report
            }
        )

        setupCategoryChips()
        setupButtons()
        observeState()
        observeCameraResult()
    }

    private fun observeCameraResult() {

        val handle =
            findNavController()
                .currentBackStackEntry
                ?.savedStateHandle
                ?: return

        handle.getLiveData<String>(CameraFragment.RESULT_URI)
            .observe(viewLifecycleOwner) { uriString ->

                if (!uriString.isNullOrBlank()) {

                    val isVideo =
                        handle.get<Boolean>(
                            CameraFragment.RESULT_IS_VIDEO
                        ) == true

                    handle[CameraFragment.RESULT_URI] = ""
                    handle[CameraFragment.RESULT_IS_VIDEO] = false

                    val gallery = handle
                        .get<String>(
                            CameraFragment.RESULT_GALLERY_URI
                        )
                        ?.takeIf { it.isNotBlank() }

                    handle[CameraFragment.RESULT_GALLERY_URI] = ""

                    val uri = Uri.parse(uriString)

                    if (isVideo) {

                        viewModel.processVideo(
                            uri,
                            galleryUri = gallery,
                            fromCamera = true
                        )

                    } else {

                        viewModel.processPhoto(
                            uri,
                            fromCamera = true,
                            galleryUri = gallery
                        )
                    }
                }
            }

        handle.getLiveData<Double>(LocationPickerFragment.KEY_LAT)
            .observe(viewLifecycleOwner) { lat ->

                val lng = handle.get<Double>(LocationPickerFragment.KEY_LNG)

                if (lat == null || lng == null) return@observe

                handle.remove<Double>(LocationPickerFragment.KEY_LAT)
                handle.remove<Double>(LocationPickerFragment.KEY_LNG)

                viewModel.setPickedLocation(lat, lng)
            }

        handle.getLiveData<String>(GalleryPick.KEY_URI)
            .observe(viewLifecycleOwner) { uriString ->

                if (uriString.isNullOrBlank()) return@observe

                val isVideo =
                    handle.get<Boolean>(GalleryPick.KEY_IS_VIDEO) == true

                val fromAlbum =
                    handle.get<Boolean>(GalleryPick.KEY_FROM_ALBUM) == true

                handle[GalleryPick.KEY_URI] = ""
                handle[GalleryPick.KEY_IS_VIDEO] = false
                handle[GalleryPick.KEY_FROM_ALBUM] = false

                val uri = Uri.parse(uriString)

                // Item album LaporWarga boleh ikut dihapus dari galeri
                // saat laporan dihapus (sama seperti hasil kamera).
                val galleryUri = if (fromAlbum) uriString else null

                if (isVideo) {
                    viewModel.processVideo(
                        uri,
                        galleryUri = galleryUri,
                        fromCamera = false
                    )
                } else {
                    viewModel.processPhoto(
                        uri,
                        fromCamera = false,
                        galleryUri = galleryUri
                    )
                }
            }

        handle.getLiveData<Boolean>(CameraFragment.RESULT_FALLBACK)
            .observe(viewLifecycleOwner) { fallback ->

                if (fallback == true) {
                    handle[CameraFragment.RESULT_FALLBACK] = false
                    launchCamera()
                }
            }
    }

    private fun setupCategoryChips() {

        val group = binding.chipGroupCategory
        group.removeAllViews()

        ReportCategory.entries.forEach { category ->

            val chip = layoutInflater.inflate(
                R.layout.item_chip_filter,
                group,
                false
            ) as Chip

            chip.id = View.generateViewId()
            chip.text = getString(category.label)
            chip.isChecked = category == viewModel.category

            chip.setOnClickListener {
                viewModel.category = category
            }

            group.addView(chip)
        }
    }

    private fun setupButtons() {

        binding.btnCamera.setOnClickListener {

            if (
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                cameraPermission.launch(
                    Manifest.permission.CAMERA
                )
            }
        }

        // Membuka galeri aplikasi (pilih foto/video, pratinjau, hapus).
        binding.btnGallery.setOnClickListener {

            findNavController().navigate(
                R.id.action_form_to_gallery,
                bundleOf(GalleryPick.ARG_PICK_MODE to true)
            )
        }

        // Menghapus foto dari formulir.
        binding.btnRemovePhoto.setOnClickListener {
            viewModel.removePhoto()
        }

        // Menghapus video dari formulir.
        binding.btnRemoveVideo.setOnClickListener {
            viewModel.removeVideo()
        }

        binding.btnLocation.setOnClickListener {

            if (LocationHelper.hasPermission(requireContext())) {
                viewModel.fetchCurrentLocation()
            } else {
                locationPermission.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        binding.btnPickMap.setOnClickListener {

            val coords = viewModel.coordinates.value

            findNavController().navigate(
                R.id.action_form_to_picker,
                bundleOf(
                    LocationPickerFragment.ARG_LAT to (coords?.first ?: 0.0),
                    LocationPickerFragment.ARG_LNG to (coords?.second ?: 0.0)
                )
            )
        }

        binding.btnSave.setOnClickListener {
            attemptSave()
        }
    }

    private fun openCamera() {

        findNavController().navigate(
            R.id.action_form_to_camera
        )
    }

    private fun launchCamera() {

        val uri = ImageUtils.createCameraUri(
            requireContext()
        )

        viewModel.pendingCameraUri = uri
        takePicture.launch(uri)
    }

    private fun observeState() {

        collectFlow(viewModel.editReport) { report ->

            if (report != null && !viewModel.prefilled) {

                viewModel.prefilled = true

                binding.etTitle.setText(report.title)
                binding.etDescription.setText(report.description)
                binding.etAddress.setText(report.address)

                setupCategoryChips()
            }
        }

        collectFlow(viewModel.loadingReport) {
            binding.progressLoad.isVisible = it
        }

        collectFlow(viewModel.photoPath) {
            renderPhoto(it)
        }

        collectFlow(viewModel.videoPath) {
            renderVideo(it)
        }

        collectFlow(viewModel.processingPhoto) {

            binding.progressPhoto.isVisible = it

            binding.btnCamera.isEnabled = !it
            binding.btnGallery.isEnabled = !it

            binding.btnSave.isEnabled =
                !it && !viewModel.saving.value
        }

        collectFlow(viewModel.coordinates) { coords ->

            binding.tvCoordinates.text =
                if (coords == null) {

                    getString(R.string.coordinates_empty)

                } else {

                    getString(
                        R.string.coordinates_format,
                        coords.first,
                        coords.second
                    )
                }
        }

        collectFlow(viewModel.locating) { locating ->

            binding.btnLocation.isEnabled = !locating

            binding.btnLocation.setText(
                if (locating) {
                    R.string.locating
                } else {
                    R.string.use_current_location
                }
            )
        }

        collectFlow(viewModel.saving) { saving ->

            binding.progressSave.isVisible = saving

            binding.btnSave.isEnabled =
                !saving && !viewModel.processingPhoto.value
        }

        collectFlow(viewModel.events) { event ->

            when (event) {

                is FormEvent.AddressResolved -> {

                    binding.etAddress.setText(event.address)
                    binding.tilAddress.showError(null)
                }

                is FormEvent.Saved -> {

                    Toast.makeText(
                        requireContext(),
                        if (event.wasEdit) {
                            R.string.report_updated
                        } else {
                            R.string.report_created
                        },
                        Toast.LENGTH_SHORT
                    ).show()

                    findNavController().navigateUp()
                }

                is FormEvent.Error -> {
                    snackbar(event.message)
                }
            }
        }
    }

    private fun renderVideo(path: String) {

        val video = ImageUtils.fileOrNull(path)

        binding.videoContainer.isVisible = video != null
        binding.btnRemoveVideo.isVisible = video != null

        if (video != null) {

            Glide.with(this)
                .load(video)
                .centerCrop()
                .into(binding.ivVideoPreview)

            binding.ivVideoPreview.setOnClickListener {

                VideoPlayerDialog.show(
                    requireContext(),
                    video.absolutePath
                )
            }

        } else {

            Glide.with(this).clear(binding.ivVideoPreview)

            binding.ivVideoPreview.setOnClickListener(null)
        }
    }

    private fun renderPhoto(path: String) {

        val photo = ImageUtils.fileOrNull(path)

        binding.btnRemovePhoto.isVisible = photo != null
        binding.tvPhotoHint.isVisible = photo == null

        if (photo != null) {

            binding.ivPreview.imageTintList = null

            binding.ivPreview.scaleType =
                ImageView.ScaleType.CENTER_CROP

            Glide.with(this)
                .load(photo)
                .centerCrop()
                .into(binding.ivPreview)

            binding.tvPhotoError.isVisible = false

            // Ketuk pratinjau untuk membuka foto layar penuh.
            binding.ivPreview.setOnClickListener {
                findNavController().navigate(
                    R.id.action_form_to_viewer,
                    bundleOf(
                        GalleryPick.ARG_FILE_PATH to photo.absolutePath,
                        GalleryPick.ARG_FILE_IS_VIDEO to false
                    )
                )
            }

        } else {

            Glide.with(this).clear(binding.ivPreview)

            binding.ivPreview.setOnClickListener(null)
            binding.ivPreview.isClickable = false

            binding.ivPreview.scaleType =
                ImageView.ScaleType.CENTER

            binding.ivPreview.setImageResource(
                R.drawable.ic_camera
            )

            binding.ivPreview.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.md_on_surface_variant
                    )
                )
        }
    }

    private fun attemptSave() {

        binding.root.hideKeyboard()

        val title = binding.etTitle.value()
        val description = binding.etDescription.value()
        val address = binding.etAddress.value()

        val titleError = Validators.title(title)

        val descriptionError =
            Validators.description(description)

        val addressError = Validators.address(address)

        val photoMissing =
            viewModel.photoPath.value.isBlank()

        binding.tilTitle.showError(titleError)
        binding.tilDescription.showError(descriptionError)
        binding.tilAddress.showError(addressError)

        binding.tvPhotoError.isVisible = photoMissing

        if (
            titleError != null ||
            descriptionError != null ||
            addressError != null ||
            photoMissing
        ) {

            if (photoMissing) {
                snackbar(R.string.error_photo_required)
            }

            return
        }

        viewModel.save(
            title,
            description,
            address
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
