package com.example.tugashelloworld

import android.app.Application
import com.example.tugashelloworld.util.NotificationHelper
import com.example.tugashelloworld.util.ThemePreference

class LaporWargaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Terapkan tema yang tersimpan.
        ThemePreference.applySaved(this)

        // Buat channel notifikasi.
        NotificationHelper.createChannel(this)
    }
}
