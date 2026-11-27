package co.id.lintasarta.tagpollinginventory.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.export.ExportEngine
import co.id.lintasarta.tagpollinginventory.network.GDriveUploadResponse
import co.id.lintasarta.tagpollinginventory.ui.components.NetworkStatusDot
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
    val networkStatus by viewModel.networkStatus.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()

    val exportEngine = remember { ExportEngine(context) }

    val allPoles = remember(polesMap) {
        polesMap.values.toList().sortedWith(
            compareByDescending<Pole> { it.capturedTimestamp }.thenBy { it.sequence }
        )
    }

    // Export & Upload States
    var isExportingKmz by remember { mutableStateOf(false) }
    var isExportingCsv by remember { mutableStateOf(false) }
    var exportedFileResult by remember { mutableStateOf<File?>(null) }

    val isUploadingGDrive by viewModel.isUploadingGDrive.collectAsState()
    val gdriveUploadMessage by viewModel.gdriveUploadMessage.collectAsState()
    var gdriveUploadResultDialog by remember { mutableStateOf<GDriveUploadResponse?>(null) }
    var gdriveUploadErrorDialog by remember { mutableStateOf<String?>(null) }
    var poleToDelete by remember { mutableStateOf<Pole?>(null) }

    val totalPolesCount = allPoles.size
    val totalP7Count = allPoles.count { it.assetClass == "pole_7" }
    val totalP9Count = allPoles.count { it.assetClass == "pole_9" || it.assetClass.isBlank() }
    val photosCount = allPoles.count { !it.photoPath.isNullOrBlank() }
    val safetyDeferredCount = allPoles.count { it.isSafetyDeferred }

    Scaffold(
        topBar = {
            Surface(
                color = TelecomPrimary,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Sync & Export",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Area ${currentUser.areaId} - ${currentUser.areaLabel} • $totalPolesCount Tiang Disurvei",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    NetworkStatusDot(networkStatus = networkStatus, size = 10.dp)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NeutralBackground)
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. RINGKASAN STATISTIK
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                            Text(
                                text = "RINGKASAN SURVEI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TelecomPrimary
                            )
                            if (photosCount > 0) {
                                Text(
                                    text = "$photosCount Foto Terlampir",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatBox(
                                label = "Total Tiang",
                                value = "$totalPolesCount",
                                icon = Icons.Default.LocationOn,
                                color = TelecomPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Tiang 7M",
                                value = "$totalP7Count",
                                icon = Icons.Default.Height,
                                color = Color(0xFFE65100),
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Tiang 9M",
                                value = "$totalP9Count",
                                icon = Icons.Default.VerticalAlignTop,
                                color = Color(0xFF1565C0),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (safetyDeferredCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFEBEE),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFC62828),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$safetyDeferredCount tiang tercatat Safety Deferral",
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

            // 2. AKSI EKSPOR & UPLOAD (RESPONSIF & BERSIH)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "EKSPOR & CADANGAN DATA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF37474F)
                        )

                        // TOMBOL UTAMA: UPLOAD GOOGLE DRIVE
                        Button(
                            onClick = {
                                if (allPoles.isEmpty()) {
                                    Toast.makeText(context, "Belum ada tiang yang disurvei untuk dikirim.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.uploadSurveyToGoogleDrive { success, resp, msg ->
                                    if (success && resp != null) {
                                        gdriveUploadResultDialog = resp
                                    } else {
                                        gdriveUploadErrorDialog = msg
                                    }
                                }
                            },
                            enabled = !isUploadingGDrive && !isExportingKmz && !isExportingCsv,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isUploadingGDrive) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = gdriveUploadMessage ?: "Mengunggah KMZ...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Upload KMZ ke Google Drive",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // DUA TOMBOL EKSPOR LOKAL (KMZ & CSV)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Ekspor KMZ
                            Button(
                                onClick = {
                                    if (allPoles.isEmpty()) {
                                        Toast.makeText(context, "Belum ada tiang untuk diekspor.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isExportingKmz = true
                                    val surveyorInfo = "Di-tag oleh : ${currentUser.displayName.ifBlank { "Field Surveyor" }} (${viewModel.getDeviceModel()})"
                                    coroutineScope.launch {
                                        try {
                                            val kmzFile = withContext(Dispatchers.IO) {
                                                exportEngine.generateAlpacaMasterKmz(
                                                    poles = allPoles,
                                                    segmentName = project.name,
                                                    importedRoute = activeRoute,
                                                    surveyorInfo = surveyorInfo
                                                )
                                            }
                                            exportedFileResult = kmzFile
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal ekspor KMZ: ${e.message ?: "Error"}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isExportingKmz = false
                                        }
                                    }
                                },
                                enabled = !isUploadingGDrive && !isExportingKmz && !isExportingCsv,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isExportingKmz) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Proses...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ekspor KMZ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Ekspor CSV
                            Button(
                                onClick = {
                                    if (allPoles.isEmpty()) {
                                        Toast.makeText(context, "Belum ada tiang untuk diekspor.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isExportingCsv = true
                                    val surveyorInfo = "Di-tag oleh : ${currentUser.displayName.ifBlank { "Field Surveyor" }} (${viewModel.getDeviceModel()})"
                                    coroutineScope.launch {
                                        try {
                                            val csvFile = withContext(Dispatchers.IO) {
                                                exportEngine.generateAlpacaMasterCsv(allPoles, surveyorInfo = surveyorInfo)
                                            }
                                            exportedFileResult = csvFile
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal ekspor CSV: ${e.message ?: "Error"}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isExportingCsv = false
                                        }
                                    }
                                },
                                enabled = !isUploadingGDrive && !isExportingKmz && !isExportingCsv,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isExportingCsv) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Proses...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ekspor CSV", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. DAFTAR TIANG LAPANGAN
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Tiang ($totalPolesCount)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF455A64)
                    )
                }
            }

            if (allPoles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inbox,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Belum Ada Tiang Disurvei",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF37474F)
                            )
                            Text(
                                text = "Gunakan menu Tagging untuk mulai menandai tiang di lapangan.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                items(allPoles, key = { it.id }) { pole ->
                    PoleReviewItemCard(
                        pole = pole,
                        onDeleteClick = {
                            poleToDelete = pole
                        }
                    )
                }
            }
        }
    }

    // DIALOG KONFIRMASI HAPUS TIANG
    if (poleToDelete != null) {
        val pole = poleToDelete!!
        AlertDialog(
            onDismissRequest = { poleToDelete = null },
            title = { Text("Hapus Tiang Survei?", fontWeight = FontWeight.Bold) },
            text = { Text("Data tiang ${pole.effectiveAssetUid} akan dihapus dari penyimpanan lokal HP. Lanjutkan?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.repository.deletePole(pole.id)
                        Toast.makeText(context, "${pole.effectiveAssetUid} dihapus", Toast.LENGTH_SHORT).show()
                        poleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { poleToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG HASIL UPLOAD GOOGLE DRIVE
    if (gdriveUploadResultDialog != null) {
        val result = gdriveUploadResultDialog!!
        AlertDialog(
            onDismissRequest = { gdriveUploadResultDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = Color(0xFF1B5E20),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(text = "Tersimpan di Google Drive", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "File survei KMZ berhasil dikirim dan diarsipkan otomatis ke Google Drive.",
                        fontSize = 12.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Folder: ${result.folder_name ?: "Area_${currentUser.areaId}_${currentUser.areaLabel}"}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "File: ${result.file_name ?: ""}",
                                fontSize = 11.sp,
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!result.file_url.isNullOrBlank()) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.file_url))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Tidak ada browser untuk membuka tautan", Toast.LENGTH_SHORT).show()
                            }
                            gdriveUploadResultDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                    ) {
                        Text("Buka Link")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { gdriveUploadResultDialog = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (gdriveUploadErrorDialog != null) {
        AlertDialog(
            onDismissRequest = { gdriveUploadErrorDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(text = "Gagal Kirim ke Google Drive", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(text = gdriveUploadErrorDialog ?: "Terjadi kendala koneksi internet.", fontSize = 13.sp)
            },
            confirmButton = {
                Button(onClick = { gdriveUploadErrorDialog = null }) {
                    Text("OK")
                }
            }
        )
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
                Text(text = "Ekspor Berhasil", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "File ${exported.name} berhasil dibuat.",
                        fontSize = 12.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF5F5F5),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Ukuran: ${exported.length() / 1024} KB\nPath: ${exported.absolutePath}",
                            fontSize = 10.sp,
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
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan File Survei"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        exportedFileResult = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportedFileResult = null }) {
                    Text("Tutup")
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
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
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
                fontWeight = FontWeight.Bold,
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
fun PoleReviewItemCard(
    pole: Pole,
    onDeleteClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Baris 1: Urutan + Nama Tiang + Tipe (7M/9M)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFE3F2FD)
                    ) {
                        Text(
                            text = "#${pole.sequence}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = pole.effectiveAssetUid,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF263238),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val isP7 = pole.assetClass == "pole_7"
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isP7) Color(0xFFFFF3E0) else Color(0xFFE3F2FD)
                ) {
                    Text(
                        text = if (isP7) "7M" else "9M",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isP7) Color(0xFFE65100) else TelecomPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Baris 2: Koordinat, Akurasi, dan Foto Geotag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TelecomPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.5f", pole.latitude)}, ${String.format(Locale.US, "%.5f", pole.longitude)} (±${String.format(Locale.US, "%.1f", pole.accuracy)}m)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF455A64)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!pole.photoPath.isNullOrEmpty()) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Foto Ada",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(15.dp)
                        )
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

            // Safety Deferral Info jika ada
            if (pole.isSafetyDeferred) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Safety Deferral: ${pole.safetyDeferralReason ?: "Terhalang Medan"}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }
        }
    }
}
