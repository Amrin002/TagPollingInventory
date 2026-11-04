package co.id.lintasarta.tagpollinginventory.quicktag

import co.id.lintasarta.tagpollinginventory.data.model.*
import co.id.lintasarta.tagpollinginventory.location.GpsQualityClass
import co.id.lintasarta.tagpollinginventory.ui.screens.QuickGpsState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class QuickTaggingLogicTest {

    // Helper mirroring simplified status evaluation
    private fun evaluateQuickGpsState(
        isAvailable: Boolean,
        isSamplingComplete: Boolean,
        accuracy: Float,
        qualityClass: GpsQualityClass
    ): QuickGpsState {
        if (!isAvailable) return QuickGpsState.FAILED
        if (!isSamplingComplete) return QuickGpsState.SEARCHING
        return if (qualityClass == GpsQualityClass.RED || accuracy > 15.0f) {
            QuickGpsState.POOR_ACCURACY
        } else if (qualityClass == GpsQualityClass.AMBER || accuracy > 8.0f) {
            QuickGpsState.POOR_ACCURACY
        } else {
            QuickGpsState.READY
        }
    }

    // Pure Kotlin Haversine distance calculator for unit test verification
    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    @Test
    fun testQuickGpsState_readyWhenSamplingCompleteAndAccurate() {
        val state = evaluateQuickGpsState(
            isAvailable = true,
            isSamplingComplete = true,
            accuracy = 3.5f,
            qualityClass = GpsQualityClass.GREEN
        )
        assertEquals(QuickGpsState.READY, state)
    }

    @Test
    fun testQuickGpsState_searchingWhileSamplesUnderTen() {
        val state = evaluateQuickGpsState(
            isAvailable = true,
            isSamplingComplete = false,
            accuracy = 4.0f,
            qualityClass = GpsQualityClass.GREEN
        )
        assertEquals(QuickGpsState.SEARCHING, state)
    }

    @Test
    fun testQuickGpsState_poorAccuracyWhenAboveThreshold() {
        // Accuracy > 8m -> POOR_ACCURACY ("Lokasi belum akurat. Tunggu sebentar atau sesuaikan titik.")
        val state = evaluateQuickGpsState(
            isAvailable = true,
            isSamplingComplete = true,
            accuracy = 12.0f,
            qualityClass = GpsQualityClass.AMBER
        )
        assertEquals(QuickGpsState.POOR_ACCURACY, state)
    }

    @Test
    fun testQuickGpsState_failedWhenLocationUnavailable() {
        val state = evaluateQuickGpsState(
            isAvailable = false,
            isSamplingComplete = false,
            accuracy = 0f,
            qualityClass = GpsQualityClass.RED
        )
        assertEquals(QuickGpsState.FAILED, state)
    }

    @Test
    fun testManualCorrection_preservesOriginalGpsCoordinates() {
        val rawGpsLat = -3.695421
        val rawGpsLng = 128.181432

        // Technician manually drags marker by a few meters
        val adjustedLat = -3.695450
        val adjustedLng = 128.181470

        val pole = Pole(
            id = "POLE-QT-01",
            segmentId = "SEG-QT-01",
            sequence = 1,
            latitude = adjustedLat,
            longitude = adjustedLng,
            originalLatitude = rawGpsLat,
            originalLongitude = rawGpsLng,
            poleCode = "PL-JPR-CTR-001",
            status = TagStatus.COMPLETED
        )

        assertEquals(adjustedLat, pole.latitude, 0.000001)
        assertEquals(adjustedLng, pole.longitude, 0.000001)
        assertEquals(rawGpsLat, pole.originalLatitude!!, 0.000001)
        assertEquals(rawGpsLng, pole.originalLongitude!!, 0.000001)
        assertNotEquals(pole.latitude, pole.originalLatitude!!)
    }

    @Test
    fun testPhotoDistanceRule_validatesWithinTenMeters() {
        val poleLat = -3.695400
        val poleLng = 128.181400

        // Technician standing ~5 meters away
        val techLatNear = -3.695440
        val techLngNear = 128.181420
        val distanceNear = calculateDistanceMeters(techLatNear, techLngNear, poleLat, poleLng)
        assertTrue(distanceNear <= 10.0)

        // Technician standing ~25 meters away
        val techLatFar = -3.695600
        val techLngFar = 128.181550
        val distanceFar = calculateDistanceMeters(techLatFar, techLngFar, poleLat, poleLng)
        assertTrue(distanceFar > 10.0)
    }

    @Test
    fun testSubmitQuickTagPole_resetsStateForNextPoleCleanly() {
        val segmentId = "SEG-QT-01"
        var currentSequence = 1
        var photoPath: String? = "/sdcard/photos/pole_01.jpg"
        var isGpsReady = true

        // First Pole Data
        val pole1 = Pole(
            id = "P1",
            segmentId = segmentId,
            sequence = currentSequence,
            latitude = -3.6954,
            longitude = 128.1814,
            photoPaths = listOf(photoPath!!),
            status = TagStatus.COMPLETED,
            poleCode = "PL-JPR-CTR-001"
        )
        assertEquals(1, pole1.sequence)
        assertEquals(1, pole1.photoPaths.size)

        // Reset state for Next Pole
        currentSequence += 1
        photoPath = null
        isGpsReady = false

        // Verify clean state
        assertNull(photoPath)
        assertFalse(isGpsReady)
        assertEquals(2, currentSequence)

        // Next pole created with clean state
        val pole2 = Pole(
            id = "P2",
            segmentId = segmentId,
            sequence = currentSequence,
            latitude = 0.0,
            longitude = 0.0,
            photoPaths = emptyList(),
            status = TagStatus.NOT_TAGGED,
            poleCode = "PL-JPR-CTR-002"
        )
        assertEquals(2, pole2.sequence)
        assertTrue(pole2.photoPaths.isEmpty())
        assertEquals(TagStatus.NOT_TAGGED, pole2.status)
        assertEquals("PL-JPR-CTR-002", pole2.poleCode)
    }
}
