package com.example.tugashelloworld.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.tugashelloworld.BuildConfig
import com.example.tugashelloworld.R
import com.example.tugashelloworld.data.AuthRepository
import com.example.tugashelloworld.databinding.FragmentProfileBinding
import com.example.tugashelloworld.model.ReportStatus
import com.example.tugashelloworld.model.statusEnum
import com.example.tugashelloworld.ui.home.ReportViewModel
import com.example.tugashelloworld.util.Resource
import com.example.tugashelloworld.util.GradientAnimator
import com.example.tugashelloworld.util.ThemePreference
import com.example.tugashelloworld.util.WallpaperPreference
import com.example.tugashelloworld.util.animateWallpaper
import com.example.tugashelloworld.util.collectFlow

/** Profil pengguna: statistik laporan, pengaturan dark mode, dan logout. */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReportViewModel by activityViewModels()
    private lateinit var authRepository: AuthRepository
    private var wallpaperAnimator: GradientAnimator? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.start()
        authRepository = AuthRepository(requireContext())

        val user = authRepository.currentUser
        val name = user?.name.orEmpty().ifBlank { user?.email.orEmpty().substringBefore("@") }
        binding.tvAvatar.text = name.firstOrNull()?.uppercase() ?: "?"
        binding.tvName.text = name
        binding.tvEmail.text = user?.email.orEmpty()
        binding.tvVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)

        // Dark mode: set status awal dulu, baru pasang listener agar tidak memicu recreate berulang.
        binding.switchDark.isChecked = ThemePreference.isDarkActive(requireContext())
        binding.switchDark.setOnCheckedChangeListener { _, checked ->
            ThemePreference.setDark(requireContext(), checked)
        }

        // Wallpaper header berganti warna otomatis (dapat dimatikan)
        wallpaperAnimator = animateWallpaper(binding.headerWallpaper, 28f)
        binding.switchWallpaper.isChecked = WallpaperPreference.isAnimated(requireContext())
        binding.switchWallpaper.setOnCheckedChangeListener { _, checked ->
            WallpaperPreference.setAnimated(requireContext(), checked)
            wallpaperAnimator?.refresh()
        }

        binding.btnLogout.setOnClickListener { showLogoutDialog() }

        collectFlow(viewModel.allReports) { state ->
            if (state is Resource.Success) {
                val mine = state.data.filter { it.userId == user?.id }
                binding.tvStatTotal.text = mine.size.toString()
                binding.tvStatProcessing.text =
                    mine.count { it.statusEnum() == ReportStatus.PROCESSING }.toString()
                binding.tvStatDone.text = mine.count { it.statusEnum() == ReportStatus.DONE }.toString()
            }
        }
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.logout) { _, _ ->
                authRepository.logout()
                viewModel.onLogout()
                findNavController().navigate(R.id.action_profile_to_login)
            }
            .show()
    }

    override fun onDestroyView() {
        wallpaperAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
