package co.id.lintasarta.tagpollinginventory.repository

import co.id.lintasarta.tagpollinginventory.data.model.PoleEntity
import co.id.lintasarta.tagpollinginventory.data.model.PoleCondition
import co.id.lintasarta.tagpollinginventory.data.model.PoleOwnership
import co.id.lintasarta.tagpollinginventory.data.model.PoleType
import co.id.lintasarta.tagpollinginventory.data.model.TagStatus
import org.junit.Assert.*
import org.junit.Test

class PmSurveyPhotoSlotTest {

    private fun normalizePhotoSlots(paths: List<String>): MutableList<String> {
        val result = MutableList(10) { "" }
        for (i in paths.indices) {
            if (i < 10) {
                result[i] = paths[i]
            }
        }
        return result
    }

    private fun simulateSetDraftPhotoForSlot(
        current: PoleEntity,
        slotIndex: Int,
        photoPath: String
    ): PoleEntity {
        val baseList = if (current.photoPaths.isNotEmpty()) current.photoPaths
                       else if (!current.photoPath.isNullOrEmpty()) listOf(current.photoPath!!)
                       else emptyList()
        val currentSlots = normalizePhotoSlots(baseList)
        if (slotIndex in 0..9) {
            currentSlots[slotIndex] = photoPath
        }
        val primaryPhoto = currentSlots.firstOrNull { it.isNotEmpty() } ?: photoPath
        return current.copy(
            photoPath = primaryPhoto,
            photoPaths = currentSlots.toList()
        )
    }

    private fun simulateRemoveDraftPhotoFromSlot(
        current: PoleEntity,
        slotIndex: Int
    ): PoleEntity {
        val baseList = if (current.photoPaths.isNotEmpty()) current.photoPaths
                       else if (!current.photoPath.isNullOrEmpty()) listOf(current.photoPath!!)
                       else emptyList()
        val currentSlots = normalizePhotoSlots(baseList)
        if (slotIndex in 0..9) {
            currentSlots[slotIndex] = ""
        }
        val remainingPhotos = currentSlots.filter { it.isNotEmpty() }
        val primaryPhoto = remainingPhotos.firstOrNull()
        return current.copy(
            photoPath = primaryPhoto,
            photoPaths = currentSlots.toList()
        )
    }

    private fun createInitialPole(): PoleEntity {
        return PoleEntity(
            id = "POL-TEST-001",
            segmentId = "SEG-TEST",
            tagNumber = "TAG-001",
            latitude = -3.7,
            longitude = 128.18,
            accuracy = 1.5f,
            type = PoleType.STEEL,
            condition = PoleCondition.GOOD,
            ownership = PoleOwnership.LINTASARTA,
            height = "9m",
            photoPath = null,
            photoPaths = emptyList()
        )
    }

    @Test
    fun testNormalizePhotoSlotsReturnsTenSlots() {
        val slots = normalizePhotoSlots(listOf("/path/0", "/path/1"))
        assertEquals(10, slots.size)
        assertEquals("/path/0", slots[0])
        assertEquals("/path/1", slots[1])
        assertEquals("", slots[2])
        assertEquals("", slots[9])
    }

