package co.id.lintasarta.tagpollinginventory.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import co.id.lintasarta.tagpollinginventory.data.model.ExportFormat
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.export.ExportEngine
import co.id.lintasarta.tagpollinginventory.ui.components.calculateRealDistanceMeters
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * LAYAR 4: REVIEW, EXPORT & SYNC SERVER
 *
 * Mengakomodasi 100% Standar Alpaca PRA & Architecture v2.3:
 * - Statistik Segmen: Total Tiang, Total T7 (pole_7), Total T9 (pole_9), Rata-rata Span Jarak Tiang.
 * - Tombol 1: "Export Alpaca Master CSV" (Alpaca_Master_Poles.csv)
 * - Tombol 2: "Export Alpaca Master KMZ" (Alpaca_Master.kmz)
 * - Tombol 3: "Sinkronisasi ke API Alpaca RNIMS / eNims"
 * - Daftar Tiang Detail dengan indikator P7/P9, Safety Deferral, dan SHA-256 watermark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewExportSyncScreen(
    viewModel: MainViewModel,
    onNavigateToTagging: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val project by viewModel.project.collectAsState()
    val polesMap by viewModel.poles.collectAsState()
    val isSyncingPoles by viewModel.isSyncingPoles.collectAsState()
    val rnimsBaseUrl by viewModel.rnimsBaseUrl.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val selectedSegmentId by viewModel.selectedSegmentId.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()

    val exportEngine = remember { ExportEngine(context) }

    val allPoles = remember(polesMap) {
        polesMap.values.toList().sortedWith(
            compareByDescending<Pole> { it.capturedTimestamp }.thenBy { it.sequence }
        )
    }

    var selectedFilterSegment by remember { mutableStateOf("ALL") }
    var selectedFilterStatus by remember { mutableStateOf("ALL") } // ALL, PENDING, SYNCED, SAFETY
    var syncResultDialogMessage by remember { mutableStateOf<String?>(null) }
    var syncSuccessState by remember { mutableStateOf(false) }

    // Export Result Dialog State
    var exportedFileResult by remember { mutableStateOf<File?>(null) }
    var isExportingLocal by remember { mutableStateOf(false) }

    val filteredPoles = remember(allPoles, selectedFilterSegment, selectedFilterStatus) {
        allPoles.filter { pole ->
            val matchSegment = selectedFilterSegment == "ALL" || pole.segmentId == selectedFilterSegment
            val matchStatus = when (selectedFilterStatus) {
                "PENDING" -> pole.syncStatus == "PENDING"
                "SYNCED" -> pole.syncStatus == "SYNCED"
                "SAFETY" -> pole.isSafetyDeferred
                else -> true
            }
            matchSegment && matchStatus
        }
    }

    // Statistik Alpaca PRA
    val totalPolesCount = allPoles.size
    val totalP7Count = allPoles.count { it.assetClass == "pole_7" }
    val totalP9Count = allPoles.count { it.assetClass == "pole_9" || it.assetClass.isBlank() }
    val pendingPolesCount = allPoles.count { it.syncStatus == "PENDING" }
    val syncedPolesCount = allPoles.count { it.syncStatus == "SYNCED" }
    val safetyDeferredCount = allPoles.count { it.isSafetyDeferred }

    // Estimasi Rata-rata Span Jarak Antar-Tiang
    val averageSpanMeters = remember(allPoles) {
        val sortedBySeq = allPoles.filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .sortedBy { it.sequence }
        if (sortedBySeq.size >= 2) {
            var totalDist = 0.0
            var count = 0
            for (i in 0 until sortedBySeq.size - 1) {
                val d = calculateRealDistanceMeters(
                    sortedBySeq[i].latitude,
                    sortedBySeq[i].longitude,
                    sortedBySeq[i + 1].latitude,
                    sortedBySeq[i + 1].longitude
                )
                if (d in 5.0..200.0) { // filter out outlier distance
                    totalDist += d
                    count++
                }
            }
            if (count > 0) (totalDist / count).roundToInt() else 45
        } else {
            50
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = TelecomPrimary,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Sync & Export",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Area ${currentUser.areaId} ${currentUser.areaLabel.uppercase()} • Alpaca PRA Master & Sync",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    // Staging Mode Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (networkStatus.isOnline) Color(0xFF1B5E20) else Color(0xFF455A64)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (networkStatus.isOnline) Color(0xFF00E676) else Color(0xFFFFB74D))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (rnimsBaseUrl.contains("127.0.0.1")) "USB (ADB)" else if (networkStatus.isOnline) "ONLINE" else "OFFLINE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NeutralBackground)
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // 1. STATISTIK INVENTARIS ALPACA PRA
            // =========================================================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
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
                            Text(
                                text = "RINGKASAN ASET TIANG SEWA (PRA)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TelecomPrimary,
                                letterSpacing = 0.8.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = "Span Rata-rata: ±${averageSpanMeters}m",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox(
                                label = "Total Tiang",
                                value = "$totalPolesCount",
                                icon = Icons.Default.LocationOn,
                                color = TelecomPrimary
                            )
                            StatBox(
                                label = "Tiang 7M (T7)",
                                value = "$totalP7Count",
                                icon = Icons.Default.Height,
                                color = Color(0xFFFF9800)
                            )
                            StatBox(
                                label = "Tiang 9M (T9)",
                                value = "$totalP9Count",
                                icon = Icons.Default.VerticalAlignTop,
                                color = Color(0xFF1565C0)
                            )
                            StatBox(
                                label = "Pending Sync",
                                value = "$pendingPolesCount",
                                icon = Icons.Outlined.CloudQueue,
                                color = Color(0xFFEF6C00)
                            )
                        }

                        if (safetyDeferredCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFEBEE),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFC62828),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$safetyDeferredCount tiang tercatat Approach Safety Deferral (Terhalang medan/bahaya)",
                                        fontSize = 11.sp,
                                        color = Color(0xFFC62828),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 2. TIGA TOMBOL AKSI UTAMA (CSV, KMZ, SYNC SERVER)
            // =========================================================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "AKSI EKSPOR & SINKRONISASI ALPACA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF37474F)
                        )

                        // TOMBOL 1: EXPORT ALPACA MASTER CSV (Alpaca_Master_Poles.csv)
                        Button(
                            onClick = {
                                if (allPoles.isEmpty()) {
                                    Toast.makeText(context, "Belum ada tiang untuk diekspor.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isExportingLocal = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val csvFile = exportEngine.generateAlpacaMasterCsv(allPoles)
                                    withContext(Dispatchers.Main) {
                                        isExportingLocal = false
                                        exportedFileResult = csvFile
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export Alpaca Master CSV (Alpaca_Master_Poles.csv)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // TOMBOL 2: EXPORT ALPACA MASTER KMZ (Alpaca_Master.kmz)
                        Button(
                            onClick = {
                                if (allPoles.isEmpty()) {
                                    Toast.makeText(context, "Belum ada tiang untuk diekspor.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isExportingLocal = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val kmzFile = exportEngine.generateAlpacaMasterKmz(
                                        poles = allPoles,
                                        segmentName = project.name,
                                        importedRoute = activeRoute
                                    )
                                    withContext(Dispatchers.Main) {
                                        isExportingLocal = false
                                        exportedFileResult = kmzFile
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export Alpaca Master KMZ (Alpaca_Master.kmz)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // TOMBOL 3: SINKRONISASI KE API DASHBOARD ALPACA RNIMS / eNims
                        Button(
                            onClick = {
                                if (pendingPolesCount == 0 && totalPolesCount == 0) {
                                    Toast.makeText(context, "Belum ada data tiang yang disurvei.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                viewModel.syncPolesToServer { success, msg ->
                                    syncSuccessState = success
                                    syncResultDialogMessage = msg
                                }
                            },
                            enabled = !isSyncingPoles && !isExportingLocal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pendingPolesCount > 0) Color(0xFF2E7D32) else Color(0xFF455A64)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSyncingPoles) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mengirim ke API Alpaca PRA...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sinkronisasi ke API Alpaca RNIMS / eNims",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 3. FILTER LIST TIANG
            // =========================================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DAFTAR TIANG LAPANGAN (${filteredPoles.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF455A64)
                    )

                    // Status Filters
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusFilterChip(
                            label = "Semua",
                            selected = selectedFilterStatus == "ALL",
                            onClick = { selectedFilterStatus = "ALL" }
                        )
                        StatusFilterChip(
                            label = "Pending",
                            selected = selectedFilterStatus == "PENDING",
                            onClick = { selectedFilterStatus = "PENDING" }
                        )
                        StatusFilterChip(
                            label = "Synced",
                            selected = selectedFilterStatus == "SYNCED",
                            onClick = { selectedFilterStatus = "SYNCED" }
                        )
                    }
                }
            }

            // =========================================================================
            // 4. LIST TIANG SURVEI
            // =========================================================================
            if (filteredPoles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inbox,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Belum Ada Tiang yang Disurvei",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF37474F)
                            )
                            Text(
                                text = "Gunakan Quick Tagging Canvas untuk mulai mencatat tiang di lapangan (< 1 menit per tiang).",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                items(filteredPoles, key = { it.id }) { pole ->
                    PoleReviewItemCard(
                        pole = pole,
                        onDeleteClick = {
                            viewModel.repository.deletePole(pole.id)
                            Toast.makeText(context, "Aset ${pole.effectiveAssetUid} dihapus", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // DIALOG HASIL EKSPOR FILE
    if (exportedFileResult != null) {
        val exported = exportedFileResult!!
        AlertDialog(
            onDismissRequest = { exportedFileResult = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(text = "Ekspor Berhasil", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "File ${exported.name} berhasil dibuat dan siap dibagikan.",
                        fontSize = 13.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF5F5F5),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Path: ${exported.absolutePath}\nUkuran: ${exported.length() / 1024} KB",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                exported
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = if (exported.name.endsWith(".csv")) "text/csv" else "application/vnd.google-earth.kmz"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan File Ekspor Alpaca"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        exportedFileResult = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan File")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportedFileResult = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    // DIALOG HASIL SINKRONISASI SERVER
    if (syncResultDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { syncResultDialogMessage = null },
            icon = {
                Icon(
                    imageVector = if (syncSuccessState) Icons.Default.CheckCircle else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (syncSuccessState) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (syncSuccessState) "Sinkronisasi Berhasil" else "Hasil Sinkronisasi Server",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = syncResultDialogMessage ?: "",
                        fontSize = 14.sp
                    )
                    if (!syncSuccessState) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF3E0),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Catatan: Mode Offline aktif. Seluruh data koordinat & foto tersimpan 100% aman di database Room SQLite lokal HP dan tidak akan hilang.",
                                fontSize = 12.sp,
                                color = Color(0xFFE65100),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { syncResultDialogMessage = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (syncSuccessState) Color(0xFF2E7D32) else TelecomPrimary
                    )
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun StatBox(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        modifier = Modifier.width(76.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF546E7A)
            )
        }
    }
}

@Composable
fun StatusFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) TelecomPrimary else Color.White,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCFD8DC)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFF546E7A),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun PoleReviewItemCard(
    pole: Pole,
    onDeleteClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sequence Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE3F2FD)
                    ) {
                        Text(
                            text = "#${pole.sequence}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TelecomPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Asset UID Resmi Alpaca PRA: POL-MPO-[KOTA]-[SEQ:04d]
                    Text(
                        text = pole.effectiveAssetUid,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF263238),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Badges: T7/T9 Klasifikasi & Sync Status
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Badge T7/T9
                    val isP7 = pole.assetClass == "pole_7"
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isP7) Color(0xFFFFF3E0) else Color(0xFFE3F2FD)
                    ) {
                        Text(
                            text = if (isP7) "T7 (7M)" else "T9 (9M)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isP7) Color(0xFFE65100) else TelecomPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Sync Status Badge
                    val isSynced = pole.syncStatus == "SYNCED"
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSynced) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = if (isSynced) "SYNCED" else "PENDING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSynced) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFECEFF1))

            // Koordinat & Akurasi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TelecomPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.6f", pole.latitude)}, ${String.format(Locale.US, "%.6f", pole.longitude)}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF37474F)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF1F8E9)
                ) {
                    Text(
                        text = "±${String.format(Locale.US, "%.1f", pole.accuracy)}m",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF33691E),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Safety Deferral Info jika ada
            if (pole.isSafetyDeferred) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Safety Deferral: ${pole.safetyDeferralReason ?: "Terhalang Medan"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }

            // Atribut & SHA-256 Photo Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF5F5F5)
                    ) {
                        Text(
                            text = pole.ownership.displayName,
                            fontSize = 10.sp,
                            color = Color(0xFF616161),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF5F5F5)
                    ) {
                        Text(
                            text = pole.condition.displayName,
                            fontSize = 10.sp,
                            color = Color(0xFF616161),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!pole.photoPath.isNullOrEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFF00897B),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (!pole.photoSha256.isNullOrEmpty()) "SHA-256 Terverifikasi" else "Foto Terlampir",
                                fontSize = 11.sp,
                                color = Color(0xFF00897B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus",
                            tint = Color(0xFFB0BEC5),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
