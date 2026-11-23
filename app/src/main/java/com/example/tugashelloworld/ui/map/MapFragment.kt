package com.example.tugashelloworld.ui.map

import android.Manifest
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Peta sebaran laporan memakai OpenStreetMap (osmdroid), tanpa API key.
 * Marker diwarnai sesuai status; ketuk marker untuk melihat ringkasan, lalu buka detail.
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReportViewModel by activityViewModels()

    private var latestReports: List<Report> = emptyList()
    private var stateLoaded = false
    private var hasFittedCamera = false
    private var myLocation: GeoPoint? = null

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) {
                goToMyLocation()
            } else {
                // Izin ditolak: peta tetap berfungsi normal
                snackbar(R.string.permission_location_denied)
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.start()

        setupMap()

        binding.fabMyLocation.setOnClickListener {
            if (LocationHelper.hasPermission(requireContext())) {
                goToMyLocation()
            } else {
                locationPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }

        collectFlow(viewModel.uiState) { state ->
            stateLoaded = state is Resource.Success
            latestReports = (state as? Resource.Success)?.data?.items.orEmpty()
            binding.progress.isVisible = state is Resource.Loading
            renderMarkers()
        }
        collectFlow(NetworkMonitor.observe(requireContext())) { online ->
            binding.tvMapOffline.isVisible = !online
        }
    }

    private fun setupMap() {
        val appContext = requireContext().applicationContext
        Configuration.getInstance().apply {
            load(appContext, appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = appContext.packageName
            osmdroidBasePath = File(appContext.cacheDir, "osmdroid")
            osmdroidTileCache = File(appContext.cacheDir, "osmdroid/tiles")
        }
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(4.5)
            controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))
        }
    }

    private fun renderMarkers() {
        if (_binding == null) return
        val map = binding.mapView
        map.overlays.clear()

        // Ketuk area kosong pada peta untuk menutup kartu info
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                _binding?.cardSelected?.isVisible = false
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }))

        val located = latestReports.filter { it.hasLocation() }
        binding.tvNoMarker.isVisible = stateLoaded && located.isEmpty()

        located.forEach { report ->
            val marker = Marker(map).apply {
                position = GeoPoint(report.latitude, report.longitude)
                title = report.title
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = tintedPin(ContextCompat.getColor(requireContext(), report.statusEnum().color))
                setOnMarkerClickListener { _, _ ->
                    showSelected(report)
                    true
                }
            }
            map.overlays.add(marker)
        }

        myLocation?.let { point ->
            map.overlays.add(Marker(map).apply {
                position = point
                title = getString(R.string.my_location_marker)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = tintedPin(ContextCompat.getColor(requireContext(), R.color.brand_dark))
            })
        }

        if (!hasFittedCamera && located.isNotEmpty()) {
            hasFittedCamera = true
            fitCamera(located)
        }
        map.invalidate()
    }

    private fun tintedPin(color: Int): Drawable? {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_pin)?.mutate()
            ?: return null
        DrawableCompat.setTint(drawable, color)
        return drawable
    }

    private fun showSelected(report: Report) {
        binding.cardSelected.isVisible = true
        binding.tvSelTitle.text = report.title
        binding.tvSelSubtitle.text = getString(
            R.string.marker_snippet,
            getString(report.categoryEnum().label),
            getString(report.statusEnum().label)
        )
        binding.btnSelDetail.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_detail, bundleOf("reportId" to report.id))
        }
    }

    private fun fitCamera(reports: List<Report>) {
        val map = binding.mapView
        val points = reports.map { GeoPoint(it.latitude, it.longitude) }
        map.post {
            if (_binding == null) return@post
            val box = BoundingBox.fromGeoPoints(points)
            if (points.size == 1 || box.latitudeSpan == 0.0 || box.longitudeSpan == 0.0) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(points.first())
            } else {
                map.zoomToBoundingBox(box.increaseByScale(1.4f), true)
            }
        }
    }

    private fun goToMyLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val location = LocationHelper.currentLocation(requireContext())
            if (location == null) {
                snackbar(R.string.error_location_failed)
                return@launch
            }
            val point = GeoPoint(location.latitude, location.longitude)
            myLocation = point
            renderMarkers()
            binding.mapView.controller.setZoom(16.0)
            binding.mapView.controller.animateTo(point)
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
    }

    override fun onPause() {
        _binding?.mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        _binding?.mapView?.onDetach()
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val DEFAULT_LAT = -2.5
        const val DEFAULT_LNG = 118.0 // Pusat Indonesia
    }
}
