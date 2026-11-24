package co.id.lintasarta.tagpollinginventory.model

import co.id.lintasarta.tagpollinginventory.data.model.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AlpacaPraModelAndParserTest {

    private val gson = Gson()

    @Test
    fun testAlpacaPraJsonParsing() {
        val sampleJson = """
            {
              "run": { "revision": "verified_poles_50m_20260806", "status": "ready" },
              "ift": {
                "po_id": "089",
                "po_name": "Tiakur",
                "area": "MPO",
                "length_po": 15883.29,
                "geometry": {
                  "type": "MultiLineString",
                  "coordinates": [
                    [ [127.821303, -8.1448433], [127.8188281, -8.1435506] ]
                  ]
                }
              },
              "assets": [
                {
                  "asset_uid": "089P700001",
                  "feature_name": "244 tiang 7",
                  "asset_type": "pole",
                  "asset_class": "pole_7",
                  "source_proximity": "near route",
                  "classification_confidence": "high",
                  "match_status": "strong",
                  "matched_po_id": "089",
                  "matched_po_distance_m": "4.538",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [127.78867, -8.16496]
                  }
                },
                {
                  "asset_uid": "089P900002",
                  "feature_name": "tiang 9",
                  "asset_type": "pole",
                  "asset_class": "pole_9",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [127.78865, -8.16480]
                  }
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(sampleJson, AlpacaMapResponseDto::class.java)

        assertNotNull("AlpacaMapResponseDto tidak boleh null", response)
        assertNotNull("IFT metadata tidak boleh null", response.ift)

        val ift = response.ift!!
        assertEquals("PO ID harus 089", "089", ift.poId)
        assertEquals("PO Name harus Tiakur", "Tiakur", ift.poName)
        assertEquals("Panjang rute harus 15883.29 m", 15883.29, ift.lengthPo, 0.01)

        val flatCoords = ift.geometry?.toFlatRouteCoordinates() ?: emptyList()
        assertEquals("MultiLineString rute harus memiliki 2 titik koordinat", 2, flatCoords.size)
        assertEquals("Koordinat awal rute longitude harus sesuai", 127.821303, flatCoords.first().longitude, 0.0001)
        assertEquals("Koordinat awal rute latitude harus sesuai", -8.1448433, flatCoords.first().latitude, 0.0001)

        val assets = response.assets
        assertEquals("Total tiang sampel harus tepat 2 unit", 2, assets.size)

        val p7Count = assets.count { it.assetClass == "pole_7" }
        val p9Count = assets.count { it.assetClass == "pole_9" }
        assertEquals("Jumlah tiang pole_7 harus 1 unit", 1, p7Count)
        assertEquals("Jumlah tiang pole_9 harus 1 unit", 1, p9Count)

        val firstAsset = assets.first()
        assertEquals("UID tiang pertama harus 089P700001", "089P700001", firstAsset.assetUid)
        assertEquals("Nama fitur harus 244 tiang 7", "244 tiang 7", firstAsset.featureName)
        assertEquals("Tipe aset harus pole_7", "pole_7", firstAsset.assetClass)
        assertEquals("Jarak rute harus 4.538 m", "4.538", firstAsset.matchedPoDistanceM)
        assertEquals("Koordinat latitude harus valid", -8.16496, firstAsset.latitude, 0.0001)
        assertEquals("Koordinat longitude harus valid", 127.78867, firstAsset.longitude, 0.0001)
    }

    @Test
    fun testUniversalAssetUidGenerationForAllCities() {
        // Ambon (Area 18 MPO)
        val uidAmbon = PoleEntity.formatAlpacaAssetUid("AMB", 1)
        assertEquals("POL-MPO-AMB-0001", uidAmbon)

        // Saumlaki (Area 18 MPO)
        val uidSaumlaki = PoleEntity.formatAlpacaAssetUid("SAU", 42)
        assertEquals("POL-MPO-SAU-0042", uidSaumlaki)

        // Tual (Area 18 MPO)
        val uidTual = PoleEntity.formatAlpacaAssetUid("TUL", 15)
        assertEquals("POL-MPO-TUL-0015", uidTual)

        // Tiakur (Tiang tambahan baru di atas 373)
        val uidTiakurBaru = PoleEntity.formatAlpacaAssetUid("TKR", 374)
        assertEquals("POL-MPO-TKR-0374", uidTiakurBaru)
    }

    @Test
    fun testMatchStatusAndMissingHandling() {
        // 1. Verifikasi enum MatchStatus.MISSING
        val missingStatus = MatchStatus.fromCode("MISSING")
        assertEquals(MatchStatus.MISSING, missingStatus)

        // 2. Tiang yang ditandai MISSING harus menghasilkan TagStatus.MISSING
        val missingPole = PoleEntity(
            id = "089P700025",
            segmentId = "SEG-PO-089",
            sequence = 25,
            assetUid = "089P700025",
            matchStatus = "MISSING",
            isExistingPole = true
        )
        assertEquals(TagStatus.MISSING, missingPole.calculateStatus())

        // 3. Tiang eksisting referensi yang BELUM disurvei (foto masih kosong) harus berstatus NOT_TAGGED
        val unverifiedPole = PoleEntity(
            id = "089P700001",
            segmentId = "SEG-PO-089",
            sequence = 1,
            assetUid = "089P700001",
            latitude = -8.16496,
            longitude = 127.78867,
            photoPath = null,
            isExistingPole = true,
            syncStatus = "PENDING"
        )
        assertEquals(TagStatus.NOT_TAGGED, unverifiedPole.calculateStatus())

        // 4. Setelah divalidasi dengan foto fisik lapangan, status berubah menjadi COMPLETED
        val verifiedPole = unverifiedPole.copy(
            photoPath = "/storage/emulated/0/DCIM/TagPolling/089P700001.jpg"
        )
        assertEquals(TagStatus.COMPLETED, verifiedPole.calculateStatus())
    }
}
