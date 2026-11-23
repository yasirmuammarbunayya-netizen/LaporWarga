package com.example.tugashelloworld.util

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.annotation.StringRes
import com.example.tugashelloworld.R
import com.example.tugashelloworld.data.EmailInUseException
import com.example.tugashelloworld.data.InvalidCredentialsException
import com.example.tugashelloworld.data.NotLoggedInException
import java.io.IOException

/** Mengubah exception teknis menjadi pesan yang mudah dipahami pengguna. */
object ErrorMapper {

    @StringRes
    private fun resOf(t: Throwable): Int = when (t) {
        is EmailInUseException -> R.string.error_email_in_use
        is InvalidCredentialsException -> R.string.error_invalid_credentials
        is NotLoggedInException -> R.string.error_not_logged_in
        is SQLiteException -> R.string.error_database
        is IOException -> R.string.error_storage
        else -> R.string.error_generic
    }

    fun message(context: Context, t: Throwable): String = context.getString(resOf(t))
}
