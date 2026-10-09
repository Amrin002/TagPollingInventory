package co.id.lintasarta.tagpollinginventory.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlin.math.sqrt

enum class GpsQualityClass {
    GREEN,  // Accuracy <= 8m & stable dispersion
    AMBER,  // Accuracy > 8m and <= 15m
    RED     // Accuracy > 15m or unstable
}

data class LocationData(
    val latitude: Double = -3.6954,
    val longitude: Double = 128.1814,
    val accuracy: Float = 2.8f,
    val altitude: Double = 15.0,
    val isAvailable: Boolean = false,
    val providerName: String = "GPS (Hardware)",
    val timestamp: Long = System.currentTimeMillis(),
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val avgCn0DbHz: Float = 0f,
    val constellations: List<String> = emptyList(),
    val sampleCount: Int = 0,
    val targetSamples: Int = 10,
    val sampleDispersionMeters: Float = 0f,
    val qualityClass: GpsQualityClass = GpsQualityClass.GREEN,
    val isSamplingComplete: Boolean = false
)

class LocationProvider(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow(LocationData())
    val currentLocation: StateFlow<LocationData> = _currentLocation.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var nativeLocationListener: LocationListener? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null

    // Multi-sample averaging buffers
    private val locationSamples = mutableListOf<Location>()
    private val targetSampleCount = 10

    // Satellite telemetry buffers
    private var currentSatellitesUsed = 0
    private var currentSatellitesVisible = 0
    private var currentAvgCn0 = 0f
    private var currentConstellations = listOf<String>()

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

        registerGnssStatusCallback()

        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            ).setMinUpdateIntervalMillis(1000L)
                .setWaitForAccurateLocation(true)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    processNewLocationSample(location, "GPS (Hardware)")
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
                    processNewLocationSample(loc, "Fused Location")
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
    private fun registerGnssStatusCallback() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            gnssStatusCallback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val total = status.satelliteCount
                    var used = 0
                    var sumCn0 = 0f
                    val constellationSet = mutableSetOf<String>()

                    for (i in 0 until total) {
                        if (status.usedInFix(i)) {
                            used++
                            sumCn0 += status.getCn0DbHz(i)
                        }
                        val constellationName = when (status.getConstellationType(i)) {
                            GnssStatus.CONSTELLATION_GPS -> "GPS"
                            GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
                            GnssStatus.CONSTELLATION_BEIDOU -> "BEIDOU"
                            GnssStatus.CONSTELLATION_GALILEO -> "GALILEO"
                            GnssStatus.CONSTELLATION_QZSS -> "QZSS"
                            else -> "OTHER"
                        }
                        constellationSet.add(constellationName)
                    }

                    currentSatellitesVisible = total
                    currentSatellitesUsed = used
                    currentAvgCn0 = if (used > 0) sumCn0 / used else 0f
                    currentConstellations = constellationSet.toList()

                    // Refresh state flow with latest satellite telemetry
                    val current = _currentLocation.value
                    _currentLocation.value = current.copy(
                        satellitesUsed = currentSatellitesUsed,
                        satellitesVisible = currentSatellitesVisible,
                        avgCn0DbHz = currentAvgCn0,
                        constellations = currentConstellations
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.registerGnssStatusCallback(context.mainExecutor, gnssStatusCallback!!)
            } else {
                @Suppress("DEPRECATION")
                locationManager.registerGnssStatusCallback(gnssStatusCallback!!,
                    Handler(context.mainLooper)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                processNewLocationSample(gpsLoc, "Native GPS")
            }

            nativeLocationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    processNewLocationSample(location, "Native GPS")
                }
                @Deprecated("Deprecated in API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    nativeLocationListener!!,
                    context.mainLooper
                )
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1f,
                    nativeLocationListener!!,
                    context.mainLooper
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetSampling() {
        synchronized(locationSamples) {
            locationSamples.clear()
        }
    }

    private fun processNewLocationSample(location: Location, provider: String) {
        synchronized(locationSamples) {
            // Filter out obviously bad fixes
            if (location.hasAccuracy() && location.accuracy > 50f) {
                return
            }

            if (locationSamples.size >= targetSampleCount) {
                locationSamples.removeAt(0) // Sliding window
            }
            locationSamples.add(location)

            // Calculate Centroid (Averaged Lat/Lng)
            var sumLat = 0.0
            var sumLng = 0.0
            var sumAcc = 0f

            for (sample in locationSamples) {
                sumLat += sample.latitude
                sumLng += sample.longitude
                sumAcc += if (sample.hasAccuracy()) sample.accuracy else 5f
            }

            val avgLat = sumLat / locationSamples.size
            val avgLng = sumLng / locationSamples.size
            val avgAccuracy = sumAcc / locationSamples.size

            // Calculate Dispersion (standard deviation of distance from centroid)
            var sumDistSq = 0.0
            val results = FloatArray(1)
            for (sample in locationSamples) {
                Location.distanceBetween(avgLat, avgLng, sample.latitude, sample.longitude, results)
                sumDistSq += results[0].toDouble().pow(2.0)
            }
            val dispersion = if (locationSamples.size > 1) {
                sqrt(sumDistSq / locationSamples.size).toFloat()
            } else 0f

            // Determine Quality Class
            val qualityClass = when {
                avgAccuracy <= 8.0f && dispersion <= 3.0f -> GpsQualityClass.GREEN
                avgAccuracy <= 15.0f -> GpsQualityClass.AMBER
                else -> GpsQualityClass.RED
            }

            _currentLocation.value = LocationData(
                latitude = avgLat,
                longitude = avgLng,
                accuracy = avgAccuracy,
                altitude = if (location.hasAltitude()) location.altitude else 15.0,
                isAvailable = true,
                providerName = provider,
                timestamp = location.time,
                satellitesUsed = currentSatellitesUsed,
                satellitesVisible = currentSatellitesVisible,
                avgCn0DbHz = currentAvgCn0,
                constellations = currentConstellations,
                sampleCount = locationSamples.size,
                targetSamples = targetSampleCount,
                sampleDispersionMeters = dispersion,
                qualityClass = qualityClass,
                isSamplingComplete = locationSamples.size >= targetSampleCount
            )
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

        gnssStatusCallback?.let {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager?.unregisterGnssStatusCallback(it)
        }
        gnssStatusCallback = null
    }

    fun simulateLocationFix(lat: Double, lng: Double, accuracy: Float) {
        _currentLocation.value = LocationData(
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            altitude = 15.0,
            isAvailable = true,
            providerName = "Simulated GPS Fix",
            satellitesUsed = 12,
            satellitesVisible = 18,
            avgCn0DbHz = 32.5f,
            constellations = listOf("GPS", "GLONASS", "GALILEO"),
            sampleCount = 10,
            targetSamples = 10,
            sampleDispersionMeters = 0.8f,
            qualityClass = if (accuracy <= 8f) GpsQualityClass.GREEN else if (accuracy <= 15f) GpsQualityClass.AMBER else GpsQualityClass.RED,
            isSamplingComplete = true
        )
    }
}

