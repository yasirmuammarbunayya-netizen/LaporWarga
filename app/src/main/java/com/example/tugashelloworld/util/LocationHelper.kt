package com.example.tugashelloworld.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Fungsi bantu GPS memakai LocationManager bawaan Android
 * (tidak bergantung pada Google Play Services).
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    /**
     * Mengambil lokasi terkini.
     * Mengembalikan null jika izin/GPS tidak tersedia atau waktu habis.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null

        val manager = context.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter {
            runCatching {
                manager.isProviderEnabled(it)
            }.getOrDefault(false)
        }

        if (providers.isEmpty()) return null

        for (provider in providers) {
            val fresh = withTimeoutOrNull(8_000) {
                requestFix(context, manager, provider)
            }

            if (fresh != null) return fresh
        }

        // Cadangan: lokasi terakhir yang diketahui.
        return providers.mapNotNull {
            runCatching {
                manager.getLastKnownLocation(it)
            }.getOrNull()
        }.maxByOrNull {
            it.time
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFix(
        context: Context,
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { cont ->

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()

                cont.invokeOnCancellation {
                    signal.cancel()
                }

                manager.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context)
                ) { location ->
                    if (cont.isActive) {
                        cont.resume(location)
                    }
                }

            } else {
                val listener = object : LocationListener {

                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)

                        if (cont.isActive) {
                            cont.resume(location)
                        }
                    }

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(
                        provider: String?,
                        status: Int,
                        extras: Bundle?
                    ) = Unit
                }

                cont.invokeOnCancellation {
                    manager.removeUpdates(listener)
                }

                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(
                    provider,
                    listener,
                    Looper.getMainLooper()
                )
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cont.isActive) {
                cont.resume(null)
            }
        }
    }

    /**
     * Mengubah koordinat menjadi alamat.
     * Membutuhkan layanan Geocoder dan dapat mengembalikan null jika gagal.
     */
    suspend fun addressFrom(
        context: Context,
        lat: Double,
        lng: Double
    ): String? {
        if (!Geocoder.isPresent()) return null

        val geocoder = Geocoder(context, Locale("id", "ID"))

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<String?> { cont ->

                    geocoder.getFromLocation(
                        lat,
                        lng,
                        1,
                        object : Geocoder.GeocodeListener {

                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) {
                                    cont.resume(
                                        addresses.firstOrNull()?.getAddressLine(0)
                                    )
                                }
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) {
                                    cont.resume(null)
                                }
                            }
                        }
                    )
                }

            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)
                        ?.firstOrNull()
                        ?.getAddressLine(0)
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
