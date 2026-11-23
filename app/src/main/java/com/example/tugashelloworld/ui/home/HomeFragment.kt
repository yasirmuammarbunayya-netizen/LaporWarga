package com.example.tugashelloworld.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.example.tugashelloworld.data.SessionManager
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentHomeBinding
import com.example.tugashelloworld.model.ReportCategory
import com.example.tugashelloworld.model.ReportStatus
import com.example.tugashelloworld.model.SortOrder
import com.example.tugashelloworld.util.ErrorMapper
import com.example.tugashelloworld.util.Resource
import com.example.tugashelloworld.util.animateWallpaper
import com.example.tugashelloworld.util.collectFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Beranda: daftar laporan + pencarian + filter kategori/status + pengurutan. */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReportViewModel by activityViewModels()
    private lateinit var adapter: ReportAdapter

    /** Aksi tombol pada empty state: buat laporan, atau reset filter. */
    private var emptyAction: () -> Unit = {}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.start()

        animateWallpaper(binding.headerWallpaper, 28f)
        setupHeader()
        setupRecycler()
        setupSearch()
        setupFilters()
        setupSort()
        setupActions()
        observeState()
    }

    private fun setupHeader() {
        val name = SessionManager(requireContext()).currentUser?.name.orEmpty()
        binding.tvGreeting.text = getString(R.string.home_greeting, name)
    }

    private fun setupRecycler() {
        adapter = ReportAdapter { report ->
            findNavController().navigate(
                R.id.action_home_to_detail, bundleOf("reportId" to report.id)
            )
        }
        binding.rvReports.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReports.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.reload()
            // Data bersifat realtime; spinner cukup tampil singkat.
            viewLifecycleOwner.lifecycleScope.launch {
                delay(700)
                _binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun setupSearch() {
        val searchView = binding.searchView
        searchView.setQuery(viewModel.query.value, false)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.query.value = newText.orEmpty()
                return true
            }
        })
    }

    private fun setupFilters() {
        buildChips(
            group = binding.chipGroupCategory,
            items = listOf<ReportCategory?>(null) + ReportCategory.values().toList(),
            labelOf = { it?.let { c -> getString(c.label) } ?: getString(R.string.filter_all_category) },
            selected = viewModel.category.value
        ) { viewModel.category.value = it }

        buildChips(
            group = binding.chipGroupStatus,
            items = listOf<ReportStatus?>(null) + ReportStatus.values().toList(),
            labelOf = { it?.let { s -> getString(s.label) } ?: getString(R.string.filter_all_status) },
            selected = viewModel.status.value
        ) { viewModel.status.value = it }
    }

    private fun <T : Any> buildChips(
        group: ChipGroup,
        items: List<T?>,
        labelOf: (T?) -> String,
        selected: T?,
        onSelected: (T?) -> Unit
    ) {
        group.removeAllViews()
        items.forEach { item ->
            val chip = layoutInflater.inflate(R.layout.item_chip_filter, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = labelOf(item)
            chip.isChecked = item == selected
            chip.setOnClickListener { onSelected(item) }
            group.addView(chip)
        }
    }

    private fun setupSort() {
        binding.btnSort.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            SortOrder.values().forEach { popup.menu.add(0, it.ordinal, it.ordinal, getString(it.label)) }
            popup.setOnMenuItemClickListener { item ->
                viewModel.sortOrder.value = SortOrder.values()[item.itemId]
                true
            }
            popup.show()
        }
    }

    private fun setupActions() {
        binding.fabAdd.setOnClickListener { findNavController().navigate(R.id.action_home_to_form) }
        binding.btnEmptyAction.setOnClickListener { emptyAction() }
        binding.btnRetry.setOnClickListener { viewModel.reload() }
    }

    private fun observeState() {
        collectFlow(viewModel.uiState) { render(it) }
        collectFlow(viewModel.sortOrder) {
            binding.btnSort.text = getString(R.string.sort_format, getString(it.label))
        }
    }

    private fun render(state: Resource<ListState>) {
        binding.progress.isVisible = state is Resource.Loading
        binding.stateError.isVisible = state is Resource.Error
        if (state !is Resource.Success) {
            binding.stateEmpty.isVisible = false
            binding.swipeRefresh.isVisible = false
            binding.tvResultCount.text = ""
            if (state is Resource.Error) {
                binding.tvErrorMessage.text = ErrorMapper.message(requireContext(), state.throwable)
            }
            return
        }

        val data = state.data
        adapter.submitList(data.items)
        binding.tvResultCount.text = getString(R.string.result_count, data.items.size)

        val hasItems = data.items.isNotEmpty()
        binding.swipeRefresh.isVisible = hasItems
        binding.stateEmpty.isVisible = !hasItems

        if (!hasItems) {
            if (data.totalCount == 0) {
                // Belum ada laporan sama sekali
                binding.tvEmptyTitle.setText(R.string.empty_title)
                binding.tvEmptyMessage.setText(R.string.empty_message)
                binding.btnEmptyAction.setText(R.string.fab_add)
                emptyAction = { findNavController().navigate(R.id.action_home_to_form) }
            } else {
                // Ada data, tetapi tidak cocok dengan pencarian/filter
                binding.tvEmptyTitle.setText(R.string.not_found_title)
                binding.tvEmptyMessage.setText(R.string.not_found_message)
                binding.btnEmptyAction.setText(R.string.reset_filter)
                emptyAction = {
                    viewModel.resetFilters()
                    binding.searchView.setQuery("", false)
                    setupFilters()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
