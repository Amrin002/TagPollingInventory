package co.id.lintasarta.tagpollinginventory.data.repository

import android.content.Context
import android.util.Log
import co.id.lintasarta.tagpollinginventory.camera.calculateFileSha256
import co.id.lintasarta.tagpollinginventory.data.local.AppDatabase
import co.id.lintasarta.tagpollinginventory.data.model.*
import co.id.lintasarta.tagpollinginventory.ui.screens.deriveCodesFromSegmentName
import co.id.lintasarta.tagpollinginventory.network.AreaDto
import co.id.lintasarta.tagpollinginventory.network.RnimsApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    val areas: List<AreaDto> = RnimsApiClient.loadBundledAreas(context)

    private val _selectedAreaId = MutableStateFlow<Int>(18)
    val selectedAreaId: StateFlow<Int> = _selectedAreaId.asStateFlow()

    fun setAreaFilter(areaId: Int) {
        _selectedAreaId.value = areaId
    }

    init {
        if (oldDataFile.exists()) {
            oldDataFile.delete()
        }

        // Observasi daftar Segmen dari SQLite Room, difilter berdasarkan areaId operasional yang aktif (Group by Area ID)
        scope.launch {
            combine(dao.getAllSegmentsFlow(), _selectedAreaId) { allSegs, areaId ->
                val filtered = if (areaId > 0) allSegs.filter { it.areaId == areaId } else allSegs
                val totalPoles = filtered.sumOf { it.totalPoles }
                val completedPoles = filtered.sumOf { it.completedPoles }
                val conflictPoles = filtered.sumOf { it.conflictPoles }

                val currentArea = areas.find { it.areaId == areaId }
                val areaCode = currentArea?.code ?: "MPO"
                val areaName = currentArea?.name ?: "Maluku Papua Operation"

                val p = _project.value
                _project.value = p.copy(
                    id = "PRJ-$areaCode-ALPACA",
                    name = "Alpaca PRA - Regional Fiber Optic Survey",
                    location = "Area $areaId $areaName ($areaCode)",
                    segments = filtered,
                    totalPoles = totalPoles,
                    completedPoles = completedPoles,
                    conflictPoles = conflictPoles,
                    uncompletedPoles = (totalPoles - completedPoles).coerceAtLeast(0)
                )

                if (filtered.isNotEmpty() && (_selectedSegmentId.value.isEmpty() || filtered.none { it.id == _selectedSegmentId.value })) {
                    _selectedSegmentId.value = filtered.first().id
                }
            }.collect { }
        }

        // Observasi seluruh tiang dari SQLite Room
        scope.launch {
            dao.getAllPolesFlow().collect { poleList ->
                val healedPoles = poleList.map { it.withCalculatedStatus() }
                // Self-healing: jika tiang sudah ada foto & koordinat tapi status belum COMPLETED, update SQLite
                healedPoles.forEach { healed ->
                    val original = poleList.find { it.id == healed.id }
                    if (original != null && original.status != healed.status) {
                        scope.launch { dao.insertPole(healed) }
                    }
                }
                _poles.value = healedPoles.associateBy { it.id }
                recalculateProjectStats()
            }
        }

        // Observasi file export
        scope.launch {
            dao.getAllExportFilesFlow().collect { files ->
                _exportFiles.value = files
            }
        }

        // Auto-seed 2,505 segmen nasional jika tabel segments di Room masih kosong atau kurang dari 2,500
        scope.launch {
            val existingSegments = dao.getAllSegmentsSync()
            if (existingSegments.size < 2500) {
                seedAllSegments()
            }
        }
    }

    /**
     * Membaca dan menanam (seed) seluruh 2,505 segmen nasional dari assets/all_segments.json ke database Room.
     * Mengelompokkan segmen berdasarkan area_id dan memelihara data progres tiang yang sudah disurvei.
     */
    fun seedAllSegments(forceReload: Boolean = false) {
        scope.launch {
            val current = dao.getAllSegmentsSync()
            if (current.size >= 2500 && !forceReload) return@launch

            val bundledDtos = RnimsApiClient.loadBundledSegments(context)
            if (bundledDtos.isEmpty()) return@launch

            val existingMap = current.associateBy { it.id }
            val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
            val seededSegments = bundledDtos.map { dto ->
                val coords = dto.route_path?.map {
                    RouteCoordinate(longitude = it.lng, latitude = it.lat)
                } ?: emptyList()

                val segId = "SEG-${dto.id}"
                val existing = existingMap[segId]

                val cityCode = dto.org_site?.take(3)?.uppercase() ?: "TMK"
                val locationCode = dto.dst_site?.take(3)?.uppercase() ?: "PMK"
                val cityFilter = determineCityFilterKey(dto.org_city, dto.dst_city, dto.org_site)
                val priorityKey = determinePriorityRouteKey(dto.segment_name, dto.org_site, dto.dst_site)

                SegmentEntity(
                    id = segId,
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
                    poId = "PO-AREA${dto.area_id}-${dto.id}",
                    route = if (dto.org_site.isNullOrEmpty()) dto.segment_name else "${dto.org_site} -> ${dto.dst_site}",
                    description = "Segmen FO ${dto.org_site ?: ""} - ${dto.dst_site ?: ""}",
                    startPoint = if (coords.isNotEmpty()) Pair(coords.first().latitude, coords.first().longitude) else Pair(0.0, 0.0),
                    endPoint = if (coords.isNotEmpty()) Pair(coords.last().latitude, coords.last().longitude) else Pair(0.0, 0.0),
                    status = existing?.status ?: SegmentStatus.NOT_STARTED,
                    totalPoles = existing?.totalPoles ?: 0,
                    completedPoles = existing?.completedPoles ?: 0,
                    conflictPoles = existing?.conflictPoles ?: 0,
                    isPinned = existing?.isPinned ?: false,
                    cityCode = cityCode,
                    locationCode = locationCode,
                    currentSequence = existing?.currentSequence ?: 0,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            }

            dao.insertSegments(seededSegments)
            Log.i("InventoryRepository", "Seeded ${seededSegments.size} segments into Room successfully.")
        }
    }

    /**
     * Kompatibilitas mundur: memanggil seedAllSegments()
     */
    fun seedMpoSegments(forceReload: Boolean = false) {
        seedAllSegments(forceReload)
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
        val cityRaw = when {
            !orgCity.isNullOrBlank() -> orgCity
            !dstCity.isNullOrBlank() -> dstCity
            else -> ""
        }
        if (cityRaw.isNotBlank()) {
            val cleaned = cityRaw
                .replace("Kabupaten ", "", ignoreCase = true)
                .replace("Kota ", "", ignoreCase = true)
                .replace(" Regency", "", ignoreCase = true)
                .replace(" City", "", ignoreCase = true)
                .trim()
            if (cleaned.isNotBlank()) {
                return cleaned.split(" ").joinToString(" ") { word ->
                    word.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
                }
            }
        }

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
        val cleanId = segmentId.removePrefix("SEG-")
        val seg = _project.value.segments.find { it.id == segmentId || it.id.removePrefix("SEG-") == cleanId || it.name == segmentId }
        val segName = seg?.name ?: ""

        val matchingPoles = _poles.value.values.filter { 
            it.segmentId == segmentId || 
            it.segmentId == segName || 
            it.segmentId.removePrefix("SEG-") == cleanId
        }.map { it.withCalculatedStatus() }.sortedBy { it.sequence }

        val target = matchingPoles.firstOrNull { it.status == TagStatus.NOT_TAGGED } 
            ?: matchingPoles.firstOrNull()

        if (target != null) {
            _targetPoleId.value = target.id
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
        val cleanId = segmentId.removePrefix("SEG-")
        val segment = _project.value.segments.find { it.id == segmentId || it.id.removePrefix("SEG-") == cleanId || it.name == segmentId }
        val segName = segment?.name ?: ""
        val (autoCity, autoLoc) = deriveCodesFromSegmentName(segName)
        val cityCode = segment?.cityCode?.takeIf { it.isNotBlank() } ?: autoCity
        val locationCode = segment?.locationCode?.takeIf { it.isNotBlank() } ?: autoLoc

        val polesInSeg = _poles.value.values.filter { 
            it.segmentId == segmentId || 
            it.segmentId == segName || 
            it.segmentId.removePrefix("SEG-") == cleanId 
        }
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
        val baseList = if (current.photoPaths.isNotEmpty()) current.photoPaths
                       else if (!current.photoPath.isNullOrEmpty()) listOf(current.photoPath!!)
                       else emptyList()
        val currentSlots = normalizePhotoSlots(baseList)
        if (slotIndex in 0..9) {
            val oldPath = currentSlots[slotIndex]
            if (oldPath.isNotEmpty() && oldPath != photoPath) {
                try {
                    val oldFile = File(oldPath)
                    if (oldFile.exists()) oldFile.delete()
                } catch (_: Exception) {}
            }
            currentSlots[slotIndex] = photoPath
        }
        val primaryPhoto = currentSlots.firstOrNull { it.isNotEmpty() } ?: photoPath
        val sha256 = try {
            calculateFileSha256(File(primaryPhoto))
        } catch (_: Exception) {
            current.photoSha256
        }
        val updated = current.copy(
            photoPath = primaryPhoto,
            photoSha256 = sha256,
            photoPaths = currentSlots.toList()
        )
        _currentDraftPole.value = updated
        val updatedMap = _poles.value.toMutableMap()
        updatedMap[updated.id] = updated
        _poles.value = updatedMap
        scope.launch {
            dao.insertPole(updated)
        }
    }

    fun removeDraftPhotoFromSlot(slotIndex: Int) {
        val current = _currentDraftPole.value ?: return
        val baseList = if (current.photoPaths.isNotEmpty()) current.photoPaths
                       else if (!current.photoPath.isNullOrEmpty()) listOf(current.photoPath!!)
                       else emptyList()
        val currentSlots = normalizePhotoSlots(baseList)
        if (slotIndex in 0..9) {
            val oldSlotPath = currentSlots[slotIndex]
            if (oldSlotPath.isNotEmpty()) {
                try {
                    val file = File(oldSlotPath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
                currentSlots[slotIndex] = ""
            }
        }
        val remainingPhotos = currentSlots.filter { it.isNotEmpty() }
        val primaryPhoto = remainingPhotos.firstOrNull()
        val sha256 = primaryPhoto?.let {
            try { calculateFileSha256(File(it)) } catch (_: Exception) { null }
        }
        val updated = current.copy(
            photoPath = primaryPhoto,
            photoSha256 = sha256,
            photoPaths = currentSlots.toList()
        )
        _currentDraftPole.value = updated
        val updatedMap = _poles.value.toMutableMap()
        updatedMap[updated.id] = updated
        _poles.value = updatedMap
        scope.launch {
            dao.insertPole(updated)
        }
    }

    fun addDraftPhoto(photoPath: String) {
        val current = _currentDraftPole.value ?: return
        val currentSlots = normalizePhotoSlots(current.photoPaths)
        val targetIndex = currentSlots.indexOfFirst { it.isEmpty() }.let { if (it == -1) 0 else it }
        setDraftPhotoForSlot(targetIndex, photoPath)
    }

    fun addDraftPhotoWithNotes(photoPath: String, additionalNote: String) {
        val current = _currentDraftPole.value ?: return
        val updatedNotes = if (additionalNote.isNotBlank()) {
            if (current.notes.isEmpty()) "[Tambahan]: $additionalNote" else "${current.notes}\n[Tambahan]: $additionalNote"
        } else current.notes
        val currentSlots = normalizePhotoSlots(current.photoPaths)
        val targetIndex = currentSlots.indexOfFirst { it.isEmpty() }.let { if (it == -1) 0 else it }
        val oldPath = currentSlots[targetIndex]
        if (oldPath.isNotEmpty() && oldPath != photoPath) {
            try {
                val oldFile = File(oldPath)
                if (oldFile.exists()) oldFile.delete()
            } catch (_: Exception) {}
        }
        currentSlots[targetIndex] = photoPath
        val primaryPhoto = currentSlots.firstOrNull { it.isNotEmpty() } ?: photoPath
        val sha256 = try {
            calculateFileSha256(File(primaryPhoto))
        } catch (_: Exception) {
            current.photoSha256
        }
        val updated = current.copy(
            photoPath = primaryPhoto,
            photoSha256 = sha256,
            photoPaths = currentSlots.toList(),
            notes = updatedNotes
        )
        _currentDraftPole.value = updated
        val updatedMap = _poles.value.toMutableMap()
        updatedMap[updated.id] = updated
        _poles.value = updatedMap
        scope.launch {
            dao.insertPole(updated)
        }
    }

    fun removeDraftPhoto(photoPath: String) {
        val current = _currentDraftPole.value ?: return
        val slotIndex = current.photoPaths.indexOf(photoPath)
        if (slotIndex >= 0) {
            removeDraftPhotoFromSlot(slotIndex)
        } else if (current.photoPath == photoPath) {
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

        val calculatedStatus = when {
            draft.isSafetyDeferred -> TagStatus.CONFLICT
            draft.condition == PoleCondition.CRITICAL || draft.condition == PoleCondition.DAMAGED -> TagStatus.CONFLICT
            draft.isExistingPole || draft.syncStatus == "SYNCED" -> TagStatus.COMPLETED
            draft.latitude != 0.0 && (!draft.photoPath.isNullOrEmpty() || draft.photoPaths.any { it.isNotEmpty() }) -> TagStatus.COMPLETED
            else -> TagStatus.NOT_TAGGED
        }

        val preservedPhotoPaths = if (draft.photoPaths.isNotEmpty()) {
            draft.photoPaths
        } else if (!draft.photoPath.isNullOrEmpty()) {
            listOf(draft.photoPath!!)
        } else {
            emptyList()
        }

        val primaryPhoto = preservedPhotoPaths.firstOrNull { it.isNotEmpty() } ?: draft.photoPath

        val savedPole = draft.copy(
            capturedTimestamp = now,
            timestamp = now,
            syncStatus = "PENDING",
            photoPath = primaryPhoto,
            photoPaths = preservedPhotoPaths,
            status = calculatedStatus
        ).withCalculatedStatus()

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

        val calculatedStatus = when {
            isSafetyDeferred -> TagStatus.CONFLICT
            draft.condition == PoleCondition.CRITICAL || draft.condition == PoleCondition.DAMAGED -> TagStatus.CONFLICT
            isExistingPole || draft.syncStatus == "SYNCED" -> TagStatus.COMPLETED
            latitude != 0.0 && !photoPath.isNullOrEmpty() -> TagStatus.COMPLETED
            else -> TagStatus.NOT_TAGGED
        }

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
            isLocationLocked = true,
            photoPaths = if (!photoPath.isNullOrEmpty()) listOf(photoPath) else emptyList(),
            status = calculatedStatus
        ).withCalculatedStatus()

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
        val allPoles = dao.getAllPolesSync().map { it.withCalculatedStatus() }

        val updatedSegments = allSegments.map { seg ->
            val cleanSegId = seg.id.removePrefix("SEG-")
            val segPoles = allPoles.filter { 
                it.segmentId == seg.id || 
                it.segmentId == seg.name || 
                it.segmentId.removePrefix("SEG-") == cleanSegId 
            }
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
        if (_currentDraftPole.value?.id == poleId) {
            _currentDraftPole.value = null
        }
        if (_targetPoleId.value == poleId) {
            _targetPoleId.value = ""
        }
        val updated = _poles.value.toMutableMap()
        updated.remove(poleId)
        _poles.value = updated

        scope.launch {
            dao.deletePoleById(poleId)
            recalculateProjectStats()
        }
    }

    fun deletePoleById(poleId: String) = deletePole(poleId)

    /**
     * Memproses dan mengimpor Peta Aset Alpaca PRA (Rute + Tiang Eksisting) ke SQLite Room.
     * Bersifat universal dan data-driven untuk segmen manapun di semua Area ID.
     */
    suspend fun importSegmentAssetMap(
        mapData: AlpacaMapResponseDto,
        customSegmentId: String? = null
    ): Int = withContext(Dispatchers.IO) {
        val ift = mapData.ift
        val targetPoId = ift?.poId?.takeIf { it.isNotBlank() }
        val allSegments = dao.getAllSegmentsSync()

        // Cari segmen yang cocok berdasarkan ID atau PO ID atau Nama
        val matchedSegment = allSegments.find { seg ->
            (customSegmentId != null && (seg.id == customSegmentId || seg.id == "SEG-$customSegmentId")) ||
            (targetPoId != null && (seg.poId == targetPoId || seg.poId == "PO-$targetPoId" || seg.poId == "PO-MPO-$targetPoId" || seg.id == "SEG-$targetPoId")) ||
            (ift?.poName?.isNotBlank() == true && seg.name.contains(ift.poName, ignoreCase = true))
        }

        val effectiveSegmentId = matchedSegment?.id 
            ?: customSegmentId 
            ?: (if (targetPoId != null) "SEG-$targetPoId" else "SEG-${UUID.randomUUID().toString().take(6)}")

        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())

        // Jika rute pada segmen belum ada atau kosong, perbarui dengan geometri dari IFT
        val iftCoordinates: List<RouteCoordinate> = ift?.geometry?.toFlatRouteCoordinates() ?: emptyList()
        if (matchedSegment != null) {
            if (matchedSegment.routeCoordinates.isEmpty() && iftCoordinates.isNotEmpty()) {
                val updatedSegment = matchedSegment.copy(
                    routeCoordinates = iftCoordinates,
                    lengthMeters = if (matchedSegment.lengthMeters <= 0.0) (ift?.lengthPo ?: 0.0) else matchedSegment.lengthMeters,
                    updatedAt = now
                )
                dao.insertSegment(updatedSegment)
            }
        } else if (ift != null) {
            // Buat segmen baru jika belum ada di database
            val cityCode = ift.poName.take(3).uppercase().ifBlank { "MPO" }
            val newSegment = SegmentEntity(
                id = effectiveSegmentId,
                name = ift.poName.ifBlank { "Segmen PO $targetPoId" },
                lengthMeters = ift.lengthPo,
                routeCoordinates = iftCoordinates,
                areaId = 18,
                orgSite = ift.poName,
                dstSite = ift.poName,
                orgCity = ift.poName,
                dstCity = ift.poName,
                cityFilterKey = determineCityFilterKey(ift.poName, null, null),
                isAlpacaProject = true,
                priorityRouteKey = determinePriorityRouteKey(ift.poName, null, null),
                poId = targetPoId ?: effectiveSegmentId.removePrefix("SEG-"),
                route = ift.poName,
                description = "Segmen Fiber Optic PRA ${ift.poName}",
                startPoint = if (iftCoordinates.isNotEmpty()) Pair(iftCoordinates.first().latitude, iftCoordinates.first().longitude) else Pair(0.0, 0.0),
                endPoint = if (iftCoordinates.isNotEmpty()) Pair(iftCoordinates.last().latitude, iftCoordinates.last().longitude) else Pair(0.0, 0.0),
                status = SegmentStatus.NOT_STARTED,
                totalPoles = 0,
                completedPoles = 0,
                conflictPoles = 0,
                isPinned = false,
                cityCode = cityCode,
                locationCode = cityCode,
                currentSequence = 0,
                createdAt = now,
                updatedAt = now
            )
            dao.insertSegment(newSegment)
        }

        // Ambil tiang-tiang yang sudah ada di Room untuk segmen ini guna proteksi hasil survei surveyor
        val existingPolesInDb = dao.getPolesBySegmentSync(effectiveSegmentId).associateBy { it.id }

        var importedCount = 0
        val polesToInsert = mutableListOf<PoleEntity>()

        mapData.assets.forEachIndexed { index, asset ->
            val lat = asset.latitude
            val lng = asset.longitude
            if (lat == 0.0 && lng == 0.0) return@forEachIndexed

            val poleId = asset.assetUid.ifBlank { "POL-${effectiveSegmentId}-${String.format(Locale.US, "%04d", index + 1)}" }
            val existing = existingPolesInDb[poleId]

            // Jika tiang ini sudah disurvei/diisi foto oleh surveyor lapangan, JANGAN overwrite hasil survei!
            if (existing != null && existing.status == TagStatus.COMPLETED) {
                return@forEachIndexed
            }

            val derivedHeight = if (asset.assetClass?.contains("7") == true) "7m" else "9m"
            val derivedClass = asset.assetClass?.lowercase() ?: if (derivedHeight == "7m") "pole_7" else "pole_9"
            val equipmentList = parseEquipmentFromFeature(asset.featureName)
            val parsedSequence = extractPoleNumber(asset.featureName) ?: (index + 1)

            val poleEntity = PoleEntity(
                id = poleId,
                segmentId = effectiveSegmentId,
                tagNumber = asset.featureName ?: "Tiang $parsedSequence",
                latitude = lat,
                longitude = lng,
                originalLatitude = lat,
                originalLongitude = lng,
                rawLatitude = lat,
                rawLongitude = lng,
                accuracy = 1.0f,
                type = PoleType.STEEL,
                condition = PoleCondition.GOOD,
                ownership = PoleOwnership.LINTASARTA,
                height = derivedHeight,
                hasFoCable = true,
                cableCondition = CableCondition.GOOD,
                equipment = equipmentList.toSet(),
                photoPath = existing?.photoPath,
                photoSha256 = existing?.photoSha256,
                photoPaths = existing?.photoPaths ?: emptyList(),
                status = existing?.status ?: TagStatus.NOT_TAGGED,
                timestamp = existing?.timestamp ?: now,
                capturedTimestamp = existing?.capturedTimestamp ?: "",
                isLocationLocked = true,
                isExistingPole = true,
                syncStatus = existing?.syncStatus ?: "PENDING",
                notes = if (asset.sourceProximity?.isNotBlank() == true) "Proximity: ${asset.sourceProximity}" else "",
                assetClass = derivedClass,
                assetType = asset.assetType ?: "pole",
                matchStatus = asset.matchStatus?.uppercase() ?: "MATCHED",
                sequence = parsedSequence,
                missingReason = existing?.missingReason
            ).withCalculatedStatus()

            polesToInsert.add(poleEntity)
            importedCount++
        }

        if (polesToInsert.isNotEmpty()) {
            dao.insertPoles(polesToInsert)
        }

        recalculateProjectStats()
        return@withContext importedCount
    }

    fun parseEquipmentFromFeature(featureName: String?): List<String> {
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

    fun extractPoleNumber(featureName: String?): Int? {
        if (featureName.isNullOrBlank()) return null
        val match = Regex("\\d+").find(featureName)
        return match?.value?.toIntOrNull()
    }

    /**
     * Menarik tiang eksisting dari server untuk sebuah segmen tertentu secara on-demand.
     * Jika server tidak mengembalikan tiang (misal luar cakupan PRA seperti Ambon, Saumlaki, Tual),
     * segmen tetap tersimpan dengan 0 tiang, siap untuk tagging awal mandiri.
     */
    suspend fun pullPolesForSegment(
        baseUrl: String,
        segmentId: String,
        poId: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val seg = dao.getSegmentById(segmentId)
            val effectivePoId = poId 
                ?: seg?.poId?.takeIf { it.isNotBlank() } 
                ?: segmentId.removePrefix("SEG-")

            val mapResult = RnimsApiClient.fetchSegmentAssetMap(baseUrl, effectivePoId)
            if (mapResult.isSuccess) {
                val mapData = mapResult.getOrThrow()
                val count = importSegmentAssetMap(mapData, segmentId)
                Result.success(count)
            } else {
                Result.failure(mapResult.exceptionOrNull() ?: Exception("Gagal menarik data aset untuk PO ID $effectivePoId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Menandai tiang eksisting sebagai NIHIL / MISSING di lapangan.
     * Data tiang TIDAK dihapus dari database agar audit trail rekonsiliasi tetap utuh.
     */
    fun markPoleAsMissing(poleId: String, reason: String? = null) {
        val current = _poles.value[poleId] ?: return
        val updated = current.copy(
            matchStatus = "MISSING",
            status = TagStatus.MISSING,
            missingReason = reason ?: "Nihil Lapangan / Tidak Ditemukan"
        )
        val updatedMap = _poles.value.toMutableMap()
        updatedMap[poleId] = updated
        _poles.value = updatedMap

        if (_currentDraftPole.value?.id == poleId) {
            _currentDraftPole.value = updated
        }

        scope.launch {
            dao.updatePoleMissing(poleId, reason ?: "Nihil Lapangan / Tidak Ditemukan")
            recalculateProjectStats()
        }
    }

    /**
     * Memulihkan tiang yang sebelumnya ditandai NIHIL / MISSING kembali ke status semula.
     */
    fun restoreMissingPole(poleId: String) {
        val current = _poles.value[poleId] ?: return
        val updated = current.copy(
            matchStatus = if (current.isExistingPole) "MATCHED" else "PROVISIONAL",
            status = TagStatus.NOT_TAGGED,
            missingReason = null
        ).withCalculatedStatus()
        val updatedMap = _poles.value.toMutableMap()
        updatedMap[poleId] = updated
        _poles.value = updatedMap

        if (_currentDraftPole.value?.id == poleId) {
            _currentDraftPole.value = updated
        }

        scope.launch {
            dao.insertPole(updated)
            recalculateProjectStats()
        }
    }

    /**
     * Menambahkan tiang baru mandiri (Provisional Pole) di lapangan pada segmen mana pun.
     * Digunakan baik untuk segmen tanpa tiang eksisting (Ambon, Saumlaki, Tual)
     * maupun segmen PRA yang memiliki tiang tambahan baru di lapangan.
     */
    fun addProvisionalPole(
        segmentId: String,
        latitude: Double,
        longitude: Double,
        assetClass: String = "pole_9",
        cityCode: String = "MPO"
    ): PoleEntity {
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        val existingPolesInSeg = _poles.value.values.filter { 
            it.segmentId == segmentId || it.segmentId.removePrefix("SEG-") == segmentId.removePrefix("SEG-") 
        }
        val nextSeq = (existingPolesInSeg.maxOfOrNull { it.sequence } ?: 0) + 1
        val poleUid = "POL-$cityCode-${String.format(Locale.US, "%04d", nextSeq)}"

        val derivedHeight = if (assetClass == "pole_7") "7m" else "9m"

        val newPole = PoleEntity(
            id = poleUid,
            segmentId = segmentId,
            tagNumber = "Tiang $nextSeq",
            latitude = latitude,
            longitude = longitude,
            originalLatitude = latitude,
            originalLongitude = longitude,
            rawLatitude = latitude,
            rawLongitude = longitude,
            accuracy = 2.0f,
            type = PoleType.STEEL,
            condition = PoleCondition.GOOD,
            ownership = PoleOwnership.LINTASARTA,
            height = derivedHeight,
            hasFoCable = true,
            cableCondition = CableCondition.GOOD,
            equipment = emptySet(),
            photoPath = null,
            photoSha256 = null,
            photoPaths = emptyList(),
            status = TagStatus.NOT_TAGGED,
            timestamp = now,
            capturedTimestamp = "",
            isLocationLocked = false,
            isExistingPole = false,
            syncStatus = "PENDING",
            notes = "Tiang Baru Lapangan (Provisional)",
            assetClass = assetClass,
            assetType = "pole",
            matchStatus = "PROVISIONAL",
            sequence = nextSeq
        ).withCalculatedStatus()

        val updatedMap = _poles.value.toMutableMap()
        updatedMap[newPole.id] = newPole
        _poles.value = updatedMap
        _targetPoleId.value = newPole.id
        _currentDraftPole.value = newPole

        scope.launch {
            dao.insertPole(newPole)
            recalculateProjectStats()
        }

        return newPole
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
