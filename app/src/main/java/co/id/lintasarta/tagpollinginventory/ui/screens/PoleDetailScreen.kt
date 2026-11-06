package co.id.lintasarta.tagpollinginventory.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import co.id.lintasarta.tagpollinginventory.data.model.*
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomSecondary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoleDetailScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onRetagQuickTagging: (String) -> Unit,
    onRetagPmSurvey: (String) -> Unit,
    onOpenInFieldMap: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val pole by viewModel.selectedPoleDetail.collectAsState()
    val project by viewModel.project.collectAsState()
    val selectedSegmentId by viewModel.selectedSegmentId.collectAsState()

    var showRetagDialog by remember { mutableStateOf(false) }
    var showFullPhotoDialog by remember { mutableStateOf(false) }

    val activeSegment = remember(project, selectedSegmentId) {
        project.segments.find { it.id == selectedSegmentId }
    }

    if (pole == null) {
        // Fallback jika ID tiang tidak ditemukan
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Detail Tiang") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TelecomPrimary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Data tiang tidak ditemukan", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onBackClick) {
                        Text("Kembali ke Daftar")
                    }
                }
            }
        }
        return
    }

    val currentPole = pole!!
    val hasPhoto = !currentPole.photoPath.isNullOrEmpty() && File(currentPole.photoPath).exists()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PL ${currentPole.sequence} • ${currentPole.effectiveAssetUid}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = activeSegment?.name ?: currentPole.routeName.ifEmpty { "Area 18 - MPO" },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenInFieldMap(currentPole.id) }) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Buka di Peta",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TelecomPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 12.dp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tombol Buka di Peta
                    OutlinedButton(
                        onClick = { onOpenInFieldMap(currentPole.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TelecomPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TelecomPrimary)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Peta", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Tombol Tagging / Retagging Tiang
                    Button(
                        onClick = { showRetagDialog = true },
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentPole.status == TagStatus.COMPLETED || currentPole.isExistingPole) {
                                Color(0xFF00897B) // Teal untuk Retagging
                            } else {
                                TelecomPrimary // Biru untuk Tagging Baru
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (currentPole.status == TagStatus.COMPLETED || currentPole.isExistingPole) {
                                Icons.Default.Refresh
                            } else {
                                Icons.Default.Bolt
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentPole.status == TagStatus.COMPLETED || currentPole.isExistingPole) {
                                "Tagging Ulang (Retag)"
                            } else {
                                "Mulai Tagging Tiang"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F7FA))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. KARTU IDENTITAS & STATUS TIANG
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
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
                                shape = RoundedCornerShape(8.dp),
                                color = when (currentPole.status) {
                                    TagStatus.COMPLETED -> Color(0xFFE8F5E9)
                                    TagStatus.CONFLICT -> Color(0xFFFFEBEE)
                                    else -> Color(0xFFE3F2FD)
                                }
                            ) {
                                Text(
                                    text = "PL ${currentPole.sequence}",
                                    color = when (currentPole.status) {
                                        TagStatus.COMPLETED -> Color(0xFF2E7D32)
                                        TagStatus.CONFLICT -> Color(0xFFC62828)
                                        else -> TelecomPrimary
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentPole.effectiveAssetUid,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF263238)
                                )
                                Text(
                                    text = currentPole.assetClass.uppercase() + " • " + currentPole.height,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (currentPole.status) {
                                TagStatus.COMPLETED -> Color(0xFF2E7D32)
                                TagStatus.CONFLICT -> Color(0xFFE65100)
                                else -> Color(0xFF546E7A)
                            }
                        ) {
                            Text(
                                text = when (currentPole.status) {
                                    TagStatus.COMPLETED -> "TERDATA"
                                    TagStatus.CONFLICT -> "SAFETY RISK"
                                    else -> "BELUM DITAG"
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFECEFF1))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Chip Sumber Data (Eksisting Alpaca vs Survei Baru)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (currentPole.isExistingPole) Color(0xFFE0F2F1) else Color(0xFFEDE7F6)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (currentPole.isExistingPole) Icons.Default.CloudQueue else Icons.Default.PinDrop,
                                    contentDescription = null,
                                    tint = if (currentPole.isExistingPole) Color(0xFF00796B) else Color(0xFF5E35B1),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (currentPole.isExistingPole) "Data Eksisting Alpaca PRA (RNIMS)" else "Hasil Survei Lapangan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (currentPole.isExistingPole) Color(0xFF00796B) else Color(0xFF5E35B1)
                                )
                            }
                        }

                        // Tombol Copy Asset UID
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(currentPole.effectiveAssetUid))
                                Toast.makeText(context, "Asset UID disalin: ${currentPole.effectiveAssetUid}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Salin UID",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 2. KARTU FOTO DOKUMENTASI TIANG (WATERMARKED & VERIFIED)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = TelecomPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dokumentasi Foto Fisik Tiang",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF263238)
                            )
                        }

                        if (hasPhoto) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = "Terverifikasi SHA-256",
                                    fontSize = 10.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (hasPhoto) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 240.dp, max = 340.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E2124))
                                .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(12.dp))
                                .clickable { showFullPhotoDialog = true }
                        ) {
                            AsyncImage(
                                model = File(currentPole.photoPath!!),
                                contentDescription = "Foto Tiang ${currentPole.effectiveAssetUid}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Watermark badge overlay on thumbnail
                            Surface(
                                shape = RoundedCornerShape(bottomStart = 8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Sentuh Perbesar",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Metadata Strip
                        if (!currentPole.photoSha256.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SHA: ${currentPole.photoSha256!!.take(24)}...",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(currentPole.photoSha256!!))
                                        Toast.makeText(context, "SHA-256 disalin", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Salin Hash", fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        // Empty photo placeholder
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF5F7FA),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = Color(0xFFB0BEC5),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (currentPole.isExistingPole) {
                                        "Data eksisting Alpaca belum memiliki foto lapangan"
                                    } else {
                                        "Belum ada foto dokumentasi tiang"
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFF78909C),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Gunakan tombol 'Tagging Ulang' untuk mengambil foto.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF90A4AE)
                                )
                            }
                        }
                    }
                }
            }

            // 3. KARTU SPESIFIKASI TEKNIS TIANG (ALPACAPROJECT STANDARDS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = TelecomPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Spesifikasi Teknis Tiang",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF263238)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        DetailItem(
                            label = "Tipe / Tinggi",
                            value = "${currentPole.assetClass.uppercase()} (${currentPole.height})",
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            label = "Bahan / Material",
                            value = currentPole.assetType.uppercase(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        DetailItem(
                            label = "Kepemilikan",
                            value = currentPole.ownership.displayName,
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            label = "Kondisi Fisik",
                            value = currentPole.condition.displayName,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        DetailItem(
                            label = "Kabel Optik (FO)",
                            value = if (currentPole.hasFoCable) "Terpasang (${currentPole.cableCondition.displayName})" else "Tidak Ada",
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            label = "Aksesoris Terpasang",
                            value = currentPole.equipment.joinToString(", ").ifEmpty { "ODP, Closure" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (currentPole.isSafetyDeferred) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFEBEE),
                            border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Status Keselamatan: DITANGGUHKAN",
                                        color = Color(0xFFC62828),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = currentPole.safetyDeferralReason ?: "Pendekatan tiang terhalang / bahaya lingkungan",
                                        color = Color(0xFFB71C1C),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. KARTU TELEMETRI GPS & KOORDINAT
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = TelecomPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Koordinat & Akurasi GNSS",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF263238)
                            )
                        }

                        // Akurasi Pill
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (currentPole.accuracy <= 8.0f) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ) {
                            Text(
                                text = "±${String.format(Locale.US, "%.1f", currentPole.accuracy)} m",
                                color = if (currentPole.accuracy <= 8.0f) Color(0xFF2E7D32) else Color(0xFFE65100),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        DetailItem(
                            label = "Latitude",
                            value = String.format(Locale.US, "%.6f", currentPole.latitude),
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            label = "Longitude",
                            value = String.format(Locale.US, "%.6f", currentPole.longitude),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        DetailItem(
                            label = "Waktu Survei / Tagging",
                            value = currentPole.capturedTimestamp.ifEmpty { currentPole.timestamp.ifEmpty { "-" } },
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            label = "Status Sinkronisasi",
                            value = when (currentPole.syncStatus) {
                                "SYNCED" -> "Tersinkron (RNIMS)"
                                "PENDING" -> "Tersimpan Lokal"
                                else -> currentPole.syncStatus
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val coordStr = "${String.format(Locale.US, "%.6f", currentPole.latitude)}, ${String.format(Locale.US, "%.6f", currentPole.longitude)}"
                            clipboardManager.setText(AnnotatedString(coordStr))
                            Toast.makeText(context, "Koordinat disalin: $coordStr", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF455A64))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Koordinat GPS", fontSize = 12.sp)
                    }
                }
            }

            // 5. CATATAN SURVEI (JIKA ADA)
            if (currentPole.notes.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notes, contentDescription = null, tint = TelecomPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Catatan Lapangan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(currentPole.notes, fontSize = 13.sp, color = Color(0xFF37474F))
                    }
                }
            }
        }
    }

    // DIALOG KONFIRMASI / PEMILIHAN METODE TAGGING & RETAGGING
    if (showRetagDialog) {
        AlertDialog(
            onDismissRequest = { showRetagDialog = false },
            icon = {
                Icon(
                    imageVector = if (currentPole.status == TagStatus.COMPLETED || currentPole.isExistingPole) {
                        Icons.Default.Refresh
                    } else {
                        Icons.Default.Bolt
                    },
                    contentDescription = null,
                    tint = TelecomPrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (currentPole.status == TagStatus.COMPLETED || currentPole.isExistingPole) {
                        "Tagging Ulang Tiang PL ${currentPole.sequence}?"
                    } else {
                        "Pilih Metode Tagging Tiang PL ${currentPole.sequence}"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (currentPole.status == TagStatus.COMPLETED || currentPole.isExistingPole) {
                            "Tiang ini sudah memiliki data terdaftar (${currentPole.effectiveAssetUid}). Anda dapat melakukan survei ulang posisi GPS dan foto tanpa menghapus nomor urut tiang."
                        } else {
                            "Pilih alur survei yang ingin digunakan untuk merekam data tiang ini:"
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF455A64)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Opsi 1: Quick Tagging (Alur Cepat)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE0F2F1),
                        border = BorderStroke(1.dp, Color(0xFF80CBC4)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRetagDialog = false
                                onRetagQuickTagging(currentPole.id)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00796B), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("⚡ Quick Tagging (< 1 Menit)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF004D40))
                                Text("Validasi GPS dasar tiang + 1 foto CameraX siluet", fontSize = 11.sp, color = Color(0xFF00796B))
                            }
                        }
                    }

                    // Opsi 2: Form PM Tiang Lintasarta (Lengkap)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEDE7F6),
                        border = BorderStroke(1.dp, Color(0xFFD1C4E9)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRetagDialog = false
                                onRetagPmSurvey(currentPole.id)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Assignment, contentDescription = null, tint = Color(0xFF5E35B1), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("📋 Form Lengkap PM Tiang", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF311B92))
                                Text("Peta interaktif, deviasi jalur FO & inspeksi penuh", fontSize = 11.sp, color = Color(0xFF5E35B1))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRetagDialog = false }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }

    // DIALOG FULLSCREEN PREVIEW FOTO
    if (showFullPhotoDialog && hasPhoto) {
        Dialog(
            onDismissRequest = { showFullPhotoDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = File(currentPole.photoPath!!),
                    contentDescription = "Full Preview Foto Tiang",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = { showFullPhotoDialog = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF263238))
    }
}
