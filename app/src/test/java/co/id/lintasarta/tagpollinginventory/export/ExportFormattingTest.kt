package co.id.lintasarta.tagpollinginventory.export

import co.id.lintasarta.tagpollinginventory.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ExportFormattingTest {

    private fun generateTestCsvString(poles: List<Pole>, options: ExportOptions): String {
        val sb = StringBuilder()
        sb.append("Pole Code,Internal ID,Segment,Latitude,Longitude,GPS Accuracy (m),Pole Type,Condition,Ownership,Height,Tag Number,FO Cable,Cable Condition,Equipment,Notes,Timestamp,Photo\n")

        for (pole in poles) {
            sb.append("\"${pole.poleCode}\",")
            sb.append("\"${pole.id}\",")
            sb.append("\"${pole.segmentId}\",")
            sb.append("${if (options.includeCoordinates) pole.latitude else ""},")
            sb.append("${if (options.includeCoordinates) pole.longitude else ""},")
            sb.append("${if (options.includeCoordinates) pole.accuracy else ""},")
            sb.append("\"${if (options.includeAttributes) pole.type.displayName else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.condition.displayName else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.ownership.displayName else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.height else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.tagNumber else ""}\",")
            sb.append("\"${if (options.includeAttributes) if (pole.hasFoCable) "Yes" else "No" else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.cableCondition.displayName else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.equipment.joinToString("; ") else ""}\",")
            sb.append("\"${if (options.includeNotes) pole.notes.replace("\"", "'") else ""}\",")
            sb.append("\"${if (options.includeTimestamp) pole.capturedTimestamp else ""}\",")
            sb.append("\"${if (options.includePhotos) pole.photoPaths.joinToString("; ") else ""}\"\n")
        }
        return sb.toString()
    }

    @Test
    fun testCsvHeader_matchesExpectedFormat() {
        val csv = generateTestCsvString(emptyList(), ExportOptions())
        val lines = csv.lines()
        assertEquals(
            "Pole Code,Internal ID,Segment,Latitude,Longitude,GPS Accuracy (m),Pole Type,Condition,Ownership,Height,Tag Number,FO Cable,Cable Condition,Equipment,Notes,Timestamp,Photo",
            lines[0]
        )
    }

    @Test
    fun testCsvRowFormatting_withFullOptions() {
        val pole = Pole(
            id = "POLE-001",
            segmentId = "SEG-19",
            sequence = 1,
            poleCode = "PL-JPR-CTR-001",
            latitude = -3.6954,
            longitude = 128.1814,
            accuracy = 2.5f,
            type = PoleType.STEEL,
            condition = PoleCondition.GOOD,
            ownership = PoleOwnership.LINTASARTA,
            height = "9m",
            tagNumber = "TAG-7788",
            hasFoCable = true,
            cableCondition = CableCondition.GOOD,
            equipment = setOf("ODP", "Closure"),
            notes = "Tiang bagus, aman",
            capturedTimestamp = "2026-10-23 10:00:00",
            photoPaths = listOf("photo_001_0.jpg", "photo_001_1.jpg"),
            status = TagStatus.COMPLETED
        )

        val csv = generateTestCsvString(listOf(pole), ExportOptions())
        val lines = csv.lines()

        assertTrue(lines.size >= 2)
        val row = lines[1]
        assertTrue(row.contains("\"PL-JPR-CTR-001\""))
        assertTrue(row.contains("-3.6954,128.1814,2.5"))
        assertTrue(row.contains("\"Steel\""))
        assertTrue(row.contains("\"Lintasarta\""))
        assertTrue(row.contains("\"TAG-7788\""))
        assertTrue(row.contains("\"Tiang bagus, aman\""))
        assertTrue(row.contains("photo_001_0.jpg; photo_001_1.jpg"))
    }

    @Test
    fun testCsvFilter_omitsNotesAndPhotosWhenUnchecked() {
        val pole = Pole(
            id = "POLE-002",
            segmentId = "SEG-19",
            sequence = 2,
            poleCode = "PL-JPR-CTR-002",
            latitude = -3.6955,
            longitude = 128.1815,
            notes = "Rahasia lapangan",
            photoPaths = listOf("secret.jpg")
        )

        val restrictedOptions = ExportOptions(
            includeNotes = false,
            includePhotos = false
        )

        val csv = generateTestCsvString(listOf(pole), restrictedOptions)
        assertFalse(csv.contains("Rahasia lapangan"))
        assertFalse(csv.contains("secret.jpg"))
    }

    @Test
    fun testExportOptions_poleStatusFiltering() {
        val poles = listOf(
            Pole(id = "1", segmentId = "S1", sequence = 1, latitude = 0.0, longitude = 0.0, status = TagStatus.COMPLETED),
            Pole(id = "2", segmentId = "S1", sequence = 2, latitude = 0.0, longitude = 0.0, status = TagStatus.CONFLICT),
            Pole(id = "3", segmentId = "S1", sequence = 3, latitude = 0.0, longitude = 0.0, status = TagStatus.NOT_TAGGED)
        )

        // Only include completed
        val optionsOnlyCompleted = ExportOptions(includeCompleted = true, includeConflict = false, includeIncomplete = false)
        val filteredCompleted = poles.filter { pole ->
            (optionsOnlyCompleted.includeCompleted && pole.status == TagStatus.COMPLETED) ||
            (optionsOnlyCompleted.includeConflict && pole.status == TagStatus.CONFLICT) ||
            (optionsOnlyCompleted.includeIncomplete && pole.status == TagStatus.NOT_TAGGED)
        }

        assertEquals(1, filteredCompleted.size)
        assertEquals("1", filteredCompleted.first().id)
    }
}
