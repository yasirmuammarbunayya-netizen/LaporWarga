package com.example.tugashelloworld.ui.gallery

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentGalleryBinding
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Galeri aplikasi: menampilkan foto & video hasil kamera (album LaporWarga).
 *
 * - Ketuk item        -> buka pratinjau layar penuh
 * - Tekan lama        -> mode pilih (bisa pilih banyak) lalu hapus
 * - pickMode = true   -> dibuka dari formulir, pratinjau punya tombol "Gunakan"
 */
class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by activityViewModels()

    private val pickMode: Boolean
        get() = arguments?.getBoolean(GalleryPick.ARG_PICK_MODE, false) == true

    private lateinit var adapter: GalleryAdapter

    private val selected = linkedSetOf<String>()
    private val selecting get() = selected.isNotEmpty()

    private val confirmDelete =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            viewModel.onConfirmResult(result.resultCode == Activity.RESULT_OK)
        }

    // Photo Picker bawaan Android untuk media di luar album aplikasi.
    private val pickFromDevice =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult

            val mime = runCatching {
                requireContext().contentResolver.getType(uri)
            }.getOrNull()

            when {
                mime?.startsWith("video/") == true ->
                    GalleryPick.deliver(findNavController(), uri, isVideo = true, fromAlbum = false)

                mime?.startsWith("image/") == true ->
                    GalleryPick.deliver(findNavController(), uri, isVideo = false, fromAlbum = false)

                else -> snackbar(R.string.gallery_unsupported)
            }
        }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = clearSelection()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, backCallback)

        adapter = GalleryAdapter(
            onClick = { index -> onItemClick(index) },
            onLongClick = { index -> toggle(index) }
        )

        binding.recycler.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recycler.adapter = adapter
        binding.recycler.itemAnimator = null

        binding.toolbar.inflateMenu(R.menu.gallery_menu)
        binding.toolbar.menu.findItem(R.id.action_pick_device).isVisible = pickMode

        binding.toolbar.setNavigationOnClickListener {
            if (selecting) clearSelection() else findNavController().navigateUp()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> {
                    askDelete()
                    true
                }

                R.id.action_pick_device -> {
                    pickFromDevice.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                    true
                }

                else -> false
            }
        }

        collectFlow(viewModel.items) { list ->
            // Buang pilihan yang itemnya sudah tidak ada.
            val alive = list.map { it.uri.toString() }.toSet()
            selected.retainAll(alive)

            adapter.submitList(list)
            binding.tvEmpty.isVisible = list.isEmpty() && !viewModel.loading.value
            updateSelectionUi()
        }

        collectFlow(viewModel.loading) { loading ->
            binding.progress.isVisible = loading && viewModel.items.value.isEmpty()
            binding.tvEmpty.isVisible = !loading && viewModel.items.value.isEmpty()
        }

        collectFlow(viewModel.events) { event ->
            when (event) {
                is GalleryEvent.Deleted -> {
                    selected.clear()
                    updateSelectionUi()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.gallery_deleted_count, event.count),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is GalleryEvent.ConfirmDelete ->
                    confirmDelete.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(event.sender).build()
                    )

                is GalleryEvent.DeleteFailed -> snackbar(R.string.gallery_delete_failed)
            }
        }

        updateSelectionUi()
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    private fun onItemClick(index: Int) {
        if (selecting) {
            toggle(index)
            return
        }

        findNavController().navigate(
            R.id.action_gallery_to_viewer,
            bundleOf(
                GalleryPick.ARG_INDEX to index,
                GalleryPick.ARG_PICK_MODE to pickMode
            )
        )
    }

    private fun toggle(index: Int) {
        val item = adapter.currentList.getOrNull(index) ?: return
        val key = item.uri.toString()

        if (!selected.add(key)) selected.remove(key)
        updateSelectionUi()
    }

    private fun clearSelection() {
        selected.clear()
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        if (_binding == null) return

        adapter.setSelection(selected.toSet(), selecting)
        backCallback.isEnabled = selecting

        binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = selecting

        if (selecting) {
            binding.toolbar.title = getString(R.string.gallery_selected_count, selected.size)
            binding.toolbar.setNavigationIcon(R.drawable.ic_close)
        } else {
            binding.toolbar.setTitle(R.string.gallery_title)
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        }
    }

    private fun askDelete() {
        val uris = adapter.currentList
            .filter { it.uri.toString() in selected }
            .map { it.uri }

        if (uris.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.gallery_delete_title)
            .setMessage(getString(R.string.gallery_delete_message, uris.size))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(uris) }
            .show()
    }

    override fun onDestroyView() {
        binding.recycler.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
