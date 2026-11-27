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
import com.example.tugashelloworld.databinding.FragmentLoginBinding
import com.example.tugashelloworld.util.Validators
import com.example.tugashelloworld.util.animateWallpaper
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.hideKeyboard
import com.example.tugashelloworld.util.rawValue
import com.example.tugashelloworld.util.showError
import com.example.tugashelloworld.util.snackbar
import com.example.tugashelloworld.util.value

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        animateWallpaper(binding.rootWallpaper)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }
        binding.btnGoRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.btnForgot.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgot)
        }

        // Setelah mendaftar: email terisi otomatis dan muncul pesan agar pengguna masuk
        val handle = findNavController().currentBackStackEntry?.savedStateHandle
        handle?.getLiveData<String>(RegisterFragment.RESULT_EMAIL)?.observe(viewLifecycleOwner) { email ->
            if (!email.isNullOrBlank()) {
                handle[RegisterFragment.RESULT_EMAIL] = ""
                binding.etEmail.setText(email)
                binding.etPassword.requestFocus()
                snackbar(R.string.register_success)
            }
        }
        // Setelah reset password: email terisi otomatis
        handle?.getLiveData<String>(ForgotPasswordFragment.RESULT_EMAIL)?.observe(viewLifecycleOwner) { email ->
            if (!email.isNullOrBlank()) {
                handle[ForgotPasswordFragment.RESULT_EMAIL] = ""
                binding.etEmail.setText(email)
                binding.etPassword.text = null
                binding.etPassword.requestFocus()
                snackbar(R.string.reset_success)
            }
        }

        collectFlow(viewModel.loading) { loading ->
            binding.progress.isVisible = loading
            binding.btnLogin.isEnabled = !loading
            binding.btnLogin.setText(if (loading) R.string.loading else R.string.login_button)
        }
        collectFlow(viewModel.events) { event ->
            when (event) {
                is AuthEvent.Success -> findNavController().navigate(R.id.action_login_to_home)
                is AuthEvent.Registered, is AuthEvent.PasswordReset -> Unit
                is AuthEvent.Error -> snackbar(event.message)
            }
        }
    }

    private fun attemptLogin() {
        binding.root.hideKeyboard()
        val email = binding.etEmail.value()
        val password = binding.etPassword.rawValue()

        val emailError = Validators.email(email)
        val passwordError = Validators.passwordRequired(password)
        binding.tilEmail.showError(emailError)
        binding.tilPassword.showError(passwordError)
        if (emailError != null || passwordError != null) return

        viewModel.login(email, password)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
