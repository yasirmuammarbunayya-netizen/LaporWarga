package com.example.tugashelloworld.data

import android.content.Context
import com.example.tugashelloworld.db.AppDatabase
import com.example.tugashelloworld.model.SessionUser
import com.example.tugashelloworld.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Registrasi, login, dan logout secara lokal (Room + SharedPreferences). */
class AuthRepository(context: Context) {

    private val userDao = AppDatabase.get(context).userDao()
    private val session = SessionManager(context)

    val currentUser: SessionUser? get() = session.currentUser

    suspend fun register(name: String, email: String, password: String): SessionUser =
        withContext(Dispatchers.IO) {
            val normalized = email.trim().lowercase()
            if (userDao.findByEmail(normalized) != null) throw EmailInUseException()

            val salt = PasswordHasher.newSalt()
            val user = User(
                name = name.trim(),
                email = normalized,
                passwordHash = PasswordHasher.hash(password, salt),
                salt = salt,
                createdAt = System.currentTimeMillis()
            )
            val id = userDao.insert(user)
            // Registrasi TIDAK langsung login: pengguna diarahkan ke halaman Login terlebih dahulu.
            SessionUser(id, user.name, user.email)
        }

    suspend fun login(email: String, password: String): SessionUser =
        withContext(Dispatchers.IO) {
            val user = userDao.findByEmail(email.trim().lowercase())
                ?: throw InvalidCredentialsException()
            if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
                throw InvalidCredentialsException()
            }
            SessionUser(user.id, user.name, user.email).also { session.save(it) }
        }

    /**
     * Reset password lokal: pengguna harus memasukkan email DAN nama lengkap yang
     * cocok dengan akun. Salt baru dibuat setiap reset. Sesi tidak diubah.
     */
    suspend fun resetPassword(email: String, name: String, newPassword: String) =
        withContext(Dispatchers.IO) {
            val user = userDao.findByEmail(email.trim().lowercase())
            if (user == null || !user.name.trim().equals(name.trim(), ignoreCase = true)) {
                throw ResetVerificationException()
            }
            val salt = PasswordHasher.newSalt()
            userDao.updatePassword(user.id, PasswordHasher.hash(newPassword, salt), salt)
        }

    fun logout() = session.clear()
}
