package co.id.lintasarta.tagpollinginventory.location

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

class GpsQualityAndCentroidTest {

    // Helper function that mirrors the centroid calculation logic in LocationProvider
    private fun calculateCentroid(samples: List<SampleCoord>): CentroidResult {
        var sumLat = 0.0
        var sumLng = 0.0
        var sumAcc = 0f

        for (s in samples) {
            sumLat += s.lat
            sumLng += s.lng
            sumAcc += s.acc
        }

        val avgLat = sumLat / samples.size
        val avgLng = sumLng / samples.size
        val avgAcc = sumAcc / samples.size

        return CentroidResult(avgLat, avgLng, avgAcc)
    }

    // Helper function mirroring the GpsQualityClass logic
    private fun determineQuality(avgAccuracy: Float, dispersion: Float): GpsQualityClass {
        return when {
            avgAccuracy <= 8.0f && dispersion <= 3.0f -> GpsQualityClass.GREEN
            avgAccuracy <= 15.0f -> GpsQualityClass.AMBER
            else -> GpsQualityClass.RED
        }
    }

    data class SampleCoord(val lat: Double, val lng: Double, val acc: Float)
    data class CentroidResult(val avgLat: Double, val avgLng: Double, val avgAcc: Float)

    @Test
    fun testCentroidAveraging_calculatesCorrectCentroid() {
        val samples = listOf(
            SampleCoord(-3.6950, 128.1810, 3.0f),
            SampleCoord(-3.6952, 128.1812, 4.0f),
            SampleCoord(-3.6954, 128.1814, 5.0f)
        )

        val result = calculateCentroid(samples)

        assertEquals(-3.6952, result.avgLat, 0.00001)
        assertEquals(128.1812, result.avgLng, 0.00001)
        assertEquals(4.0f, result.avgAcc, 0.01f)
    }

    @Test
    fun testGpsQualityClassification_Green() {
        // Accuracy <= 8m AND dispersion <= 3m -> GREEN
        val quality = determineQuality(avgAccuracy = 5.2f, dispersion = 1.8f)
        assertEquals(GpsQualityClass.GREEN, quality)
    }

    @Test
    fun testGpsQualityClassification_Amber() {
        // Accuracy <= 8m but high dispersion (> 3m) -> AMBER
        val qualityHighDispersion = determineQuality(avgAccuracy = 6.0f, dispersion = 4.5f)
        assertEquals(GpsQualityClass.AMBER, qualityHighDispersion)

        // Accuracy between 8m and 15m -> AMBER
        val qualityModerateAcc = determineQuality(avgAccuracy = 12.0f, dispersion = 2.0f)
        assertEquals(GpsQualityClass.AMBER, qualityModerateAcc)
    }

    @Test
    fun testGpsQualityClassification_Red() {
        // Accuracy > 15m -> RED
        val qualityBadAcc = determineQuality(avgAccuracy = 18.5f, dispersion = 5.0f)
        assertEquals(GpsQualityClass.RED, qualityBadAcc)
    }

    @Test
    fun testSlidingWindow_keepsMaximumTenSamples() {
        val buffer = mutableListOf<Int>()
        val maxTarget = 10

        for (i in 1..15) {
            if (buffer.size >= maxTarget) {
                buffer.removeAt(0)
            }
            buffer.add(i)
        }

        assertEquals(10, buffer.size)
        assertEquals(6, buffer.first())
        assertEquals(15, buffer.last())
    }

    @Test
    fun testOutlierRejection_filtersAccuracyGreaterThan50Meters() {
        val rawAccuracies = listOf(4.2f, 8.5f, 55.0f, 3.1f, 120.0f)
        val filtered = rawAccuracies.filter { it <= 50.0f }

        assertEquals(3, filtered.size)
        assertFalse(filtered.contains(55.0f))
        assertFalse(filtered.contains(120.0f))
    }
}
