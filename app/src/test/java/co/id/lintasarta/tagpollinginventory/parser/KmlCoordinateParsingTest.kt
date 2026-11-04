package co.id.lintasarta.tagpollinginventory.parser

import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import org.junit.Assert.*
import org.junit.Test

class KmlCoordinateParsingTest {

    private fun parseSingleCoordinate(token: String): RouteCoordinate? {
        val parts = token.split(",")
        if (parts.size >= 2) {
            try {
                val lng = parts[0].trim().toDouble()
                val lat = parts[1].trim().toDouble()
                val alt = if (parts.size >= 3) parts[2].trim().toDoubleOrNull() ?: 0.0 else 0.0

                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    return RouteCoordinate(longitude = lng, latitude = lat, altitude = alt)
                }
            } catch (e: Exception) {
                // Skip invalid coordinate token
            }
        }
        return null
    }

    private fun parseCoordinatesString(coordinatesStr: String): List<RouteCoordinate> {
        val list = mutableListOf<RouteCoordinate>()
        val tokens = coordinatesStr.split(Regex("\\s+"))

        for (token in tokens) {
            val coord = parseSingleCoordinate(token)
            if (coord != null) {
                list.add(coord)
            }
        }
        return list
    }

    @Test
    fun testParseSingleCoordinate_standard3D() {
        val token = "140.1845,-3.6921,25.5"
        val result = parseSingleCoordinate(token)

        assertNotNull(result)
        assertEquals(140.1845, result!!.longitude, 0.0001)
        assertEquals(-3.6921, result.latitude, 0.0001)
        assertEquals(25.5, result.altitude, 0.0001)
    }

    @Test
    fun testParseSingleCoordinate_withoutAltitudeDefaultsToZero() {
        val token = "140.1845,-3.6921"
        val result = parseSingleCoordinate(token)

        assertNotNull(result)
        assertEquals(140.1845, result!!.longitude, 0.0001)
        assertEquals(-3.6921, result.latitude, 0.0001)
        assertEquals(0.0, result.altitude, 0.0001)
    }

    @Test
    fun testParseSingleCoordinate_rejectsOutOfBounds() {
        // Latitude > 90
        val invalidLat = "140.1845,95.0,10.0"
        assertNull(parseSingleCoordinate(invalidLat))

        // Longitude > 180
        val invalidLng = "195.0,-3.6921,10.0"
        assertNull(parseSingleCoordinate(invalidLng))
    }

    @Test
    fun testParseSingleCoordinate_handlesMalformedGracefully() {
        assertNull(parseSingleCoordinate("invalid,text"))
        assertNull(parseSingleCoordinate(""))
        assertNull(parseSingleCoordinate("140.18"))
    }

    @Test
    fun testParseCoordinatesString_multipleTokens() {
        val rawKmlString = """
            140.1800,-3.6900,10.0
            140.1810,-3.6910,12.0
            140.1820,-3.6920,15.0
        """.trimIndent()

        val list = parseCoordinatesString(rawKmlString)
        assertEquals(3, list.size)
        assertEquals(140.1800, list[0].longitude, 0.0001)
        assertEquals(140.1810, list[1].longitude, 0.0001)
        assertEquals(140.1820, list[2].longitude, 0.0001)
    }

    @Test
    fun testRouteNameParsing_extractsProjectAndSegment() {
        val routeName = "PRJ_JAYAPURA-SEGMENT_02"
        val parts = routeName.split("-")
        val detectedProject = parts.getOrNull(0)?.trim() ?: ""
        val detectedSegment = parts.getOrNull(1)?.trim() ?: ""

        assertEquals("PRJ_JAYAPURA", detectedProject)
        assertEquals("SEGMENT_02", detectedSegment)
    }
}
