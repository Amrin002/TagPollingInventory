package co.id.lintasarta.tagpollinginventory.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.id.lintasarta.tagpollinginventory.data.model.*
import co.id.lintasarta.tagpollinginventory.data.repository.InventoryRepository
import co.id.lintasarta.tagpollinginventory.data.repository.RouteRepository
import co.id.lintasarta.tagpollinginventory.export.ExportEngine
import co.id.lintasarta.tagpollinginventory.location.LocationData
import co.id.lintasarta.tagpollinginventory.location.LocationProvider
import co.id.lintasarta.tagpollinginventory.network.NetworkObserver
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.parser.KmlRouteParser
import co.id.lintasarta.tagpollinginventory.parser.KmzRouteParser
import co.id.lintasarta.tagpollinginventory.service.RouteDeviationService
import co.id.lintasarta.tagpollinginventory.service.MapDownloaderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTab {
    DASHBOARD,
    SEGMENTS,
    MAP,
    EXPORT,
    SETTINGS
}

enum class ScreenFlow {
    TAB_ROOT,
    CREATE_SEGMENT,
    SEGMENT_DETAIL,
    FIELD_MAP,
    GPS_CAPTURE,
    POLE_INFO,
    PHOTO_CAPTURE,
    REVIEW_POLE,
    SAVE_SUCCESS,
    INVENTORY_LIST,
    EXPORT_OPTIONS,
    EXPORT_PROGRESS,
    FILE_MANAGEMENT,
    IMPORT_ROUTE,
    IMPORT_PREVIEW
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = InventoryRepository(application)
    val routeRepository = RouteRepository(application)
    private val exportEngine = ExportEngine(application)
    val locationProvider = LocationProvider(application)
    val networkObserver = NetworkObserver(application)
    val networkStatus: StateFlow<NetworkStatus> = networkObserver.networkStatus

    private val kmlParser = KmlRouteParser()
    private val kmzParser = KmzRouteParser()
    private val deviationService = RouteDeviationService()
    private val mapDownloader = MapDownloaderService(application)

    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _currentScreen = MutableStateFlow(ScreenFlow.TAB_ROOT)
    val currentScreen: StateFlow<ScreenFlow> = _currentScreen.asStateFlow()

    // Navigation Stack for handling hardware and top bar back navigation
    private val screenStack = mutableListOf<ScreenFlow>()

    val project = repository.project
    val selectedSegmentId = repository.selectedSegmentId
    val targetPoleId = repository.targetPoleId
    val poles = repository.poles
    val exportFiles = repository.exportFiles
    val currentDraftPole = repository.currentDraftPole

    private val _pendingPhotoPath = MutableStateFlow<String?>(null)
    val pendingPhotoPath: StateFlow<String?> = _pendingPhotoPath.asStateFlow()

    // Route State
    val importedRoutes = routeRepository.importedRoutes
    val activeRoute = routeRepository.activeRoute

    private val _importPreviewData = MutableStateFlow<ImportPreviewData?>(null)
    val importPreviewData: StateFlow<ImportPreviewData?> = _importPreviewData.asStateFlow()

    private val _isParsingFile = MutableStateFlow(false)
    val isParsingFile: StateFlow<Boolean> = _isParsingFile.asStateFlow()

    // Real Location & Deviation Flow
    val currentLocation: StateFlow<LocationData> = locationProvider.currentLocation

