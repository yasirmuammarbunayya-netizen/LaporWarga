package com.example.tugashelloworld.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entitas data utama: laporan kerusakan fasilitas umum.
 * Disimpan di tabel SQLite "reports" melalui Room.
 */
@Entity(
    tableName = "reports",
    indices = [Index("userId")]
)
data class Report(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val title: String = "",
    val description: String = "",
    val category: String = ReportCategory.OTHER.name,
    val address: String = "",

    // Koordinat lokasi laporan
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    // Media laporan
    val photoPath: String = "",
    val videoPath: String = "",
    val photoGalleryUri: String = "",
    val videoGalleryUri: String = "",

    // Status laporan
    val status: String = ReportStatus.SENT.name,

    // Informasi pengguna
    val userId: Long = 0L,
    val userName: String = "",

    // Waktu pembuatan dan pembaruan
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * Mengubah kategori laporan menjadi enum.
 */
fun Report.categoryEnum(): ReportCategory =
    ReportCategory.fromName(category)

/**
 * Mengubah status laporan menjadi enum.
 */
fun Report.statusEnum(): ReportStatus =
    ReportStatus.fromName(status)

/**
 * Memeriksa apakah laporan memiliki koordinat lokasi.
 * Pasangan 0.0, 0.0 digunakan sebagai penanda
 * bahwa koordinat belum tersedia.
 */
fun Report.hasLocation(): Boolean {
    return latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            (latitude != 0.0 || longitude != 0.0)
}
