package co.id.lintasarta.tagpollinginventory.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab {
    DASHBOARD,
    SEGMENTS,
    MAP,
    EXPORT,
    SETTINGS
}

enum class ScreenFlow {
    TAB_ROOT,
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

    // GPS Status State
    private val _gpsAccuracy = MutableStateFlow(2.8f)
    val gpsAccuracy: StateFlow<Float> = _gpsAccuracy.asStateFlow()

    private val _gpsIsStable = MutableStateFlow(true)
    val gpsIsStable: StateFlow<Boolean> = _gpsIsStable.asStateFlow()

    init {
        // Clear old sample data to ensure clean state
        repository.clearAllData()
        routeRepository.clearAllRoutes()

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

    fun navigateTo(screen: ScreenFlow) {
        if (_currentScreen.value != screen) {
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

    fun confirmRouteImport(customProjectName: String, customSegmentName: String) {
        val preview = _importPreviewData.value ?: return
        val currentProj = project.value

        val projId = currentProj.id
        val segId = selectedSegmentId.value

        val routeId = "ROUTE-" + System.currentTimeMillis().toString().takeLast(6)
        val importedRoute = ImportedRoute(
            id = routeId,
            name = if (preview.routeName.isNotEmpty()) preview.routeName else customSegmentName,
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
        _currentScreen.value = ScreenFlow.FIELD_MAP
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
        _gpsIsStable.value = true
        _gpsAccuracy.value = (2.1f + Math.random().toFloat() * 1.5f)
    }

    fun captureCoordinatesAndContinue() {
        val loc = currentLocation.value
        val draft = currentDraftPole.value
        if (draft != null) {
            repository.updateDraftLocation(
                if (loc.latitude != 0.0) loc.latitude else draft.latitude,
                if (loc.longitude != 0.0) loc.longitude else draft.longitude,
                _gpsAccuracy.value
            )
        }
        navigateTo(ScreenFlow.POLE_INFO)
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

    fun usePhotoAndContinue(photoPath: String) {
        repository.updateDraftPhoto(photoPath)
        navigateTo(ScreenFlow.REVIEW_POLE)
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
}
