package com.example.tugashelloworld.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentForgotPasswordBinding
import com.example.tugashelloworld.util.Validators
import com.example.tugashelloworld.util.animateWallpaper
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.hideKeyboard
import com.example.tugashelloworld.util.rawValue
import com.example.tugashelloworld.util.showError
import com.example.tugashelloworld.util.snackbar
import com.example.tugashelloworld.util.value

/** Layar "Lupa password": verifikasi email + nama lengkap, lalu setel password baru. */
class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        animateWallpaper(binding.rootWallpaper)

        binding.btnReset.setOnClickListener { attemptReset() }
        binding.etConfirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptReset()
                true
            } else false
        }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGoLogin.setOnClickListener { findNavController().navigateUp() }

        collectFlow(viewModel.loading) { loading ->
            binding.progress.isVisible = loading
            binding.btnReset.isEnabled = !loading
            binding.btnReset.setText(if (loading) R.string.loading else R.string.reset_button)
        }
        collectFlow(viewModel.events) { event ->
            when (event) {
                is AuthEvent.PasswordReset -> {
                    // Kembali ke Login dan kirim email agar terisi otomatis
                    findNavController().previousBackStackEntry?.savedStateHandle
                        ?.set(RESULT_EMAIL, binding.etEmail.value())
                    findNavController().navigateUp()
                }
                is AuthEvent.Success, is AuthEvent.Registered -> Unit
                is AuthEvent.Error -> snackbar(event.message)
            }
        }
    }

    private fun attemptReset() {
        binding.root.hideKeyboard()
        val email = binding.etEmail.value()
        val name = binding.etName.value()
        val password = binding.etPassword.rawValue()
        val confirm = binding.etConfirm.rawValue()

        val errors = listOf(
            binding.tilEmail to Validators.email(email),
            binding.tilName to Validators.name(name),
            binding.tilPassword to Validators.newPassword(password),
            binding.tilConfirm to Validators.confirmPassword(password, confirm)
        )
        errors.forEach { (layout, error) -> layout.showError(error) }
        if (errors.any { it.second != null }) return

        viewModel.resetPassword(email, name, password)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Kunci hasil yang dibaca LoginFragment setelah reset berhasil. */
        const val RESULT_EMAIL = "reset_email"
    }
}
