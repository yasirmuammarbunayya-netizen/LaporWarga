package com.example.tugashelloworld.data

/** Exception khusus aplikasi agar pesan error dapat ditampilkan dengan jelas. */
class EmailInUseException : Exception("Email sudah terdaftar")

class InvalidCredentialsException : Exception("Email atau password salah")

class NotLoggedInException : Exception("Pengguna belum login")
