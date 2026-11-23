package com.example.tugashelloworld.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tugashelloworld.data.ReportRepository
import com.example.tugashelloworld.model.Report
import com.example.tugashelloworld.model.ReportCategory
import com.example.tugashelloworld.model.ReportStatus
import com.example.tugashelloworld.model.SortOrder
import com.example.tugashelloworld.model.categoryEnum
import com.example.tugashelloworld.model.statusEnum
import com.example.tugashelloworld.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Hasil akhir daftar setelah dicari/difilter, plus total data sebelum difilter. */
data class ListState(val items: List<Report>, val totalCount: Int)

/**
 * ViewModel yang dipakai bersama oleh Beranda, Peta, Laporan Saya, dan Profil
 * (di-scope ke Activity) sehingga hanya ada satu pengamat database dan data tidak hilang saat layar diputar.
 */
class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReportRepository(application)
    private var loadJob: Job? = null

    private val _allReports = MutableStateFlow<Resource<List<Report>>>(Resource.Loading)
    val allReports: StateFlow<Resource<List<Report>>> = _allReports.asStateFlow()

    // Input pencarian & filter dari pengguna
    val query = MutableStateFlow("")
    val category = MutableStateFlow<ReportCategory?>(null)
    val status = MutableStateFlow<ReportStatus?>(null)
    val sortOrder = MutableStateFlow(SortOrder.NEWEST)

    /** Daftar yang sudah dicari + difilter + diurutkan. Berubah otomatis mengikuti input pengguna. */
    val uiState: StateFlow<Resource<ListState>> = combine(
        _allReports, query, category, status, sortOrder
    ) { result, q, cat, st, sort ->
        val state: Resource<ListState> = when (result) {
            is Resource.Loading -> Resource.Loading
            is Resource.Error -> Resource.Error(result.throwable)
            is Resource.Success -> Resource.Success(
                ListState(applyFilters(result.data, q, cat, st, sort), result.data.size)
            )
        }
        state
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Resource.Loading)

    /** Mulai mendengarkan data. Aman dipanggil berulang kali. */
    fun start() {
        if (loadJob?.isActive == true) return
        if (_allReports.value is Resource.Error) _allReports.value = Resource.Loading
        loadJob = viewModelScope.launch {
            repository.observeReports()
                .catch { _allReports.value = Resource.Error(it) }
                .collect { _allReports.value = Resource.Success(it) }
        }
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = null
        start()
    }

    /** Dipanggil saat logout agar pencarian/filter pengguna lama dibersihkan. */
    fun onLogout() {
        resetFilters()
    }

    fun resetFilters() {
        query.value = ""
        category.value = null
        status.value = null
        sortOrder.value = SortOrder.NEWEST
    }

    private fun applyFilters(
        data: List<Report>,
        q: String,
        cat: ReportCategory?,
        st: ReportStatus?,
        sort: SortOrder
    ): List<Report> {
        val keyword = q.trim().lowercase()
        val filtered = data.filter { report ->
            val matchQuery = keyword.isEmpty() ||
                report.title.lowercase().contains(keyword) ||
                report.description.lowercase().contains(keyword) ||
                report.address.lowercase().contains(keyword)
            val matchCategory = cat == null || report.categoryEnum() == cat
            val matchStatus = st == null || report.statusEnum() == st
            matchQuery && matchCategory && matchStatus
        }
        return when (sort) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
    }
}
