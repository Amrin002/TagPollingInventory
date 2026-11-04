package co.id.lintasarta.tagpollinginventory.model

import co.id.lintasarta.tagpollinginventory.data.model.*
import org.junit.Assert.*
import org.junit.Test

class PoleModelAndStateTest {

    @Test
    fun testPoleDefaultValues() {
        val pole = Pole(
            id = "POLE-001",
            segmentId = "SEG-01",
            sequence = 1,
            latitude = -3.6954,
            longitude = 128.1814
        )

        assertEquals("POLE-001", pole.id)
        assertEquals("SEG-01", pole.segmentId)
        assertEquals(1, pole.sequence)
        assertEquals(-3.6954, pole.latitude, 0.0001)
        assertEquals(128.1814, pole.longitude, 0.0001)
        assertEquals(TagStatus.NOT_TAGGED, pole.status)
        assertEquals(PoleType.STEEL, pole.type)
        assertEquals(PoleCondition.GOOD, pole.condition)
        assertEquals(PoleOwnership.LINTASARTA, pole.ownership)
        assertTrue(pole.hasFoCable)
        assertEquals(CableCondition.GOOD, pole.cableCondition)
        assertTrue(pole.equipment.contains("ODP"))
        assertTrue(pole.equipment.contains("Closure"))
        assertFalse(pole.isLocationLocked)
        assertTrue(pole.photoPaths.isEmpty())
    }

    @Test
    fun testPoleStateTransitions() {
        val initialPole = Pole(
            id = "POLE-001",
            segmentId = "SEG-01",
            sequence = 1,
            latitude = -3.6954,
            longitude = 128.1814,
            status = TagStatus.NOT_TAGGED
        )

        // Simulate GPS lock
        val lockedPole = initialPole.copy(isLocationLocked = true)
        assertTrue(lockedPole.isLocationLocked)
        assertEquals(TagStatus.NOT_TAGGED, lockedPole.status)

        // Simulate completing pole survey
        val completedPole = lockedPole.copy(
            status = TagStatus.COMPLETED,
            poleCode = "PL-JPR-CTR-001",
            photoPaths = listOf("/data/photo_1.jpg", "/data/photo_2.jpg"),
            notes = "Tiang berdiri kokoh di trotoar"
        )

        assertEquals(TagStatus.COMPLETED, completedPole.status)
        assertEquals("PL-JPR-CTR-001", completedPole.poleCode)
        assertEquals(2, completedPole.photoPaths.size)
        assertEquals("Tiang berdiri kokoh di trotoar", completedPole.notes)
    }

    @Test
    fun testEnumDisplayNames() {
        assertEquals("Steel", PoleType.STEEL.displayName)
        assertEquals("Concrete", PoleType.CONCRETE.displayName)
        assertEquals("Wooden", PoleType.WOODEN.displayName)
        assertEquals("Other", PoleType.OTHER.displayName)

        assertEquals("Good", PoleCondition.GOOD.displayName)
        assertEquals("Fair", PoleCondition.FAIR.displayName)
        assertEquals("Damaged", PoleCondition.DAMAGED.displayName)
        assertEquals("Critical", PoleCondition.CRITICAL.displayName)

        assertEquals("Lintasarta", PoleOwnership.LINTASARTA.displayName)
        assertEquals("PLN", PoleOwnership.PLN.displayName)
        assertEquals("Telkom", PoleOwnership.TELKOM.displayName)
        assertEquals("Customer", PoleOwnership.CUSTOMER.displayName)

        assertEquals("CSV", ExportFormat.CSV.displayName)
        assertEquals("csv", ExportFormat.CSV.extension)
        assertEquals("KML", ExportFormat.KML.displayName)
        assertEquals("kml", ExportFormat.KML.extension)
        assertEquals("KMZ", ExportFormat.KMZ.displayName)
        assertEquals("kmz", ExportFormat.KMZ.extension)
    }

    @Test
    fun testSegmentStatsCalculation() {
        val segment = Segment(
            id = "SEG-01",
            name = "Jayapura Sentani",
            totalPoles = 10,
            completedPoles = 7,
            conflictPoles = 1,
            status = SegmentStatus.IN_PROGRESS
        )

        val pendingPoles = segment.totalPoles - (segment.completedPoles + segment.conflictPoles)
        assertEquals(2, pendingPoles)
        assertEquals(SegmentStatus.IN_PROGRESS, segment.status)
    }
}
