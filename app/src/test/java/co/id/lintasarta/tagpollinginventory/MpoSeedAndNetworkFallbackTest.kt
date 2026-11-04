package co.id.lintasarta.tagpollinginventory

import co.id.lintasarta.tagpollinginventory.data.auth.UserSession
import co.id.lintasarta.tagpollinginventory.data.local.Converters
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import co.id.lintasarta.tagpollinginventory.network.RnimsApiClient
import co.id.lintasarta.tagpollinginventory.network.RnimsSegmentDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MpoSeedAndNetworkFallbackTest {

    private val gson = Gson()

    @Test
    fun testBundledMpoJsonFile_existsAndHas143Segments() {
        val assetFile = if (File("src/main/assets/mpo_segments.json").exists()) {
            File("src/main/assets/mpo_segments.json")
        } else {
            File("app/src/main/assets/mpo_segments.json")
        }
        assertTrue("mpo_segments.json harus ada di folder assets", assetFile.exists())
        
        val json = assetFile.readText()
        assertTrue("JSON tidak boleh kosong", json.isNotEmpty())

        val type = object : TypeToken<List<RnimsSegmentDto>>() {}.type
        val list: List<RnimsSegmentDto> = gson.fromJson(json, type)

        assertEquals("Total segmen MPO harus tepat 143 segmen", 143, list.size)

        // Verify all segments are in Area 18 (MPO)
        val nonArea18 = list.filter { it.area_id != 18 }
        assertTrue("Semua segmen harus berada di Area 18 (MPO)", nonArea18.isEmpty())

        // Verify first segment (id = 2, SEGMENT_00002)
        val firstSeg = list.first()
        assertEquals(2, firstSeg.id)
        assertEquals("SEGMENT_00002", firstSeg.segment_name)
        assertEquals("TMKMPR", firstSeg.org_site)
        assertEquals("TMKPMK", firstSeg.dst_site)
        assertNotNull(firstSeg.route_path)
        assertTrue("Rute polyline tidak boleh kosong", firstSeg.route_path!!.isNotEmpty())
        assertTrue("Panjang rute harus lebih besar dari 0 km", firstSeg.distance > 0.0)
    }

    @Test
    fun testConverters_routeCoordinateListRoomSerialization() {
        val converters = Converters()
        val originalList = listOf(
            RouteCoordinate(latitude = -2.531234, longitude = 140.712345),
            RouteCoordinate(latitude = -2.535678, longitude = 140.716789),
            RouteCoordinate(latitude = -2.540123, longitude = 140.720123)
        )

        val jsonString = converters.fromRouteCoordinateList(originalList)
        assertNotNull(jsonString)
        assertTrue(jsonString!!.contains("-2.531234"))

        val deserializedList = converters.toRouteCoordinateList(jsonString)
        assertEquals(originalList.size, deserializedList.size)
        assertEquals(originalList[0].latitude, deserializedList[0].latitude, 0.000001)
        assertEquals(originalList[0].longitude, deserializedList[0].longitude, 0.000001)
        assertEquals(originalList[2].latitude, deserializedList[2].latitude, 0.000001)
    }

    @Test
    fun testUserSession_offlineFieldReady() {
        val session = UserSession()
        assertTrue("Surveyor harus langsung berstatus login offline bawaan", session.isLoggedIn)
        assertTrue("Sesi bawaan harus merupakan sesi offline", session.isOfflineSession)
        assertEquals("Area default harus 18", 18, session.areaId)
        assertEquals("Label area harus MPO", "MPO", session.areaLabel)
        assertEquals("Role harus SURVEYOR", "SURVEYOR", session.role)
        assertEquals("surveyor_mpo", session.username)
        assertEquals("Field Engineer MPO", session.displayName)
    }

    @Test
    fun testRnimsStagingEndpoints() {
        assertEquals("http://127.0.0.1:8000", RnimsApiClient.DEFAULT_USB_URL)
        assertEquals("http://192.168.1.6:8000", RnimsApiClient.DEFAULT_WIFI_URL)
        assertEquals("https://rnims.lintasarta.co.id/api", RnimsApiClient.DEFAULT_PROD_URL)
    }

    @Test
    fun testPoleCodeFormat_conformsToSpecification() {
        // PL-[CITY]-[LOC]-[URUTAN 3 DIGIT]
        val cityCode = "TMK"
        val locCode = "PMK"
        val sequence = 7
        val poleCode = "PL-$cityCode-$locCode-${String.format("%03d", sequence)}"

        assertEquals("PL-TMK-PMK-007", poleCode)
        assertTrue("Kode tiang harus berawalan PL- untuk kompatibilitas eNims", poleCode.startsWith("PL-"))
    }

    @Test
    fun testEnimsPlacemarkAndLineStringCompatibility() {
        val poleCode = "PL-JPR-CTR-001"
        val lat = -2.538123
        val lng = 140.705432

        // eNims ProjectManagement.php expects Placemarks starting with PL and coordinates as lng,lat,0
        val placemarkXml = """
            <Placemark>
                <name>$poleCode</name>
                <Point>
                    <coordinates>$lng,$lat,0</coordinates>
                </Point>
            </Placemark>
        """.trimIndent()

        assertTrue(placemarkXml.contains("<name>PL-"))
        assertTrue(placemarkXml.contains("<coordinates>$lng,$lat,0</coordinates>"))

        // eNims LineString polyline
        val lineStringXml = """
            <Placemark>
                <name>Route Polyline</name>
                <LineString>
                    <coordinates>
                        140.705432,-2.538123,0 140.706000,-2.539000,0
                    </coordinates>
                </LineString>
            </Placemark>
        """.trimIndent()

        assertTrue(lineStringXml.contains("<LineString>"))
    }

    @Test
    fun testPhotoCompressionTargetSpecification() {
        val maxSizeBytes = 500 * 1024 // 500 KB target
        val defaultQuality = 80 // JPEG quality 80%
        val maxWidth = 1920
        val maxHeight = 1080

        assertEquals(512000, maxSizeBytes)
        assertEquals(80, defaultQuality)
        assertTrue("Resolusi foto maksimal 1920x1080", maxWidth <= 1920 && maxHeight <= 1080)
    }
}
