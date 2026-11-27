package com.example.tugashelloworld.ui.map

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentMapBinding
import com.example.tugashelloworld.model.Report
import com.example.tugashelloworld.model.categoryEnum
import com.example.tugashelloworld.model.hasLocation
import com.example.tugashelloworld.model.statusEnum
import com.example.tugashelloworld.ui.home.ReportViewModel
import com.example.tugashelloworld.util.LocationHelper
import com.example.tugashelloworld.util.NetworkMonitor
import com.example.tugashelloworld.util.Resource
import com.example.tugashelloworld.util.collectFlow
import com.example.tugashelloworld.util.snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

/**
 * Peta sebaran laporan menggunakan MapLibre dan OpenFreeMap.
 *
 * Fitur:
 * - Menampilkan seluruh laporan yang mempunyai koordinat.
 * - Marker berada pada koordinat laporan yang tersimpan di Room.
 * - Warna marker mengikuti status laporan terbaru.
 * - Pembaruan marker otomatis melalui Flow Room.
 * - Ringkasan dan navigasi detail laporan.
 * - Lokasi pengguna menggunakan GPS.
 * - Penyesuaian kamera otomatis.
 * - Indikator koneksi internet.
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportViewModel by activityViewModels()

    private var mapLibreMap: MapLibreMap? = null
    private var mapStyleLoaded = false

    private var latestReports: List<Report> = emptyList()
    private var stateLoaded = false
    private var hasFittedCamera = false
    private var myLocation: LatLng? = null

    private val locationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.values.any { granted -> granted }) {
                goToMyLocation()
            } else {
                snackbar(R.string.permission_location_denied)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        MapLibre.getInstance(requireContext())

        _binding = FragmentMapBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.onCreate(savedInstanceState)

        setupMap()

        viewModel.start()

        binding.fabMyLocation.setOnClickListener {
            if (LocationHelper.hasPermission(requireContext())) {
                goToMyLocation()
            } else {
                locationPermission.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        // Mengamati SELURUH laporan dari database Room.
        // Tidak menggunakan uiState agar filter halaman lain
        // tidak menyembunyikan marker pada peta.
        collectFlow(viewModel.allReports) { state ->

            stateLoaded = state is Resource.Success

            when (state) {
                is Resource.Success -> {
                    latestReports = state.data
                }

                is Resource.Loading -> {
                    latestReports = emptyList()
                }

                is Resource.Error -> {
                    latestReports = emptyList()
                }
            }

            binding.progress.isVisible =
                state is Resource.Loading

            renderMarkers()
        }

        collectFlow(NetworkMonitor.observe(requireContext())) { online ->
            binding.tvMapOffline.isVisible = !online
        }
    }

    /**
     * Menginisialisasi peta OpenFreeMap.
     */
    private fun setupMap() {

        binding.mapView.getMapAsync { map ->

            if (_binding == null) return@getMapAsync

            mapLibreMap = map

            map.setMinZoomPreference(3.0)

            map.uiSettings.apply {
                isCompassEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
            }

            map.setStyle(MAP_STYLE_URL) {

                if (_binding == null) return@setStyle

                mapStyleLoaded = true

                map.cameraPosition =
                    org.maplibre.android.camera.CameraPosition.Builder()
                        .target(LatLng(DEFAULT_LAT, DEFAULT_LNG))
                        .zoom(4.5)
                        .build()

                map.addOnMapClickListener {
                    _binding?.cardSelected?.isVisible = false
                    true
                }

                renderMarkers()
            }
        }
    }

    /**
     * Menampilkan seluruh marker laporan.
     *
     * Setiap kali Room mengirim data terbaru,
     * marker dibuat ulang dengan warna sesuai status terbaru.
     */
    private fun renderMarkers() {

        if (_binding == null || !mapStyleLoaded) return

        val map = mapLibreMap ?: return

        // Menghapus marker lama agar tidak terjadi duplikasi.
        map.removeAnnotations()

        // Hanya laporan dengan latitude dan longitude valid.
        val located = latestReports.filter { report ->
            report.hasLocation()
        }

        binding.tvNoMarker.isVisible =
            stateLoaded && located.isEmpty()

        // Laporan dengan koordinat yang (hampir) sama, mis. GPS emulator yang tetap,
        // digeser melingkar sangat kecil agar setiap laporan tetap terlihat dan bisa diketuk.
        val seen = HashMap<String, Int>()

        // Marker setiap laporan.
        located.forEach { report ->

            val key = String.format(
                java.util.Locale.US, "%.5f,%.5f",
                report.latitude, report.longitude
            )
            val index = seen.merge(key, 1, Int::plus)!! - 1
            val position = spreadPosition(report.latitude, report.longitude, index)

            val color = ContextCompat.getColor(
                requireContext(),
                report.statusEnum().color
            )

            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(report.id.toString())
                    .snippet(report.title)
                    .icon(createMarkerIcon(color))
            )
        }

        // Marker lokasi pengguna.
        myLocation?.let { point ->

            val color = ContextCompat.getColor(
                requireContext(),
                R.color.brand_dark
            )

            map.addMarker(
                MarkerOptions()
                    .position(point)
                    .title(MY_LOCATION_TITLE)
                    .snippet(
                        getString(R.string.my_location_marker)
                    )
                    .icon(createMarkerIcon(color))
            )
        }

        // Menangani klik marker.
        map.setOnMarkerClickListener { marker ->

            if (marker.title == MY_LOCATION_TITLE) {
                true
            } else {

                val selectedReport = latestReports.firstOrNull {
                    it.id.toString() == marker.title
                }

                selectedReport?.let { report ->
                    showSelected(report)
                }

                true
            }
        }

        // Kamera hanya disesuaikan otomatis saat pertama kali
        // laporan dengan koordinat tersedia.
        if (!hasFittedCamera && located.isNotEmpty()) {
            hasFittedCamera = true
            fitCamera(located)
        }
    }

    /**
     * Menggeser marker ke-[index] pada koordinat yang sama secara melingkar (±8 m per cincin).
     */
    private fun spreadPosition(lat: Double, lng: Double, index: Int): LatLng {
        if (index == 0) return LatLng(lat, lng)
        val ring = (index - 1) / 6 + 1
        val angle = Math.toRadians(((index - 1) % 6) * 60.0 + ring * 20.0)
        val meters = 8.0 * ring
        val dLat = meters * Math.sin(angle) / 111_320.0
        val dLng = meters * Math.cos(angle) /
                (111_320.0 * Math.cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return LatLng(lat + dLat, lng + dLng)
    }

    /**
     * Membuat ikon marker sesuai warna status.
     */
    private fun createMarkerIcon(color: Int) =
        IconFactory.getInstance(requireContext()).fromBitmap(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_pin
            )!!.mutate().apply {
                setTint(color)
            }.toBitmap(
                config = Bitmap.Config.ARGB_8888
            )
        )

    /**
     * Menampilkan informasi laporan yang dipilih.
     */
    private fun showSelected(report: Report) {

        if (_binding == null) return

        binding.cardSelected.isVisible = true

        binding.tvSelTitle.text = report.title

        binding.tvSelSubtitle.text = getString(
            R.string.marker_snippet,
            getString(report.categoryEnum().label),
            getString(report.statusEnum().label)
        )

        binding.btnSelDetail.setOnClickListener {

            findNavController().navigate(
                R.id.action_map_to_detail,
                bundleOf("reportId" to report.id)
            )
        }
    }

    /**
     * Menyesuaikan kamera berdasarkan koordinat laporan.
     */
    private fun fitCamera(reports: List<Report>) {

        if (reports.isEmpty()) return

        val map = mapLibreMap ?: return

        val points = reports.map { report ->
            LatLng(
                report.latitude,
                report.longitude
            )
        }

        binding.mapView.post {

            if (_binding == null) return@post

            if (points.size == 1) {

                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        points.first(),
                        16.0
                    )
                )

            } else {

                val builder = LatLngBounds.Builder()

                points.forEach { point ->
                    builder.include(point)
                }

                val bounds = builder.build()

                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds,
                        80
                    )
                )
            }
        }
    }

    /**
     * Mengambil lokasi pengguna dan memindahkan kamera.
     */
    private fun goToMyLocation() {

        if (_binding == null) return

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                val location = LocationHelper.currentLocation(
                    requireContext()
                )

                if (_binding == null) return@launch

                if (location == null) {
                    snackbar(R.string.error_location_failed)
                    return@launch
                }

                val point = LatLng(
                    location.latitude,
                    location.longitude
                )

                myLocation = point

                renderMarkers()

                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        point,
                        16.0
                    )
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {

                if (_binding != null) {
                    snackbar(R.string.error_location_failed)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        _binding?.mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
    }

    override fun onPause() {
        _binding?.mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        _binding?.mapView?.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _binding?.mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        _binding?.mapView?.onDestroy()

        mapLibreMap = null
        mapStyleLoaded = false
        hasFittedCamera = false

        super.onDestroyView()

        _binding = null
    }

    private companion object {

        const val DEFAULT_LAT = -2.5
        const val DEFAULT_LNG = 118.0

        const val MAP_STYLE_URL =
            "https://tiles.openfreemap.org/styles/liberty"

        const val MY_LOCATION_TITLE =
            "__my_location__"
    }
}
