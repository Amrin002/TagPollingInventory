package co.id.lintasarta.tagpollinginventory.export

import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.data.model.PoleCondition
import co.id.lintasarta.tagpollinginventory.data.model.PoleEntity
import co.id.lintasarta.tagpollinginventory.data.model.PoleOwnership
import co.id.lintasarta.tagpollinginventory.data.model.PoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AlpacaPraExportTest {

    @Test
    fun testAlpacaAssetUidFormatting() {
        val uid1 = PoleEntity.formatAlpacaAssetUid("TMK", 1)
        assertEquals("POL-MPO-TMK-0001", uid1)

        val uid2 = PoleEntity.formatAlpacaAssetUid("JPR", 42)
        assertEquals("POL-MPO-JPR-0042", uid2)

        val uid3 = PoleEntity.formatAlpacaAssetUid("BIK", 999)
        assertEquals("POL-MPO-BIK-0999", uid3)
    }

    @Test
    fun testAlpacaMasterCsvRowGeneration() {
        val poleP9 = Pole(
            id = "POLE-UUID-001",
            segmentId = "SEG-101",
            sequence = 1,
            assetUid = "POL-MPO-TMK-0001",
            poleCode = "POL-MPO-TMK-0001",
            routeName = "Timika - Kuala Kencana FO",
            poId = "PO-ALPACA-2026-001",
            assetClass = "pole_9",
            assetType = "steel",
            latitude = -4.5468,
            longitude = 136.8837,
            rawLatitude = -4.5467,
            rawLongitude = 136.8836,
            accuracy = 2.4f,
            photoPath = "/storage/emulated/0/photos/photo_POL-MPO-TMK-0001.jpg",
            photoSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ownership = PoleOwnership.LINTASARTA,
            condition = PoleCondition.GOOD,
            isSafetyDeferred = false,
            matchStatus = "MATCHED",
            syncStatus = "PENDING",
            capturedTimestamp = "2026-11-04 09:30:00"
        )

        val poleP7Safety = Pole(
            id = "POLE-UUID-002",
            segmentId = "SEG-101",
            sequence = 2,
            assetUid = "POL-MPO-TMK-0002",
            poleCode = "POL-MPO-TMK-0002",
            routeName = "Timika - Kuala Kencana FO",
            poId = "PO-ALPACA-2026-001",
            assetClass = "pole_7",
            assetType = "concrete",
            latitude = -4.5470,
            longitude = 136.8840,
            rawLatitude = -4.5470,
            rawLongitude = 136.8840,
            accuracy = 3.1f,
            photoPath = null,
            ownership = PoleOwnership.TELKOM,
            condition = PoleCondition.FAIR,
            isSafetyDeferred = true,
            safetyDeferralReason = "Tebing Curam / Rawa",
            matchStatus = "ANOMALY",
            syncStatus = "PENDING",
            capturedTimestamp = "2026-11-04 09:35:00"
        )

        val poles = listOf(poleP9, poleP7Safety)

        // Helper to format CSV identical to ExportEngine
        val sb = StringBuilder()
        sb.append("asset_uid,segment_id,route_name,po_id,asset_class,asset_type,latitude,longitude,raw_latitude,raw_longitude,accuracy,photo_filename,ownership,condition,is_safety_deferred,match_status,sync_status,timestamp,photo_sha256\n")
        for (pole in poles) {
            val photoFileName = if (!pole.photoPath.isNullOrEmpty()) File(pole.photoPath).name else ""
            val rawLat = pole.rawLatitude ?: pole.latitude
            val rawLng = pole.rawLongitude ?: pole.longitude

            sb.append("\"${pole.effectiveAssetUid}\",")
            sb.append("\"${pole.segmentId}\",")
            sb.append("\"${pole.routeName.replace("\"", "'")}\",")
            sb.append("\"${pole.poId}\",")
            sb.append("\"${pole.assetClass}\",")
            sb.append("\"${pole.assetType}\",")
            sb.append("${pole.latitude},")
            sb.append("${pole.longitude},")
            sb.append("$rawLat,")
            sb.append("$rawLng,")
            sb.append("${pole.accuracy},")
            sb.append("\"$photoFileName\",")
            sb.append("\"${pole.ownership.displayName}\",")
            sb.append("\"${pole.condition.displayName}\",")
            sb.append("${pole.isSafetyDeferred},")
            sb.append("\"${pole.matchStatus}\",")
            sb.append("\"${pole.syncStatus}\",")
            sb.append("\"${pole.capturedTimestamp}\",")
            sb.append("\"${pole.photoSha256 ?: ""}\"\n")
        }

        val lines = sb.toString().lines()
        assertEquals(4, lines.size) // header + 2 rows + trailing empty

        // Row 1 (pole_9)
        val row1 = lines[1]
        assertTrue(row1.contains("\"POL-MPO-TMK-0001\""))
        assertTrue(row1.contains("\"pole_9\""))
        assertTrue(row1.contains("\"steel\""))
        assertTrue(row1.contains("photo_POL-MPO-TMK-0001.jpg"))
        assertTrue(row1.contains("false,\"MATCHED\""))
        assertTrue(row1.contains("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))

        // Row 2 (pole_7 + Safety Deferral)
        val row2 = lines[2]
        assertTrue(row2.contains("\"POL-MPO-TMK-0002\""))
        assertTrue(row2.contains("\"pole_7\""))
        assertTrue(row2.contains("\"concrete\""))
        assertTrue(row2.contains("true,\"ANOMALY\""))
    }
}
