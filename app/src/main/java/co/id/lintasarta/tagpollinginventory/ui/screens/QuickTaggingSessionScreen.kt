package co.id.lintasarta.tagpollinginventory.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import co.id.lintasarta.tagpollinginventory.camera.CameraPreview
import co.id.lintasarta.tagpollinginventory.camera.calculateFileSha256
import co.id.lintasarta.tagpollinginventory.camera.takePhoto
import co.id.lintasarta.tagpollinginventory.data.model.PoleEntity
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.components.calculateRealDistanceMeters
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class QuickGpsState {
    NOT_CAPTURED,
    SEARCHING,
    READY,
    POOR_ACCURACY,
    FAILED
}

/**
 * LAYAR 3: ULTRA-FAST QUICK TAGGING CANVAS (< 1 Menit per Tiang)
 *
 * Menggabungkan Dua Pilar Inti:
 * 1. UNSUR APLIKASI LAPANGAN (POLE_INVENTORY_ARCHITECTURE.md):
 *    - Alur survei 2-tahap (Two-Action Cadence):
 *      a) Aksi 1 — "Validate position": Berdiri di dekat dasar tiang (1–3 m) dengan GNSS warming/averaging.
 *      b) Aksi 2 — "Take photo": Surveyor mundur ke jarak optimal (~10–15 m) mengambil foto siluet CameraX.
 *    - Kamera CameraX dengan panduan siluet portrait (NO ARCore), auto-compress JPEG < 400KB, watermark GPS + Timestamp + SHA-256.
 *    - Evaluasi Keselamatan & Aksesibilitas ("Approach Safety Deferral") tanpa memalsukan titik koordinat tiang.
 *    - 100% Offline-first dengan peta lokal OpenStreetMap (OSM).
 *
 * 2. STANDAR MODEL DATA ALPACA PRA:
 *    - Klasifikasi Tiang Resmi: pole_7 (T7) dan pole_9 (T9, default) via toggle 1-tap.
 *    - Format Asset UID: POL-MPO-[KOTA]-[SEQ:04d] (contoh: POL-MPO-TMK-0001).
 *    - Deteksi Proksimitas Tiang Eksisting Alpaca (< 20 meter): Pilihan [Gunakan Tiang Eksisting] vs [Buat Tiang Baru].
 *    - Tombol 1-Tap: "SIMPAN & LANJUT KE TIANG BERIKUTNYA".
 */
