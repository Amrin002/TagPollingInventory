package co.id.lintasarta.tagpollinginventory.repository

import co.id.lintasarta.tagpollinginventory.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

class UniversalSegmentBusinessLogicTest {

    private fun parseEquipmentFromFeature(featureName: String?): List<String> {
        if (featureName.isNullOrBlank()) return emptyList()
        val lower = featureName.lowercase(Locale.ROOT)
        val list = mutableListOf<String>()
        if (lower.contains("slekan") || lower.contains("slk")) list.add("Slekan FO")
        if (lower.contains("jb") || lower.contains("closure") || lower.contains("joint")) list.add("Joint Closure (JB)")
        if (lower.contains("sp") || lower.contains("slack")) list.add("Slack Point (SP)")
        if (lower.contains("panel") || lower.contains("box") || lower.contains("otb")) list.add("Box Panel OTB")
        if (lower.contains("cross arm") || lower.contains("crossarm")) list.add("Cross Arm")
        return list
    }

    private fun extractPoleNumber(featureName: String?): Int? {
        if (featureName.isNullOrBlank()) return null
        val match = Regex("\\d+").find(featureName)
        return match?.value?.toIntOrNull()
    }

    private fun determineCityFilterKey(orgCity: String?, dstCity: String?, orgSite: String?): String {
        val text = "${orgCity.orEmpty()} ${dstCity.orEmpty()} ${orgSite.orEmpty()}".lowercase(Locale.ROOT)
        return when {
            text.contains("mimika") || text.contains("timika") || text.contains("tmk") -> "Timika"
            text.contains("sorong") || text.contains("srg") || text.contains("soq") -> "Sorong"
            text.contains("jayapura") || text.contains("sentani") || text.contains("abepura") || text.contains("jyp") || text.contains("djj") -> "Jayapura"
            text.contains("ambon") || text.contains("amb") -> "Ambon"
            text.contains("merauke") || text.contains("mrk") || text.contains("mkq") -> "Merauke"
            text.contains("manokwari") || text.contains("mwr") || text.contains("mkw") -> "Manokwari"
            text.contains("biak") || text.contains("bik") -> "Biak"
            text.contains("nabire") || text.contains("nbr") -> "Nabire"
            text.contains("tual") -> "Tual"
            text.contains("dobo") || text.contains("dbo") -> "Dobo"
            text.contains("tiakur") || text.contains("tkr") -> "Tiakur"
            else -> "Lainnya"
        }
    }

    @Test
    fun testFeatureNameEquipmentParsing() {
        val eq1 = parseEquipmentFromFeature("244 tiang 7 slekan")
        assertTrue("Harus mengandung Slekan FO", eq1.contains("Slekan FO"))

        val eq2 = parseEquipmentFromFeature("Tiang 9 JB Closure + SP Slack")
        assertTrue("Harus mengandung Joint Closure (JB)", eq2.contains("Joint Closure (JB)"))
        assertTrue("Harus mengandung Slack Point (SP)", eq2.contains("Slack Point (SP)"))

        val eq3 = parseEquipmentFromFeature("Tiang 7 Box Panel OTB + Cross Arm")
        assertTrue("Harus mengandung Box Panel OTB", eq3.contains("Box Panel OTB"))
        assertTrue("Harus mengandung Cross Arm", eq3.contains("Cross Arm"))

        val eqEmpty = parseEquipmentFromFeature("Tiang polos biasa")
        assertTrue("Harus kosong tanpa aksesoris", eqEmpty.isEmpty())
    }

    @Test
    fun testExtractPoleNumber() {
        assertEquals(244, extractPoleNumber("244 tiang 7"))
        assertEquals(42, extractPoleNumber("PL 42"))
        assertEquals(108, extractPoleNumber("Tiang ke-108 jalan utama"))
        assertNull(extractPoleNumber("Tiang tanpa angka"))
    }

