
package com.example.tugashelloworld.ui.detail

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.tugashelloworld.R
import com.example.tugashelloworld.data.SessionManager
import com.example.tugashelloworld.databinding.DialogDeleteBinding
import com.example.tugashelloworld.databinding.FragmentDetailBinding
import com.example.tugashelloworld.model.Report
import com.example.tugashelloworld.model.ReportStatus
import com.example.tugashelloworld.model.categoryEnum
import com.example.tugashelloworld.model.hasLocation
import com.example.tugashelloworld.model.statusEnum
import com.example.tugashelloworld.ui.common.VideoPlayerDialog
import com.example.tugashelloworld.ui.gallery.GalleryPick
import com.example.tugashelloworld.util.ErrorMapper
import com.example.tugashelloworld.util.ImageUtils
import com.example.tugashelloworld.util.NotificationHelper
import com.example.tugashelloworld.util.Resource
import com.example.tugashelloworld.util.TimeFormat
import com.example.tugashelloworld.util.animateWallpaper
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by viewModels()

    private var currentReport: Report? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        animateWallpaper(binding.toolbar)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.toolbar.inflateMenu(R.menu.detail_menu)

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_share) {
                currentReport?.let { report ->
                    shareReport(report)
                }
                true
            } else {
                false
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.retry()
        }

        binding.btnOpenMap.setOnClickListener {
            currentReport?.let { report ->
                openInMaps(report)
            }
        }

        binding.btnEdit.setOnClickListener {
            currentReport?.let { report ->
                findNavController().navigate(
                    R.id.action_detail_to_form,
                    bundleOf("reportId" to report.id)
                )
            }
        }

        binding.btnChangeStatus.setOnClickListener {
            currentReport?.let { report ->
                showStatusDialog(report)
            }
        }

        binding.btnDelete.setOnClickListener {
            currentReport?.let { report ->
                showDeleteDialog(report)
            }
        }

        collectFlow(viewModel.report) { state ->
            render(state)
        }

        collectFlow(viewModel.events) { event ->
            handleEvent(event)
        }
    }

    private fun render(state: Resource<Report?>) {
        binding.progress.isVisible = state is Resource.Loading

        binding.stateError.isVisible =
            state is Resource.Error ||
                    (state is Resource.Success && state.data == null)

        binding.scrollContent.isVisible =
            state is Resource.Success && state.data != null

        when (state) {
            is Resource.Error -> {
                binding.tvErrorMessage.text =
                    ErrorMapper.message(requireContext(), state.throwable)
            }

            is Resource.Success -> {
                val report = state.data
                currentReport = report

                if (report == null) {
                    binding.tvErrorMessage.setText(
                        R.string.error_report_not_found
                    )
                } else {
                    bind(report)
                }
            }

            Resource.Loading -> Unit
        }
    }

    private fun bind(report: Report) {
        val context = requireContext()

        val photo = ImageUtils.fileOrNull(report.photoPath)

        if (photo != null) {
            binding.ivPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
            binding.ivPhoto.imageTintList = null

            Glide.with(this)
                .load(photo)
                .centerCrop()
                .into(binding.ivPhoto)

            // Ketuk foto untuk membuka layar penuh.
            binding.ivPhoto.setOnClickListener {
                findNavController().navigate(
                    R.id.action_detail_to_viewer,
                    bundleOf(
                        GalleryPick.ARG_FILE_PATH to photo.absolutePath,
                        GalleryPick.ARG_FILE_IS_VIDEO to false
                    )
                )
            }
        } else {
            binding.ivPhoto.setOnClickListener(null)
            binding.ivPhoto.isClickable = false
            binding.ivPhoto.scaleType = ImageView.ScaleType.CENTER
            binding.ivPhoto.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    R.color.md_on_surface_variant
                )
            )
            binding.ivPhoto.setImageResource(R.drawable.ic_gallery)
        }

        val video = ImageUtils.fileOrNull(report.videoPath)

        binding.cardVideo.isVisible = video != null

        if (video != null) {
            Glide.with(this)
                .load(video)
                .centerCrop()
                .into(binding.ivVideoThumb)

            binding.cardVideo.setOnClickListener {
                VideoPlayerDialog.show(
                    requireContext(),
                    video.absolutePath
                )
            }
        } else {
            binding.cardVideo.setOnClickListener(null)
        }

        val status = report.statusEnum()

        binding.tvStatus.text = getString(status.label)

        binding.tvStatus.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, status.color)
        )

        binding.tvCategory.text =
            getString(report.categoryEnum().label)

        binding.tvTitle.text = report.title

        binding.tvAuthor.text = getString(
            R.string.reported_by,
            report.userName.ifBlank { "-" }
        )

        binding.tvDate.text = TimeFormat.full(report.createdAt)

        binding.tvAddress.text =
            report.address.ifBlank { getString(R.string.no_address) }

        binding.tvDescription.text = report.description

        binding.tvCoordinates.isVisible = report.hasLocation()

        binding.tvCoordinates.text = getString(
            R.string.coordinates_format,
            report.latitude,
            report.longitude
        )

        binding.btnOpenMap.isVisible =
            report.hasLocation() || report.address.isNotBlank()

        val isOwner =
            report.userId == SessionManager(context).currentUser?.id

        binding.ownerActions.isVisible = isOwner
    }

    private fun handleEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.Deleted -> {
                val message = if (event.galleryFailed) {
                    R.string.report_deleted_gallery_failed
                } else {
                    R.string.report_deleted
                }

                Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_LONG
                ).show()

                findNavController().navigateUp()
            }

            is DetailEvent.StatusUpdated -> {
                snackbar(
                    getString(
                        R.string.status_updated,
                        getString(event.status.label)
                    )
                )

                NotificationHelper.showStatusChanged(
                    requireContext(),
                    event.title,
                    getString(event.status.label)
                )
            }

            is DetailEvent.Error -> {
                snackbar(event.message)
            }
        }
    }

    private fun showStatusDialog(report: Report) {
        val statuses = ReportStatus.values()

        val labels = statuses.map { status ->
            getString(status.label)
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.change_status)
            .setSingleChoiceItems(
                labels,
                report.statusEnum().ordinal
            ) { dialog: DialogInterface, which: Int ->
                dialog.dismiss()

                if (which in statuses.indices) {
                    val selectedStatus = statuses[which]

                    if (selectedStatus != report.statusEnum()) {
                        viewModel.updateStatus(report, selectedStatus)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(report: Report) {
        val dialogBinding = DialogDeleteBinding.inflate(layoutInflater)

        val hasGallery =
            report.photoGalleryUri.isNotBlank() ||
                    report.videoGalleryUri.isNotBlank()

        dialogBinding.cbGallery.isVisible = hasGallery

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(
                R.string.delete
            ) { _: DialogInterface, _: Int ->
                viewModel.delete(
                    report,
                    hasGallery && dialogBinding.cbGallery.isChecked
                )
            }
            .show()
    }

    private fun openInMaps(report: Report) {
        val label = Uri.encode(report.title)

        val uri = if (report.hasLocation()) {
            Uri.parse(
                "geo:${report.latitude},${report.longitude}" +
                        "?q=${report.latitude},${report.longitude}($label)"
            )
        } else {
            Uri.parse(
                "geo:0,0?q=${Uri.encode(report.address)}"
            )
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            val query = if (report.hasLocation()) {
                "${report.latitude},${report.longitude}"
            } else {
                Uri.encode(report.address)
            }

            val webUri = Uri.parse(
                "https://www.google.com/maps/search/?api=1&query=$query"
            )

            try {
                startActivity(Intent(Intent.ACTION_VIEW, webUri))
            } catch (e2: ActivityNotFoundException) {
                snackbar(R.string.error_no_maps_app)
            }
        }
    }

    private fun shareReport(report: Report) {
        val text = getString(
            R.string.share_text,
            report.title,
            getString(report.categoryEnum().label),
            report.address.ifBlank { "-" },
            report.description
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        startActivity(
            Intent.createChooser(
                intent,
                getString(R.string.action_share)
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
