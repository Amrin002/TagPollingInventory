package co.id.lintasarta.tagpollinginventory.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocationData(
    val latitude: Double = -3.6954,
    val longitude: Double = 128.1814,
    val accuracy: Float = 2.8f,
    val altitude: Double = 15.0,
    val isAvailable: Boolean = false,
    val providerName: String = "GPS (Hardware)",
    val timestamp: Long = System.currentTimeMillis()
)

class LocationProvider(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow(LocationData())
    val currentLocation: StateFlow<LocationData> = _currentLocation.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var nativeLocationListener: LocationListener? = null

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L
            ).setMinUpdateIntervalMillis(1000L)
                .setWaitForAccurateLocation(true)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    updateLocationData(location, "GPS (Hardware)")
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                context.mainLooper
            )

            // Also request last known location immediately
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    updateLocationData(loc, "Fused Location")
                } else {
                    tryFallbackNativeLocationManager()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tryFallbackNativeLocationManager()
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryFallbackNativeLocationManager() {
        if (!hasLocationPermission()) return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        try {
            val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (gpsLoc != null) {
                updateLocationData(gpsLoc, "Native GPS")
            }

            nativeLocationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    updateLocationData(location, "Native GPS")
                }
                @Deprecated("Deprecated in API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    1f,
                    nativeLocationListener!!,
                    context.mainLooper
                )
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    1f,
                    nativeLocationListener!!,
                    context.mainLooper
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null

        nativeLocationListener?.let {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager?.removeUpdates(it)
        }
        nativeLocationListener = null
    }

    private fun updateLocationData(location: Location, provider: String) {
        _currentLocation.value = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy else 3.0f,
            altitude = if (location.hasAltitude()) location.altitude else 15.0,
            isAvailable = true,
            providerName = provider,
            timestamp = location.time
        )
    }

    fun simulateLocationFix(lat: Double, lng: Double, accuracy: Float) {
        _currentLocation.value = LocationData(
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            altitude = 15.0,
            isAvailable = true,
            providerName = "Simulated GPS Fix"
        )
    }
}
