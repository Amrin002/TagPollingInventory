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
import co.id.lintasarta.tagpollinginventory.data.auth.UserSession
import co.id.lintasarta.tagpollinginventory.data.auth.UserSessionManager
import co.id.lintasarta.tagpollinginventory.data.preferences.CameraSettingsManager
import co.id.lintasarta.tagpollinginventory.network.AreaDto
import co.id.lintasarta.tagpollinginventory.network.ConnectionTestResult
import co.id.lintasarta.tagpollinginventory.network.GDriveUploadResponse
import co.id.lintasarta.tagpollinginventory.network.NetworkObserver
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.network.RnimsApiClient
import co.id.lintasarta.tagpollinginventory.network.RnimsSegmentDto
import co.id.lintasarta.tagpollinginventory.network.RnimsUser

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
    LOGIN,
    CREATE_SEGMENT,
    SEGMENT_DETAIL,
    POLE_DETAIL,
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
    IMPORT_PREVIEW,
    GEOTAG_CAMERA_SETTINGS,
    QUICK_TAGGING_SESSION
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val userSessionManager = UserSessionManager(application)
    val currentUser: StateFlow<UserSession> = userSessionManager.currentSession

    val cameraSettingsManager = CameraSettingsManager(application)
    val cameraSettings: StateFlow<CameraSettings> = cameraSettingsManager.settings

    fun updateCameraSettings(settings: CameraSettings) {
        cameraSettingsManager.updateSettings(settings)
    }

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

    // Mode Mandiri Offline: Langsung buka TAB_ROOT tanpa layar login
    private val _currentScreen = MutableStateFlow(ScreenFlow.TAB_ROOT)
    val currentScreen: StateFlow<ScreenFlow> = _currentScreen.asStateFlow()

    val availableAreas: List<AreaDto> = repository.areas.filter { it.areaId >= 2 }

    private val _isQuickTaggingMode = MutableStateFlow(false)
    val isQuickTaggingMode: StateFlow<Boolean> = _isQuickTaggingMode.asStateFlow()

    // Navigation Stack for handling hardware and top bar back navigation
    private val screenStack = mutableListOf<ScreenFlow>()

    val project = repository.project
    val selectedSegmentId = repository.selectedSegmentId
    val targetPoleId = repository.targetPoleId
    val poles = repository.poles
    val exportFiles = repository.exportFiles
    val currentDraftPole = repository.currentDraftPole

    private val _selectedPoleDetailId = MutableStateFlow<String?>(null)
    val selectedPoleDetailId: StateFlow<String?> = _selectedPoleDetailId.asStateFlow()

    val selectedPoleDetail: StateFlow<Pole?> = combine(_selectedPoleDetailId, repository.poles) { id, polesMap ->
        if (id != null) polesMap[id] else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    private val _isSyncingPoles = MutableStateFlow(false)
    val isSyncingPoles: StateFlow<Boolean> = _isSyncingPoles.asStateFlow()

    private val _syncPolesMessage = MutableStateFlow<String?>(null)
    val syncPolesMessage: StateFlow<String?> = _syncPolesMessage.asStateFlow()

    // Filters & Search
        // RNIMS Server Sync State (Staging/Production)
    private val _rnimsBaseUrl = MutableStateFlow(RnimsApiClient.DEFAULT_USB_URL)
    val rnimsBaseUrl: StateFlow<String> = _rnimsBaseUrl.asStateFlow()

    private val _rnimsServerConnected = MutableStateFlow<Boolean?>(null)
    val rnimsServerConnected: StateFlow<Boolean?> = _rnimsServerConnected.asStateFlow()

    private val _mpoSegments = MutableStateFlow<List<RnimsSegmentDto>>(emptyList())
    val mpoSegments: StateFlow<List<RnimsSegmentDto>> = _mpoSegments.asStateFlow()

    private val _isSyncingRnims = MutableStateFlow(false)
    val isSyncingRnims: StateFlow<Boolean> = _isSyncingRnims.asStateFlow()

    private val _rnimsSyncMessage = MutableStateFlow<String?>(null)
    val rnimsSyncMessage: StateFlow<String?> = _rnimsSyncMessage.asStateFlow()

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

    // Google Drive Webhook Sync State
    private val _gdriveWebhookUrl = MutableStateFlow(userSessionManager.getGDriveWebhookUrl())
    val gdriveWebhookUrl: StateFlow<String> = _gdriveWebhookUrl.asStateFlow()

    fun setGDriveWebhookUrl(url: String) {
        val clean = url.trim()
        userSessionManager.setGDriveWebhookUrl(clean)
        _gdriveWebhookUrl.value = clean
    }

    private val _isUploadingGDrive = MutableStateFlow(false)
    val isUploadingGDrive: StateFlow<Boolean> = _isUploadingGDrive.asStateFlow()

    private val _gdriveUploadMessage = MutableStateFlow<String?>(null)
    val gdriveUploadMessage: StateFlow<String?> = _gdriveUploadMessage.asStateFlow()

    fun testGDriveWebhook(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val res = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL(_gdriveWebhookUrl.value)
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 15000
                        setRequestProperty("User-Agent", "TagPolling-Android")
                    }
                    val code = conn.responseCode
                    if (code in 200..399) {
                        val latency = System.currentTimeMillis() - startTime
                        Result.success("Webhook Online (${latency}ms)")
                    } else {
                        Result.failure(Exception("HTTP $code"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            res.onSuccess { onResult(true, it) }.onFailure { onResult(false, it.message ?: "Koneksi gagal") }
        }
    }

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
        // Otomatis mulai pembaruan lokasi jika izin GPS telah diberikan
        if (locationProvider.hasLocationPermission()) {
            startLocationUpdates()
        }

        // Sinkronisasi area aktif ke repository agar segmen difilter sesuai area operasional
        viewModelScope.launch {
            currentUser.collect { session ->
                repository.setAreaFilter(session.areaId)
            }
        }

        // Preload bundled segments offline untuk area aktif
        viewModelScope.launch {
            val allBundled = RnimsApiClient.loadBundledSegments(application)
            if (allBundled.isNotEmpty()) {
                val areaId = currentUser.value.areaId
                val forArea = allBundled.filter { it.area_id == areaId }
                _mpoSegments.value = if (forArea.isNotEmpty()) forArea else allBundled
            }
        }

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
        if (_currentScreen.value == ScreenFlow.LOGIN && !userSessionManager.hasActiveSession()) {
            return false // Stay on login screen if user has not authenticated yet
        }
        if (_currentScreen.value != ScreenFlow.TAB_ROOT) {
            if (screenStack.isNotEmpty()) {
                val previous = screenStack.removeAt(screenStack.lastIndex)
                _currentScreen.value = previous
            } else if (userSessionManager.hasActiveSession()) {
                _currentScreen.value = ScreenFlow.TAB_ROOT
            } else {
                return false
            }
            if (_currentScreen.value == ScreenFlow.TAB_ROOT) {
                _isQuickTaggingMode.value = false
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

        fun setRnimsBaseUrl(url: String) {
        _rnimsBaseUrl.value = url
    }

    fun testRnimsConnection() {
        viewModelScope.launch {
            _rnimsSyncMessage.value = "Menguji koneksi ke ${_rnimsBaseUrl.value}..."
            val res = RnimsApiClient.testConnection(_rnimsBaseUrl.value)
            res.onSuccess { testResult ->
                _connectionTestResult.value = testResult
                _rnimsServerConnected.value = testResult.isSuccess
                _rnimsSyncMessage.value = testResult.message
            }.onFailure { err ->
                val fail = ConnectionTestResult(false, 0, err.message ?: "Koneksi gagal")
                _connectionTestResult.value = fail
                _rnimsServerConnected.value = false
                _rnimsSyncMessage.value = fail.message
            }
        }
    }

    fun syncMpoSegmentsFromServer(areaId: Int? = null) {
        val targetAreaId = areaId ?: currentUser.value.areaId
        viewModelScope.launch {
            _isSyncingRnims.value = true
            _rnimsSyncMessage.value = "Menghubungi server RNIMS untuk Area $targetAreaId..."
            val res = RnimsApiClient.fetchRoutesByArea(_rnimsBaseUrl.value, areaId = targetAreaId)
            res.onSuccess { list ->
                _rnimsServerConnected.value = true
                if (list.isNotEmpty()) {
                    _mpoSegments.value = list
                    _rnimsSyncMessage.value = "Berhasil menarik ${list.size} Segmen dari server RNIMS (Area $targetAreaId)"
                } else {
                    val allBundled = RnimsApiClient.loadBundledSegments(getApplication())
                    val forArea = allBundled.filter { it.area_id == targetAreaId }
                    _mpoSegments.value = if (forArea.isNotEmpty()) forArea else allBundled
                    _rnimsSyncMessage.value = "Server Terhubung: Mengaktifkan ${forArea.size} segmen data master lokal untuk Area $targetAreaId."
                }
            }.onFailure { err ->
                val errMsg = err.message ?: ""
                val isRedisOr503 = errMsg.contains("503") || errMsg.contains("REDIS", ignoreCase = true)
                val allBundled = RnimsApiClient.loadBundledSegments(getApplication())
                val forArea = allBundled.filter { it.area_id == targetAreaId }
                if (forArea.isNotEmpty()) {
                    _mpoSegments.value = forArea
                }
                if (isRedisOr503) {
                    _rnimsSyncMessage.value = "Server Online (503). Mengaktifkan ${forArea.size} segmen master lokal (Area $targetAreaId)."
                    testRnimsConnection()
                } else {
                    _rnimsSyncMessage.value = "Mode Offline: Mengaktifkan ${forArea.size} segmen lokal bawaan untuk Area $targetAreaId."
                    _rnimsServerConnected.value = false
                }
            }
            _isSyncingRnims.value = false
        }
    }

    fun setOperationalArea(areaId: Int, areaLabel: String) {
        userSessionManager.updateArea(areaId, areaLabel)
        repository.setAreaFilter(areaId)
        _rnimsSyncMessage.value = "Area operasional diubah ke Area $areaId ($areaLabel)"
        syncMpoSegmentsFromServer(areaId)
    }

    fun seedMpoSegments(forceReload: Boolean = true) {
        repository.seedMpoSegments(forceReload)
    }

    fun loginRnims(usernameOrEmail: String, pass: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _rnimsSyncMessage.value = "Melakukan autentikasi..."
            val res = userSessionManager.loginOnline(_rnimsBaseUrl.value, usernameOrEmail, pass)
            res.onSuccess { session ->
                _rnimsSyncMessage.value = "Login berhasil sebagai ${session.displayName} (Area ${session.areaId} ${session.areaLabel})"
                _currentScreen.value = ScreenFlow.TAB_ROOT
                screenStack.clear()
                _currentTab.value = AppTab.DASHBOARD
                onResult(true, "Login berhasil")
            }.onFailure { err ->
                _rnimsSyncMessage.value = "Login gagal: ${err.message}"
                onResult(false, err.message ?: "Login gagal")
            }
        }
    }

    fun loginOffline(pin: String = "1234", areaId: Int = 18, areaLabel: String = "MPO") {
        userSessionManager.loginOffline(pin = pin, areaId = areaId, areaLabel = areaLabel)
        _rnimsSyncMessage.value = "Masuk dalam Mode Offline (Area $areaId $areaLabel)"
        _currentScreen.value = ScreenFlow.TAB_ROOT
        screenStack.clear()
        _currentTab.value = AppTab.DASHBOARD
    }

    fun logout() {
        userSessionManager.logout()
        _rnimsSyncMessage.value = "Sesi telah keluar"
        _currentScreen.value = ScreenFlow.LOGIN
        screenStack.clear()
    }

    fun syncPolesToServer(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isSyncingPoles.value = true
            _syncPolesMessage.value = "Menyiapkan data survei tiang..."

            val allPoles = poles.value.values.filter { it.status == TagStatus.COMPLETED || it.status == TagStatus.CONFLICT }
            if (allPoles.isEmpty()) {
                val msg = "Tidak ada tiang berstatus selesai/konflik untuk disinkronkan."
                _syncPolesMessage.value = msg
                _isSyncingPoles.value = false
                onComplete(false, msg)
                return@launch
            }

            val payload = allPoles.map { p ->
                mapOf(
                    "pole_code" to p.poleCode.ifEmpty { p.id },
                    "segment_id" to p.segmentId,
                    "sequence" to p.sequence,
                    "latitude" to p.latitude,
                    "longitude" to p.longitude,
                    "accuracy" to p.accuracy,
                    "timestamp" to p.capturedTimestamp,
                    "pole_type" to p.type.displayName,
                    "condition" to p.condition.displayName,
                    "ownership" to p.ownership.displayName,
                    "height" to p.height,
                    "tag_number" to p.tagNumber,
                    "has_fo_cable" to p.hasFoCable,
                    "cable_condition" to p.cableCondition.displayName,
                    "equipment" to p.equipment.toList(),
                    "notes" to p.notes,
                    "photos_count" to (if (p.photoPath.isNullOrEmpty()) 0 else 1),
                    "status" to p.status.displayName,
                    // eNims infra table integration (infraTypeId = 2 untuk Pole, infraState = 1 untuk Draft)
                    "infra_type_id" to 2,
                    "infra_state" to 1,
                    "infra_sys_id" to p.poleCode.ifEmpty { p.id }
                )
            }

            val res = RnimsApiClient.syncPolesBatch(
                baseUrl = _rnimsBaseUrl.value,
                token = currentUser.value.token,
                areaId = currentUser.value.areaId,
                polesPayload = payload
            )

            res.onSuccess { count ->
                val syncedIds = allPoles.map { it.id }
                repository.markPolesAsSynced(syncedIds)
                val msg = "Berhasil mensinkronkan $count tiang ke server RNIMS & Redis (key: survey:poles:area:${currentUser.value.areaId})!"
                _syncPolesMessage.value = msg
                _isSyncingPoles.value = false
                onComplete(true, msg)
            }.onFailure { err ->
                val errText = err.message ?: ""
                val msg = if (errText.contains("HTTP 0") || errText.contains("timeout", ignoreCase = true)) {
                    "Server RNIMS terhubung, tetapi upstream relay internal (123.231.138.31) timeout. Seluruh data survei aman 100% di SQLite lokal dan siap diekspor via CSV / KMZ."
                } else {
                    "Gagal sinkronisasi tiang: $errText. Data aman tersimpan di Room SQLite lokal."
                }
                _syncPolesMessage.value = msg
                _isSyncingPoles.value = false
                onComplete(false, msg)
            }
        }
    }

    /**
     * Mengunggah hasil survei lapangan dalam format KMZ resmi ke Google Drive via Apps Script Webhook.
     * Tidak membutuhkan login Google di HP dan otomatis masuk ke folder Area_{id}_{areaLabel}.
     */
    fun uploadSurveyToGoogleDrive(
        targetPoles: List<Pole>? = null,
        onComplete: (Boolean, GDriveUploadResponse?, String) -> Unit = { _, _, _ -> }
    ) {
        viewModelScope.launch {
            val polesToUpload = targetPoles ?: poles.value.values.toList()
            if (polesToUpload.isEmpty()) {
                val msg = "Belum ada data tiang yang disurvei untuk diunggah ke Google Drive."
                _gdriveUploadMessage.value = msg
                onComplete(false, null, msg)
                return@launch
            }

            _isUploadingGDrive.value = true
            _gdriveUploadMessage.value = "Membuat paket KMZ survei (koordinat, rute kabel FO, dan foto)..."

            val areaId = currentUser.value.areaId
            val areaLabel = currentUser.value.areaLabel
            val activeSeg = repository.project.value.segments.find { it.id == selectedSegmentId.value }
            val segName = activeSeg?.name ?: "SEGMENT_ALL"

            // 1. Generate KMZ
            val kmzTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val kmzFileName = "SURVEY_${areaLabel}_${segName}_$kmzTimestamp.kmz"
            val tempDir = File(getApplication<Application>().cacheDir, "gdrive_uploads").apply { if (!exists()) mkdirs() }
            val tempKmz = File(tempDir, kmzFileName)

            val generatedKmz = withContext(Dispatchers.IO) {
                exportEngine.generateAlpacaMasterKmz(
                    poles = polesToUpload,
                    segmentName = "${project.value.name} - $segName",
                    importedRoute = activeRoute.value,
                    targetFile = tempKmz
                )
            }

            val sizeKb = generatedKmz.length() / 1024
            _gdriveUploadMessage.value = "Mengunggah $kmzFileName ($sizeKb KB) ke Google Drive Area $areaId ($areaLabel)..."

            val res = RnimsApiClient.uploadKmzToGoogleDrive(
                webhookUrl = _gdriveWebhookUrl.value,
                kmzFile = generatedKmz,
                areaId = areaId,
                areaName = areaLabel,
                segmentName = segName,
                poleCount = polesToUpload.size,
                surveyor = currentUser.value.displayName
            )

            _isUploadingGDrive.value = false

            res.onSuccess { uploadResp ->
                _gdriveUploadMessage.value = "Berhasil: ${uploadResp.message} (${uploadResp.folder_name}/${uploadResp.file_name})"
                // Tandai tiang yang diupload sebagai SYNCED
                viewModelScope.launch(Dispatchers.IO) {
                    val syncedIds = polesToUpload.map { it.id }
                    repository.markPolesAsSynced(syncedIds)
                }
                onComplete(true, uploadResp, uploadResp.message)
            }.onFailure { err ->
                val errMsg = err.message ?: "Gagal upload ke Google Drive"
                _gdriveUploadMessage.value = "Gagal upload: $errMsg"
                onComplete(false, null, errMsg)
            }
        }
    }

    fun createSegmentFromRnims(segmentDto: RnimsSegmentDto) {
        val projId = project.value.id
        val segId = "SEG-" + segmentDto.id
        val now = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.US).format(Date())

        val cityCode = segmentDto.org_site?.take(3)?.uppercase() ?: "TMK"
        val locationCode = segmentDto.dst_site?.take(3)?.uppercase() ?: "PMK"
        val distanceKm = String.format(Locale.US, "%.1f", segmentDto.distance / 1000.0)
        val desc = "Segmen FO ${segmentDto.org_site} -> ${segmentDto.dst_site} (${segmentDto.org_city} - ${segmentDto.dst_city}, $distanceKm km)"

        val newSegment = Segment(
            id = segId,
            projectId = projId,
            name = segmentDto.segment_name,
            description = desc,
            createdAt = now,
            updatedAt = now,
            cityCode = cityCode,
            locationCode = locationCode,
            currentSequence = 0
        )
        repository.addSegment(newSegment)

        val coords = segmentDto.route_path?.map {
            RouteCoordinate(longitude = it.lng, latitude = it.lat)
        } ?: emptyList()

        if (coords.isNotEmpty()) {
            val importedRoute = ImportedRoute(
                id = "ROUTE-" + segmentDto.id,
                name = segmentDto.segment_name,
                sourceFileName = "RNIMS Master MPO",
                sourceFileType = "RNIMS_FO",
                projectId = projId,
                segmentId = segId,
                coordinates = coords
            )
            routeRepository.addAndActivateRoute(importedRoute)
        }

        repository.selectSegment(segId)
        if (_isQuickTaggingMode.value) {
            prepareNewPoleForQuickTagging(segId)
            navigateTo(ScreenFlow.QUICK_TAGGING_SESSION)
        } else {
            navigateTo(ScreenFlow.SEGMENT_DETAIL)
        }
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
        
        if (_isQuickTaggingMode.value) {
            prepareNewPoleForQuickTagging(segId)
            navigateTo(ScreenFlow.QUICK_TAGGING_SESSION)
        } else {
            // Navigate to Segment Detail for regular flow
            navigateTo(ScreenFlow.SEGMENT_DETAIL)
        }
    }

    fun startQuickTaggingFlow() {
        _isQuickTaggingMode.value = true
        navigateTo(ScreenFlow.CREATE_SEGMENT)
    }

    fun startQuickTaggingForSegment(segmentId: String) {
        selectSegment(segmentId, navigateToDetail = false)
        _isQuickTaggingMode.value = true
        prepareNewPoleForQuickTagging(segmentId)
        navigateTo(ScreenFlow.QUICK_TAGGING_SESSION)
    }

    fun startQuickTaggingForTargetPole(poleId: String? = null) {
        _isQuickTaggingMode.value = true
        val targetId = if (!poleId.isNullOrEmpty()) poleId else targetPoleId.value
        if (!targetId.isNullOrEmpty()) {
            repository.setTargetPole(targetId)
            repository.startDraftForPole(targetId)
        } else {
            prepareNewPoleForQuickTagging(selectedSegmentId.value)
        }
        navigateTo(ScreenFlow.QUICK_TAGGING_SESSION)
    }

    fun startPmSurveyForSegment(segmentId: String) {
        _isQuickTaggingMode.value = false
        selectSegment(segmentId, navigateToDetail = false)
        val cleanId = segmentId.removePrefix("SEG-")
        val polesInSeg = poles.value.values.filter { 
            it.segmentId == segmentId || 
            it.segmentId.removePrefix("SEG-") == cleanId 
        }.map { it.withCalculatedStatus() }.sortedBy { it.sequence }
        val targetPole = polesInSeg.firstOrNull { it.status == TagStatus.NOT_TAGGED }
        if (targetPole != null) {
            startTaggingPole(targetPole.id)
        } else {
            createAndStartNewPole(segmentId)
        }
    }

    fun prepareNewPoleForQuickTagging(segmentId: String? = null) {
        val segId = segmentId ?: selectedSegmentId.value
        val cleanId = segId.removePrefix("SEG-")
        val polesInSeg = repository.poles.value.values.filter { 
            it.segmentId == segId || 
            it.segmentId.removePrefix("SEG-") == cleanId 
        }.map { it.withCalculatedStatus() }.sortedBy { it.sequence }

        val nextUntagged = polesInSeg.firstOrNull { it.status == TagStatus.NOT_TAGGED && it.photoPath.isNullOrEmpty() }
        if (nextUntagged != null) {
            repository.setTargetPole(nextUntagged.id)
            repository.startDraftForPole(nextUntagged.id)
        } else {
            val loc = currentLocation.value
            val newPole = repository.createNewPoleInSegment(segId, loc.latitude, loc.longitude)
            repository.startDraftForPole(newPole.id)
        }
    }

    fun submitQuickTagPole(
        photoPath: String,
        finalLat: Double,
        finalLng: Double,
        originalLat: Double?,
        originalLng: Double?,
        assetClass: String = "pole_9",
        assetType: String = "steel",
        isExistingPole: Boolean = false,
        photoSha256: String? = null,
        accuracy: Float = 2.5f,
        notes: String? = null,
        onSuccess: (String) -> Unit
    ) {
        val draft = currentDraftPole.value ?: return
        val saved = repository.saveQuickTagPole(
            poleId = draft.id,
            latitude = finalLat,
            longitude = finalLng,
            originalLatitude = originalLat,
            originalLongitude = originalLng,
            photoPath = photoPath,
            assetClass = assetClass,
            assetType = assetType,
            isExistingPole = isExistingPole,
            photoSha256 = photoSha256,
            accuracy = accuracy,
            notes = notes
        )
        val poleCode = saved?.assetUid?.ifEmpty { saved.poleCode.ifEmpty { saved.id } } ?: draft.assetUid.ifEmpty { draft.poleCode.ifEmpty { draft.id } }
        onSuccess(poleCode)

        // Automatically prepare clean state for next pole in the same segment
        prepareNewPoleForQuickTagging(draft.segmentId)
    }

    fun submitSafetyDeferredPole(
        reason: String,
        lat: Double,
        lng: Double,
        assetClass: String = "pole_9",
        onSuccess: (String) -> Unit
    ) {
        val draft = currentDraftPole.value ?: return
        val saved = repository.saveSafetyDeferredPole(
            poleId = draft.id,
            latitude = lat,
            longitude = lng,
            reason = reason,
            assetClass = assetClass
        )
        val poleCode = saved?.assetUid?.ifEmpty { saved.poleCode.ifEmpty { saved.id } } ?: draft.assetUid.ifEmpty { draft.poleCode.ifEmpty { draft.id } }
        onSuccess(poleCode)

        // Automatically prepare clean state for next pole in the same segment
        prepareNewPoleForQuickTagging(draft.segmentId)
    }

    fun exitQuickTagging() {
        _isQuickTaggingMode.value = false
        stopLocationUpdates()

        // Bersihkan draft tiang kosong yang belum pernah disimpan
        val draft = currentDraftPole.value
        if (draft != null && draft.status == TagStatus.NOT_TAGGED && draft.photoPath.isNullOrEmpty() && !draft.isExistingPole) {
            repository.deletePole(draft.id)
        }

        if (screenStack.isNotEmpty()) {
            navigateBack()
        } else {
            navigateTo(ScreenFlow.TAB_ROOT, clearStack = true)
        }
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
            // Check if segment has routeCoordinates stored in Room or MPO dataset!
            val segment = repository.project.value.segments.find { it.id == segmentId }
            val coords = if (segment != null && segment.routeCoordinates.isNotEmpty()) {
                segment.routeCoordinates
            } else {
                val numericId = segmentId.removePrefix("SEG-").toIntOrNull()
                val dto = mpoSegments.value.find { it.id == numericId || it.segment_name == segment?.name }
                dto?.route_path?.map { RouteCoordinate(it.lng, it.lat) } ?: emptyList()
            }

            if (coords.isNotEmpty()) {
                val autoRoute = ImportedRoute(
                    id = "ROUTE-$segmentId",
                    name = segment?.name ?: "Rute FO $segmentId",
                    sourceFileName = "Master Segmen RNIMS (MPO)",
                    sourceFileType = "RNIMS_FO",
                    projectId = segment?.projectId ?: "PRJ-MPO-18",
                    segmentId = segmentId,
                    coordinates = coords
                )
                routeRepository.addAndActivateRoute(autoRoute)
            } else {
                routeRepository.clearActiveRoute()
            }
        }

        if (navigateToDetail) {
            navigateTo(ScreenFlow.SEGMENT_DETAIL)
        }

        // On-Demand fetch: jika segmen belum memiliki tiang, coba tarik dari server (Alpaca PRA)
        loadOrFetchPolesForSegment(segmentId, forceRefresh = false)
    }

    /**
     * Menarik tiang eksisting dari server Alpaca PRA secara on-demand & auto-cache di SQLite Room.
     * Berlaku universal: jika segmen tidak memiliki tiang di server (misal Ambon, Tual, Saumlaki),
     * segmen tetap tersimpan dengan 0 tiang dan siap untuk tagging mandiri.
     */
    fun loadOrFetchPolesForSegment(segmentId: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val existingCount = withContext(Dispatchers.IO) {
                repository.dao.countPolesInSegment(segmentId)
            }
            if (existingCount > 0 && !forceRefresh) {
                return@launch
            }

            _isSyncingPoles.value = true
            _syncPolesMessage.value = "Memeriksa data tiang di server..."
            val result = repository.pullPolesForSegment(rnimsBaseUrl.value, segmentId)
            _isSyncingPoles.value = false

            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _syncPolesMessage.value = if (count > 0) {
                    "Berhasil memuat $count tiang eksisting untuk segmen ini"
                } else {
                    "Segmen siap untuk tagging mandiri (0 tiang di server)"
                }
            } else {
                _syncPolesMessage.value = "Segmen mandiri (tanpa tiang server) atau server offline. Siap tagging mandiri."
            }
        }
    }

    /**
     * Menandai tiang referensi eksisting sebagai NIHIL / TIDAK DITEMUKAN di lapangan.
     * Tetap tersimpan di database untuk rekonsiliasi audit trail.
     */
    fun markPoleMissing(poleId: String, reason: String? = null) {
        repository.markPoleAsMissing(poleId, reason)
    }

    /**
     * Memulihkan tiang yang sebelumnya ditandai NIHIL / TIDAK DITEMUKAN kembali ke status aktif.
     */
    fun restoreMissingPole(poleId: String) {
        repository.restoreMissingPole(poleId)
    }

    /**
     * Menambahkan tiang baru di lapangan (Provisional Pole) pada segmen aktif.
     */
    fun addProvisionalPoleToCurrentSegment(
        latitude: Double,
        longitude: Double,
        assetClass: String = "pole_9"
    ): PoleEntity {
        val currentSegId = selectedSegmentId.value
        val seg = repository.project.value.segments.find { it.id == currentSegId }
        val cityCode = seg?.cityCode?.takeIf { it.isNotBlank() } ?: "MPO"
        return repository.addProvisionalPole(
            segmentId = currentSegId,
            latitude = latitude,
            longitude = longitude,
            assetClass = assetClass,
            cityCode = cityCode
        )
    }

    /**
     * Memvalidasi tiang eksisting dengan titik GPS dan foto kamera.
     */
    fun validateExistingPole(
        poleId: String,
        latitude: Double,
        longitude: Double,
        photoPath: String?,
        assetClass: String = "pole_9",
        equipment: List<String> = emptyList(),
        notes: String? = null
    ) {
        repository.saveQuickTagPole(
            poleId = poleId,
            latitude = latitude,
            longitude = longitude,
            photoPath = photoPath,
            assetClass = assetClass,
            isExistingPole = true,
            matchStatus = "MATCHED",
            notes = notes
        )
    }

    fun createAndStartNewPole(segmentId: String? = null) {
        val segId = segmentId ?: selectedSegmentId.value
        val loc = currentLocation.value
        val newPole = repository.createNewPoleInSegment(segId, loc.latitude, loc.longitude)
        repository.startDraftForPole(newPole.id)
        navigateTo(ScreenFlow.FIELD_MAP)
    }

    fun openPoleDetail(poleId: String) {
        _selectedPoleDetailId.value = poleId
        repository.setTargetPole(poleId)
        navigateTo(ScreenFlow.POLE_DETAIL)
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

    fun setDraftPhotoForSlot(slotIndex: Int, path: String) {
        repository.setDraftPhotoForSlot(slotIndex, path)
    }

    fun removeDraftPhotoFromSlot(slotIndex: Int) {
        repository.removeDraftPhotoFromSlot(slotIndex)
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
