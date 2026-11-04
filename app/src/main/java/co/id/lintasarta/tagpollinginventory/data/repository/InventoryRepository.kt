package co.id.lintasarta.tagpollinginventory.data.repository

import android.content.Context
import android.util.Log
import co.id.lintasarta.tagpollinginventory.data.local.AppDatabase
import co.id.lintasarta.tagpollinginventory.data.model.*
import co.id.lintasarta.tagpollinginventory.ui.screens.deriveCodesFromSegmentName
import co.id.lintasarta.tagpollinginventory.network.RnimsApiClient
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
    val dao = db.inventoryDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Legacy JSON file reference (deleted on launch if present)
    private val oldDataFile = File(context.filesDir, "inventory_data.json")

    private val _project = MutableStateFlow(createInitialProject())
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _selectedSegmentId = MutableStateFlow("SEG-2")
    val selectedSegmentId: StateFlow<String> = _selectedSegmentId.asStateFlow()

    private val _targetPoleId = MutableStateFlow("")
    val targetPoleId: StateFlow<String> = _targetPoleId.asStateFlow()

    private val _poles = MutableStateFlow<Map<String, PoleEntity>>(emptyMap())
    val poles: StateFlow<Map<String, PoleEntity>> = _poles.asStateFlow()

    private val _exportFiles = MutableStateFlow<List<ExportFile>>(emptyList())
    val exportFiles: StateFlow<List<ExportFile>> = _exportFiles.asStateFlow()

    private val _currentDraftPole = MutableStateFlow<PoleEntity?>(null)
    val currentDraftPole: StateFlow<PoleEntity?> = _currentDraftPole.asStateFlow()

    init {
        if (oldDataFile.exists()) {
            oldDataFile.delete()
        }

        // Observasi daftar Segmen dari SQLite Room
        scope.launch {
            dao.getAllSegmentsFlow().collect { segs ->
                val p = _project.value
                val totalPoles = segs.sumOf { it.totalPoles }
                val completedPoles = segs.sumOf { it.completedPoles }
                val conflictPoles = segs.sumOf { it.conflictPoles }
                _project.value = p.copy(
                    segments = segs,
                    totalPoles = totalPoles,
                    completedPoles = completedPoles,
                    conflictPoles = conflictPoles,
                    uncompletedPoles = (totalPoles - completedPoles).coerceAtLeast(0)
                )

                if (_selectedSegmentId.value.isEmpty() && segs.isNotEmpty()) {
                    _selectedSegmentId.value = segs.first().id
                }
            }
        }

        // Observasi seluruh tiang dari SQLite Room
        scope.launch {
            dao.getAllPolesFlow().collect { poleList ->
                _poles.value = poleList.associateBy { it.id }
                recalculateProjectStats()
            }
        }

        // Observasi file export
        scope.launch {
            dao.getAllExportFilesFlow().collect { files ->
                _exportFiles.value = files
            }
        }

        // Auto-seed 143 segmen MPO jika tabel segments di Room masih kosong
        scope.launch {
            val existingSegments = dao.getAllSegmentsSync()
            if (existingSegments.isEmpty()) {
                seedMpoSegments()
            }
        }
    }

    /**
     * Membaca dan menanam (seed) 143 segmen MPO dari assets/mpo_segments.json ke database Room
     */
    fun seedMpoSegments(forceReload: Boolean = false) {
        scope.launch {
            val current = dao.getAllSegmentsSync()
            if (current.isNotEmpty() && !forceReload) return@launch

            val bundledDtos = RnimsApiClient.loadBundledMpoSegments(context)
            if (bundledDtos.isEmpty()) return@launch

            val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
            val seededSegments = bundledDtos.map { dto ->
                val coords = dto.route_path?.map {
                    RouteCoordinate(longitude = it.lng, latitude = it.lat)
                } ?: emptyList()

                val cityCode = dto.org_site?.take(3)?.uppercase() ?: "TMK"
                val locationCode = dto.dst_site?.take(3)?.uppercase() ?: "PMK"
                val cityFilter = determineCityFilterKey(dto.org_city, dto.dst_city, dto.org_site)
                val priorityKey = determinePriorityRouteKey(dto.segment_name, dto.org_site, dto.dst_site)

                SegmentEntity(
                    id = "SEG-${dto.id}",
                    name = dto.segment_name,
                    lengthMeters = dto.distance,
                    routeCoordinates = coords,
                    areaId = dto.area_id,
                    orgSite = dto.org_site ?: "",
                    dstSite = dto.dst_site ?: "",
                    orgCity = dto.org_city ?: "",
                    dstCity = dto.dst_city ?: "",
                    cityFilterKey = cityFilter,
                    isAlpacaProject = true,
                    priorityRouteKey = priorityKey,
                    poId = "PO-MPO-${dto.id}",
                    route = if (dto.org_site.isNullOrEmpty()) dto.segment_name else "${dto.org_site} -> ${dto.dst_site}",
                    description = "Segmen FO ${dto.org_site ?: ""} - ${dto.dst_site ?: ""}",
                    startPoint = if (coords.isNotEmpty()) Pair(coords.first().latitude, coords.first().longitude) else Pair(0.0, 0.0),
                    endPoint = if (coords.isNotEmpty()) Pair(coords.last().latitude, coords.last().longitude) else Pair(0.0, 0.0),
                    status = SegmentStatus.NOT_STARTED,
                    totalPoles = 0,
                    completedPoles = 0,
                    conflictPoles = 0,
                    isPinned = false,
                    cityCode = cityCode,
                    locationCode = locationCode,
                    currentSequence = 0,
                    createdAt = now,
                    updatedAt = now
                )
            }

            dao.insertSegments(seededSegments)

            val initialProject = Project(
                id = "PRJ-MPO-ALPACA",
                name = "Alpaca PRA - Regional Fiber Optic Survey",
                location = "Area 18 Maluku Papua Operation",
                totalPoles = 0,
                completedPoles = 0,
                conflictPoles = 0,
                uncompletedPoles = 0,
                totalP7 = 0,
                totalP9 = 0,
                segments = seededSegments
            )
            _project.value = initialProject

            if (_selectedSegmentId.value.isEmpty() || _selectedSegmentId.value == "SEG-19") {
                seededSegments.firstOrNull()?.let { firstSeg ->
                    _selectedSegmentId.value = firstSeg.id
                }
            }
        }
    }

    private fun determinePriorityRouteKey(name: String, orgSite: String?, dstSite: String?): String {
        val text = "$name ${orgSite.orEmpty()} ${dstSite.orEmpty()}".lowercase(Locale.ROOT)
        return when {
            text.contains("sentani") || text.contains("abepura") -> "SENTANI_ABEPURA"
            text.contains("manokwari") || text.contains("mkw") || text.contains("mnk") -> "MANOKWARI"
            text.contains("timika") || text.contains("tmk") || text.contains("mimika") -> "TIMIKA"
            text.contains("sorong") || text.contains("srg") || text.contains("soq") -> "SORONG"
            text.contains("dobo") || text.contains("dbo") -> "DOBO"
            text.contains("tiakur") || text.contains("tkr") -> "TIAKUR"
            else -> ""
        }
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

    private fun createInitialProject(): Project {
        return Project(
            id = "PRJ-MPO-ALPACA",
            name = "Alpaca PRA - Regional Fiber Optic Survey",
            location = "Area 18 Maluku Papua Operation",
            totalPoles = 0,
            completedPoles = 0,
            conflictPoles = 0,
            uncompletedPoles = 0,
            totalP7 = 0,
            totalP9 = 0,
            segments = emptyList()
        )
    }

    fun saveData() {
        // Room auto-persists in real-time
    }

    fun addSegment(segment: SegmentEntity) {
        val currentProj = _project.value
        if (currentProj.segments.none { it.id == segment.id }) {
            _project.value = currentProj.copy(segments = currentProj.segments + segment)
        }
        scope.launch {
            dao.insertSegment(segment)
            recalculateProjectStats()
        }
    }

    fun togglePinSegment(segmentId: String) {
        scope.launch {
            val seg = dao.getSegmentById(segmentId) ?: return@launch
            val newPinned = !seg.isPinned
            dao.updateSegmentPin(segmentId, newPinned)
            val updatedList = _project.value.segments.map {
                if (it.id == segmentId) it.copy(isPinned = newPinned) else it
            }.sortedWith(compareByDescending<SegmentEntity> { it.isPinned }.thenBy { it.id })
            _project.value = _project.value.copy(segments = updatedList)
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

    /**
     * Membuat Tiang Baru Sesuai Standar Alpaca PRA:
     * - assetUid: POL-MPO-[KOTA]-[SEQ:04d]
     * - assetClass: pole_7 / pole_9
     * - rawLatitude / rawLongitude tersimpan untuk pembuktian GNSS
     */
    fun createNewPoleInSegment(
        segmentId: String,
        currentLat: Double,
        currentLng: Double,
        assetClass: String = "pole_9"
    ): PoleEntity {
        val segment = _project.value.segments.find { it.id == segmentId }
        val (autoCity, autoLoc) = deriveCodesFromSegmentName(segment?.name ?: "")
        val cityCode = segment?.cityCode?.takeIf { it.isNotBlank() } ?: autoCity
        val locationCode = segment?.locationCode?.takeIf { it.isNotBlank() } ?: autoLoc

        val polesInSeg = _poles.value.values.filter { it.segmentId == segmentId }
        val nextSeq = if (polesInSeg.isEmpty()) {
            if ((segment?.currentSequence ?: 0) > 0) segment!!.currentSequence else 1
        } else {
            val maxSeqFromPoles = polesInSeg.maxOfOrNull { it.sequence } ?: 0
            val segmentSeq = segment?.currentSequence ?: 0
            maxOf(maxSeqFromPoles, segmentSeq) + 1
        }

        // Format Asset UID Resmi Alpaca PRA: POL-MPO-[KOTA]-[SEQ:04d]
        val cleanCity = cityCode.trim().uppercase().take(3).ifEmpty { "TMK" }
        val assetUidStr = "POL-MPO-$cleanCity-${String.format(Locale.US, "%04d", nextSeq)}"
        val poleId = UUID.randomUUID().toString()

        if (segment != null) {
            val updatedSegment = segment.copy(currentSequence = nextSeq)
            val currProj = _project.value
            _project.value = currProj.copy(
                segments = currProj.segments.map { if (it.id == segmentId) updatedSegment else it }
            )
            scope.launch {
                dao.updateSegment(updatedSegment)
            }
        }

        val defaultLat = if (currentLat != 0.0) currentLat else -3.6954
        val defaultLng = if (currentLng != 0.0) currentLng else 128.1814
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())

        val newPole = PoleEntity(
            id = poleId,
            segmentId = segmentId,
            sequence = nextSeq,
            assetUid = assetUidStr,
            poleCode = assetUidStr,
            routeName = segment?.name ?: "",
            poId = segment?.poId ?: "",
            assetClass = assetClass,
            assetType = "steel",
            latitude = defaultLat,
            longitude = defaultLng,
            rawLatitude = defaultLat,
            rawLongitude = defaultLng,
            accuracy = 2.8f,
            photoPath = null,
            type = PoleType.STEEL,
            condition = PoleCondition.GOOD,
            ownership = PoleOwnership.LINTASARTA,
            syncStatus = "PENDING",
            capturedTimestamp = now,
            timestamp = now,
            height = if (assetClass == "pole_7") "7m" else "9m",
            tagNumber = "",
            hasFoCable = true,
            cableCondition = CableCondition.GOOD,
            equipment = setOf("ODP", "Closure"),
            notes = "",
            isSafetyDeferred = false,
            matchStatus = "MATCHED",
            isExistingPole = false,
            isLocationLocked = false,
            originalLatitude = defaultLat,
            originalLongitude = defaultLng
        )

        _currentDraftPole.value = newPole
        _targetPoleId.value = poleId
        _selectedSegmentId.value = segmentId
        _poles.value = _poles.value + (poleId to newPole)

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
        if (current?.isLocationLocked == true) return

        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        if (current != null) {
            val updated = current.copy(
                latitude = lat,
                longitude = lng,
                rawLatitude = current.rawLatitude ?: lat,
                rawLongitude = current.rawLongitude ?: lng,
                accuracy = accuracy,
                capturedTimestamp = now,
                timestamp = now
            )
            _currentDraftPole.value = updated
            scope.launch {
                dao.insertPole(updated)
            }
        } else {
            val targetId = _targetPoleId.value
            scope.launch {
                val pole = if (targetId.isNotEmpty()) dao.getPoleById(targetId) else null
                if (pole != null && !pole.isLocationLocked) {
                    val updated = pole.copy(
                        latitude = lat,
                        longitude = lng,
                        rawLatitude = pole.rawLatitude ?: lat,
                        rawLongitude = pole.rawLongitude ?: lng,
                        accuracy = accuracy,
                        capturedTimestamp = now,
                        timestamp = now
                    )
                    _currentDraftPole.value = updated
                    dao.insertPole(updated)
                }
            }
        }
    }

    fun lockDraftLocation(lat: Double, lng: Double, accuracy: Float) {
        val current = _currentDraftPole.value ?: return
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        val finalLat = if (lat != 0.0) lat else current.latitude
        val finalLng = if (lng != 0.0) lng else current.longitude
        val locked = current.copy(
            latitude = finalLat,
            longitude = finalLng,
            rawLatitude = current.rawLatitude ?: finalLat,
            rawLongitude = current.rawLongitude ?: finalLng,
            accuracy = accuracy,
            capturedTimestamp = now,
            timestamp = now,
            isLocationLocked = true
        )
        _currentDraftPole.value = locked
        scope.launch {
            dao.insertPole(locked)
        }
    }

    fun unlockDraftLocation() {
        val current = _currentDraftPole.value ?: return
        val unlocked = current.copy(isLocationLocked = false)
        _currentDraftPole.value = unlocked
        scope.launch {
            dao.insertPole(unlocked)
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
        val derivedClass = if (height.contains("7")) "pole_7" else "pole_9"
        if (current != null) {
            val updated = current.copy(
                type = type,
                condition = condition,
                ownership = ownership,
                height = height,
                assetClass = derivedClass,
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
                        assetClass = derivedClass,
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

    fun normalizePhotoSlots(paths: List<String>): MutableList<String> {
        val result = MutableList(10) { "" }
        for (i in paths.indices) {
            if (i < 10) {
                result[i] = paths[i]
            }
        }
        return result
    }

    fun setDraftPhotoForSlot(slotIndex: Int, photoPath: String) {
        val current = _currentDraftPole.value ?: return
        val updated = current.copy(photoPath = photoPath)
        _currentDraftPole.value = updated
        scope.launch {
            dao.insertPole(updated)
        }
    }

    fun removeDraftPhotoFromSlot(slotIndex: Int) {
        val current = _currentDraftPole.value ?: return
        current.photoPath?.let { oldPath ->
            val file = File(oldPath)
            if (file.exists()) file.delete()
        }
        val updated = current.copy(photoPath = null, photoSha256 = null)
        _currentDraftPole.value = updated
        scope.launch {
            dao.insertPole(updated)
        }
    }

    fun addDraftPhoto(photoPath: String) {
        val current = _currentDraftPole.value ?: return
        val updated = current.copy(photoPath = photoPath)
        _currentDraftPole.value = updated
        scope.launch {
            dao.insertPole(updated)
        }
    }

    fun addDraftPhotoWithNotes(photoPath: String, additionalNote: String) {
        val current = _currentDraftPole.value ?: return
        val updatedNotes = if (additionalNote.isNotBlank()) {
            if (current.notes.isEmpty()) "[Tambahan]: $additionalNote" else "${current.notes}\n[Tambahan]: $additionalNote"
        } else current.notes
        val updated = current.copy(photoPath = photoPath, notes = updatedNotes)
        _currentDraftPole.value = updated
        scope.launch {
            dao.insertPole(updated)
        }
    }

    fun removeDraftPhoto(photoPath: String) {
        val current = _currentDraftPole.value ?: return
        if (current.photoPath == photoPath) {
            removeDraftPhotoFromSlot(0)
        }
    }

    fun saveDraftPole(): PoleEntity? {
        var draft = _currentDraftPole.value
        if (draft == null && _targetPoleId.value.isNotEmpty()) {
            draft = _poles.value[_targetPoleId.value]
        }
        if (draft == null) return null

        val now = if (draft.capturedTimestamp.isEmpty()) {
            SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        } else draft.capturedTimestamp

        val savedPole = draft.copy(
            capturedTimestamp = now,
            timestamp = now,
            syncStatus = "PENDING"
        )

        _currentDraftPole.value = savedPole

        scope.launch {
            dao.insertPole(savedPole)
            recalculateProjectStats()
        }

        return savedPole
    }

    /**
     * Menyimpan Hasil Quick Tagging Tiang dengan Standar Alpaca PRA & Architecture v2.3
     */
    fun saveQuickTagPole(
        poleId: String,
        latitude: Double,
        longitude: Double,
        originalLatitude: Double? = null,
        originalLongitude: Double? = null,
        photoPath: String?,
        assetClass: String = "pole_9",
        assetType: String = "steel",
        isExistingPole: Boolean = false,
        isSafetyDeferred: Boolean = false,
        safetyDeferralReason: String? = null,
        photoSha256: String? = null,
        matchStatus: String = "MATCHED",
        notes: String? = null,
        accuracy: Float = 2.5f
    ): PoleEntity? {
        val draft = _currentDraftPole.value?.takeIf { it.id == poleId } ?: _poles.value[poleId] ?: return null
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())

        val savedPole = draft.copy(
            latitude = latitude,
            longitude = longitude,
            rawLatitude = originalLatitude ?: draft.rawLatitude ?: latitude,
            rawLongitude = originalLongitude ?: draft.rawLongitude ?: longitude,
            originalLatitude = originalLatitude ?: latitude,
            originalLongitude = originalLongitude ?: longitude,
            accuracy = accuracy,
            photoPath = photoPath,
            photoSha256 = photoSha256,
            assetClass = assetClass,
            assetType = assetType,
            height = if (assetClass == "pole_7") "7m" else "9m",
            capturedTimestamp = now,
            timestamp = now,
            syncStatus = "PENDING",
            isExistingPole = isExistingPole,
            isSafetyDeferred = isSafetyDeferred,
            safetyDeferralReason = safetyDeferralReason,
            matchStatus = matchStatus,
            notes = notes ?: draft.notes,
            isLocationLocked = true
        )

        val updatedMap = _poles.value.toMutableMap()
        updatedMap[savedPole.id] = savedPole
        _poles.value = updatedMap
        _currentDraftPole.value = savedPole

        scope.launch {
            dao.insertPole(savedPole)
            recalculateProjectStats()
        }

        return savedPole
    }

    /**
     * Menyimpan hasil evaluasi keselamatan lapangan (Approach Safety Deferral)
     * tanpa memalsukan titik koordinat tiang selesai.
     */
    fun saveSafetyDeferredPole(
        poleId: String,
        latitude: Double,
        longitude: Double,
        reason: String,
        assetClass: String = "pole_9"
    ): PoleEntity? {
        return saveQuickTagPole(
            poleId = poleId,
            latitude = latitude,
            longitude = longitude,
            originalLatitude = latitude,
            originalLongitude = longitude,
            photoPath = null,
            assetClass = assetClass,
            isSafetyDeferred = true,
            safetyDeferralReason = reason,
            matchStatus = "ANOMALY",
            notes = "[SAFETY DEFERRAL]: $reason"
        )
    }

    private suspend fun recalculateProjectStats() {
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

        dao.insertSegments(updatedSegments)

        val totalPoles = updatedSegments.sumOf { it.totalPoles }
        val completedPoles = updatedSegments.sumOf { it.completedPoles }
        val conflictPoles = updatedSegments.sumOf { it.conflictPoles }
        val totalP7 = allPoles.count { it.assetClass == "pole_7" }
        val totalP9 = allPoles.count { it.assetClass == "pole_9" }

        val currentProj = _project.value
        _project.value = currentProj.copy(
            segments = updatedSegments,
            totalPoles = totalPoles,
            completedPoles = completedPoles,
            conflictPoles = conflictPoles,
            uncompletedPoles = (totalPoles - completedPoles).coerceAtLeast(0),
            totalP7 = totalP7,
            totalP9 = totalP9
        )
    }

    fun markPolesAsSynced(poleIds: List<String>) {
        scope.launch {
            poleIds.forEach { id ->
                dao.updatePoleSyncStatus(id, "SYNCED")
            }
            val updated = _poles.value.toMutableMap()
            poleIds.forEach { id ->
                val p = updated[id]
                if (p != null) {
                    updated[id] = p.copy(syncStatus = "SYNCED")
                }
            }
            _poles.value = updated
        }
    }

    fun deletePole(poleId: String) {
        scope.launch {
            dao.deletePoleById(poleId)
            val updated = _poles.value.toMutableMap()
            updated.remove(poleId)
            _poles.value = updated
            recalculateProjectStats()
        }
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
            dao.clearSegments()
            dao.clearPoles()
            dao.clearExportFiles()

            // Re-seed 143 segmen MPO
            seedMpoSegments(forceReload = true)
        }

        _targetPoleId.value = ""
        _currentDraftPole.value = null
    }
}
