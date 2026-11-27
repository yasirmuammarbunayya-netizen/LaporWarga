
package com.example.tugashelloworld.util

import android.util.Patterns
import androidx.annotation.StringRes
import com.example.tugashelloworld.R

/**
 * Kumpulan fungsi validasi form.
 * Mengembalikan ID string error, atau null jika valid.
 */
object Validators {

    @StringRes
    fun name(value: String): Int? = when {
        value.isBlank() ->
            R.string.error_name_empty

        value.trim().length < 3 ->
            R.string.error_name_short

        else -> null
    }

    @StringRes
    fun email(value: String): Int? = when {
        value.isBlank() ->
            R.string.error_email_empty

        !Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches() ->
            R.string.error_email_invalid

        else -> null
    }

    /**
     * Untuk form login: cukup tidak boleh kosong.
     */
    @StringRes
    fun passwordRequired(value: String): Int? =
        if (value.isEmpty()) {
            R.string.error_password_empty
        } else {
            null
        }

    /**
     * Untuk form registrasi: minimal 6 karakter.
     */
    @StringRes
    fun newPassword(value: String): Int? = when {
        value.isEmpty() ->
            R.string.error_password_empty

        value.length < 6 ->
            R.string.error_password_short

        else -> null
    }

    @StringRes
    fun confirmPassword(
        password: String,
        confirm: String
    ): Int? = when {
        confirm.isEmpty() ->
            R.string.error_confirm_empty

        confirm != password ->
            R.string.error_confirm_mismatch

        else -> null
    }

    @StringRes
    fun title(value: String): Int? = when {
        value.isBlank() ->
            R.string.error_title_empty

        value.trim().length < 5 ->
            R.string.error_title_short

        value.trim().length > 80 ->
            R.string.error_title_long

        else -> null
    }

    @StringRes
    fun description(value: String): Int? = when {
        value.isBlank() ->
            R.string.error_description_empty

        value.trim().length < 10 ->
            R.string.error_description_short

        else -> null
    }

    @StringRes
    fun address(value: String): Int? =
        if (value.isBlank()) {
            R.string.error_address_empty
        } else {
            null
        }
}
