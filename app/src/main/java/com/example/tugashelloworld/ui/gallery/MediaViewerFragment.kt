package com.example.tugashelloworld.ui.gallery

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentMediaViewerBinding
import com.example.tugashelloworld.databinding.ItemMediaPageBinding
import com.example.tugashelloworld.util.GalleryUtils.MediaItem
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Locale

/**
 * Pratinjau layar penuh. Geser kiri/kanan untuk pindah item,
 * ketuk tombol putar untuk video, tombol Hapus untuk menghapus dari galeri.
 *
 * Mode berkas tunggal (filePath) dipakai untuk membuka lampiran
 * di formulir tanpa tombol hapus galeri.
 */
class MediaViewerFragment : Fragment() {

    private var _binding: FragmentMediaViewerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by activityViewModels()

    private val pickMode: Boolean
        get() = arguments?.getBoolean(GalleryPick.ARG_PICK_MODE, false) == true

    private val singleFile: MediaItem? by lazy {
        val path = arguments?.getString(GalleryPick.ARG_FILE_PATH)
        if (path.isNullOrBlank() || !File(path).isFile) {
            null
        } else {
            MediaItem(
                uri = Uri.fromFile(File(path)),
                isVideo = arguments?.getBoolean(GalleryPick.ARG_FILE_IS_VIDEO) == true,
                dateAdded = 0L
            )
        }
    }

    private val singleMode: Boolean
        get() = !arguments?.getString(GalleryPick.ARG_FILE_PATH).isNullOrBlank()

    private lateinit var pagerAdapter: PageAdapter
    private var startIndexApplied = false

