package com.example.tugashelloworld

import android.app.Application
import com.example.tugashelloworld.util.NotificationHelper
import com.example.tugashelloworld.util.ThemePreference

/** Dijalankan sekali saat proses aplikasi dibuat. */
class LaporWargaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePreference.applySaved(this)
        NotificationHelper.createChannel(this)
    }
}
