package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.data.model.TagStatus
import co.id.lintasarta.tagpollinginventory.ui.components.OsmSegmentDetailMapView
import co.id.lintasarta.tagpollinginventory.ui.components.TagStatusBadge
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.util.Locale

/**
 * LAYAR DETAIL SEGMEN DENGAN MINIMAP RUTE & DATA TIANG (PL 1, PL 2, ...)
 *
 * Fitur:
 * 1. Minimap interaktif OpenStreetMap di bagian atas menampilkan rute polyline FO segmen terpilih.
 * 2. Seluruh tiang pada segmen ini ditampilkan di minimap dengan label badge "PL 1", "PL 2", dst.
 *    beserta pewarnaan status (Hijau = Selesai, Merah = Konflik, Biru = Belum Tagging).
 * 3. Box Mode Survei di bawah minimap (Quick Tagging < 1 Menit & Tag Polling PM).
 * 4. Daftar tiang (Pole Inventory List) di bawahnya mengikuti urutan sequence tiang.
 * 5. Mengetuk kartu tiang akan menyorot tiang tersebut di minimap di atas.
 */
@Composable
fun SegmentDetailScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onStartTagging: (String) -> Unit
) {
    val project by viewModel.project.collectAsState()
    val segmentId by viewModel.selectedSegmentId.collectAsState()
    val polesMap by viewModel.poles.collectAsState()
    val targetPoleId by viewModel.targetPoleId.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val mpoList by viewModel.mpoSegments.collectAsState()

    // Cari segmen aktif berdasarkan ID atau nama segmen
    val activeSegment = remember(project, segmentId, mpoList) {
        val found = project.segments.find { 
            it.id == segmentId || 
            it.name.equals(segmentId, ignoreCase = true) ||
            it.id == "SEG-$segmentId" ||
            it.id.removePrefix("SEG-") == segmentId.removePrefix("SEG-")
        }
        if (found != null) {
            found
        } else {
            // Fallback lookup dari katalog 143 MPO
            val numericId = segmentId.removePrefix("SEG-").toIntOrNull()
            val dto = mpoList.find { it.id == numericId || it.segment_name.equals(segmentId, ignoreCase = true) }
            if (dto != null) {
                val coords = dto.route_path?.map { RouteCoordinate(latitude = it.lat, longitude = it.lng) } ?: emptyList()
                Segment(
                    id = "SEG-${dto.id}",
                    name = dto.segment_name,
                    lengthMeters = dto.distance,
                    routeCoordinates = coords,
                    areaId = 18,
                    orgSite = dto.org_site ?: "",
                    dstSite = dto.dst_site ?: "",
                    orgCity = dto.org_city ?: "",
                    dstCity = dto.dst_city ?: "",
                    cityFilterKey = dto.org_city ?: "MPO",
                    route = "${dto.org_site ?: ""} -> ${dto.dst_site ?: ""}",
                    isAlpacaProject = true,
                    status = SegmentStatus.NOT_STARTED
                )
            } else {
                project.segments.firstOrNull() ?: Segment(
                    id = "",
                    name = segmentId.ifEmpty { "Segment Area 18" },
                    route = "T/A",
                    status = SegmentStatus.NOT_STARTED
                )
            }
        }
    }

    // Koordinat rute efektif (fallback ke MPO JSON jika belum tersimpan di Room)
    val effectiveRouteCoords = remember(activeSegment, mpoList) {
        if (activeSegment.routeCoordinates.size >= 2) {
            activeSegment.routeCoordinates
        } else {
            val numericId = activeSegment.id.removePrefix("SEG-").toIntOrNull()
            val dto = mpoList.find { it.id == numericId || it.segment_name.equals(activeSegment.name, ignoreCase = true) }
            dto?.route_path?.map { RouteCoordinate(latitude = it.lat, longitude = it.lng) } ?: emptyList()
        }
    }

    // Seluruh tiang yang terdaftar pada segmen ini, diurutkan berdasarkan sequence (PL 1, PL 2, dst.)
    val segmentPoles = remember(polesMap, activeSegment) {
        polesMap.values.filter { 
            it.segmentId == activeSegment.id || 
            it.segmentId == activeSegment.name ||
            it.segmentId.removePrefix("SEG-") == activeSegment.id.removePrefix("SEG-")
        }.sortedBy { it.sequence }
    }

    val completedCount = segmentPoles.count { it.status == TagStatus.COMPLETED }
    val conflictCount = segmentPoles.count { it.status == TagStatus.CONFLICT }
    val remainingCount = segmentPoles.size - completedCount

    var showCompleteDialog by remember { mutableStateOf(false) }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Selesaikan Segment?") },
            text = { Text("Anda telah mensurvei $completedCount tiang pada segmen ini.\nSegment akan ditandai sebagai selesai.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markActiveSegmentCompleted()
                    showCompleteDialog = false
                }) {
                    Text("SELESAIKAN SEGMENT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) {
                    Text("BATAL")
                }
            }
        )
    }

    val subtitleText = remember(activeSegment) {
        val routeStr = when {
            activeSegment.route.isNotBlank() && activeSegment.route != "T/A" && activeSegment.route != "N/A" -> activeSegment.route
            activeSegment.orgSite.isNotBlank() && activeSegment.dstSite.isNotBlank() -> "${activeSegment.orgSite} -> ${activeSegment.dstSite}"
            activeSegment.orgCity.isNotBlank() -> activeSegment.orgCity
            else -> "Rute Fiber Optic Area 18"
        }
        val distKm = (effectiveRouteCoords.size * 0.05).coerceAtLeast(activeSegment.lengthMeters / 1000.0)
        if (distKm > 0.0) {
            "$routeStr • ${String.format(Locale.US, "%.1f", distKm)} km"
        } else {
            routeStr
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = activeSegment.name,
                subtitle = subtitleText,
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
        ) {
            // 1. STATS METRIC BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Tiang", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                    Text("${segmentPoles.size}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Selesai", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontSize = 10.sp)
                    Text("$completedCount", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2E7D32))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tersisa", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                    Text("$remainingCount", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Konflik", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontSize = 10.sp)
                    Text("$conflictCount", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFC62828))
                }
            }

            HorizontalDivider()

            // 2. MINIMAP INTERAKTIF OPENSTREETMAP: RUTE SEGMEN & MARKER TIANG (PL 1, PL 2, ...)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clipToBounds()
            ) {
                OsmSegmentDetailMapView(
                    segment = activeSegment,
                    routeCoordinates = effectiveRouteCoords,
                    poles = segmentPoles,
                    selectedPoleId = targetPoleId,
                    userLocation = currentLocation,
                    onPoleClick = { pole ->
                        viewModel.startTaggingPole(pole.id)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            HorizontalDivider()

            // 3. BOX PILIH MODE SURVEI UNTUK SEGMEN INI
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val targetPole = segmentPoles.find { it.id == targetPoleId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (targetPole != null) "Target: PL ${targetPole.sequence} (${targetPole.effectiveAssetUid})" else "Pilih Mode Survei untuk Segment Ini:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TelecomPrimary
                            )
                            Text(
                                text = if (targetPole != null) "Lanjutkan survei tiang target dengan salah satu mode di bawah:" else "Mulai pengambilan data tiang baru:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tombol 1: Quick Tagging (< 1 Menit)
                        Button(
                            onClick = {
                                if (targetPoleId.isNotEmpty()) {
                                    viewModel.startQuickTaggingForTargetPole(targetPoleId)
                                } else {
                                    viewModel.startQuickTaggingForSegment(activeSegment.id)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Quick Tagging",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }

                        // Tombol 2: Tag Polling PM (Survei Lengkap)
                        Button(
                            onClick = {
                                if (targetPoleId.isNotEmpty()) {
                                    onStartTagging(targetPoleId)
                                } else {
                                    viewModel.startPmSurveyForSegment(activeSegment.id)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = if (targetPoleId.isNotEmpty()) Icons.Default.PlayArrow else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tag Polling PM",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // 4. DATA TIANG MENGIKUTI (POLE INVENTORY LIST)
            if (segmentPoles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(NeutralBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE3F2FD),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = TelecomPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "Belum Ada Data Tiang",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF263238)
                        )

                        Text(
                            text = "Rute kabel fiber optic sepanjang ${(effectiveRouteCoords.size * 0.05).coerceAtLeast(activeSegment.lengthMeters / 1000.0).coerceAtLeast(0.5)} km telah dimuat di minimap atas.\nKlik tombol di bawah untuk mulai mensurvei tiang pertama (PL 1).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.startQuickTaggingForSegment(activeSegment.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mulai Tagging Tiang Pertama (PL 1)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(NeutralBackground)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TIANG PADA SEGMEN INI (${segmentPoles.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF546E7A)
                            )

                            TextButton(onClick = { viewModel.createAndStartNewPole(activeSegment.id) }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah Tiang", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    if (activeSegment.status != SegmentStatus.COMPLETED && segmentPoles.isNotEmpty()) {
                        item {
                            Button(
                                onClick = { showCompleteDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TANDAI SEGMENT SELESAI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    items(segmentPoles) { pole ->
                        val isSelected = (pole.id == targetPoleId)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.startTaggingPole(pole.id)
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = if (isSelected) BorderStroke(2.dp, Color(0xFF00E5FF)) else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Badge Sequence Tiang: PL 1, PL 2, dst.
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isSelected -> Color(0xFF00E5FF)
                                            pole.status == TagStatus.COMPLETED -> Color(0xFFE8F5E9)
                                            pole.status == TagStatus.CONFLICT -> Color(0xFFFFEBEE)
                                            else -> Color(0xFFE3F2FD)
                                        }
                                    ) {
                                        Text(
                                            text = "PL ${pole.sequence}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = when {
                                                isSelected -> Color(0xFF004D40)
                                                pole.status == TagStatus.COMPLETED -> Color(0xFF2E7D32)
                                                pole.status == TagStatus.CONFLICT -> Color(0xFFC62828)
                                                else -> TelecomPrimary
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pole.effectiveAssetUid,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF263238)
                                        )
                                        Text(
                                            text = "${pole.assetClass.uppercase()} (${pole.height}) • ${pole.type.displayName} • ${pole.ownership.displayName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                        if (pole.latitude != 0.0 && pole.longitude != 0.0) {
                                            Text(
                                                text = "GPS: ${String.format(Locale.US, "%.5f", pole.latitude)}, ${String.format(Locale.US, "%.5f", pole.longitude)} (±${pole.accuracy}m)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF546E7A),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TagStatusBadge(status = pole.status)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
