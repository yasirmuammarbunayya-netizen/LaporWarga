package com.example.tugashelloworld.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash password menggunakan PBKDF2-HMAC-SHA256
 * dengan salt acak.
 */
object PasswordHasher {

    private const val ITERATIONS = 600_000
    private const val KEY_LENGTH = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            ITERATIONS,
            KEY_LENGTH
        )

        return try {
            val bytes = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(
        password: String,
        salt: String,
        expectedHash: String
    ): Boolean {
        return hash(password, salt) == expectedHash
    }
}