    private val confirmDelete =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            viewModel.onConfirmResult(result.resultCode == Activity.RESULT_OK)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pagerAdapter = PageAdapter()
        binding.pager.adapter = pagerAdapter

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pagerAdapter.resetVideos(binding.pager)
                updateCounter()
            }
        })

        if (singleMode) {
            // Lampiran formulir: hanya melihat.
            val item = singleFile
            if (item == null) {
                Toast.makeText(requireContext(), R.string.viewer_missing, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
                return
            }

            binding.bottomBar.isVisible = false
            binding.tvCounter.isVisible = false
            pagerAdapter.submit(listOf(item))
            return
        }

        binding.btnDelete.setOnClickListener { askDelete() }

        binding.btnUse.isVisible = pickMode
        binding.btnUse.setOnClickListener {
            currentItem()?.let {
                GalleryPick.deliver(
                    findNavController(),
                    it.uri,
                    isVideo = it.isVideo,
                    fromAlbum = true
                )
            }
        }

        collectFlow(viewModel.items) { list ->
            if (list.isEmpty() && startIndexApplied) {
                findNavController().navigateUp()
                return@collectFlow
            }

            pagerAdapter.submit(list)

            if (!startIndexApplied && list.isNotEmpty()) {
                startIndexApplied = true
                val index = (arguments?.getInt(GalleryPick.ARG_INDEX, 0) ?: 0)
                    .coerceIn(0, list.lastIndex)
                binding.pager.setCurrentItem(index, false)
            }

            updateCounter()
        }

        collectFlow(viewModel.events) { event ->
            when (event) {
                is GalleryEvent.Deleted ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.gallery_deleted_count, event.count),
                        Toast.LENGTH_SHORT
                    ).show()

                is GalleryEvent.ConfirmDelete ->
                    confirmDelete.launch(IntentSenderRequest.Builder(event.sender).build())

                is GalleryEvent.DeleteFailed -> snackbar(R.string.gallery_delete_failed)
            }
        }
    }

    private fun currentItem(): MediaItem? =
        pagerAdapter.currentList.getOrNull(binding.pager.currentItem)

    private fun updateCounter() {
        if (_binding == null || singleMode) return

        val total = pagerAdapter.itemCount
        binding.tvCounter.text = getString(
            R.string.viewer_counter,
            (binding.pager.currentItem + 1).coerceAtMost(total.coerceAtLeast(1)),
            total
        )
    }

    private fun askDelete() {
        val item = currentItem() ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (item.isVideo) R.string.gallery_delete_video_title
                else R.string.gallery_delete_photo_title
            )
            .setMessage(R.string.gallery_delete_one_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(listOf(item.uri)) }
            .show()
    }

    override fun onPause() {
        // Hentikan video saat layar ditinggalkan.
        if (_binding != null) pagerAdapter.resetVideos(binding.pager)
        super.onPause()
    }

    override fun onDestroyView() {
        binding.pager.adapter = null
        super.onDestroyView()
        _binding = null
    }

    // ---------------------------------------------------------
    // Adapter halaman
    // ---------------------------------------------------------

    private class PageAdapter : RecyclerView.Adapter<PageAdapter.Page>() {

        var currentList: List<MediaItem> = emptyList()
            private set

        fun submit(list: List<MediaItem>) {
            if (list == currentList) return
            currentList = list
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        override fun getItemCount() = currentList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Page(
            ItemMediaPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: Page, position: Int) =
            holder.bind(currentList[position])

        override fun onViewRecycled(holder: Page) {
            holder.reset()
            super.onViewRecycled(holder)
        }

        /** Hentikan semua video yang sedang terlihat pada pager. */
        fun resetVideos(pager: ViewPager2) {
            val rv = pager.getChildAt(0) as? RecyclerView ?: return
            for (i in 0 until rv.childCount) {
                (rv.getChildViewHolder(rv.getChildAt(i)) as? Page)?.reset()
            }
        }

        class Page(private val b: ItemMediaPageBinding) : RecyclerView.ViewHolder(b.root) {

            private var isVideo = false
            private var ended = false
            private var userSeeking = false

            private val handler = Handler(Looper.getMainLooper())

            private val progressLoop = object : Runnable {
                override fun run() {
                    if (!userSeeking && b.videoView.isVisible) updateProgress()
                    if (b.videoView.isPlaying) handler.postDelayed(this, 250)
                }
            }

            private val hideControls = Runnable {
                if (b.videoView.isPlaying) b.layoutControls.isVisible = false
            }

            fun bind(item: MediaItem) {
                isVideo = item.isVideo
                reset()

                Glide.with(b.ivMedia).load(item.uri).fitCenter().into(b.ivMedia)

                b.btnPlay.setOnClickListener { play(item.uri) }
                b.btnPlayPause.setOnClickListener { togglePlayPause() }

                // Ketuk video: tampil/sembunyikan kontrol, seperti galeri pada umumnya.
                b.videoView.setOnClickListener { toggleControls() }

                b.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) b.tvPosition.text = format(progress)
                    }

                    override fun onStartTrackingTouch(sb: SeekBar) {
                        userSeeking = true
                        handler.removeCallbacks(hideControls)
                    }

                    override fun onStopTrackingTouch(sb: SeekBar) {
                        userSeeking = false
                        b.videoView.seekTo(sb.progress)
                        if (ended && sb.progress < sb.max) {
                            ended = false
                            updatePlayPauseIcon()
                        }
                        scheduleHide()
                    }
                })
            }

            private fun play(uri: Uri) {
                ended = false
                b.btnPlay.isVisible = false
                b.ivMedia.isVisible = false
                b.videoView.isVisible = true
                b.layoutControls.isVisible = true
                b.seek.progress = 0
                b.tvPosition.text = format(0)
                b.tvDuration.text = format(0)

                b.videoView.setVideoURI(uri)
                b.videoView.setOnPreparedListener { mp ->
                    mp.isLooping = false
                    b.seek.max = b.videoView.duration.coerceAtLeast(1)
                    b.tvDuration.text = format(b.videoView.duration)
                    b.videoView.keepScreenOn = true
                    b.videoView.start()
                    updatePlayPauseIcon()
                    handler.removeCallbacks(progressLoop)
                    handler.post(progressLoop)
                    scheduleHide()
                }
                b.videoView.setOnCompletionListener {
                    // Berhenti di akhir video; tombol berubah menjadi "ulang".
                    ended = true
                    b.videoView.keepScreenOn = false
                    b.seek.progress = b.seek.max
                    b.tvPosition.text = format(b.seek.max)
                    b.layoutControls.isVisible = true
                    handler.removeCallbacks(hideControls)
                    updatePlayPauseIcon()
                }
                b.videoView.setOnErrorListener { _, _, _ ->
                    Toast.makeText(
                        b.root.context,
                        R.string.viewer_video_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    reset()
                    true
                }
            }

            private fun togglePlayPause() {
                if (!b.videoView.isVisible) return

                when {
                    ended -> {
                        ended = false
                        b.videoView.seekTo(0)
                        b.videoView.start()
                        handler.removeCallbacks(progressLoop)
                        handler.post(progressLoop)
                        b.videoView.keepScreenOn = true
                    }
                    b.videoView.isPlaying -> {
                        b.videoView.pause()
                        b.videoView.keepScreenOn = false
                        handler.removeCallbacks(hideControls)
                    }
                    else -> {
                        b.videoView.start()
                        handler.removeCallbacks(progressLoop)
                        handler.post(progressLoop)
                        b.videoView.keepScreenOn = true
                    }
                }
                updatePlayPauseIcon()
                scheduleHide()
            }

            private fun toggleControls() {
                if (b.layoutControls.isVisible) {
                    b.layoutControls.isVisible = false
                    handler.removeCallbacks(hideControls)
                } else {
                    b.layoutControls.isVisible = true
                    scheduleHide()
                }
            }

            /** Kontrol hilang otomatis 3 detik setelah video berjalan. */
            private fun scheduleHide() {
                handler.removeCallbacks(hideControls)
                if (b.videoView.isPlaying) handler.postDelayed(hideControls, 3000)
            }

            private fun updateProgress() {
                val pos = b.videoView.currentPosition
                b.seek.progress = pos
                b.tvPosition.text = format(pos)
            }

            private fun updatePlayPauseIcon() {
                b.btnPlayPause.setImageResource(
                    when {
                        ended -> R.drawable.ic_replay
                        b.videoView.isPlaying -> R.drawable.ic_pause
                        else -> R.drawable.ic_play_small
                    }
                )
            }

            private fun format(ms: Int): String {
                val total = (ms / 1000).coerceAtLeast(0)
                val h = total / 3600
                val m = (total % 3600) / 60
                val s = total % 60
                return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                else String.format(Locale.US, "%d:%02d", m, s)
            }

            fun reset() {
                handler.removeCallbacks(progressLoop)
                handler.removeCallbacks(hideControls)
                ended = false
                userSeeking = false
                b.layoutControls.isVisible = false

                if (b.videoView.isVisible) {
                    b.videoView.stopPlayback()
                    b.videoView.keepScreenOn = false
                    b.videoView.isVisible = false
                    b.ivMedia.isVisible = true
                }
                b.btnPlay.isVisible = isVideo
            }
        }
    }
}
