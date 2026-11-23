package com.example.tugashelloworld.util

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun EditText.value(): String = text?.toString()?.trim().orEmpty()

fun EditText.rawValue(): String = text?.toString().orEmpty()

fun TextInputLayout.showError(@StringRes res: Int?) {
    error = res?.let { context.getString(it) }
    isErrorEnabled = res != null
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

/** Mengumpulkan Flow hanya saat tampilan fragment aktif (aman terhadap rotasi layar). */
fun <T> Fragment.collectFlow(flow: Flow<T>, action: suspend (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

fun Fragment.snackbar(message: String) {
    view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
}

fun Fragment.snackbar(@StringRes res: Int) = snackbar(getString(res))
