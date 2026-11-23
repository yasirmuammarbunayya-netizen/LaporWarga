package com.example.tugashelloworld.util

import android.content.Context

/** Menyimpan pilihan pengguna: wallpaper berganti warna otomatis (aktif secara default). */
object WallpaperPreference {
    private const val PREFS = "settings"
    private const val KEY_ANIMATED = "wallpaper_animated"

    fun isAnimated(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ANIMATED, true)

    fun setAnimated(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ANIMATED, enabled).apply()
    }
}
