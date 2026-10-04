package co.id.lintasarta.tagpollinginventory.data.repository

import android.content.Context
import co.id.lintasarta.tagpollinginventory.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InventoryRepository(private val context: Context) {

    private val dataFile = File(context.filesDir, "inventory_data.json")

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
        loadData()
    }

    private fun createInitialProject(): Project {
        val seg19 = Segment(
            id = "SEG-19",
            name = "Segment 19",
            route = "Jl. Pattimura → Teluk Ambon (JPA)",
            startPoint = Pair(-3.6954, 128.1814),
            endPoint = Pair(-3.6558, 128.1912),
            status = SegmentStatus.NOT_STARTED,
            totalPoles = 0,
            completedPoles = 0,
            conflictPoles = 0
        )

        val seg2212 = Segment(
            id = "SEG-2212",
            name = "Segment 2212",
            route = "Teluk Ambon → Bandara Pattimura",
            startPoint = Pair(-3.6558, 128.1912),
            endPoint = Pair(-3.7075, 128.0890),
            status = SegmentStatus.NOT_STARTED,
            totalPoles = 0,
            completedPoles = 0,
            conflictPoles = 0
        )

        val seg105 = Segment(
            id = "SEG-105",
            name = "Segment 105",
            route = "Wayame → Poka Substation",
            startPoint = Pair(-3.6500, 128.1750),
            endPoint = Pair(-3.6600, 128.1850),
            status = SegmentStatus.NOT_STARTED,
            totalPoles = 0,
            completedPoles = 0,
            conflictPoles = 0
        )

        val seg88 = Segment(
            id = "SEG-88",
            name = "Segment 88",
            route = "Ring Ambon Kota",
            startPoint = Pair(-3.6900, 128.1800),
            endPoint = Pair(-3.7000, 128.1900),
            status = SegmentStatus.NOT_STARTED,
            totalPoles = 0,
            completedPoles = 0,
            conflictPoles = 0
        )

        val segments = listOf(seg19, seg2212, seg105, seg88)

        return Project(
            id = "PRJ-AMB-01",
            name = "Ambon Sector 1",
            location = "Kota Ambon, Maluku",
            totalPoles = 0,
            completedPoles = 0,
            conflictPoles = 0,
            uncompletedPoles = 0,
            segments = segments
        )
    }

    private fun loadData() {
        if (!dataFile.exists()) {
            _poles.value = emptyMap()
            _exportFiles.value = emptyList()
            recalculateProjectStats()
            saveData()
            return
        }

        try {
            val text = dataFile.readText()
            val json = JSONObject(text)

            // Parse poles
            val polesJson = json.optJSONObject("poles")
            val loadedPoles = mutableMapOf<String, Pole>()
            if (polesJson != null) {
                val keys = polesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = polesJson.getJSONObject(key)
                    loadedPoles[key] = Pole(
                        id = obj.getString("id"),
                        segmentId = obj.getString("segmentId"),
                        sequence = obj.getInt("sequence"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        accuracy = obj.getDouble("accuracy").toFloat(),
                        capturedTimestamp = obj.optString("capturedTimestamp", ""),
                        type = PoleType.valueOf(obj.optString("type", "CONCRETE")),
                        condition = PoleCondition.valueOf(obj.optString("condition", "GOOD")),
                        ownership = PoleOwnership.valueOf(obj.optString("ownership", "LINTASARTA")),
                        height = obj.optString("height", "9m"),
                        tagNumber = obj.optString("tagNumber", ""),
                        hasFoCable = obj.optBoolean("hasFoCable", true),
                        cableCondition = CableCondition.valueOf(obj.optString("cableCondition", "GOOD")),
                        equipment = parseEquipmentSet(obj.optJSONArray("equipment")),
                        notes = obj.optString("notes", ""),
                        photoPath = if (obj.has("photoPath") && !obj.isNull("photoPath")) obj.getString("photoPath") else null,
                        status = TagStatus.valueOf(obj.optString("status", "NOT_TAGGED"))
                    )
                }
            }
            _poles.value = loadedPoles

            // Parse export files
            val exportJson = json.optJSONArray("exportFiles")
            val loadedExports = mutableListOf<ExportFile>()
            if (exportJson != null) {
                for (i in 0 until exportJson.length()) {
                    val obj = exportJson.getJSONObject(i)
                    loadedExports.add(
                        ExportFile(
                            id = obj.getString("id"),
                            fileName = obj.getString("fileName"),
                            format = ExportFormat.valueOf(obj.getString("format")),
                            sizeBytes = obj.getLong("sizeBytes"),
                            recordCount = obj.getInt("recordCount"),
                            createdAt = obj.getString("createdAt"),
                            filePath = obj.getString("filePath"),
                            segmentName = obj.optString("segmentName", "Segment 19")
                        )
                    )
                }
            }
            _exportFiles.value = loadedExports

            recalculateProjectStats()
        } catch (e: Exception) {
            e.printStackTrace()
            _poles.value = emptyMap()
            _exportFiles.value = emptyList()
        }
    }

    private fun parseEquipmentSet(arr: JSONArray?): Set<String> {
        if (arr == null) return setOf("ODP", "Closure")
        val set = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            set.add(arr.getString(i))
        }
        return set
    }

    fun saveData() {
        try {
            val json = JSONObject()

            val polesJson = JSONObject()
            _poles.value.forEach { (id, pole) ->
                val obj = JSONObject()
                obj.put("id", pole.id)
                obj.put("segmentId", pole.segmentId)
                obj.put("sequence", pole.sequence)
                obj.put("latitude", pole.latitude)
                obj.put("longitude", pole.longitude)
                obj.put("accuracy", pole.accuracy)
                obj.put("capturedTimestamp", pole.capturedTimestamp)
                obj.put("type", pole.type.name)
                obj.put("condition", pole.condition.name)
                obj.put("ownership", pole.ownership.name)
                obj.put("height", pole.height)
                obj.put("tagNumber", pole.tagNumber)
                obj.put("hasFoCable", pole.hasFoCable)
                obj.put("cableCondition", pole.cableCondition.name)

                val eqArr = JSONArray()
                pole.equipment.forEach { eqArr.put(it) }
                obj.put("equipment", eqArr)

                obj.put("notes", pole.notes)
                obj.put("photoPath", pole.photoPath)
                obj.put("status", pole.status.name)

                polesJson.put(id, obj)
            }
            json.put("poles", polesJson)

            val exportArr = JSONArray()
            _exportFiles.value.forEach { file ->
                val obj = JSONObject()
                obj.put("id", file.id)
                obj.put("fileName", file.fileName)
                obj.put("format", file.format.name)
                obj.put("sizeBytes", file.sizeBytes)
                obj.put("recordCount", file.recordCount)
                obj.put("createdAt", file.createdAt)
                obj.put("filePath", file.filePath)
                obj.put("segmentName", file.segmentName)
                exportArr.put(obj)
            }
            json.put("exportFiles", exportArr)

            dataFile.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
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
        }
    }

    fun createNewPoleInSegment(segmentId: String, currentLat: Double, currentLng: Double): Pole {
        val polesInSeg = _poles.value.values.filter { it.segmentId == segmentId }
        val nextSeq = polesInSeg.size + 1
        val numStr = String.format(Locale.US, "%03d", nextSeq)
        val cleanSegCode = segmentId.replace("SEG-", "").replace("SEG_", "")
        val poleId = "P-$cleanSegCode-$numStr"

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
            tagNumber = "TAG-LTA-$poleId",
            hasFoCable = true,
            cableCondition = CableCondition.GOOD,
            equipment = setOf("ODP", "Closure"),
            notes = "",
            photoPath = null,
            status = TagStatus.NOT_TAGGED
        )

        _currentDraftPole.value = newPole
        _targetPoleId.value = poleId
        _selectedSegmentId.value = segmentId
        return newPole
    }

    fun startDraftForPole(poleId: String) {
        val existing = _poles.value[poleId] ?: return
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        _currentDraftPole.value = existing.copy(
            capturedTimestamp = if (existing.capturedTimestamp.isEmpty()) now else existing.capturedTimestamp,
            accuracy = 2.8f
        )
    }

    fun updateDraftLocation(lat: Double, lng: Double, accuracy: Float) {
        val current = _currentDraftPole.value ?: return
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        _currentDraftPole.value = current.copy(
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            capturedTimestamp = now
        )
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
        val current = _currentDraftPole.value ?: return
        _currentDraftPole.value = current.copy(
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
    }

    fun updateDraftPhoto(photoPath: String) {
        val current = _currentDraftPole.value ?: return
        _currentDraftPole.value = current.copy(photoPath = photoPath)
    }

    fun saveDraftPole(): Pole? {
        val draft = _currentDraftPole.value ?: return null
        val now = if (draft.capturedTimestamp.isEmpty()) {
            SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        } else draft.capturedTimestamp

        val finalStatus = if (draft.condition == PoleCondition.CRITICAL) TagStatus.CONFLICT else TagStatus.COMPLETED

        val savedPole = draft.copy(
            capturedTimestamp = now,
            status = finalStatus
        )

        val map = _poles.value.toMutableMap()
        map[savedPole.id] = savedPole
        _poles.value = map

        recalculateProjectStats()
        saveData()
        return savedPole
    }

    private fun recalculateProjectStats() {
        val currentProj = _project.value
        val polesMap = _poles.value

        val updatedSegments = currentProj.segments.map { seg ->
            val segPoles = polesMap.values.filter { it.segmentId == seg.id }
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

        val totalPoles = updatedSegments.sumOf { it.totalPoles }
        val completedPoles = updatedSegments.sumOf { it.completedPoles }
        val conflictPoles = updatedSegments.sumOf { it.conflictPoles }

        _project.value = currentProj.copy(
            totalPoles = totalPoles,
            completedPoles = completedPoles,
            conflictPoles = conflictPoles,
            uncompletedPoles = totalPoles - completedPoles,
            segments = updatedSegments
        )
    }

    fun addExportFile(file: ExportFile) {
        val list = _exportFiles.value.toMutableList()
        list.add(0, file)
        _exportFiles.value = list
        saveData()
    }

    fun deleteExportFile(fileId: String) {
        val list = _exportFiles.value.filter { it.id != fileId }
        _exportFiles.value = list
        saveData()
    }

    fun clearAllData() {
        dataFile.delete()
        _poles.value = emptyMap()
        _exportFiles.value = emptyList()
        _targetPoleId.value = ""
        _currentDraftPole.value = null
        recalculateProjectStats()
        saveData()
    }
}
