// ============================================================
// FILE: service/location/LocationProvider.kt
// Purpose: Fetches device location with GPS → Network → IP fallback
// ============================================================

package com.app.notisync_sender.service.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.app.notisync_sender.domain.model.DeviceLocation
import com.app.notisync_sender.domain.model.DeviceInfo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceInfo: DeviceInfo
) {

    companion object {
        private const val TAG = "LocationProvider"
        private const val TOTAL_TIMEOUT_MS = 30_000L
        private const val FRESH_REQUEST_TIMEOUT = 12_000L
        private const val IP_API_URL = "https://ipapi.co/json/"
        // OpenCellID or Google Geolocation would be better, but ipapi.co is simplest for demonstration
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    fun isGpsEnabled(): Boolean = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
    fun isNetworkLocationEnabled(): Boolean = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }

    suspend fun getCurrentLocation(userId: String, requestId: String? = null): Result<DeviceLocation> {
        return try {
            val locationResult = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                fetchLocationWithFallback(userId, requestId)
            }

            if (locationResult != null) {
                Result.success(locationResult)
            } else {
                Log.e(TAG, "Failed to get any location (GPS/Network/Cell/IP/Cached)")
                Result.failure(Exception("Location unavailable"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getCurrentLocation: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun fetchLocationWithFallback(userId: String, requestId: String?): DeviceLocation? {
        if (hasLocationPermission()) {
            // 1. Try Cached Location
            val cached = getLastKnownLocation()
            if (cached != null && isLocationFresh(cached)) {
                return cached.toDeviceLocation(userId, requestId)
            }

            // 2. Try Fresh Request via Fused
            val fresh = withTimeoutOrNull(FRESH_REQUEST_TIMEOUT) { requestFreshLocation() }
            if (fresh != null) return fresh.toDeviceLocation(userId, requestId)

            // 3. Try Legacy Network
            val network = withTimeoutOrNull(8000) { requestLegacyNetworkLocation() }
            if (network != null) return network.toDeviceLocation(userId, requestId)
        }

        // 4. Try Cell ID Fallback (Requires Phone State permission)
        if (hasLocationPermission() && hasPhoneStatePermission()) {
            Log.d(TAG, "Attempting Cell ID fallback...")
            val cellLocation = fetchCellIdLocation(userId, requestId)
            if (cellLocation != null) return cellLocation
        }

        // 5. Ultimate Fallback: IP-based Location
        Log.d(TAG, "Attempting IP Geolocation fallback...")
        val ipLocation = fetchIpLocation(userId, requestId)
        if (ipLocation != null) return ipLocation

        return null
    }

    private suspend fun getLastKnownLocation(): Location? {
        return try {
            @Suppress("MissingPermission")
            fusedLocationClient.lastLocation.await() ?: getLastKnownLocationLegacy()
        } catch (e: Exception) {
            getLastKnownLocationLegacy()
        }
    }

    private fun getLastKnownLocationLegacy(): Location? {
        return try {
            @Suppress("MissingPermission")
            val net = if (isNetworkLocationEnabled()) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
            @Suppress("MissingPermission")
            val gps = if (isGpsEnabled()) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            
            if (net != null && gps != null) {
                if (net.time > gps.time) net else gps
            } else {
                net ?: gps
            }
        } catch (e: Exception) { null }
    }

    private suspend fun requestFreshLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            val priority = if (isGpsEnabled()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val request = LocationRequest.Builder(priority, 5000).setMaxUpdates(1).build()
            
            val callback = object : LocationCallback() {
                override fun onLocationResult(res: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    if (continuation.isActive) continuation.resume(res.lastLocation)
                }
            }

            try {
                @Suppress("MissingPermission")
                fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                continuation.invokeOnCancellation { fusedLocationClient.removeLocationUpdates(callback) }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private suspend fun requestLegacyNetworkLocation(): Location? {
        if (!isNetworkLocationEnabled()) return null
        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(loc)
                }
                @Deprecated("Deprecated")
                override fun onStatusChanged(p: String?, s: Int, b: android.os.Bundle?) {}
            }
            try {
                @Suppress("MissingPermission")
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private suspend fun fetchCellIdLocation(userId: String, requestId: String?): DeviceLocation? {
        return withContext(Dispatchers.IO) {
            try {
                @Suppress("MissingPermission")
                val cellInfo = telephonyManager.allCellInfo
                if (cellInfo.isNullOrEmpty()) return@withContext null

                // Extract Cell ID from the first available tower info
                val identity = when (val info = cellInfo[0]) {
                    is CellInfoGsm -> info.cellIdentity
                    is CellInfoLte -> info.cellIdentity
                    is CellInfoWcdma -> info.cellIdentity
                    else -> null
                } ?: return@withContext null

                Log.d(TAG, "Read Cell ID: $identity")
                
                // Real Cell ID tracking requires an external API call to resolve coordinates.
                // For now, if we reach this point and GPS is OFF, we automatically trigger IP fallback 
                // which is more reliable than manual Cell-to-Lat conversion without a paid API.
                null 
            } catch (e: Exception) {
                Log.e(TAG, "Cell ID read failed: ${e.message}")
                null
            }
        }
    }

    private suspend fun fetchIpLocation(userId: String, requestId: String?): DeviceLocation? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(IP_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(response).asJsonObject
                    
                    val lat = json.get("latitude").asDouble
                    val lon = json.get("longitude").asDouble
                    
                    Log.d(TAG, "IP Geolocation success: $lat, $lon")
                    
                    DeviceLocation(
                        locationId = UUID.randomUUID().toString(),
                        userId = userId,
                        deviceId = deviceInfo.deviceId,
                        deviceName = deviceInfo.deviceName,
                        latitude = lat,
                        longitude = lon,
                        accuracy = 10000f,
                        timestamp = System.currentTimeMillis(),
                        provider = DeviceLocation.LocationProvider.IP,
                        requestId = requestId
                    )
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "IP Geolocation failed: ${e.message}")
                null
            }
        }
    }

    private fun isLocationFresh(loc: Location): Boolean = (System.currentTimeMillis() - loc.time) < (2 * 60 * 1000L)

    private fun Location.toDeviceLocation(userId: String, requestId: String?): DeviceLocation {
        val prov = when (this.provider) {
            LocationManager.GPS_PROVIDER -> DeviceLocation.LocationProvider.GPS
            LocationManager.NETWORK_PROVIDER -> DeviceLocation.LocationProvider.NETWORK
            else -> DeviceLocation.LocationProvider.FUSED
        }
        return DeviceLocation(UUID.randomUUID().toString(), userId, deviceInfo.deviceId, deviceInfo.deviceName, latitude, longitude, accuracy, System.currentTimeMillis(), prov, requestId)
    }
}