    @Test
    fun testSetDraftPhotoForSlotMaintainsSlotPositions() {
        var pole = createInitialPole()

        // 1. Capture Slot 0 (Pondasi)
        pole = simulateSetDraftPhotoForSlot(pole, 0, "/storage/photo_pondasi.jpg")
        assertEquals("/storage/photo_pondasi.jpg", pole.photoPath)
        assertEquals(10, pole.photoPaths.size)
        assertEquals("/storage/photo_pondasi.jpg", pole.photoPaths[0])
        assertEquals("", pole.photoPaths[1])

        // 2. Capture Slot 2 (Perangkat Atas) without slot 1 yet
        pole = simulateSetDraftPhotoForSlot(pole, 2, "/storage/photo_odp.jpg")
        assertEquals("/storage/photo_pondasi.jpg", pole.photoPath)
        assertEquals("/storage/photo_pondasi.jpg", pole.photoPaths[0])
        assertEquals("", pole.photoPaths[1])
        assertEquals("/storage/photo_odp.jpg", pole.photoPaths[2])

        // 3. Capture Slot 1 (Label Tag)
        pole = simulateSetDraftPhotoForSlot(pole, 1, "/storage/photo_label.jpg")
        assertEquals("/storage/photo_pondasi.jpg", pole.photoPaths[0])
        assertEquals("/storage/photo_label.jpg", pole.photoPaths[1])
        assertEquals("/storage/photo_odp.jpg", pole.photoPaths[2])

        // 4. Capture Slot 9 (Dokumentasi Opsional 7)
        pole = simulateSetDraftPhotoForSlot(pole, 9, "/storage/photo_extra.jpg")
        assertEquals("/storage/photo_extra.jpg", pole.photoPaths[9])
    }

    @Test
    fun testRemoveDraftPhotoFromSlotOnlyClearsTargetSlot() {
        var pole = createInitialPole()
        pole = simulateSetDraftPhotoForSlot(pole, 0, "/storage/photo_0.jpg")
        pole = simulateSetDraftPhotoForSlot(pole, 1, "/storage/photo_1.jpg")
        pole = simulateSetDraftPhotoForSlot(pole, 2, "/storage/photo_2.jpg")

        // Remove slot 1
        pole = simulateRemoveDraftPhotoFromSlot(pole, 1)
        assertEquals("/storage/photo_0.jpg", pole.photoPaths[0])
        assertEquals("", pole.photoPaths[1])
        assertEquals("/storage/photo_2.jpg", pole.photoPaths[2])
        assertEquals("/storage/photo_0.jpg", pole.photoPath)

        // Remove slot 0 -> primary becomes slot 2
        pole = simulateRemoveDraftPhotoFromSlot(pole, 0)
        assertEquals("", pole.photoPaths[0])
        assertEquals("", pole.photoPaths[1])
        assertEquals("/storage/photo_2.jpg", pole.photoPaths[2])
        assertEquals("/storage/photo_2.jpg", pole.photoPath)
    }

    @Test
    fun testSaveDraftPreservesAllSlotsAndStatusCompleted() {
        var pole = createInitialPole()
        pole = simulateSetDraftPhotoForSlot(pole, 0, "/storage/photo_0.jpg")
        pole = simulateSetDraftPhotoForSlot(pole, 1, "/storage/photo_1.jpg")
        pole = simulateSetDraftPhotoForSlot(pole, 2, "/storage/photo_2.jpg")

        val preserved = if (pole.photoPaths.isNotEmpty()) pole.photoPaths
                        else if (!pole.photoPath.isNullOrEmpty()) listOf(pole.photoPath!!)
                        else emptyList()

        val primary = preserved.firstOrNull { it.isNotEmpty() } ?: pole.photoPath

        val calculatedStatus = when {
            pole.latitude != 0.0 && (!pole.photoPath.isNullOrEmpty() || pole.photoPaths.any { it.isNotEmpty() }) -> TagStatus.COMPLETED
            else -> TagStatus.NOT_TAGGED
        }

        val saved = pole.copy(
            photoPath = primary,
            photoPaths = preserved,
            status = calculatedStatus
        )

        assertEquals(TagStatus.COMPLETED, saved.status)
        assertEquals(10, saved.photoPaths.size)
        assertEquals("/storage/photo_0.jpg", saved.photoPaths[0])
        assertEquals("/storage/photo_1.jpg", saved.photoPaths[1])
        assertEquals("/storage/photo_2.jpg", saved.photoPaths[2])
        assertEquals(3, saved.photoPaths.filter { it.isNotEmpty() }.size)
    }
}
