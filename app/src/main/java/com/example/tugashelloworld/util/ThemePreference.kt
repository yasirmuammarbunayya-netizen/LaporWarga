package com.example.tugashelloworld.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * Menyimpan pilihan dark mode pengguna dan menerapkannya.
 */
object ThemePreference {

    private const val PREFS = "settings"
    private const val KEY_MODE = "night_mode"

    fun applySaved(context: Context) {
        val mode = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getInt(
            KEY_MODE,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )

        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun setDark(context: Context, enabled: Boolean) {
        val mode = if (enabled) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putInt(KEY_MODE, mode)
            .apply()

        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isDarkActive(context: Context): Boolean {
        return (
                context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                ) == Configuration.UI_MODE_NIGHT_YES
    }
}
