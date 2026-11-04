package co.id.lintasarta.tagpollinginventory.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.id.lintasarta.tagpollinginventory.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class InventoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: InventoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.inventoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadProject() = runBlocking {
        val project = Project(
            id = "PRJ-001",
            name = "Jayapura Sector 2",
            totalPoles = 300,
            completedPoles = 142,
            conflictPoles = 3
        )
        dao.insertProject(project)

        val loaded = dao.getProjectSync()
        assertNotNull(loaded)
        assertEquals("PRJ-001", loaded?.id)
        assertEquals("Jayapura Sector 2", loaded?.name)
        assertEquals(300, loaded?.totalPoles)
        assertEquals(142, loaded?.completedPoles)
        assertEquals(3, loaded?.conflictPoles)
    }

    @Test
    fun writeAndReadSegments() = runBlocking {
        val segment1 = Segment(
            id = "SEG-01",
            projectId = "PRJ-001",
            name = "Sentani Barat",
            totalPoles = 25,
            cityCode = "JPR",
            locationCode = "STN"
        )
        val segment2 = Segment(
            id = "SEG-02",
            projectId = "PRJ-001",
            name = "Abepura Sentral",
            totalPoles = 15,
            cityCode = "JPR",
            locationCode = "ABP"
        )
        dao.insertSegments(listOf(segment1, segment2))

        val allSegments = dao.getAllSegmentsSync()
        assertEquals(2, allSegments.size)

        val fetched = dao.getSegmentById("SEG-01")
        assertNotNull(fetched)
        assertEquals("Sentani Barat", fetched?.name)
        assertEquals("JPR", fetched?.cityCode)
    }

    @Test
    fun writeAndReadPoleWithComplexConverters() = runBlocking {
        val pole = Pole(
            id = "POLE-001",
            segmentId = "SEG-01",
            sequence = 1,
            poleCode = "PL-JPR-STN-001",
            latitude = -3.6954,
            longitude = 128.1814,
            accuracy = 2.1f,
            type = PoleType.STEEL,
            condition = PoleCondition.GOOD,
            ownership = PoleOwnership.LINTASARTA,
            height = "9m",
            tagNumber = "TAG-1234",
            hasFoCable = true,
            cableCondition = CableCondition.GOOD,
            equipment = setOf("ODP", "Closure"),
            notes = "Tiang survei uji coba",
            photoPaths = listOf("/sdcard/photo1.jpg", "/sdcard/photo2.jpg"),
            status = TagStatus.COMPLETED,
            isLocationLocked = true
        )
        dao.insertPole(pole)

        val loaded = dao.getPoleById("POLE-001")
        assertNotNull(loaded)
        assertEquals("PL-JPR-STN-001", loaded?.poleCode)
        assertEquals(PoleType.STEEL, loaded?.type)
        assertEquals(PoleOwnership.LINTASARTA, loaded?.ownership)
        assertTrue(loaded!!.equipment.contains("ODP"))
        assertTrue(loaded.equipment.contains("Closure"))
        assertEquals(2, loaded.photoPaths.size)
        assertTrue(loaded.isLocationLocked)
    }

    @Test
    fun deleteSegmentsAndCascadePoles() = runBlocking {
        val segId = "SEG-DEL"
        val segment = Segment(id = segId, name = "Segment to Delete")
        val pole1 = Pole(id = "P1", segmentId = segId, sequence = 1, latitude = 0.0, longitude = 0.0)
        val pole2 = Pole(id = "P2", segmentId = segId, sequence = 2, latitude = 0.0, longitude = 0.0)

        dao.insertSegment(segment)
        dao.insertPoles(listOf(pole1, pole2))

        assertEquals(1, dao.getAllSegmentsSync().filter { it.id == segId }.size)
        assertEquals(2, dao.getAllPolesSync().filter { it.segmentId == segId }.size)

        // Delete segment and associated poles
        dao.deleteSegments(listOf(segId))
        dao.deletePolesBySegmentIds(listOf(segId))

        assertEquals(0, dao.getAllSegmentsSync().filter { it.id == segId }.size)
        assertEquals(0, dao.getAllPolesSync().filter { it.segmentId == segId }.size)
    }
}