    @Test
    fun testUniversalCityFilterKeysForAllRegions() {
        assertEquals("Ambon", determineCityFilterKey("Ambon", "Ambon", "AMB"))
        assertEquals("Tual", determineCityFilterKey("Tual", "Tual", "TUL"))
        assertEquals("Tiakur", determineCityFilterKey("Tiakur", "Tiakur", "TKR"))
        assertEquals("Timika", determineCityFilterKey("Mimika", "Timika", "TMK"))
        assertEquals("Jayapura", determineCityFilterKey("Jayapura", "Sentani", "DJJ"))
        assertEquals("Sorong", determineCityFilterKey("Sorong", "Sorong", "SOQ"))
        assertEquals("Manokwari", determineCityFilterKey("Manokwari", "Manokwari", "MKW"))
        assertEquals("Dobo", determineCityFilterKey("Dobo", "Dobo", "DBO"))
        assertEquals("Merauke", determineCityFilterKey("Merauke", "Merauke", "MKQ"))
        assertEquals("Biak", determineCityFilterKey("Biak", "Biak", "BIK"))
        assertEquals("Nabire", determineCityFilterKey("Nabire", "Nabire", "NBR"))
        assertEquals("Lainnya", determineCityFilterKey("Wilayah Baru", "Kota Baru", "NEW"))
    }

    @Test
    fun testProvisionalPoleSequenceAndUidForNonPraSegments() {
        val segmentId = "SEG-AMB-01"
        val existingPoles = mutableListOf<PoleEntity>()

        // 1. Tag tiang pertama mandiri di Ambon (0 tiang sebelumnya)
        val nextSeq1 = (existingPoles.maxOfOrNull { it.sequence } ?: 0) + 1
        val uid1 = PoleEntity.formatAlpacaAssetUid("AMB", nextSeq1)
        val pole1 = PoleEntity(
            id = uid1,
            segmentId = segmentId,
            sequence = nextSeq1,
            assetUid = uid1,
            latitude = -3.6958,
            longitude = 128.1831,
            isExistingPole = false,
            matchStatus = "PROVISIONAL"
        )
        existingPoles.add(pole1)

        assertEquals("POL-MPO-AMB-0001", uid1)
        assertEquals(1, pole1.sequence)
        assertEquals("PROVISIONAL", pole1.matchStatus)
        assertFalse("Bukan tiang eksisting", pole1.isExistingPole)

        // 2. Tag tiang kedua mandiri
        val nextSeq2 = (existingPoles.maxOfOrNull { it.sequence } ?: 0) + 1
        val uid2 = PoleEntity.formatAlpacaAssetUid("AMB", nextSeq2)
        val pole2 = PoleEntity(
            id = uid2,
            segmentId = segmentId,
            sequence = nextSeq2,
            assetUid = uid2,
            latitude = -3.6962,
            longitude = 128.1835,
            isExistingPole = false,
            matchStatus = "PROVISIONAL"
        )
        existingPoles.add(pole2)

        assertEquals("POL-MPO-AMB-0002", uid2)
        assertEquals(2, pole2.sequence)
        assertEquals(2, existingPoles.size)
    }

    @Test
    fun testMissingPoleAuditRetentionWorkflow() {
        // Tiang eksisting dari server
        val existingPole = PoleEntity(
            id = "089P700010",
            segmentId = "SEG-PO-089",
            sequence = 10,
            assetUid = "089P700010",
            latitude = -8.1650,
            longitude = 127.7887,
            photoPath = null,
            isExistingPole = true,
            matchStatus = "MATCHED",
            syncStatus = "PENDING"
        ).withCalculatedStatus()

        // Verifikasi awal: tiang belum ditag (karena belum ada foto)
        assertEquals(TagStatus.NOT_TAGGED, existingPole.status)
        assertTrue(existingPole.isExistingPole)

        // Surveyor menandai tiang NIHIL di lapangan
        val missingReason = "Tiang roboh tergerus longsor tebing"
        val missingPole = existingPole.copy(
            matchStatus = "MISSING",
            status = TagStatus.MISSING,
            missingReason = missingReason
        ).withCalculatedStatus()

        assertEquals(TagStatus.MISSING, missingPole.status)
        assertEquals("MISSING", missingPole.matchStatus)
        assertEquals(missingReason, missingPole.missingReason)

        // Surveyor memulihkan tiang jika salah pencet
        val restoredPole = missingPole.copy(
            matchStatus = "MATCHED",
            status = TagStatus.NOT_TAGGED,
            missingReason = null
        ).withCalculatedStatus()

        assertEquals(TagStatus.NOT_TAGGED, restoredPole.status)
        assertEquals("MATCHED", restoredPole.matchStatus)
        assertNull(restoredPole.missingReason)
    }

