package co.id.lintasarta.tagpollinginventory.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import co.id.lintasarta.tagpollinginventory.data.model.*
import co.id.lintasarta.tagpollinginventory.camera.CameraPreview
import co.id.lintasarta.tagpollinginventory.camera.takePhoto
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.location.LocationData
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.components.calculateRealDistanceMeters
import androidx.compose.ui.tooling.preview.Preview
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TagPollingInventoryTheme
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.io.File
import java.util.Locale
import kotlin.math.abs

@Composable
fun PhotoCaptureScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onSaveAndReview: () -> Unit
) {
    val draftPole by viewModel.currentDraftPole.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()

    PhotoCaptureScreenContent(
        draftPole = draftPole,
        networkStatus = networkStatus,
        currentLocation = currentLocation,
        onBackClick = onBackClick,
        onSaveAndReview = onSaveAndReview,
        onSetDraftPhotoForSlot = { slotIndex, path ->
            viewModel.repository.setDraftPhotoForSlot(slotIndex, path)
        },
        onRemoveDraftPhotoFromSlot = { slotIndex ->
            viewModel.repository.removeDraftPhotoFromSlot(slotIndex)
        },
        onUpdateNotes = { newNotes ->
            viewModel.repository.updateDraftAttributes(
                type = draftPole?.type ?: PoleType.CONCRETE,
                condition = draftPole?.condition ?: PoleCondition.GOOD,
                ownership = draftPole?.ownership ?: PoleOwnership.LINTASARTA,
                height = draftPole?.height ?: "9m",
                tagNumber = draftPole?.tagNumber ?: "",
                hasFoCable = draftPole?.hasFoCable ?: true,
                cableCondition = draftPole?.cableCondition ?: CableCondition.GOOD,
                equipment = draftPole?.equipment ?: setOf("ODP", "Closure"),
                notes = newNotes
            )
        }
    )
}

data class PhotoCategorySpec(
    val index: Int,
    val title: String,
    val subtitle: String,
    val guideText: String,
    val isRequired: Boolean
)

val photoCategorySpecs = listOf(
    PhotoCategorySpec(
        index = 0,
        title = "1. Pondasi & Lingkungan Tiang",
        subtitle = "Pole Foundation & Surroundings",
        guideText = "Ambil foto sudut lebar memperlihatkan pondasi tiang & lingkungan sekitarnya.",
        isRequired = true
    ),
    PhotoCategorySpec(
        index = 1,
        title = "2. Label Tag & Nomor Seri",
        subtitle = "Tag Label & Serial Number",
        guideText = "Ambil foto jarak dekat (close-up) membaca teks label/plat merk tiang.",
        isRequired = true
    ),
    PhotoCategorySpec(
        index = 2,
        title = "3. Perangkat Atas / Closure / ODP",
        subtitle = "Top Closure / ODP / Cables",
        guideText = "Ambil foto bagian atas tiang memperlihatkan ODP/Closure & kabel FO.",
        isRequired = true
    ),
    PhotoCategorySpec(
        index = 3,
        title = "4. Dokumentasi Tambahan 1 (Opsional)",
        subtitle = "Optional Documentation 1",
        guideText = "Foto tambahan kondisi khusus, potensi bahaya, atau akses lokasi.",
        isRequired = false
    ),
    PhotoCategorySpec(
        index = 4,
        title = "5. Dokumentasi Tambahan 2 (Opsional)",
        subtitle = "Optional Documentation 2",
        guideText = "Foto tambahan kondisi khusus, potensi bahaya, atau akses lokasi.",
        isRequired = false
    ),
    PhotoCategorySpec(
        index = 5,
        title = "6. Dokumentasi Tambahan 3 (Opsional)",
        subtitle = "Optional Documentation 3",
        guideText = "Foto tambahan kondisi khusus, potensi bahaya, atau akses lokasi.",
        isRequired = false
    ),
    PhotoCategorySpec(
        index = 6,
        title = "7. Dokumentasi Tambahan 4 (Opsional)",
        subtitle = "Optional Documentation 4",
        guideText = "Foto tambahan kondisi khusus, potensi bahaya, atau akses lokasi.",
        isRequired = false
    ),
    PhotoCategorySpec(
        index = 7,
        title = "8. Dokumentasi Tambahan 5 (Opsional)",
        subtitle = "Optional Documentation 5",
        guideText = "Foto tambahan kondisi khusus, potensi bahaya, atau akses lokasi.",
        isRequired = false
    ),
    PhotoCategorySpec(
        index = 8,
        title = "9. Dokumentasi Tambahan 6 (Opsional)",
        subtitle = "Optional Documentation 6",
        guideText = "Foto tambahan kondisi khusus, potensi bahaya, atau akses lokasi.",
        isRequired = false
    ),
    PhotoCategorySpec(
        index = 9,
        title = "10. Dokumentasi Tambahan 7 (Opsional)",
        subtitle = "Optional Documentation 7",
        guideText = "Foto tambahan kondisi khusus, potensi bahaya, atau akses lokasi.",
        isRequired = false
    )
)

