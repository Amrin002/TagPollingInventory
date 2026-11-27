package co.id.lintasarta.tagpollinginventory.export

import co.id.lintasarta.tagpollinginventory.data.model.ExportFormat
import co.id.lintasarta.tagpollinginventory.data.model.ExportOptions
import co.id.lintasarta.tagpollinginventory.data.model.ImportedRoute
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import co.id.lintasarta.tagpollinginventory.data.model.TagStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.util.zip.ZipFile
import javax.imageio.ImageIO

class AlpacaKmzLegendTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun samplePole(
        id: String,
        assetClass: String = "pole_9",
        equipment: Set<String> = emptySet(),
        isSafetyDeferred: Boolean = false,
        status: TagStatus = TagStatus.COMPLETED,
        matchStatus: String = "MATCHED"
    ): Pole = Pole(
        id = id,
        segmentId = "SEG-101",
        sequence = 1,
        assetUid = id,
        poleCode = id,
        routeName = "Timika - Kuala Kencana FO",
        assetClass = assetClass,
        assetType = "steel",
        latitude = -4.5468,
        longitude = 136.8837,
        accuracy = 2.5f,
        equipment = equipment,
        isSafetyDeferred = isSafetyDeferred,
        matchStatus = matchStatus,
        status = status,
        capturedTimestamp = "2026-11-25 10:00:00"
    )

    @Test
    fun testPoleLegendCategoryClassification() {
        assertEquals(
            KmzPoleCategory.POLE_7,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-1", assetClass = "pole_7"))
        )
        assertEquals(
            KmzPoleCategory.POLE_9,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-2", assetClass = "pole_9"))
        )
        // Default legacy equipment without user edits falls back to T7/T9
        assertEquals(
            KmzPoleCategory.POLE_7,
            KmzIconGenerator.classifyPoleCategory(
                samplePole("PL-3", assetClass = "pole_7", equipment = setOf("ODP", "Closure"))
            )
        )
        // Selecting Splitter on top of legacy default ODP+Closure must still classify as SPLITTER (not false JB)
        assertEquals(
            KmzPoleCategory.SPLITTER,
            KmzIconGenerator.classifyPoleCategory(
                samplePole("PL-3b", assetClass = "pole_9", equipment = setOf("ODP", "Closure", "Splitter"))
            )
        )

        // Single equipment categories (with typo/alias variants)
        assertEquals(
            KmzPoleCategory.JB,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-4", equipment = setOf("JB")))
        )
        assertEquals(
            KmzPoleCategory.JB,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-5", equipment = setOf("Joint Closure (JB)")))
        )
        assertEquals(
            KmzPoleCategory.SPLITTER,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-6", equipment = setOf("Splitter")))
        )
        assertEquals(
            KmzPoleCategory.SPLITTER,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-7", equipment = setOf("Spliter")))
        )
        assertEquals(
            KmzPoleCategory.SLEKAN,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-8", equipment = setOf("Slakkan")))
        )
        assertEquals(
            KmzPoleCategory.SLEKAN,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-9", equipment = setOf("Slack Support")))
        )
        assertEquals(
            KmzPoleCategory.SLEKAN,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-10", equipment = setOf("Slekan FO")))
        )

        // Two-accessory combinations
        assertEquals(
            KmzPoleCategory.JB_SPLITTER,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-11", equipment = setOf("JB", "Splitter")))
        )
        assertEquals(
            KmzPoleCategory.JB_SLEKAN,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-12", equipment = setOf("JB", "Slakkan")))
        )
        assertEquals(
            KmzPoleCategory.SPLITTER_SLEKAN,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-13", equipment = setOf("Splitter", "Slack Support")))
        )

        // All equipment present (JB + Splitter + Slakkan)
        assertEquals(
            KmzPoleCategory.ALL_EQUIPMENT,
            KmzIconGenerator.classifyPoleCategory(samplePole("PL-14", equipment = setOf("JB", "Splitter", "Slakkan")))
        )

        // Special field statuses override accessory badges
        assertEquals(
            KmzPoleCategory.SAFETY_DEFERRED,
            KmzIconGenerator.classifyPoleCategory(
                samplePole("PL-15", equipment = setOf("JB", "Splitter"), isSafetyDeferred = true)
            )
        )
        assertEquals(
            KmzPoleCategory.MISSING,
            KmzIconGenerator.classifyPoleCategory(
                samplePole("PL-16", equipment = setOf("Splitter"), status = TagStatus.MISSING, matchStatus = "MISSING")
            )
        )
    }

    @Test
    fun testGeneratedLegendIconsAreValid64x64Pngs() {
        val icons = KmzIconGenerator.generateAllLegendIcons()
        assertEquals(KmzIconGenerator.allStyleSpecs.size, icons.size)

        for (spec in KmzIconGenerator.allStyleSpecs) {
            val bytes = icons[spec.iconRelativePath]
            assertNotNull("Missing icon bytes for ${spec.iconRelativePath}", bytes)
            val image = ImageIO.read(ByteArrayInputStream(bytes))
            assertNotNull("Unreadable PNG for ${spec.iconRelativePath}", image)
            assertEquals("Width mismatch for ${spec.iconRelativePath}", 64, image.width)
            assertEquals("Height mismatch for ${spec.iconRelativePath}", 64, image.height)
        }
    }

    @Test
    fun testGenerateAlpacaMasterKmzIncludesDistinctStylesFoldersAndBundledIcons() {
        val poles = listOf(
            samplePole("PL-TMK-0001", assetClass = "pole_7", equipment = emptySet()),
            samplePole("PL-TMK-0002", assetClass = "pole_9", equipment = emptySet()),
            samplePole("PL-TMK-0003", assetClass = "pole_9", equipment = setOf("JB")),
            samplePole("PL-TMK-0004", assetClass = "pole_7", equipment = setOf("Splitter")),
            samplePole("PL-TMK-0005", assetClass = "pole_9", equipment = setOf("Slakkan")),
            samplePole("PL-TMK-0006", assetClass = "pole_9", equipment = setOf("JB", "Splitter", "Slakkan"))
        )
        val route = ImportedRoute(
            id = "ROUTE-1",
            name = "Timika - Kuala Kencana",
            sourceFileName = "tmk_kkc.kml",
            sourceFileType = "KML",
            projectId = "PRJ-MPO-18",
            segmentId = "SEG-101",
            coordinates = listOf(
                RouteCoordinate(136.8837, -4.5468),
                RouteCoordinate(136.8845, -4.5472)
            )
        )

        val engine = ExportEngine(customExportDir = tempFolder.root)
        val kmzFile = engine.generateAlpacaMasterKmz(
            poles = poles,
            segmentName = "TMK-KKC",
            importedRoute = route,
            surveyorInfo = "Di-tag oleh : Budi (Moto G67)"
        )
        assertTrue(kmzFile.exists() && kmzFile.length() > 0)

        ZipFile(kmzFile).use { zip ->
            val docEntry = zip.getEntry("doc.kml")
            assertNotNull("doc.kml missing from KMZ", docEntry)
            val kml = zip.getInputStream(docEntry).bufferedReader().use { it.readText() }

            // Check styles & relative icon references inside KMZ
            assertTrue(kml.contains("<Style id=\"p7Pole\">"))
            assertTrue(kml.contains("<Style id=\"p9Pole\">"))
            assertTrue(kml.contains("<Style id=\"poleJbP9\">"))
            assertTrue(kml.contains("<Style id=\"poleSplitterP7\">"))
            assertTrue(kml.contains("<Style id=\"poleSlekanP9\">"))
            assertTrue(kml.contains("<Style id=\"poleAllP9\">"))
            assertTrue(kml.contains("<Icon><href>icons/pole_splitter_p7.png</href></Icon>"))
            assertTrue(kml.contains("<Icon><href>icons/pole_all_p9.png</href></Icon>"))

            // Check folders separate each legend group cleanly
            assertTrue(kml.contains("<name>Rute Kabel FO</name>"))
            assertTrue(kml.contains("<name>Tiang 7M (T7) (1)</name>"))
            assertTrue(kml.contains("<name>Tiang 9M (T9) (1)</name>"))
            assertTrue(kml.contains("<name>Tiang + JB (1)</name>"))
            assertTrue(kml.contains("<name>Tiang + Splitter (1)</name>"))
            assertTrue(kml.contains("<name>Tiang + Slakkan (Slekan) (1)</name>"))
            assertTrue(kml.contains("<name>Tiang + Semua Aksesoris (JB, Splitter, Slakkan) (1)</name>"))

            // Check each pole points to its specific styleUrl while preserving asset_uid name
            assertTrue(kml.contains("<name>PL-TMK-0001</name>\n        <styleUrl>#p7Pole</styleUrl>"))
            assertTrue(kml.contains("<name>PL-TMK-0002</name>\n        <styleUrl>#p9Pole</styleUrl>"))
            assertTrue(kml.contains("<name>PL-TMK-0003</name>\n        <styleUrl>#poleJbP9</styleUrl>"))
            assertTrue(kml.contains("<name>PL-TMK-0004</name>\n        <styleUrl>#poleSplitterP7</styleUrl>"))
            assertTrue(kml.contains("<name>PL-TMK-0005</name>\n        <styleUrl>#poleSlekanP9</styleUrl>"))
            assertTrue(kml.contains("<name>PL-TMK-0006</name>\n        <styleUrl>#poleAllP9</styleUrl>"))
            assertTrue(kml.contains("Di-tag oleh : Budi (Moto G67)"))

            // Check PNG icon entries exist inside the KMZ archive
            for (spec in KmzIconGenerator.allStyleSpecs) {
                val entry = zip.getEntry(spec.iconRelativePath)
                assertNotNull("Expected icon entry ${spec.iconRelativePath} inside KMZ", entry)
                assertTrue(entry.size > 0)
            }
        }
    }

    @Test
    fun testPlainKmlExportUsesRemoteGoogleEarthFallbackIcons() {
        val pole = samplePole("PL-TMK-0099", assetClass = "pole_9", equipment = setOf("Splitter"))
        val engine = ExportEngine(customExportDir = tempFolder.root)
        val exported = engine.generateExport(
            projectName = "Alpaca",
            segmentName = "TMK",
            format = ExportFormat.KML,
            poles = listOf(pole),
            options = ExportOptions(includeCompleted = true)
        )
        val kml = java.io.File(exported.filePath).readText()
        assertTrue(kml.contains("http://maps.google.com/mapfiles/kml/paddle/"))
        assertTrue(kml.contains("<color>ff327d2e</color>"))
        assertTrue(kml.contains("<styleUrl>#poleSplitterP9</styleUrl>"))
    }
}
