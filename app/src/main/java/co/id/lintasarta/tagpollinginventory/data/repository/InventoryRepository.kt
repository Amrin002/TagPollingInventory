package co.id.lintasarta.tagpollinginventory.data.repository

import android.content.Context
import android.util.Log
import co.id.lintasarta.tagpollinginventory.data.local.AppDatabase
import co.id.lintasarta.tagpollinginventory.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InventoryRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.inventoryDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Legacy JSON file reference (used only for cleanup/migration if needed)
    private val oldDataFile = File(context.filesDir, "inventory_data.json")

    private val _project = MutableStateFlow(createInitialProject())
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _selectedSegmentId = MutableStateFlow("SEG-19")
    val selectedSegmentId: StateFlow<String> = _selectedSegmentId.asStateFlow()

    private val _targetPoleId = MutableStateFlow("")
    val targetPoleId: StateFlow<String> = _targetPoleId.asStateFlow()

    private val _poles = MutableStateFlow<Map<String, Pole>>(emptyMap())
    val poles: StateFlow<Map<String, Pole>> = _poles.asStateFlow()

    private val _exportFiles = MutableStateFlow<List<ExportFile>>(emptyList())
    val exportFiles: StateFlow<List<ExportFile>> = _exportFiles.asStateFlow()

    private val _currentDraftPole = MutableStateFlow<Pole?>(null)
    val currentDraftPole: StateFlow<Pole?> = _currentDraftPole.asStateFlow()

    init {
        // Migration: If JSON exists but DB is empty, we could migrate. 
        // For simplicity, we just delete the old JSON file now that we use Room.
        if (oldDataFile.exists()) {
            oldDataFile.delete()
        }

        // Start observing Room Database
        scope.launch {
            dao.getProjectFlow().collect { proj ->
                if (proj != null) {
                    _project.value = _project.value.copy(
                        id = proj.id,
                        name = proj.name,
                        location = proj.location,
                        totalPoles = proj.totalPoles,
                        completedPoles = proj.completedPoles,
                        conflictPoles = proj.conflictPoles,
                        uncompletedPoles = proj.uncompletedPoles
                    )
                } else {
                    // Initialize empty project in DB
                    dao.insertProject(createInitialProject())
                }
            }
        }

        scope.launch {
            dao.getAllSegmentsFlow().collect { segs ->
                val p = _project.value
                _project.value = p.copy(segments = segs)
            }
        }

        scope.launch {
            dao.getAllPolesFlow().collect { poleList ->
                _poles.value = poleList.associateBy { it.id }
            }
        }

        scope.launch {
            dao.getAllExportFilesFlow().collect { files ->
                _exportFiles.value = files
            }
        }
    }

    private fun createInitialProject(): Project {
        return Project(
            id = "PRJ-AMB-01",
            name = "Project Utama",
            location = "Lokasi Kerja",
            totalPoles = 0,
            completedPoles = 0,
            conflictPoles = 0,
            uncompletedPoles = 0,
            segments = emptyList()
        )
    }

    fun saveData() {
        // No-op for direct callers since Room auto-persists on insert/update.
        // We only explicitly persist project stats recalculation here if needed,
        // but recalculateProjectStats already saves to Room.
    }

    fun addSegment(segment: Segment) {
        scope.launch {
            dao.insertSegment(segment)
            recalculateProjectStats()
        }
    }

    fun markSegmentCompleted(segmentId: String) {
        scope.launch {
            dao.updateSegmentStatus(segmentId, SegmentStatus.COMPLETED.name)
            recalculateProjectStats()
        }
    }

    fun deleteSegments(segmentIds: List<String>) {
        Log.d("DeleteTrace", "[Repository] Executing SQLite deletion for: $segmentIds")
        scope.launch {
            try {
                dao.deletePolesBySegmentIds(segmentIds)
                dao.deleteSegments(segmentIds)
                
                Log.d("DeleteTrace", "[Repository] SQLite deletion completed, recalculating stats")
                
                // Immediately update local _project flow so UI recomposes
                val currentProj = _project.value
                val remainingSegments = currentProj.segments.filter { it.id !in segmentIds }
                _project.value = currentProj.copy(segments = remainingSegments)
                
                recalculateProjectStats()
                Log.d("DeleteTrace", "[Repository] Deletion fully processed")
            } catch (e: Exception) {
                Log.e("DeleteTrace", "[Repository] Failed to delete segments", e)
            }
        }
    }

    fun selectSegment(segmentId: String) {
        _selectedSegmentId.value = segmentId
        val poleInSeg = _poles.value.values.firstOrNull { it.segmentId == segmentId }
        if (poleInSeg != null) {
            _targetPoleId.value = poleInSeg.id
        } else {
            _targetPoleId.value = ""
        }
    }

    fun setTargetPole(poleId: String) {
        _targetPoleId.value = poleId
        val pole = _poles.value[poleId]
        if (pole != null) {
            _selectedSegmentId.value = pole.segmentId
            _currentDraftPole.value = pole
        } else {
            scope.launch {
                val dbPole = dao.getPoleById(poleId)
                if (dbPole != null) {
                    _selectedSegmentId.value = dbPole.segmentId
                    _currentDraftPole.value = dbPole
                }
            }
        }
    }

    fun createNewPoleInSegment(segmentId: String, currentLat: Double, currentLng: Double): Pole {
        val segment = _project.value.segments.find { it.id == segmentId }
        val cityCode = segment?.cityCode?.takeIf { it.isNotBlank() } ?: "ABN"
        val locationCode = segment?.locationCode?.takeIf { it.isNotBlank() } ?: "TKB"

        val polesInSeg = _poles.value.values.filter { it.segmentId == segmentId }
        val maxSeqFromPoles = polesInSeg.maxOfOrNull { it.sequence } ?: 0
        val segmentSeq = segment?.currentSequence ?: 0
        val nextSeq = maxOf(maxSeqFromPoles, segmentSeq) + 1

        val poleCodeStr = "${cityCode.uppercase()}${locationCode.uppercase()}PL-${String.format(Locale.US, "%03d", nextSeq)}"
        val poleId = UUID.randomUUID().toString()

        if (segment != null) {
            val updatedSegment = segment.copy(currentSequence = nextSeq)
            scope.launch {
                dao.updateSegment(updatedSegment)
            }
        }

        val defaultLat = if (currentLat != 0.0) currentLat else -3.6954
        val defaultLng = if (currentLng != 0.0) currentLng else 128.1814

        val newPole = Pole(
            id = poleId,
            segmentId = segmentId,
            sequence = nextSeq,
            latitude = defaultLat,
            longitude = defaultLng,
            accuracy = 2.8f,
            capturedTimestamp = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date()),
            type = PoleType.CONCRETE,
            condition = PoleCondition.GOOD,
            ownership = PoleOwnership.LINTASARTA,
            height = "9m",
            tagNumber = "",
            hasFoCable = true,
            cableCondition = CableCondition.GOOD,
            equipment = setOf("ODP", "Closure"),
            notes = "",
            photoPaths = emptyList(),
            status = TagStatus.NOT_TAGGED,
            poleCode = poleCodeStr
        )

        _currentDraftPole.value = newPole
        _targetPoleId.value = poleId
        _selectedSegmentId.value = segmentId
        
        // Simpan langsung ke Room
        scope.launch {
            dao.insertPole(newPole)
        }
        return newPole
    }

    fun startDraftForPole(poleId: String) {
        _targetPoleId.value = poleId
        val existing = _poles.value[poleId]
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        if (existing != null) {
            _currentDraftPole.value = existing.copy(
                capturedTimestamp = if (existing.capturedTimestamp.isEmpty()) now else existing.capturedTimestamp,
                accuracy = 2.8f
            )
        } else {
            scope.launch {
                val dbPole = dao.getPoleById(poleId)
                if (dbPole != null) {
                    _currentDraftPole.value = dbPole.copy(
                        capturedTimestamp = if (dbPole.capturedTimestamp.isEmpty()) now else dbPole.capturedTimestamp,
                        accuracy = 2.8f
                    )
                }
            }
        }
    }

    fun updateDraftLocation(lat: Double, lng: Double, accuracy: Float) {
        val current = _currentDraftPole.value
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        if (current != null) {
            val updated = current.copy(
                latitude = lat,
                longitude = lng,
                accuracy = accuracy,
                capturedTimestamp = now
            )
            _currentDraftPole.value = updated
            scope.launch {
                dao.insertPole(updated)
            }
        } else {
            val targetId = _targetPoleId.value
            scope.launch {
                val pole = if (targetId.isNotEmpty()) dao.getPoleById(targetId) else null
                if (pole != null) {
                    val updated = pole.copy(
                        latitude = lat,
                        longitude = lng,
                        accuracy = accuracy,
                        capturedTimestamp = now
                    )
                    _currentDraftPole.value = updated
                    dao.insertPole(updated)
                }
            }
        }
    }
    
    fun updatePoleLocationDirectly(poleId: String, lat: Double, lng: Double) {
        val pole = _poles.value[poleId]
        if (pole != null) {
            val updated = pole.copy(latitude = lat, longitude = lng)
            scope.launch {
                dao.insertPole(updated)
            }
            if (_currentDraftPole.value?.id == poleId) {
                _currentDraftPole.value = updated
            }
        } else {
            scope.launch {
                val dbPole = dao.getPoleById(poleId)
                if (dbPole != null) {
                    val updated = dbPole.copy(latitude = lat, longitude = lng)
                    dao.insertPole(updated)
                    if (_currentDraftPole.value?.id == poleId) {
                        _currentDraftPole.value = updated
                    }
                }
            }
        }
    }

    fun updateDraftAttributes(
        type: PoleType,
        condition: PoleCondition,
        ownership: PoleOwnership,
        height: String,
        tagNumber: String,
        hasFoCable: Boolean,
        cableCondition: CableCondition,
        equipment: Set<String>,
        notes: String
    ) {
        val current = _currentDraftPole.value
        if (current != null) {
            val updated = current.copy(
                type = type,
                condition = condition,
                ownership = ownership,
                height = height,
                tagNumber = tagNumber,
                hasFoCable = hasFoCable,
                cableCondition = cableCondition,
                equipment = equipment,
                notes = notes
            )
            _currentDraftPole.value = updated
            scope.launch {
                dao.insertPole(updated)
            }
        } else {
            val targetId = _targetPoleId.value
            scope.launch {
                val pole = if (targetId.isNotEmpty()) dao.getPoleById(targetId) else null
                if (pole != null) {
                    val updated = pole.copy(
                        type = type,
                        condition = condition,
                        ownership = ownership,
                        height = height,
                        tagNumber = tagNumber,
                        hasFoCable = hasFoCable,
                        cableCondition = cableCondition,
                        equipment = equipment,
                        notes = notes
                    )
                    _currentDraftPole.value = updated
                    dao.insertPole(updated)
                }
            }
        }
    }

    fun addDraftPhoto(photoPath: String) {
        val current = _currentDraftPole.value
        if (current != null) {
            val currentList = current.photoPaths.toMutableList()
            if (currentList.size < 4) { // Increased capacity to 4
                currentList.add(photoPath)
                val updated = current.copy(photoPaths = currentList)
                _currentDraftPole.value = updated
                scope.launch {
                    dao.insertPole(updated)
                }
            }
        } else {
            val targetId = _targetPoleId.value
            scope.launch {
                val pole = if (targetId.isNotEmpty()) dao.getPoleById(targetId) else null
                if (pole != null) {
                    val currentList = pole.photoPaths.toMutableList()
                    if (currentList.size < 4) { // Increased capacity to 4
                        currentList.add(photoPath)
                        val updated = pole.copy(photoPaths = currentList)
                        _currentDraftPole.value = updated
                        dao.insertPole(updated)
                    }
                }
            }
        }
    }

    fun addDraftPhotoWithNotes(photoPath: String, additionalNote: String) {
        val current = _currentDraftPole.value
        if (current != null) {
            val currentList = current.photoPaths.toMutableList()
            if (currentList.size < 4) { // Support for 4th photo
                currentList.add(photoPath)
                var updatedNotes = current.notes
                if (additionalNote.isNotBlank()) {
                    updatedNotes = if (updatedNotes.isEmpty()) {
                        "[Tambahan]: $additionalNote"
                    } else {
                        "$updatedNotes\n[Tambahan]: $additionalNote"
                    }
                }
                
                val updated = current.copy(photoPaths = currentList, notes = updatedNotes)
                _currentDraftPole.value = updated
                scope.launch {
                    dao.insertPole(updated)
                }
            }
        }
    }
    
    fun removeDraftPhoto(photoPath: String) {
        val current = _currentDraftPole.value
        if (current != null) {
            val currentList = current.photoPaths.toMutableList()
            currentList.remove(photoPath)
            val updated = current.copy(photoPaths = currentList)
            _currentDraftPole.value = updated
            scope.launch {
                dao.insertPole(updated)
            }
        } else {
            val targetId = _targetPoleId.value
            scope.launch {
                val pole = if (targetId.isNotEmpty()) dao.getPoleById(targetId) else null
                if (pole != null) {
                    val currentList = pole.photoPaths.toMutableList()
                    currentList.remove(photoPath)
                    val updated = pole.copy(photoPaths = currentList)
                    _currentDraftPole.value = updated
                    dao.insertPole(updated)
                }
            }
        }
    }

    fun saveDraftPole(): Pole? {
        var draft = _currentDraftPole.value
        if (draft == null && _targetPoleId.value.isNotEmpty()) {
            draft = _poles.value[_targetPoleId.value]
        }
        if (draft == null) return null

        val now = if (draft.capturedTimestamp.isEmpty()) {
            SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        } else draft.capturedTimestamp

        val finalStatus = if (draft.condition == PoleCondition.CRITICAL) TagStatus.CONFLICT else TagStatus.COMPLETED

        val savedPole = draft.copy(
            capturedTimestamp = now,
            status = finalStatus
        )

        _currentDraftPole.value = savedPole

        scope.launch {
            dao.insertPole(savedPole)
            recalculateProjectStats()
        }
        
        return savedPole
    }

    private suspend fun recalculateProjectStats() {
        val currentProj = dao.getProjectSync() ?: return
        val allSegments = dao.getAllSegmentsSync()
        val allPoles = dao.getAllPolesSync()

        val updatedSegments = allSegments.map { seg ->
            val segPoles = allPoles.filter { it.segmentId == seg.id }
            val total = segPoles.size
            val completed = segPoles.count { it.status == TagStatus.COMPLETED }
            val conflict = segPoles.count { it.status == TagStatus.CONFLICT }
            val status = when {
                total > 0 && completed == total -> SegmentStatus.COMPLETED
                completed > 0 || conflict > 0 || total > 0 -> SegmentStatus.IN_PROGRESS
                else -> SegmentStatus.NOT_STARTED
            }
            seg.copy(
                totalPoles = total,
                completedPoles = completed,
                conflictPoles = conflict,
                status = status
            )
        }

        // Simpan pembaruan status segmen ke database
        dao.insertSegments(updatedSegments)

        val totalPoles = updatedSegments.sumOf { it.totalPoles }
        val completedPoles = updatedSegments.sumOf { it.completedPoles }
        val conflictPoles = updatedSegments.sumOf { it.conflictPoles }

        val updatedProject = currentProj.copy(
            totalPoles = totalPoles,
            completedPoles = completedPoles,
            conflictPoles = conflictPoles,
            uncompletedPoles = totalPoles - completedPoles
        )
        
        dao.updateProject(updatedProject)
    }

    fun addExportFile(file: ExportFile) {
        scope.launch {
            dao.insertExportFile(file)
        }
    }

    fun deleteExportFile(fileId: String) {
        scope.launch {
            dao.deleteExportFile(fileId)
        }
    }

    fun clearAllData() {
        scope.launch {
            dao.clearProjects()
            dao.clearSegments()
            dao.clearPoles()
            dao.clearExportFiles()
            
            // Re-initialize barebone project
            dao.insertProject(createInitialProject())
        }
        
        _targetPoleId.value = ""
        _currentDraftPole.value = null
    }
}
