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
import com.example.tugashelloworld.databinding.FragmentRegisterBinding
import com.example.tugashelloworld.util.Validators
import com.example.tugashelloworld.util.animateWallpaper
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.hideKeyboard
import com.example.tugashelloworld.util.rawValue
import com.example.tugashelloworld.util.showError
import com.example.tugashelloworld.util.snackbar
import com.example.tugashelloworld.util.value

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        animateWallpaper(binding.rootWallpaper)

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.etConfirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister()
                true
            } else false
        }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGoLogin.setOnClickListener { findNavController().navigateUp() }

        collectFlow(viewModel.loading) { loading ->
            binding.progress.isVisible = loading
            binding.btnRegister.isEnabled = !loading
            binding.btnRegister.setText(if (loading) R.string.loading else R.string.register_button)
        }
        collectFlow(viewModel.events) { event ->
            when (event) {
                is AuthEvent.Registered -> {
                    // Kembali ke halaman Login dan kirim email agar terisi otomatis
                    findNavController().previousBackStackEntry?.savedStateHandle
                        ?.set(RESULT_EMAIL, binding.etEmail.value())
                    findNavController().navigateUp()
                }
                is AuthEvent.Success -> Unit
                is AuthEvent.Error -> snackbar(event.message)
            }
        }
    }

    private fun attemptRegister() {
        binding.root.hideKeyboard()
        val name = binding.etName.value()
        val email = binding.etEmail.value()
        val password = binding.etPassword.rawValue()
        val confirm = binding.etConfirm.rawValue()

        val errors = listOf(
            binding.tilName to Validators.name(name),
            binding.tilEmail to Validators.email(email),
            binding.tilPassword to Validators.newPassword(password),
            binding.tilConfirm to Validators.confirmPassword(password, confirm)
        )
        errors.forEach { (layout, error) -> layout.showError(error) }
        if (errors.any { it.second != null }) return

        viewModel.register(name, email, password)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Kunci hasil yang dibaca LoginFragment setelah pendaftaran berhasil. */
        const val RESULT_EMAIL = "registered_email"
    }
}
