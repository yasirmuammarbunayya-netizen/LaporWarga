package com.example.tugashelloworld.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.tugashelloworld.model.Report
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    /** READ (realtime): Room otomatis mengirim data baru setiap tabel berubah. */
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Report>>

    @Query("SELECT * FROM reports WHERE id = :id")
    fun observe(id: Long): Flow<Report?>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun get(id: Long): Report?

    /** CREATE */
    @Insert
    suspend fun insert(report: Report): Long

    /** UPDATE */
    @Update
    suspend fun update(report: Report)

    @Query("UPDATE reports SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long)

    /** DELETE */
    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun delete(id: Long)
}
