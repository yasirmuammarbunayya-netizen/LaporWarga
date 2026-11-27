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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.tugashelloworld.R
import com.example.tugashelloworld.databinding.FragmentLocationPickerBinding
import com.example.tugashelloworld.util.LocationHelper
import com.example.tugashelloworld.util.snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Memilih lokasi laporan dengan mengetuk peta.
 * Setiap laporan dapat memiliki titik lokasi yang berbeda.
 * Hasil (lat, lng) dikirim kembali ke FormFragment.
 */
class LocationPickerFragment : Fragment() {

    private var _binding: FragmentLocationPickerBinding? = null
    private val binding get() = _binding!!

    private var map: MapLibreMap? = null
    private var styleLoaded = false
    private var picked: LatLng? = null

    private val locationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.values.any { it }) goToMyLocation()
            else snackbar(R.string.permission_location_denied)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentLocationPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.onCreate(savedInstanceState)

        val startLat = arguments?.getDouble(ARG_LAT, 0.0) ?: 0.0
        val startLng = arguments?.getDouble(ARG_LNG, 0.0) ?: 0.0
        if (startLat != 0.0 || startLng != 0.0) picked = LatLng(startLat, startLng)

        // Menyimpan pilihan saat rotasi layar
        savedInstanceState?.let {
            if (it.containsKey(STATE_LAT)) picked = LatLng(it.getDouble(STATE_LAT), it.getDouble(STATE_LNG))
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnConfirm.setOnClickListener { confirm() }
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
        updateUi()

        binding.mapView.getMapAsync { m ->
            if (_binding == null) return@getMapAsync
            map = m
            m.setMinZoomPreference(3.0)
            m.setStyle(MAP_STYLE_URL) {
                if (_binding == null) return@setStyle
                styleLoaded = true
                val start = picked
                m.cameraPosition = CameraPosition.Builder()
                    .target(start ?: LatLng(DEFAULT_LAT, DEFAULT_LNG))
                    .zoom(if (start != null) 16.0 else 4.5)
                    .build()
                m.addOnMapClickListener { point ->
                    setPicked(point)
                    true
                }
                renderMarker()
                if (start == null && LocationHelper.hasPermission(requireContext())) goToMyLocation()
            }
        }
    }

    private fun setPicked(point: LatLng) {
        picked = point
        renderMarker()
        updateUi()
    }

    private fun renderMarker() {
        val m = map ?: return
        if (!styleLoaded) return
        m.removeAnnotations()
        val point = picked ?: return
        val icon = IconFactory.getInstance(requireContext()).fromBitmap(
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_pin)!!
                .mutate()
                .apply { setTint(ContextCompat.getColor(requireContext(), R.color.brand_dark)) }
                .toBitmap(config = Bitmap.Config.ARGB_8888)
        )
        m.addMarker(MarkerOptions().position(point).icon(icon))
    }

    private fun updateUi() {
        val point = picked
        binding.btnConfirm.isEnabled = point != null
        binding.tvPickedCoords.text =
            if (point == null) getString(R.string.picker_hint)
            else getString(R.string.coordinates_format, point.latitude, point.longitude)
    }

    private fun confirm() {
        val point = picked ?: return
        val handle = findNavController().previousBackStackEntry?.savedStateHandle
        // Kunci terakhir diisi paling akhir karena FormFragment mengamatinya.
        handle?.set(KEY_LNG, point.longitude)
        handle?.set(KEY_LAT, point.latitude)
        findNavController().navigateUp()
    }

    private fun goToMyLocation() {
        if (_binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val location = LocationHelper.currentLocation(requireContext())
                if (_binding == null) return@launch
                if (location == null) {
                    snackbar(R.string.error_location_failed)
                    return@launch
                }
                val point = LatLng(location.latitude, location.longitude)
                setPicked(point)
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) snackbar(R.string.error_location_failed)
            }
        }
    }

    override fun onStart() { super.onStart(); _binding?.mapView?.onStart() }
    override fun onResume() { super.onResume(); _binding?.mapView?.onResume() }
    override fun onPause() { _binding?.mapView?.onPause(); super.onPause() }
    override fun onStop() { _binding?.mapView?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); _binding?.mapView?.onLowMemory() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
        picked?.let {
            outState.putDouble(STATE_LAT, it.latitude)
            outState.putDouble(STATE_LNG, it.longitude)
        }
    }

    override fun onDestroyView() {
        _binding?.mapView?.onDestroy()
        map = null
        styleLoaded = false
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_LAT = "pickerLat"
        const val ARG_LNG = "pickerLng"
        const val KEY_LAT = "picked_lat"
        const val KEY_LNG = "picked_lng"

        private const val STATE_LAT = "state_lat"
        private const val STATE_LNG = "state_lng"
        private const val DEFAULT_LAT = -2.5
        private const val DEFAULT_LNG = 118.0
        private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    }
}
