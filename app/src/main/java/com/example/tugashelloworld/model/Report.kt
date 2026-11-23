package com.example.tugashelloworld.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entitas data utama: Laporan kerusakan fasilitas umum.
 * Disimpan di tabel SQLite "reports" melalui Room.
 *
 * Atribut: id, title, description, category, address, latitude, longitude,
 * photoPath, videoPath, photoGalleryUri, videoGalleryUri, status, userId, userName, createdAt, updatedAt.
 */
@Entity(tableName = "reports", indices = [Index("userId")])
data class Report(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val description: String = "",
    val category: String = ReportCategory.OTHER.name,
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val photoPath: String = "",
    val videoPath: String = "",
    val photoGalleryUri: String = "",
    val videoGalleryUri: String = "",
    val status: String = ReportStatus.SENT.name,
    val userId: Long = 0L,
    val userName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

fun Report.categoryEnum(): ReportCategory = ReportCategory.fromName(category)

fun Report.statusEnum(): ReportStatus = ReportStatus.fromName(status)

fun Report.hasLocation(): Boolean = latitude != 0.0 || longitude != 0.0