    @Test
    fun testExportCsvWithMissingAndProvisionalPoles() {
        val completedPole = PoleEntity(
            id = "POL-MPO-TKR-0001",
            segmentId = "SEG-PO-089",
            sequence = 1,
            assetUid = "089P700001",
            routeName = "Tiakur Backbone",
            poId = "089",
            assetClass = "pole_7",
            latitude = -8.16496,
            longitude = 127.78867,
            photoPath = "/photos/089P700001.jpg",
            isExistingPole = true,
            matchStatus = "MATCHED",
            status = TagStatus.COMPLETED
        ).withCalculatedStatus()

        val missingPole = PoleEntity(
            id = "POL-MPO-TKR-0002",
            segmentId = "SEG-PO-089",
            sequence = 2,
            assetUid = "089P700002",
            routeName = "Tiakur Backbone",
            poId = "089",
            assetClass = "pole_7",
            latitude = -8.16510,
            longitude = 127.78880,
            photoPath = null,
            isExistingPole = true,
            matchStatus = "MISSING",
            missingReason = "Nihil di lokasi",
            status = TagStatus.MISSING
        ).withCalculatedStatus()

        val provisionalPole = PoleEntity(
            id = "POL-MPO-TKR-0374",
            segmentId = "SEG-PO-089",
            sequence = 374,
            assetUid = "POL-MPO-TKR-0374",
            routeName = "Tiakur Backbone",
            poId = "089",
            assetClass = "pole_9",
            latitude = -8.16550,
            longitude = 127.78910,
            photoPath = "/photos/POL-MPO-TKR-0374.jpg",
            isExistingPole = false,
            matchStatus = "PROVISIONAL",
            status = TagStatus.COMPLETED
        ).withCalculatedStatus()

        val poles = listOf(completedPole, missingPole, provisionalPole)

        val sb = StringBuilder()
        sb.append("asset_uid,segment_id,route_name,po_id,asset_class,asset_type,latitude,longitude,raw_latitude,raw_longitude,accuracy,photo_filename,ownership,condition,is_safety_deferred,match_status,missing_reason,is_existing_pole,sync_status,timestamp,photo_sha256\n")

        for (p in poles) {
            val photoFileName = if (!p.photoPath.isNullOrEmpty()) File(p.photoPath).name else ""
            val rawLat = p.rawLatitude ?: p.latitude
            val rawLng = p.rawLongitude ?: p.longitude

            sb.append("\"${p.effectiveAssetUid}\",")
            sb.append("\"${p.segmentId}\",")
            sb.append("\"${p.routeName.replace("\"", "'")}\",")
            sb.append("\"${p.poId}\",")
            sb.append("\"${p.assetClass}\",")
            sb.append("\"${p.assetType}\",")
            sb.append("${p.latitude},")
            sb.append("${p.longitude},")
            sb.append("$rawLat,")
            sb.append("$rawLng,")
            sb.append("${p.accuracy},")
            sb.append("\"$photoFileName\",")
            sb.append("\"${p.ownership.displayName}\",")
            sb.append("\"${p.condition.displayName}\",")
            sb.append("${p.isSafetyDeferred},")
            sb.append("\"${p.matchStatus}\",")
            sb.append("\"${p.missingReason ?: ""}\",")
            sb.append("${p.isExistingPole},")
            sb.append("\"${p.syncStatus}\",")
            sb.append("\"${p.capturedTimestamp}\",")
            sb.append("\"${p.photoSha256 ?: ""}\"\n")
        }

        val lines = sb.toString().lines()
        assertEquals(5, lines.size) // Header + 3 rows + empty trailing line

        // Baris 1: Completed Existing Pole
        assertTrue(lines[1].contains("\"089P700001\""))
        assertTrue(lines[1].contains("\"MATCHED\""))
        assertTrue(lines[1].contains("true")) // isExistingPole = true

        // Baris 2: Missing Pole
        assertTrue(lines[2].contains("\"089P700002\""))
        assertTrue(lines[2].contains("\"MISSING\""))
        assertTrue(lines[2].contains("\"Nihil di lokasi\""))

        // Baris 3: Provisional Pole
        assertTrue(lines[3].contains("\"POL-MPO-TKR-0374\""))
        assertTrue(lines[3].contains("\"PROVISIONAL\""))
        assertTrue(lines[3].contains("false")) // isExistingPole = false
    }
}
