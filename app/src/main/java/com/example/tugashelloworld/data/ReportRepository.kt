package com.example.tugashelloworld.data

import android.content.Context
import com.example.tugashelloworld.db.AppDatabase
import com.example.tugashelloworld.model.Report
import kotlinx.coroutines.flow.Flow

/** Seluruh operasi CRUD laporan. Data tersimpan permanen di SQLite lewat Room. */
class ReportRepository(context: Context) {

    private val dao = AppDatabase.get(context).reportDao()

    fun observeReports(): Flow<List<Report>> = dao.observeAll()

    fun observeReport(id: Long): Flow<Report?> = dao.observe(id)

    suspend fun getReport(id: Long): Report? = dao.get(id)

    suspend fun add(report: Report): Long = dao.insert(report)

    suspend fun update(report: Report) = dao.update(report)

    suspend fun updateStatus(id: Long, status: String) =
        dao.updateStatus(id, status, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.delete(id)
}
