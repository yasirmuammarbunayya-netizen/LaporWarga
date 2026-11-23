package com.example.tugashelloworld.ui.myreports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tugashelloworld.data.SessionManager
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentMyReportsBinding
import com.example.tugashelloworld.model.ReportStatus
import com.example.tugashelloworld.model.statusEnum
import com.example.tugashelloworld.ui.home.ReportAdapter
import com.example.tugashelloworld.ui.home.ReportViewModel
import com.example.tugashelloworld.util.ErrorMapper
import com.example.tugashelloworld.util.Resource
import com.example.tugashelloworld.util.animateWallpaper
import com.example.tugashelloworld.util.collectFlow

/** Daftar laporan milik pengguna yang sedang login. */
class MyReportsFragment : Fragment() {

    private var _binding: FragmentMyReportsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReportViewModel by activityViewModels()
    private lateinit var adapter: ReportAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.start()

        animateWallpaper(binding.headerWallpaper, 28f)

        adapter = ReportAdapter { report ->
            findNavController().navigate(
                R.id.action_myreports_to_detail, bundleOf("reportId" to report.id)
            )
        }
        binding.rvReports.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReports.adapter = adapter

        binding.btnEmptyAction.setOnClickListener {
            findNavController().navigate(R.id.action_myreports_to_form)
        }
        binding.btnRetry.setOnClickListener { viewModel.reload() }

        collectFlow(viewModel.allReports) { render(it) }
    }

    private fun render(state: Resource<List<com.example.tugashelloworld.model.Report>>) {
        binding.progress.isVisible = state is Resource.Loading
        binding.stateError.isVisible = state is Resource.Error
        if (state !is Resource.Success) {
            binding.stateEmpty.isVisible = false
            binding.rvReports.isVisible = false
            if (state is Resource.Error) {
                binding.tvErrorMessage.text = ErrorMapper.message(requireContext(), state.throwable)
            }
            return
        }

        val uid = SessionManager(requireContext()).currentUser?.id
        val mine = state.data.filter { it.userId == uid }.sortedByDescending { it.createdAt }
        adapter.submitList(mine)

        val done = mine.count { it.statusEnum() == ReportStatus.DONE }
        binding.tvSummary.text = getString(R.string.my_reports_summary, mine.size, done)
        binding.rvReports.isVisible = mine.isNotEmpty()
        binding.stateEmpty.isVisible = mine.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
