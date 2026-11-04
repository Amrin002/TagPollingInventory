package co.id.lintasarta.tagpollinginventory.location

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import kotlin.math.*

/**
 * Helper untuk menentukan nama kota pengguna berdasarkan koordinat GPS.
 * Mendukung online reverse-geocoding dan fallback 100% offline
 * dengan kalkulasi jarak terdekat ke pusat-pusat kota Area 18 (Maluku & Papua).
 */
object CityLocationHelper {

    private data class CityCenter(
        val name: String,
        val latitude: Double,
        val longitude: Double
    )

    // Daftar kota & pusat operasional utama di Area 18 (Maluku Papua Operation)
    private val AREA_18_CITIES = listOf(
        CityCenter("Ambon", -3.6954, 128.1814),
        CityCenter("Tual", -5.6288, 132.7484),
        CityCenter("Timika", -4.5467, 136.8889),
        CityCenter("Jayapura", -2.5381, 140.7054),
        CityCenter("Sentani", -2.5667, 140.5167),
        CityCenter("Abepura", -2.6050, 140.6720),
        CityCenter("Manokwari", -0.8615, 134.0620),
        CityCenter("Sorong", -0.8762, 131.2558),
        CityCenter("Merauke", -8.4991, 140.4011),
        CityCenter("Biak", -1.1824, 136.0841),
        CityCenter("Nabire", -3.3667, 135.4833),
        CityCenter("Saumlaki", -7.9833, 131.3000),
        CityCenter("Namlea", -3.2500, 127.1000),
        CityCenter("Masohi", -3.3083, 128.9583),
        CityCenter("Fakfak", -2.9267, 132.2961),
        CityCenter("Kaimana", -3.6622, 133.7719),
        CityCenter("Wamena", -4.0956, 138.9442),
        CityCenter("Boven Digoel", -6.0967, 140.3008),
        CityCenter("Serui", -1.8812, 136.2415)
    )

    fun resolveCityName(context: Context?, latitude: Double, longitude: Double): String {
        // Jika koordinat kosong / belum dapat GPS, tampilkan identitas region
        if (latitude == 0.0 && longitude == 0.0) {
            return "Ambon" // Default regional center
        }

        // 1. Cek kedekatan langsung dengan kota-kota referensi (Instan 0ms, 100% offline & ANR-free)
        val nearestCity = resolveNearestKnownCity(latitude, longitude)
        if (nearestCity != "Wilayah Operasional") {
            return nearestCity
        }

        // 2. Jika di luar radius kota referensi tetapi dalam wilayah Indonesia, coba geocoder
        if (latitude in -11.5..6.5 && longitude in 95.0..142.0 && context != null) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.forLanguageTag("id-ID"))
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val rawCity = addr.subAdminArea ?: addr.locality ?: addr.adminArea
                        if (!rawCity.isNullOrBlank()) {
                            val clean = cleanCityName(rawCity)
                            if (clean.isNotBlank()) {
                                return clean
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Saat offline / timeout, langsung beralih ke fallback
            }
        }

        return "Wilayah Operasional"
    }

    private fun cleanCityName(raw: String): String {
        return raw.replace("Kota ", "", ignoreCase = true)
            .replace("Kabupaten ", "", ignoreCase = true)
            .replace("Kab. ", "", ignoreCase = true)
            .replace("Regency", "", ignoreCase = true)
            .replace("City", "", ignoreCase = true)
            .trim()
    }

    private fun resolveNearestKnownCity(lat: Double, lng: Double): String {
        var closestCity: CityCenter? = null
        var minDistanceKm = Double.MAX_VALUE

        for (city in AREA_18_CITIES) {
            val dist = calculateDistanceKm(lat, lng, city.latitude, city.longitude)
            if (dist < minDistanceKm) {
                minDistanceKm = dist
                closestCity = city
            }
        }

        return if (closestCity != null && minDistanceKm <= 350.0) {
            closestCity.name
        } else {
            "Wilayah Operasional"
        }
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radius bumi dalam KM
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
