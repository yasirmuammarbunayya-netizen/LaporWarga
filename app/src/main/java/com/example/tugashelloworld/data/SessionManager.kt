package com.example.tugashelloworld.data

import android.content.Context
import com.example.tugashelloworld.model.SessionUser

/** Menyimpan sesi login di SharedPreferences sehingga pengguna tetap masuk setelah aplikasi ditutup. */
class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("session", Context.MODE_PRIVATE)

    val currentUser: SessionUser?
        get() {
            val id = prefs.getLong(KEY_ID, 0L)
            if (id == 0L) return null
            return SessionUser(
                id = id,
                name = prefs.getString(KEY_NAME, "").orEmpty(),
                email = prefs.getString(KEY_EMAIL, "").orEmpty()
            )
        }

    val isLoggedIn: Boolean get() = currentUser != null

    fun save(user: SessionUser) {
        prefs.edit()
            .putLong(KEY_ID, user.id)
            .putString(KEY_NAME, user.name)
            .putString(KEY_EMAIL, user.email)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ID = "user_id"
        const val KEY_NAME = "user_name"
        const val KEY_EMAIL = "user_email"
    }
}
