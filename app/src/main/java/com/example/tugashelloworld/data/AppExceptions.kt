package com.example.tugashelloworld.data

/** Exception khusus aplikasi agar pesan error dapat ditampilkan dengan jelas. */
class EmailInUseException : Exception("Email sudah terdaftar")

class InvalidCredentialsException : Exception("Email atau password salah")

/** Email tidak terdaftar atau nama tidak cocok (pesan sengaja dibuat sama agar email tidak bisa ditebak). */
class ResetVerificationException : Exception("Email atau nama tidak cocok")

class NotLoggedInException : Exception("Pengguna belum login")