@Composable
fun PhotoCaptureScreenContent(
    draftPole: Pole?,
    networkStatus: NetworkStatus,
    currentLocation: LocationData,
    onBackClick: () -> Unit,
    onSaveAndReview: () -> Unit,
    onSetDraftPhotoForSlot: (Int, String) -> Unit = { _, _ -> },
    onRemoveDraftPhotoFromSlot: (Int) -> Unit = {},
    onUpdateNotes: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val rawPhotoPaths = draftPole?.photoPaths ?: emptyList()

    // Fixed 10 slots
    val slotPaths = remember(rawPhotoPaths) {
        val result = MutableList(10) { "" }
        for (i in rawPhotoPaths.indices) {
            if (i < 10) result[i] = rawPhotoPaths[i]
        }
        result
    }

    val requiredFilledCount = listOf(slotPaths[0], slotPaths[1], slotPaths[2]).count { it.isNotEmpty() }
    val isRequiredComplete = requiredFilledCount == 3

    var activeCameraSlotIndex by remember { mutableStateOf<Int?>(null) }
    var inspectingPhotoPath by remember { mutableStateOf<String?>(null) }

    val slotNotesMap = remember(draftPole?.id) {
        val map = mutableStateMapOf<Int, String>()
        val existingNotes = draftPole?.notes ?: ""
        existingNotes.lines().forEach { line ->
            photoCategorySpecs.forEach { spec ->
                val prefix = "[FOTO_${spec.index + 1}]:"
                if (line.startsWith(prefix)) {
                    map[spec.index] = line.removePrefix(prefix).trim()
                }
            }
        }
        map
    }

    fun updateSlotNote(slotIndex: Int, newNote: String) {
        slotNotesMap[slotIndex] = newNote

        val lines = mutableListOf<String>()
        val existingNotes = draftPole?.notes ?: ""
        existingNotes.lines().forEach { line ->
            if (!line.startsWith("[FOTO_")) {
                if (line.isNotBlank()) lines.add(line)
            }
        }
        slotNotesMap.forEach { (index, note) ->
            if (note.isNotBlank()) {
                lines.add("[FOTO_${index + 1}]: $note")
            }
        }

        val compiledText = lines.joinToString("\n")
        onUpdateNotes(compiledText)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isCapturing by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var imageCaptureInstance by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { ContextCompat.getMainExecutor(context) }

    // Device Tilt Level Sensor (Waterpass)
    var rollAngle by remember { mutableStateOf(0f) }
    var pitchAngle by remember { mutableStateOf(0f) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }

    DisposableEffect(Unit) {
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                }

                if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    pitchAngle = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    rollAngle = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(sensorListener, accelSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager?.registerListener(sensorListener, magnetSensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    val absRoll = abs(rollAngle)
    val isDeviceLevel = absRoll <= 5.0f
    val displayPoleCode = draftPole?.poleCode?.ifEmpty { draftPole.id } ?: "Target Pole"

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeCameraSlotIndex != null) {
            // MODE 2: PRESERVED LIVE CAMERAX VIEWFINDER & OVERLAY
            val currentSpec = photoCategorySpecs.find { it.index == activeCameraSlotIndex }
            
            Scaffold(
                topBar = {
                    TopBar(
                        title = currentSpec?.title ?: "Take Photo",
                        subtitle = displayPoleCode,
                        onBackClick = { activeCameraSlotIndex = null },
                        networkStatus = networkStatus
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color.Black)
                ) {
                    if (hasCameraPermission) {
                        CameraPreview(
                            cameraSelector = cameraSelector,
                            flashMode = flashMode,
                            onImageCaptureCreated = { imageCapture ->
                                imageCaptureInstance = imageCapture
                            }
                        )

                        // Camera Framing Overlay (Siluet Tiang & Waterpass)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val boxW = w * 0.35f
                            val boxH = h * 0.82f
                            val left = (w - boxW) / 2
                            val top = (h - boxH) / 2

                            val overlayColor = if (isDeviceLevel) Color(0xFF4CAF50) else Color(0xFFFF9800)

                            // Outer Corridor Box
                            drawRect(
                                color = overlayColor.copy(alpha = 0.6f),
                                topLeft = Offset(left, top),
                                size = Size(boxW, boxH),
                                style = Stroke(width = 3f)
                            )

                            // Vertical Centre Line
                            drawLine(
                                color = overlayColor.copy(alpha = 0.8f),
                                start = Offset(w / 2, top),
                                end = Offset(w / 2, top + boxH),
                                strokeWidth = 2f
                            )

                            // Top Safe Area Marker
                            val topAreaY = top + boxH * 0.15f
                            drawLine(
                                color = Color.White.copy(alpha = 0.7f),
                                start = Offset(left, topAreaY),
                                end = Offset(left + boxW, topAreaY),
                                strokeWidth = 2f
                            )

                            // Base Safe Area Marker
                            val baseAreaY = top + boxH * 0.85f
                            drawLine(
                                color = Color.White.copy(alpha = 0.7f),
                                start = Offset(left, baseAreaY),
                                end = Offset(left + boxW, baseAreaY),
                                strokeWidth = 2f
                            )
                        }

                        // Badges Overlay (Top-Left)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Waterpass Device Tilt Level Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDeviceLevel) Color(0xFF2E7D32).copy(alpha = 0.85f) else Color(0xFFE65100).copy(alpha = 0.85f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isDeviceLevel) Icons.Default.CheckCircle else Icons.Default.ScreenRotation,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isDeviceLevel) "Ponsel Tegak (±${String.format(Locale.US, "%.1f", absRoll)}°)" else "Ponsel Miring (±${String.format(Locale.US, "%.1f", absRoll)}°)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GpsFixed,
                                        contentDescription = null,
                                        tint = if (currentLocation.accuracy <= 5f) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("GPS ±${String.format(Locale.US, "%.1f", currentLocation.accuracy)}m", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }

                            if (draftPole != null) {
                                val distance = calculateRealDistanceMeters(
                                    currentLocation.latitude, currentLocation.longitude,
                                    draftPole.latitude, draftPole.longitude
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Straighten,
                                            contentDescription = null,
                                            tint = if (distance <= 15f) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Distance ~${String.format(Locale.US, "%.0f", distance)}m", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                }
                            }
                        }

                        // Bottom Controls
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            color = Color(0xFF1E1E1E)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (flashMode == ImageCapture.FLASH_MODE_ON) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "Flash",
                                        tint = Color.White
                                    )
                                }

                                // Shutter Button
                                IconButton(
                                    onClick = {
                                        if (isCapturing) return@IconButton
                                        isCapturing = true
                                        val poleId = draftPole?.id ?: "P-019-019"
                                        val slotIndex = activeCameraSlotIndex ?: 0

                                        takePhoto(
                                            context = context,
                                            imageCapture = imageCaptureInstance,
                                            executor = cameraExecutor,
                                            poleId = poleId,
                                            lat = draftPole?.latitude ?: 0.0,
                                            lng = draftPole?.longitude ?: 0.0,
                                            onPhotoSaved = { file ->
                                                onSetDraftPhotoForSlot(slotIndex, file.absolutePath)
                                                isCapturing = false
                                                activeCameraSlotIndex = null
                                            },
                                            onError = { e ->
                                                isCapturing = false
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(4.dp, TelecomPrimary, CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(TelecomPrimary)
                                    )
                                }

                                Spacer(modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // MODE 1: STRUCTURED PHOTO DOCUMENTATION CHECKLIST SCREEN
            Scaffold(
                topBar = {
                    TopBar(
                        title = "Capture Pole Photos",
                        subtitle = displayPoleCode,
                        onBackClick = onBackClick,
                        networkStatus = networkStatus
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(NeutralBackground)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Progress Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "DOKUMENTASI FOTO TIANG",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(
                                    text = if (isRequiredComplete) "3 Foto Wajib Lengkap" else "Lengkapi Foto Wajib",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isRequiredComplete) Color(0xFF2E7D32) else TelecomPrimary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isRequiredComplete) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                            ) {
                                Text(
                                    text = "$requiredFilledCount / 3 Wajib Terisi",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRequiredComplete) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                )
                            }
                        }
                    }

                    // 4 Category Cards
                    photoCategorySpecs.forEach { spec ->
                        val currentPath = slotPaths.getOrElse(spec.index) { "" }
                        val isFilled = currentPath.isNotEmpty() && File(currentPath).exists()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = spec.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TelecomPrimary
                                        )
                                        Text(
                                            text = spec.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isFilled) Color(0xFFE8F5E9) else if (spec.isRequired) Color(0xFFFFF3E0) else Color(0xFFF5F5F5)
                                    ) {
                                        Text(
                                            text = if (isFilled) "✓ FOTO TERSIMPAN" else if (spec.isRequired) "BELUM ADA FOTO" else "OPSIONAL",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFilled) Color(0xFF2E7D32) else if (spec.isRequired) Color(0xFFEF6C00) else Color.Gray
                                        )
                                    }
                                }

                                HorizontalDivider()

                                // ONLY FOR SLOTS 4 TO 10 (spec.index >= 3): Keterangan directly under label!
                                if (!spec.isRequired) {
                                    OutlinedTextField(
                                        value = slotNotesMap[spec.index] ?: "",
                                        onValueChange = { newText ->
                                            updateSlotNote(spec.index, newText)
                                        },
                                        label = { Text("Keterangan") },
                                        placeholder = { Text("Isikan keterangan foto opsional di sini...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                if (isFilled) {
                                    // Large Image Preview Card (like Image 2 "LKI VSAT")
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                                            .clickable { inspectingPhotoPath = currentPath },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = File(currentPath),
                                            contentDescription = spec.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.7f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomIn,
                                                    contentDescription = "Zoom",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Klik Perbesar", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                            }
                                        }
                                    }

                                    // Action Buttons Row (Retake & Delete)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { activeCameraSlotIndex = spec.index },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TelecomPrimary)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Retake", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Ulangi (Retake)")
                                        }

                                        OutlinedButton(
                                            onClick = { onRemoveDraftPhotoFromSlot(spec.index) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Hapus Foto")
                                        }
                                    }
                                } else {
                                    // Unfilled Slot Guidance & Take Photo Button
                                    Text(
                                        text = spec.guideText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray
                                    )

                                    Button(
                                        onClick = { activeCameraSlotIndex = spec.index },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Ambil Foto ${spec.subtitle}", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Continue Button
                    Button(
                        onClick = onSaveAndReview,
                        enabled = isRequiredComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isRequiredComplete) "Selesai & Lanjut ke Review" else "Lengkapi 3 Foto Wajib",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Full Screen Inspection Dialog
        if (inspectingPhotoPath != null) {
            Dialog(
                onDismissRequest = { inspectingPhotoPath = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = File(inspectingPhotoPath!!),
                        contentDescription = "Full Inspection",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    IconButton(
                        onClick = { inspectingPhotoPath = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoCaptureScreenPreview() {
    TagPollingInventoryTheme {
        PhotoCaptureScreenContent(
            draftPole = Pole(
                id = "P-019-019",
                segmentId = "SEG-001",
                sequence = 1,
                latitude = -3.6954,
                longitude = 128.1814,
                poleCode = "PL-JKT-001"
            ),
            networkStatus = NetworkStatus(
                isOnline = true,
                connectionType = "4G"
            ),
            currentLocation = LocationData(
                latitude = -3.6954,
                longitude = 128.1814,
                accuracy = 3.2f,
                isAvailable = true
            ),
            onBackClick = {},
            onSaveAndReview = {}
        )
    }
}