package com.example.tugashelloworld.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Akun pengguna lokal. Password tidak pernah disimpan polos, hanya hash PBKDF2 + salt. */
@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val email: String,
    val passwordHash: String,
    val salt: String,
    val createdAt: Long
)

/** Data pengguna yang sedang login (disimpan di SharedPreferences). */
data class SessionUser(val id: Long, val name: String, val email: String)