    val routeDeviation: StateFlow<RouteDeviation> = combine(currentLocation, activeRoute) { loc, route ->
        if (route != null && loc.isAvailable) {
            deviationService.calculateDeviation(loc.latitude, loc.longitude, route.coordinates)
        } else {
            RouteDeviation(0f, DeviationStatus.NORMAL)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RouteDeviation(0f, DeviationStatus.NORMAL))

    // Filters & Search
    private val _segmentFilter = MutableStateFlow("ALL")
    val segmentFilter: StateFlow<String> = _segmentFilter.asStateFlow()

    private val _segmentSearchQuery = MutableStateFlow("")
    val segmentSearchQuery: StateFlow<String> = _segmentSearchQuery.asStateFlow()

    private val _poleSearchQuery = MutableStateFlow("")
    val poleSearchQuery: StateFlow<String> = _poleSearchQuery.asStateFlow()

    private val _poleStatusFilter = MutableStateFlow("ALL")
    val poleStatusFilter: StateFlow<String> = _poleStatusFilter.asStateFlow()

    // Export Flow State
    private val _selectedExportFormat = MutableStateFlow(ExportFormat.KMZ)
    val selectedExportFormat: StateFlow<ExportFormat> = _selectedExportFormat.asStateFlow()

    private val _exportOptions = MutableStateFlow(ExportOptions())
    val exportOptions: StateFlow<ExportOptions> = _exportOptions.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgressStep = MutableStateFlow(0)
    val exportProgressStep: StateFlow<Int> = _exportProgressStep.asStateFlow()

    private val _lastGeneratedExport = MutableStateFlow<ExportFile?>(null)
    val lastGeneratedExport: StateFlow<ExportFile?> = _lastGeneratedExport.asStateFlow()

    // Map Downloader State
    val isMapDownloading = mapDownloader.isDownloading
    val mapDownloadProgress = mapDownloader.progressPercent
    val mapDownloadStatus = mapDownloader.downloadStatus
    
    private val _mapCacheSizeMB = MutableStateFlow(0.0)
    val mapCacheSizeMB: StateFlow<Double> = _mapCacheSizeMB.asStateFlow()

    // GPS Status State
    private val _gpsAccuracy = MutableStateFlow(2.8f)
    val gpsAccuracy: StateFlow<Float> = _gpsAccuracy.asStateFlow()

    private val _gpsIsStable = MutableStateFlow(true)
    val gpsIsStable: StateFlow<Boolean> = _gpsIsStable.asStateFlow()

    init {
        viewModelScope.launch {
            locationProvider.currentLocation.collect { loc ->
                _gpsAccuracy.value = loc.accuracy
                _gpsIsStable.value = loc.accuracy <= 5.0f

                val draft = currentDraftPole.value
                if (draft != null && draft.status == TagStatus.NOT_TAGGED) {
                    repository.updateDraftLocation(loc.latitude, loc.longitude, loc.accuracy)
                }
            }
        }
    }

    fun switchTab(tab: AppTab) {
        _currentTab.value = tab
        _currentScreen.value = ScreenFlow.TAB_ROOT
        screenStack.clear()
    }

    fun navigateTo(screen: ScreenFlow, clearStack: Boolean = false) {
        if (clearStack) {
            screenStack.clear()
            _currentScreen.value = screen
        } else if (_currentScreen.value != screen) {
            screenStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (_currentScreen.value != ScreenFlow.TAB_ROOT) {
            if (screenStack.isNotEmpty()) {
                val previous = screenStack.removeAt(screenStack.lastIndex)
                _currentScreen.value = previous
            } else {
                _currentScreen.value = ScreenFlow.TAB_ROOT
            }
            return true
        } else if (_currentTab.value != AppTab.DASHBOARD) {
            _currentTab.value = AppTab.DASHBOARD
            screenStack.clear()
            return true
        }
        return false
    }

    // Route Import Workflow
    fun parseKmlOrKmzFile(fileUri: Uri, context: Context) {
        _isParsingFile.value = true
        navigateTo(ScreenFlow.IMPORT_PREVIEW)

        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                try {
                    val contentResolver = context.contentResolver
                    val fileName = getFileNameFromUri(contentResolver, fileUri) ?: "imported_route.kml"
                    val inputStream = contentResolver.openInputStream(fileUri) ?: return@withContext null

                    if (fileName.lowercase().endsWith(".kmz")) {
                        kmzParser.parseKmz(inputStream, fileName)
                    } else {
                        kmlParser.parseKml(inputStream, fileName, "KML")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            _importPreviewData.value = preview
            _isParsingFile.value = false
        }
    }

    fun confirmRouteImport(customProjectName: String, customSegmentName: String, cityCode: String = "", locationCode: String = "", startingSequence: Int = 0) {
        val preview = _importPreviewData.value ?: return
        val currentProj = project.value

        val projId = currentProj.id
        
        // Use the customSegmentName for the route name, fallback to preview routeName if empty
        val finalRouteName = if (customSegmentName.isNotEmpty()) customSegmentName else preview.routeName

        // We create a new segment with this imported route.
        val segId = "SEG-" + System.currentTimeMillis().toString().takeLast(6)
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        
        val newSegment = Segment(
            id = segId,
            projectId = projId,
            name = finalRouteName,
            description = "Created with imported route",
            createdAt = now,
            updatedAt = now,
            referenceRouteFileName = preview.fileName,
            cityCode = cityCode,
            locationCode = locationCode,
            currentSequence = startingSequence
        )
        
        repository.addSegment(newSegment)
        repository.selectSegment(segId)

        val routeId = "ROUTE-" + System.currentTimeMillis().toString().takeLast(6)
        val importedRoute = ImportedRoute(
            id = routeId,
            name = finalRouteName,
            sourceFileName = preview.fileName,
            sourceFileType = preview.fileType,
            projectId = projId,
            segmentId = segId,
            coordinates = preview.coordinates,
            referencePoles = preview.referencePoles
        )

        routeRepository.addAndActivateRoute(importedRoute)
        _importPreviewData.value = null
        screenStack.clear()
        _currentScreen.value = ScreenFlow.SEGMENT_DETAIL // Go to segment detail instead of Field Map directly
    }

    fun createSegment(name: String, description: String, cityCode: String = "", locationCode: String = "", startingSequence: Int = 0) {
        val projId = project.value.id
        val segId = "SEG-" + System.currentTimeMillis().toString().takeLast(6)
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        
        val newSegment = Segment(
            id = segId,
            projectId = projId,
            name = name,
            description = description,
            createdAt = now,
            updatedAt = now,
            cityCode = cityCode,
            locationCode = locationCode,
            currentSequence = startingSequence
        )
        repository.addSegment(newSegment)
        repository.selectSegment(segId)
        
        // Navigate to Segment Detail
        navigateTo(ScreenFlow.SEGMENT_DETAIL)
    }

    fun markActiveSegmentCompleted() {
        val segId = selectedSegmentId.value
        if (segId.isNotEmpty()) {
            repository.markSegmentCompleted(segId)
        }
    }

    fun deleteSelectedSegments(segmentIds: List<String>) {
        Log.d("DeleteTrace", "[ViewModel] Calling repository to delete IDs: $segmentIds")
        repository.deleteSegments(segmentIds)
        // Also cleanup routes associated with these segments
        val importedRoutesList = routeRepository.importedRoutes.value
        segmentIds.forEach { segId ->
            val route = importedRoutesList.find { it.segmentId == segId }
            if (route != null) {
                routeRepository.deleteRoute(route.id)
            }
        }
    }

    fun clearImportPreview() {
        _importPreviewData.value = null
        navigateBack()
    }

    private fun getFileNameFromUri(resolver: ContentResolver, uri: Uri): String? {
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) return it.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    fun startLocationUpdates() {
        locationProvider.startLocationUpdates()
    }

    fun stopLocationUpdates() {
        locationProvider.stopLocationUpdates()
    }

    fun selectSegment(segmentId: String, navigateToDetail: Boolean = true) {
        repository.selectSegment(segmentId)
        
        // Find if we have an imported route for this segment and activate it
        val matchingRoute = routeRepository.importedRoutes.value.find { it.segmentId == segmentId }
        if (matchingRoute != null) {
            routeRepository.setActiveRoute(matchingRoute.id)
        } else {
            routeRepository.clearActiveRoute()
        }

        if (navigateToDetail) {
            navigateTo(ScreenFlow.SEGMENT_DETAIL)
        }
    }

    fun createAndStartNewPole(segmentId: String? = null) {
        val segId = segmentId ?: selectedSegmentId.value
        val loc = currentLocation.value
        val newPole = repository.createNewPoleInSegment(segId, loc.latitude, loc.longitude)
        repository.startDraftForPole(newPole.id)
        navigateTo(ScreenFlow.FIELD_MAP)
    }

    fun startTaggingPole(poleId: String) {
        repository.setTargetPole(poleId)
        repository.startDraftForPole(poleId)
        navigateTo(ScreenFlow.FIELD_MAP)
    }

    fun proceedToGpsCapture() {
        startLocationUpdates()
        navigateTo(ScreenFlow.GPS_CAPTURE)
    }

    fun retryGpsCapture() {
        startLocationUpdates()
        repository.unlockDraftLocation()
        locationProvider.resetSampling()
        _gpsIsStable.value = true
        _gpsAccuracy.value = (2.1f + Math.random().toFloat() * 1.5f)
    }

    fun captureCoordinatesAndContinue() {
        val loc = currentLocation.value
        val draft = currentDraftPole.value
        val lat = if (loc.latitude != 0.0) loc.latitude else draft?.latitude ?: 0.0
        val lng = if (loc.longitude != 0.0) loc.longitude else draft?.longitude ?: 0.0
        val acc = if (loc.isAvailable) loc.accuracy else _gpsAccuracy.value

        repository.lockDraftLocation(lat, lng, acc)
        navigateTo(ScreenFlow.POLE_INFO)
    }

    fun updatePoleLocationManually(poleId: String, lat: Double, lng: Double) {
        repository.updatePoleLocationDirectly(poleId, lat, lng)
    }

    fun savePoleInfoAndContinue(
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
        repository.updateDraftAttributes(
            type, condition, ownership, height, tagNumber, hasFoCable, cableCondition, equipment, notes
        )
        navigateTo(ScreenFlow.PHOTO_CAPTURE)
    }

    fun stagePhotoForPreview(photoPath: String) {
        _pendingPhotoPath.value = photoPath
    }

    fun acceptPendingPhoto(additionalNotes: String = "") {
        val path = _pendingPhotoPath.value ?: return
        if (additionalNotes.isNotBlank()) {
            repository.addDraftPhotoWithNotes(path, additionalNotes)
        } else {
            repository.addDraftPhoto(path)
        }
        _pendingPhotoPath.value = null
    }

    fun discardPendingPhoto() {
        val path = _pendingPhotoPath.value
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        _pendingPhotoPath.value = null
    }

    fun removePhoto(photoPath: String) {
        repository.removeDraftPhoto(photoPath)
    }

    fun savePoleLocally() {
        val saved = repository.saveDraftPole()
        if (saved != null) {
            navigateTo(ScreenFlow.SAVE_SUCCESS)
        }
    }

    fun prepareNextPoleTagging() {
        val currentSegId = selectedSegmentId.value
        val allPolesInSeg = poles.value.values.filter { it.segmentId == currentSegId }
        val nextUntagged = allPolesInSeg.firstOrNull { it.status == TagStatus.NOT_TAGGED }
        if (nextUntagged != null) {
            startTaggingPole(nextUntagged.id)
        } else {
            createAndStartNewPole(currentSegId)
        }
    }

    // Filters
    fun setSegmentFilter(filter: String) {
        _segmentFilter.value = filter
    }

    fun setSegmentSearchQuery(query: String) {
        _segmentSearchQuery.value = query
    }

    fun setPoleSearchQuery(query: String) {
        _poleSearchQuery.value = query
    }

    fun setPoleStatusFilter(filter: String) {
        _poleStatusFilter.value = filter
    }

    // Export Workflow
    fun initiateExportFormat(format: ExportFormat) {
        _selectedExportFormat.value = format
        navigateTo(ScreenFlow.EXPORT_OPTIONS)
    }

    fun updateExportOptions(options: ExportOptions) {
        _exportOptions.value = options
    }

    fun startGeneratingExport() {
        navigateTo(ScreenFlow.EXPORT_PROGRESS)
        _isExporting.value = true
        _exportProgressStep.value = 1

        viewModelScope.launch {
            delay(400)
            _exportProgressStep.value = 2 // Generate attributes
            delay(500)
            _exportProgressStep.value = 3 // Package photos
            delay(500)
            _exportProgressStep.value = 4 // Create file
            delay(400)

            val currentProj = project.value
            val segId = selectedSegmentId.value
            val activeSeg = currentProj.segments.find { it.id == segId } ?: currentProj.segments.first()
            val allPoles = poles.value.values.toList()

            val targetPoles = when (_exportOptions.value.scope) {
                ExportScope.CURRENT_SEGMENT -> allPoles.filter { it.segmentId == segId }
                ExportScope.CURRENT_PROJECT -> allPoles
                ExportScope.ALL_LOCAL_DATA -> allPoles
            }

            val resultFile = exportEngine.generateExport(
                projectName = currentProj.name,
                segmentName = activeSeg.name,
                format = _selectedExportFormat.value,
                poles = targetPoles,
                options = _exportOptions.value,
                importedRoute = activeRoute.value
            )

            repository.addExportFile(resultFile)
            _lastGeneratedExport.value = resultFile
            _isExporting.value = false
        }
    }

    fun deleteExportFile(id: String) {
        repository.deleteExportFile(id)
    }

    fun clearAllData() {
        repository.clearAllData()
        routeRepository.clearAllRoutes()
        switchTab(AppTab.DASHBOARD)
    }

    // Map Downloader
    fun startMapDownload(centerLat: Double, centerLng: Double, radiusKm: Double) {
        viewModelScope.launch {
            mapDownloader.downloadMapArea(centerLat, centerLng, radiusKm = radiusKm)
            updateMapCacheSize()
        }
    }

    fun cancelMapDownload() {
        mapDownloader.cancelDownload()
    }

    fun clearMapCache() {
        viewModelScope.launch {
            mapDownloader.clearMapCache()
            updateMapCacheSize()
        }
    }

    fun updateMapCacheSize() {
        viewModelScope.launch {
            _mapCacheSizeMB.value = mapDownloader.getCacheSizeMB()
        }
    }
}
