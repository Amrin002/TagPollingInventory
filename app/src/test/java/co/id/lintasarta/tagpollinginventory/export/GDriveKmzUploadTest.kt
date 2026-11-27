package co.id.lintasarta.tagpollinginventory.export

import co.id.lintasarta.tagpollinginventory.data.model.ImportedRoute
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import co.id.lintasarta.tagpollinginventory.data.model.TagStatus
import co.id.lintasarta.tagpollinginventory.network.RnimsApiClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Random
import java.util.zip.ZipFile

class GDriveKmzUploadTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testStreamingBase64JsonPayloadExactLengthAndRoundTrip() {
        val gson = Gson()
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        val testSizes = listOf(1, 2, 3, 4, 49151, 49152, 49153, 120_000)

        for (size in testSizes) {
            val rawBytes = ByteArray(size)
            Random(42L + size).nextBytes(rawBytes)
            val tmpFile = File(tempFolder.root, "sample_$size.kmz").apply { writeBytes(rawBytes) }

            val (prefix, suffix) = RnimsApiClient.buildKmzUploadJsonEnvelope(
                areaId = 18,
                areaName = "MPO",
                segmentName = "SEGMENT_2843",
                fileName = tmpFile.name,
                poleCount = 5,
                surveyor = "Amrin Alamsyah (moto g67 power 5G)"
            )
            val expectedLength = prefix.size.toLong() +
                RnimsApiClient.calculateBase64EncodedLength(tmpFile.length()) +
                suffix.size.toLong()

            val baos = ByteArrayOutputStream()
            RnimsApiClient.writeKmzUploadJsonPayload(baos, prefix, suffix, tmpFile)
            val jsonBytes = baos.toByteArray()

            assertEquals("Exact content-length mismatch for size=$size", expectedLength, jsonBytes.size.toLong())

            val parsed: Map<String, Any> = gson.fromJson(String(jsonBytes, Charsets.UTF_8), mapType)
            assertEquals(18.0, parsed["area_id"])
            assertEquals("MPO", parsed["area_name"])
            assertEquals(tmpFile.name, parsed["file_name"])
            val decoded = Base64.getDecoder().decode(parsed["file_base64"] as String)
            assertArrayEquals("Roundtrip bytes mismatch for size=$size", rawBytes, decoded)
        }
    }

    @Test
    fun testParseGDriveUploadResponseHandlesStatusAndHtmlErrors() {
        val okRes = RnimsApiClient.parseGDriveUploadResponse(
            """{"status":"ok","message":"File saved","file_id":"123","file_name":"a.kmz","folder_name":"Area_18_MPO"}"""
        )
        assertTrue(okRes.isSuccess)
        assertEquals("a.kmz", okRes.getOrNull()?.file_name)

        val errRes = RnimsApiClient.parseGDriveUploadResponse(
            """{"status":"error","message":"Exceed limit"}"""
        )
        assertTrue(errRes.isFailure)
        assertEquals("Exceed limit", errRes.exceptionOrNull()?.message)

        val htmlTimeout = RnimsApiClient.parseGDriveUploadResponse(
            "<!DOCTYPE html><html><head><title>Error</title></head><body>Exceeded maximum execution time</body></html>"
        )
        assertTrue(htmlTimeout.isFailure)
        assertTrue(htmlTimeout.exceptionOrNull()?.message?.contains("Google Apps Script") == true)
    }

    @Test
    fun testBuildGDriveKmzBatchesAutoSplitsHugeSurveyAndKeepsSingleFileWhenSmall() {
        val exportDir = tempFolder.newFolder("exports")
        val workDir = tempFolder.newFolder("batches")
        val engine = ExportEngine(context = null, customExportDir = exportDir)
        val rng = Random(999L)

        // Create 6 incompressible random-noise blobs (60 KB each) simulating photo payloads
        val poles = (1..6).map { idx ->
            val blob = ByteArray(60 * 1024).also { rng.nextBytes(it) }
            val photoFile = File(tempFolder.root, "photo_pole_$idx.jpg").apply { writeBytes(blob) }
            Pole(
                id = "PL-$idx",
                segmentId = "SEG-2843",
                sequence = idx,
                assetUid = "PL-ABN-TEST-000$idx",
                poleCode = "PL-ABN-TEST-000$idx",
                routeName = "SEGMENT_2843",
                assetClass = if (idx % 2 == 0) "pole_7" else "pole_9",
                assetType = "steel",
                latitude = -3.689 + idx * 0.0001,
                longitude = 128.187 + idx * 0.0001,
                accuracy = 3.0f,
                photoPath = photoFile.absolutePath,
                equipment = setOf("JB"),
                status = TagStatus.COMPLETED,
                capturedTimestamp = "25 Nov 2026 — 14:00"
            )
        }
        val route = ImportedRoute(
            id = "R-1",
            name = "SEGMENT_2843",
            sourceFileName = "seg.kml",
            sourceFileType = "KML",
            projectId = "PRJ-MPO-18",
            segmentId = "SEG-2843",
            coordinates = listOf(RouteCoordinate(128.187, -3.689))
        )

        // 1. Small budget -> forces multi-part split (~160 KB cap vs ~360 KB total)
        val splitBatches = engine.buildGDriveKmzBatches(
            poles = poles,
            segmentName = "Alpaca PRA - SEGMENT_2843",
            importedRoute = route,
            workDir = workDir,
            baseFileNamePrefix = "SURVEY_MPO_TEST",
            maxBatchBytes = 160 * 1024L
        )
        assertTrue("Expected multiple parts when exceeding budget, got ${splitBatches.size}", splitBatches.size >= 2)
        val collectedIds = mutableListOf<String>()
        splitBatches.forEachIndexed { i, batch ->
            assertEquals(i + 1, batch.partIndex)
            assertEquals(splitBatches.size, batch.totalParts)
            assertTrue(batch.file.name.contains("_Part${i + 1}of${splitBatches.size}.kmz"))
            assertTrue("Part ${batch.partIndex} too large: ${batch.file.length()}", batch.file.length() <= 160 * 1024L)
            ZipFile(batch.file).use { zf ->
                assertTrue(zf.getEntry("doc.kml") != null)
                assertTrue(zf.getEntry("icons/pole_jb_p9.png") != null || zf.getEntry("icons/pole_jb_p7.png") != null)
                batch.poles.forEach { p ->
                    val name = File(p.photoPath!!).name
                    assertTrue("Missing $name in part ${batch.partIndex}", zf.getEntry("images/$name") != null)
                }
            }
            collectedIds.addAll(batch.poles.map { it.id })
        }
        assertEquals(poles.map { it.id }, collectedIds)

        // 2. Normal 5 MB budget -> produces 1 single KMZ file
        val singleDir = tempFolder.newFolder("single")
        val singleBatches = engine.buildGDriveKmzBatches(
            poles = poles,
            segmentName = "Alpaca PRA - SEGMENT_2843",
            importedRoute = route,
            workDir = singleDir,
            baseFileNamePrefix = "SURVEY_MPO_SINGLE",
            maxBatchBytes = ExportEngine.DEFAULT_MAX_GDRIVE_KMZ_BYTES
        )
        assertEquals(1, singleBatches.size)
        assertEquals("SURVEY_MPO_SINGLE.kmz", singleBatches[0].file.name)
    }
}