@Composable
fun QuickTaggingSessionScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val draftPole by viewModel.currentDraftPole.collectAsState()
    val selectedSegmentId by viewModel.selectedSegmentId.collectAsState()
    val project by viewModel.project.collectAsState()
    val activeSegment = remember(project, selectedSegmentId) {
        project.segments.find { it.id == selectedSegmentId }
    }
    val allPoles by viewModel.poles.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    // Screen State for the current pole
    var selectedAssetClass by remember { mutableStateOf(draftPole?.assetClass ?: "pole_9") }
    var gpsState by remember { mutableStateOf(QuickGpsState.NOT_CAPTURED) }
    var capturedOriginalLat by remember { mutableStateOf<Double?>(null) }
    var capturedOriginalLng by remember { mutableStateOf<Double?>(null) }
    var correctedLat by remember { mutableStateOf<Double?>(null) }
    var correctedLng by remember { mutableStateOf<Double?>(null) }
    var isMarkerManuallyAdjusted by remember { mutableStateOf(false) }

    var capturedPhotoPath by remember { mutableStateOf<String?>(null) }
    var capturedPhotoSha256 by remember { mutableStateOf<String?>(null) }
    var isCameraOpen by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var lastSubmittedPoleCode by remember { mutableStateOf<String?>(null) }

    // State Deteksi Tiang Eksisting (< 20m)
    var detectedExistingPole by remember { mutableStateOf<PoleEntity?>(null) }
    var isUsingExistingPole by remember { mutableStateOf(false) }

    // State Safety Deferral Dialog
    var showSafetyDeferralDialog by remember { mutableStateOf(false) }
    var selectedSafetyReason by remember { mutableStateOf("Tebing Curam / Jurang") }
    var customSafetyReason by remember { mutableStateOf("") }

    var hasLocationPermission by remember { mutableStateOf(viewModel.locationProvider.hasLocationPermission()) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
    }

    BackHandler {
        onBackClick()
    }

    // Monitor GPS during SEARCHING state & Auto-Lock saat akurasi <= 8m (Hijau)
    LaunchedEffect(gpsState, currentLocation) {
        if (gpsState == QuickGpsState.SEARCHING) {
            if (currentLocation.isAvailable && currentLocation.latitude != 0.0) {
                if (currentLocation.accuracy <= 8.0f || currentLocation.isSamplingComplete) {
                    capturedOriginalLat = currentLocation.latitude
                    capturedOriginalLng = currentLocation.longitude
                    correctedLat = currentLocation.latitude
                    correctedLng = currentLocation.longitude

                    gpsState = when {
                        currentLocation.accuracy <= 8.0f -> QuickGpsState.READY
                        currentLocation.accuracy <= 15.0f -> QuickGpsState.POOR_ACCURACY
                        else -> QuickGpsState.POOR_ACCURACY
                    }
                }
            }
        }
    }

    // Deteksi Tiang Eksisting Otomatis (Radius < 20 meter)
    LaunchedEffect(correctedLat, correctedLng) {
        val lat = correctedLat
        val lng = correctedLng
        if (lat != null && lng != null && !isUsingExistingPole) {
            val nearby = allPoles.values
                .filter { it.id != draftPole?.id && it.latitude != 0.0 && it.longitude != 0.0 }
                .map { it to calculateRealDistanceMeters(lat, lng, it.latitude, it.longitude) }
                .filter { it.second <= 20.0f }
                .minByOrNull { it.second }

            if (nearby != null) {
                detectedExistingPole = nearby.first
            } else {
                detectedExistingPole = null
            }
        }
    }

    val displayAssetUid = draftPole?.assetUid?.ifEmpty { draftPole?.poleCode?.ifEmpty { draftPole?.id } } ?: "POL-MPO-TMK-0001"
    val segmentDisplayName = activeSegment?.name ?: "Segment Alpaca MPO"

    Scaffold(
        topBar = {
            TopBar(
                title = "Alpaca Field Survey",
                subtitle = "$segmentDisplayName • $displayAssetUid",
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Feedback Banner Sukses setelah tiang sebelumnya tersimpan
            AnimatedVisibility(
                visible = lastSubmittedPoleCode != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Aset $lastSubmittedPoleCode tersimpan ke database lokal SQLite! Melanjutkan ke tiang berikutnya.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // Kartu Ringkasan Tiang & Penomoran Resmi Alpaca PRA
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ALPACA ASSET PRA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Gray,
                                    letterSpacing = 0.5.sp
                                )
                                if (isUsingExistingPole) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFE0F7FA)
                                    ) {
                                        Text(
                                            text = "Aset Eksisting",
                                            color = Color(0xFF00838F),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = displayAssetUid,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = TelecomPrimary
                            )
                            Text(
                                text = "Urutan #${draftPole?.sequence ?: 1} • Segmen: $segmentDisplayName",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedAssetClass == "pole_7") Color(0xFFFFF3E0) else Color(0xFFE3F2FD)
                        ) {
                            Text(
                                text = if (selectedAssetClass == "pole_7") "Tiang 7M (T7)" else "Tiang 9M (T9)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedAssetClass == "pole_7") Color(0xFFE65100) else TelecomPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Spacer(modifier = Modifier.height(12.dp))

                    // TOGGLE BESAR TIPE TIANG ALPACA: pole_7 vs pole_9 (Default)
                    Text(
                        text = "Klasifikasi Tiang Sewa (PRA):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tombol Tiang 7 Meter (pole_7)
                        val isP7Selected = selectedAssetClass == "pole_7"
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable {
                                    selectedAssetClass = "pole_7"
                                    draftPole?.let { curr ->
                                        viewModel.repository.updateDraftAttributes(
                                            type = curr.type,
                                            condition = curr.condition,
                                            ownership = curr.ownership,
                                            height = "7m",
                                            tagNumber = curr.tagNumber,
                                            hasFoCable = curr.hasFoCable,
                                            cableCondition = curr.cableCondition,
                                            equipment = curr.equipment,
                                            notes = curr.notes
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isP7Selected) Color(0xFFFF9800) else Color(0xFFF5F5F5),
                            border = if (isP7Selected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isP7Selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "Tiang 7M (T7)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isP7Selected) Color.White else Color.DarkGray,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Tombol Tiang 9 Meter (pole_9 - Default Alpaca)
                        val isP9Selected = selectedAssetClass == "pole_9"
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable {
                                    selectedAssetClass = "pole_9"
                                    draftPole?.let { curr ->
                                        viewModel.repository.updateDraftAttributes(
                                            type = curr.type,
                                            condition = curr.condition,
                                            ownership = curr.ownership,
                                            height = "9m",
                                            tagNumber = curr.tagNumber,
                                            hasFoCable = curr.hasFoCable,
                                            cableCondition = curr.cableCondition,
                                            equipment = curr.equipment,
                                            notes = curr.notes
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isP9Selected) TelecomPrimary else Color(0xFFF5F5F5),
                            border = if (isP9Selected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isP9Selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "Tiang 9M (T9)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isP9Selected) Color.White else Color.DarkGray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // DETEKSI PROKSIMITAS TIANG EKSISTING (< 20m)
            if (detectedExistingPole != null && !isUsingExistingPole) {
                val existing = detectedExistingPole!!
                val dist = calculateRealDistanceMeters(
                    correctedLat ?: currentLocation.latitude,
                    correctedLng ?: currentLocation.longitude,
                    existing.latitude,
                    existing.longitude
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF80DEEA))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationSearching,
                                contentDescription = null,
                                tint = Color(0xFF00838F),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tiang Alpaca Eksisting Terdeteksi!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF006064),
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aset ${existing.assetUid.ifEmpty { existing.poleCode }} berjarak ±${dist.roundToInt()} meter dari posisi surveyor.",
                            fontSize = 12.sp,
                            color = Color(0xFF006064)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    isUsingExistingPole = true
                                    draftPole?.let { currDraft ->
                                        val updated = currDraft.copy(
                                            assetUid = existing.assetUid.ifEmpty { existing.poleCode },
                                            poleCode = existing.poleCode,
                                            sequence = existing.sequence,
                                            assetClass = existing.assetClass,
                                            isExistingPole = true
                                        )
                                        selectedAssetClass = existing.assetClass
                                        viewModel.repository.startDraftForPole(updated.id)
                                    }
                                    Toast.makeText(context, "Memperbarui Aset Eksisting: ${existing.assetUid}", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Gunakan Tiang Eksisting", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    isUsingExistingPole = false
                                    detectedExistingPole = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Buat Tiang Baru", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // AKSI 1: VALIDATE POSITION (DASAR TIANG 1-3M)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = TelecomPrimary.copy(alpha = 0.1f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("1", fontWeight = FontWeight.Bold, color = TelecomPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Aksi 1: Validate Position",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Berdiri di dekat dasar tiang (1–3 m)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Status Akurasi GNSS (Hijau <= 8m, Kuning 8-15m, Merah > 15m)
                        val acc = currentLocation.accuracy
                        val (statusText, statusColor, statusBg) = when (gpsState) {
                            QuickGpsState.NOT_CAPTURED -> Triple("Belum Dikunci", Color.Gray, Color(0xFFF5F5F5))
                            QuickGpsState.SEARCHING -> Triple("GNSS Warming...", TelecomPrimary, Color(0xFFE3F2FD))
                            QuickGpsState.READY -> Triple(
                                "Terkunci (±${String.format(Locale.US, "%.1f", acc)}m)",
                                Color(0xFF2E7D32),
                                Color(0xFFE8F5E9)
                            )
                            QuickGpsState.POOR_ACCURACY -> Triple(
                                if (acc > 15f) "Akurasi Lemah (> 15m)" else "Akurasi (±${String.format(Locale.US, "%.1f", acc)}m)",
                                if (acc > 15f) Color(0xFFC62828) else Color(0xFFEF6C00),
                                if (acc > 15f) Color(0xFFFFEBEE) else Color(0xFFFFF3E0)
                            )
                            QuickGpsState.FAILED -> Triple("GPS Gagal", Color(0xFFC62828), Color(0xFFFFEBEE))
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusBg
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Koordinat Display (Raw vs Corrected & Visual Offset)
                    if (correctedLat != null && correctedLng != null) {
                        val distanceOffset = if (capturedOriginalLat != null && capturedOriginalLng != null) {
                            calculateRealDistanceMeters(
                                userLat = capturedOriginalLat!!,
                                userLng = capturedOriginalLng!!,
                                targetLat = correctedLat!!,
                                targetLng = correctedLng!!
                            )
                        } else 0f
                        val isMoved = distanceOffset > 0.5f

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8F9FA),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Koordinat Tiang: ${String.format(Locale.US, "%.6f", correctedLat)}, ${String.format(Locale.US, "%.6f", correctedLng)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF263238)
                                    )
                                    if (isMoved) {
                                        Text(
                                            text = String.format(Locale.US, "Offset +%.1fm", distanceOffset),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF1565C0)
                                        )
                                    }
                                }
                                if (capturedOriginalLat != null && capturedOriginalLng != null) {
                                    Text(
                                        text = "GNSS Standpoint: ${String.format(Locale.US, "%.6f", capturedOriginalLat)}, ${String.format(Locale.US, "%.6f", capturedOriginalLng)} (±${String.format(Locale.US, "%.1f", currentLocation.accuracy)}m)",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Peta Lapangan Lengkap: Rute Kabel Biru + Pin Eksisting (Cyan) + Pin Baru (Hijau)
                        QuickTaggingMapPreview(
                            originalLatitude = capturedOriginalLat ?: correctedLat!!,
                            originalLongitude = capturedOriginalLng ?: correctedLng!!,
                            currentLatitude = correctedLat!!,
                            currentLongitude = correctedLng!!,
                            activeSegmentRoute = activeSegment?.routeCoordinates ?: emptyList(),
                            nearbyPoles = allPoles.values.toList(),
                            onLocationAdjusted = { newLat, newLng ->
                                correctedLat = newLat
                                correctedLng = newLng
                                isMarkerManuallyAdjusted = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(10.dp))
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Legenda Warna Peta
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LegendItem(color = Color(0xFF1565C0), label = "Kabel FO (Biru)")
                            LegendItem(color = Color(0xFF00BCD4), label = "Eksisting (Cyan)")
                            LegendItem(color = Color(0xFF4CAF50), label = "Terdata (Hijau)")
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Baris Tombol: VALIDATE POSITION + SAFETY DEFERRAL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!hasLocationPermission) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                    return@Button
                                }

                                gpsState = QuickGpsState.SEARCHING
                                isMarkerManuallyAdjusted = false
                                viewModel.startLocationUpdates()
                                viewModel.locationProvider.resetSampling()

                                coroutineScope.launch {
                                    delay(8000L)
                                    if (gpsState == QuickGpsState.SEARCHING) {
                                        val loc = viewModel.currentLocation.value
                                        if (loc.isAvailable && loc.latitude != 0.0) {
                                            capturedOriginalLat = loc.latitude
                                            capturedOriginalLng = loc.longitude
                                            correctedLat = loc.latitude
                                            correctedLng = loc.longitude
                                            gpsState = if (loc.accuracy <= 8.0f) QuickGpsState.READY else QuickGpsState.POOR_ACCURACY
                                        } else {
                                            gpsState = QuickGpsState.FAILED
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (gpsState == QuickGpsState.READY) Color(0xFF2E7D32) else TelecomPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = gpsState != QuickGpsState.SEARCHING
                        ) {
                            if (gpsState == QuickGpsState.SEARCHING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Averaging GNSS...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(
                                    imageVector = if (gpsState == QuickGpsState.READY) Icons.Default.Check else Icons.Default.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (gpsState == QuickGpsState.READY) "Validasi Ulang Posisi" else "Validate Position",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Tombol Safety Deferral ("Approach Safety Risk")
                        OutlinedButton(
                            onClick = {
                                if (correctedLat == null) {
                                    val loc = viewModel.currentLocation.value
                                    if (loc.isAvailable && loc.latitude != 0.0) {
                                        correctedLat = loc.latitude
                                        correctedLng = loc.longitude
                                    } else {
                                        Toast.makeText(context, "Kunci sinyal GPS terlebih dahulu untuk titik surveyor.", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                }
                                showSafetyDeferralDialog = true
                            },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Safety Risk", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ==========================================
            // AKSI 2: TAKE PHOTO (CAMERAX SILUET 10-15M)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = TelecomPrimary.copy(alpha = 0.1f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("2", fontWeight = FontWeight.Bold, color = TelecomPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Aksi 2: Take Photo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Mundur ke jarak pandang optimal (~10–15 m)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (capturedPhotoPath != null) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                        ) {
                            Text(
                                text = if (capturedPhotoPath != null) "Foto Terkompresi < 400KB" else "Belum Diambil",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (capturedPhotoPath != null) Color(0xFF2E7D32) else Color.Gray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pratinjau Foto jika sudah diambil
                    if (capturedPhotoPath != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = File(capturedPhotoPath!!),
                                contentDescription = "Foto Tiang Ber-watermark",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                shape = RoundedCornerShape(bottomStart = 8.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    text = "Watermark GPS & SHA-256 Tersemat",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Tombol Buka Kamera Siluet
                    OutlinedButton(
                        onClick = {
                            if (!hasCameraPermission) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                return@OutlinedButton
                            }
                            isCameraOpen = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (capturedPhotoPath != null) "Ambil Ulang Foto Siluet" else "Buka Kamera Siluet Tiang",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ==========================================
            // LANGKAH 3: 1-TAP SIMPAN & LANJUT KE BERIKUTNYA
            // ==========================================
            val isSubmitEnabled = (gpsState == QuickGpsState.READY || isMarkerManuallyAdjusted || correctedLat != null) &&
                    capturedPhotoPath != null &&
                    !isSubmitting

            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    val finalLat = correctedLat
                    val finalLng = correctedLng
                    val photo = capturedPhotoPath

                    if (finalLat == null || finalLng == null || photo == null) {
                        Toast.makeText(context, "Lengkapi koordinat GPS dan foto tiang.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true
                    viewModel.submitQuickTagPole(
                        photoPath = photo,
                        finalLat = finalLat,
                        finalLng = finalLng,
                        originalLat = capturedOriginalLat,
                        originalLng = capturedOriginalLng,
                        assetClass = selectedAssetClass,
                        assetType = "steel",
                        isExistingPole = isUsingExistingPole,
                        photoSha256 = capturedPhotoSha256,
                        accuracy = currentLocation.accuracy,
                        onSuccess = { savedCode ->
                            isSubmitting = false
                            lastSubmittedPoleCode = savedCode

                            // Reset state untuk tiang berikutnya secara mulus
                            gpsState = QuickGpsState.NOT_CAPTURED
                            capturedOriginalLat = null
                            capturedOriginalLng = null
                            correctedLat = null
                            correctedLng = null
                            isMarkerManuallyAdjusted = false
                            capturedPhotoPath = null
                            capturedPhotoSha256 = null
                            isUsingExistingPole = false
                            detectedExistingPole = null

                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSubmitEnabled) Color(0xFF2E7D32) else TelecomPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = isSubmitEnabled
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Menyimpan Aset Alpaca ke SQLite...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SIMPAN & LANJUT KE TIANG BERIKUTNYA",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (!isSubmitEnabled && !isSubmitting) {
                Text(
                    text = "* Kunci GPS dan ambil foto siluet untuk mengaktifkan tombol simpan",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // DIALOG SAFETY DEFERRAL ("Approach Safety Deferral")
    if (showSafetyDeferralDialog) {
        val safetyReasons = listOf(
            "Tebing Curam / Jurang",
            "Rawa / Banjir / Sungai Dalam",
            "Bahaya Hewan Liar / Anjing Galak",
            "Akses Ditutup / Properti Pribadi Tanpa Izin",
            "Trafik Padat / Bahaya Jalan Raya",
            "Lainnya"
        )

        AlertDialog(
            onDismissRequest = { showSafetyDeferralDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFD32F2F))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Approach Safety Deferral", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sesuai POLE_INVENTORY_ARCHITECTURE.md (Sec 4.9), jika tiang tidak dapat didekati dengan aman, catat alasan tanpa memalsukan titik tiang.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    safetyReasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSafetyReason = reason }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedSafetyReason == reason,
                                onClick = { selectedSafetyReason = reason }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(reason, fontSize = 13.sp)
                        }
                    }

                    if (selectedSafetyReason == "Lainnya") {
                        OutlinedTextField(
                            value = customSafetyReason,
                            onValueChange = { customSafetyReason = it },
                            label = { Text("Jelaskan kendala lapangan") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reasonText = if (selectedSafetyReason == "Lainnya") customSafetyReason.ifEmpty { "Kendala Akses Lapangan" } else selectedSafetyReason
                        val lat = correctedLat ?: currentLocation.latitude
                        val lng = correctedLng ?: currentLocation.longitude

                        showSafetyDeferralDialog = false
                        isSubmitting = true
                        viewModel.submitSafetyDeferredPole(
                            reason = reasonText,
                            lat = lat,
                            lng = lng,
                            assetClass = selectedAssetClass,
                            onSuccess = { savedCode ->
                                isSubmitting = false
                                lastSubmittedPoleCode = "$savedCode (Safety Deferral)"

                                // Reset state
                                gpsState = QuickGpsState.NOT_CAPTURED
                                capturedOriginalLat = null
                                capturedOriginalLng = null
                                correctedLat = null
                                correctedLng = null
                                isMarkerManuallyAdjusted = false
                                capturedPhotoPath = null
                                isUsingExistingPole = false
                                detectedExistingPole = null

                                coroutineScope.launch {
                                    scrollState.animateScrollTo(0)
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Catat Safety Deferral", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyDeferralDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // FULL SCREEN CAMERAX DENGAN OVERLAY PANDUAN SILUET & SENSOR LEVEL
    if (isCameraOpen) {
        Dialog(
            onDismissRequest = { isCameraOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            QuickCameraOverlay(
                poleId = displayAssetUid,
                lat = correctedLat ?: 0.0,
                lng = correctedLng ?: 0.0,
                onPhotoCaptured = { photoFile, sha256 ->
                    capturedPhotoPath = photoFile.absolutePath
                    capturedPhotoSha256 = sha256
                    isCameraOpen = false
                },
                onClose = { isCameraOpen = false }
            )
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, fontSize = 11.sp, color = Color(0xFF455A64), fontWeight = FontWeight.Medium)
    }
}

/**
 * Peta Pratinjau Cepat: Menampilkan Garis Rute FO (Biru) + Tiang Eksisting (Cyan) + Tiang Baru (Hijau)
 */
@Composable
fun QuickTaggingMapPreview(
    originalLatitude: Double,
    originalLongitude: Double,
    currentLatitude: Double,
    currentLongitude: Double,
    activeSegmentRoute: List<RouteCoordinate>,
    nearbyPoles: List<PoleEntity>,
    onLocationAdjusted: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val mapView = remember {
        val osmdroidDir = File(context.filesDir, "osmdroid").apply { if (!exists()) mkdirs() }
        val tilesDir = File(osmdroidDir, "tiles").apply { if (!exists()) mkdirs() }
        val prefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidBasePath = osmdroidDir
        Configuration.getInstance().osmdroidTileCache = tilesDir

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(18.5)
            controller.setCenter(GeoPoint(currentLatitude, currentLongitude))
            isTilesScaledToDpi = true
        }
    }

    LaunchedEffect(originalLatitude, originalLongitude, activeSegmentRoute, nearbyPoles) {
        mapView.overlays.clear()

        // 1. Gambar Garis Kabel FO RNIMS (Biru #1565C0)
        if (activeSegmentRoute.isNotEmpty()) {
            val polyline = Polyline(mapView).apply {
                outlinePaint.color = AndroidColor.parseColor("#1565C0")
                outlinePaint.strokeWidth = 6.0f
                setPoints(activeSegmentRoute.map { GeoPoint(it.latitude, it.longitude) })
            }
            mapView.overlays.add(polyline)
        }

        // 2. Gambar Pin Tiang di sekitar (Eksisting = Cyan, Baru/Selesai = Hijau)
        nearbyPoles.forEach { pole ->
            if (pole.latitude != 0.0 && pole.longitude != 0.0) {
                val marker = Marker(mapView).apply {
                    position = GeoPoint(pole.latitude, pole.longitude)
                    title = pole.assetUid.ifEmpty { pole.poleCode }
                    snippet = "Urut #${pole.sequence} • ${pole.assetClass}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        val colorHex = if (pole.isExistingPole) "#00BCD4" else "#4CAF50"
                        setColor(AndroidColor.parseColor(colorHex))
                        setStroke(2, AndroidColor.WHITE)
                        setSize(22, 22)
                    }
                    icon = shape
                }
                mapView.overlays.add(marker)
            }
        }

        // 3. Marker GPS Original Surveyor
        val originalGpsMarker = Marker(mapView).apply {
            position = GeoPoint(originalLatitude, originalLongitude)
            title = "Posisi GPS Surveyor"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AndroidColor.parseColor("#90CAF9"))
                setStroke(3, AndroidColor.parseColor("#1565C0"))
                setSize(26, 26)
            }
            icon = shape
        }
        mapView.overlays.add(originalGpsMarker)

        mapView.invalidate()
    }

    DisposableEffect(mapView) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                val center = mapView.mapCenter
                if (center != null) {
                    onLocationAdjusted(center.latitude, center.longitude)
                }
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val center = mapView.mapCenter
                if (center != null) {
                    onLocationAdjusted(center.latitude, center.longitude)
                }
                return false
            }
        }
        mapView.addMapListener(mapListener)
        onDispose {
            mapView.removeMapListener(mapListener)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = {
                mapView.apply {
                    setOnTouchListener { view, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE -> {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Stationary Center Pin (Diam di tengah, peta yang digeser)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = TelecomPrimary,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Titik Tiang",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(8.dp)
                    .background(TelecomPrimary)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFFC62828), CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            )
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.70f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Text(
                text = "📍 Geser peta untuk sesuaikan titik dasar tiang",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        SmallFloatingActionButton(
            onClick = {
                mapView.controller.animateTo(GeoPoint(originalLatitude, originalLongitude))
                onLocationAdjusted(originalLatitude, originalLongitude)
            },
            containerColor = Color.White,
            contentColor = TelecomPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Kembali ke Titik GPS Awal",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * CameraX Overlay dengan Panduan Siluet Vertikal Tiang & Sensor Leveling
 */
@Composable
fun QuickCameraOverlay(
    poleId: String,
    lat: Double,
    lng: Double,
    onPhotoCaptured: (File, String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var isBaseAndTopConfirmed by remember { mutableStateOf(true) }

    // Pitch sensor monitoring (Level indicator)
    var pitchAngle by remember { mutableStateOf(90.0) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    val y = event.values[1].toDouble()
                    val z = event.values[2].toDouble()
                    val pitch = Math.toDegrees(atan2(y, z))
                    pitchAngle = pitch
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val isPhoneUpright = pitchAngle in 65.0..115.0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
            onImageCaptureCreated = { capture ->
                imageCapture = capture
            }
        )

        // PANDUAN SILUET VERTIKAL TIANG (OVERLAY VISUAL SESUAI POLE_INVENTORY_ARCHITECTURE.MD)
        VerticalPoleSilhouetteOverlay(
            isPhoneUpright = isPhoneUpright,
            modifier = Modifier.fillMaxSize()
        )

        // Header Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.clickable { onClose() }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup",
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "Aset: $poleId",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Controls: Base & Top Confirmation + Shutter Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Konfirmasi Base & Top Visible Chip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isBaseAndTopConfirmed) Color(0xFF2E7D32).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .clickable { isBaseAndTopConfirmed = !isBaseAndTopConfirmed }
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBaseAndTopConfirmed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Base & Top Visible (Puncak & Pangkal Terlihat)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Shutter Button
            Surface(
                modifier = Modifier
                    .size(76.dp)
                    .clickable(enabled = !isCapturing) {
                        val capture = imageCapture
                        if (capture != null && !isCapturing) {
                            isCapturing = true
                            val executor = ContextCompat.getMainExecutor(context)
                            takePhoto(
                                context = context,
                                imageCapture = capture,
                                executor = executor,
                                poleId = poleId,
                                lat = lat,
                                lng = lng,
                                onPhotoSaved = { file ->
                                    isCapturing = false
                                    val sha256 = calculateFileSha256(file)
                                    onPhotoCaptured(file, sha256)
                                },
                                onError = { error ->
                                    isCapturing = false
                                    Toast.makeText(context, "Gagal mengambil foto: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = TelecomPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(62.dp),
                            shape = CircleShape,
                            color = TelecomPrimary
                        ) {}
                    }
                }
            }
        }
    }
}

/**
 * Komponen Siluet Vertikal Tiang Sesuai POLE_INVENTORY_ARCHITECTURE.md (Non-AR Visual Framing)
 */
@Composable
fun VerticalPoleSilhouetteOverlay(
    isPhoneUpright: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f

            val topSafeY = canvasHeight * 0.14f
            val bottomSafeY = canvasHeight * 0.78f

            val frameColor = if (isPhoneUpright) Color(0xFF00E676) else Color(0xFFFFB300)

            // Garis Batas Puncak Tiang (Top Margin ~14%)
            drawLine(
                color = frameColor,
                start = androidx.compose.ui.geometry.Offset(centerX - 90f, topSafeY),
                end = androidx.compose.ui.geometry.Offset(centerX + 90f, topSafeY),
                strokeWidth = 3f
            )

            // Garis Batas Pangkal Tiang / Tanah (Bottom Margin ~78%)
            drawLine(
                color = frameColor,
                start = androidx.compose.ui.geometry.Offset(centerX - 90f, bottomSafeY),
                end = androidx.compose.ui.geometry.Offset(centerX + 90f, bottomSafeY),
                strokeWidth = 3f
            )

            // Garis Vertikal Tengah Panduan
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(centerX, topSafeY),
                end = androidx.compose.ui.geometry.Offset(centerX, bottomSafeY),
                strokeWidth = 2f
            )

            // Siluet Batang Tiang
            val shaftW = 28f
            drawRect(
                color = frameColor.copy(alpha = 0.35f),
                topLeft = androidx.compose.ui.geometry.Offset(centerX - shaftW / 2, topSafeY + 25f),
                size = androidx.compose.ui.geometry.Size(shaftW, bottomSafeY - topSafeY - 25f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
            )

            // Siluet Breket / Crossarm Atas
            drawRect(
                color = frameColor.copy(alpha = 0.45f),
                topLeft = androidx.compose.ui.geometry.Offset(centerX - 65f, topSafeY + 40f),
                size = androidx.compose.ui.geometry.Size(130f, 12f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }

        // Label Puncak
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 85.dp)
        ) {
            Text(
                text = "▲ PUNCAK TIANG (TOP SAFE ZONE)",
                color = if (isPhoneUpright) Color(0xFF00E676) else Color(0xFFFFB300),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // Label Pangkal
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 150.dp)
        ) {
            Text(
                text = "▼ PANGKAL TIANG / TANAH (BASE SAFE ZONE)",
                color = if (isPhoneUpright) Color(0xFF00E676) else Color(0xFFFFB300),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // Petunjuk Leveling & Jarak
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.70f),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-90).dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isPhoneUpright) Icons.Default.Check else Icons.Default.ScreenRotation,
                    contentDescription = null,
                    tint = if (isPhoneUpright) Color(0xFF00E676) else Color(0xFFFFB300),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPhoneUpright) "Posisi HP Tegak • Jarak Ideal 10–15 m" else "Posisikan HP Lebih Tegak Lurus",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
