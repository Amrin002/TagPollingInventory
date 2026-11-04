package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.ui.components.NetworkStatusDot
import co.id.lintasarta.tagpollinginventory.ui.components.OsmMultiSegmentMapView
import co.id.lintasarta.tagpollinginventory.ui.components.SegmentStatusBadge
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaOverviewMapScreen(
    viewModel: MainViewModel,
    onNavigateToSurvey: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val project by viewModel.project.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()

    var selectedCity by remember { mutableStateOf("Semua") }
    var selectedSegment by remember { mutableStateOf<Segment?>(null) }
    var osmMapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Normalisasi kota untuk mengelompokkan 143 rute MPO
    fun getNormalizedCity(seg: Segment): String {
        val text = "${seg.description} ${seg.route} ${seg.name}".lowercase()
        return when {
            text.contains("jayapura") || text.contains("jyp") -> "Jayapura"
            text.contains("mimika") || text.contains("timika") || text.contains("tmk") -> "Timika"
            text.contains("ambon") || text.contains("amb") -> "Ambon"
            text.contains("sorong") || text.contains("srn") -> "Sorong"
            text.contains("manokwari") || text.contains("mwr") -> "Manokwari"
            text.contains("merauke") || text.contains("mrk") -> "Merauke"
            text.contains("biak") || text.contains("bik") -> "Biak"
            text.contains("nabire") || text.contains("nbr") -> "Nabire"
            text.contains("tual") || text.contains("tla") -> "Tual"
            text.contains("tanimbar") || text.contains("saumlaki") -> "Tanimbar"
            text.contains("aru") || text.contains("dobo") -> "Kep. Aru"
            else -> "Lainnya"
        }
    }

    val allSegments = remember(project.segments) {
        project.segments
    }

    // Hitung distribusi kota unik
    val cityGroups = remember(allSegments) {
        val counts = allSegments.groupBy { getNormalizedCity(it) }
        val prioritized = listOf("Semua", "Jayapura", "Timika", "Ambon", "Sorong", "Manokwari", "Merauke", "Biak", "Nabire", "Tual")
        val available = counts.keys.filter { it !in prioritized }.sorted()
        (prioritized + available).filter { it == "Semua" || (counts[it]?.isNotEmpty() == true) }
    }

    val filteredSegments = remember(allSegments, selectedCity) {
        if (selectedCity == "Semua") {
            allSegments
        } else {
            allSegments.filter { getNormalizedCity(it) == selectedCity }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = TelecomPrimary,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Peta Sebaran Segmen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Area ${currentUser.areaId} (${currentUser.areaLabel}) • ${filteredSegments.size} Rute Fiber Optic",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    NetworkStatusDot(networkStatus = networkStatus, size = 11.dp)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Kanvas OpenStreetMap Multi-Segmen
            OsmMultiSegmentMapView(
                segments = filteredSegments,
                selectedSegmentId = selectedSegment?.id,
                selectedCity = selectedCity,
                userLocation = currentLocation,
                onSegmentClick = { seg ->
                    selectedSegment = seg
                },
                onMapReady = { mv ->
                    osmMapViewInstance = mv
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Chip Filter Kota (Horizontal Scroll) di Bagian Atas Peta
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = Color.White.copy(alpha = 0.95f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cityGroups.forEach { city ->
                        val count = if (city == "Semua") allSegments.size else allSegments.count { getNormalizedCity(it) == city }
                        val isSelected = selectedCity == city

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCity = city
                                selectedSegment = null
                            },
                            label = {
                                Text(
                                    text = "$city ($count)",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelecomPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 3. Mini Legenda Status Peta (Pojok Kiri Bawah)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 18.dp),
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "LEGENDA STATUS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF1976D2), CircleShape))
                        Text("Belum Mulai", fontSize = 10.sp, color = Color.DarkGray)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF9800), CircleShape))
                        Text("Sedang Berjalan", fontSize = 10.sp, color = Color.DarkGray)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF2E7D32), CircleShape))
                        Text("Selesai", fontSize = 10.sp, color = Color.DarkGray)
                    }
                }
            }

            // 4. Tombol Kontrol Peta Mengambang (Pojok Kanan Bawah)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tombol Fokus Lokasi GPS Surveyor
                FloatingActionButton(
                    onClick = {
                        if (currentLocation.isAvailable && currentLocation.latitude != 0.0) {
                            osmMapViewInstance?.controller?.animateTo(
                                GeoPoint(currentLocation.latitude, currentLocation.longitude)
                            )
                            osmMapViewInstance?.controller?.setZoom(16.0)
                        }
                    },
                    containerColor = Color.White,
                    contentColor = TelecomPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Lokasi Saya",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Tombol Zoom In
                SmallFloatingActionButton(
                    onClick = { osmMapViewInstance?.controller?.zoomIn() },
                    containerColor = Color.White,
                    contentColor = Color.DarkGray,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Perbesar")
                }

                // Tombol Zoom Out
                SmallFloatingActionButton(
                    onClick = { osmMapViewInstance?.controller?.zoomOut() },
                    containerColor = Color.White,
                    contentColor = Color.DarkGray,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Perkecil")
                }
            }

            // 5. Bottom Sheet Ringkasan Segmen saat Rute Diketuk
            selectedSegment?.let { segment ->
                ModalBottomSheet(
                    onDismissRequest = { selectedSegment = null },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = segment.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TelecomPrimary
                                )
                                Text(
                                    text = segment.route.ifEmpty { "Rute Fiber Optic" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.DarkGray,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            SegmentStatusBadge(status = segment.status)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFF5F7FA),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Wilayah Kota", fontSize = 11.sp, color = Color.Gray)
                                    Text(
                                        text = getNormalizedCity(segment),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Panjang Rute", fontSize = 11.sp, color = Color.Gray)
                                    val dist = String.format(java.util.Locale.US, "%.1f", (segment.routeCoordinates.size * 0.05).coerceAtLeast(1.0))
                                    Text(
                                        text = "$dist km",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Progres Tiang", fontSize = 11.sp, color = Color.Gray)
                                    Text(
                                        text = "${segment.completedPoles} / ${segment.totalPoles}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (segment.completedPoles > 0) Color(0xFF2E7D32) else Color.DarkGray
                                    )
                                }
                            }
                        }

                        // Tombol Aksi Langsung
                        Button(
                            onClick = {
                                val segId = segment.id
                                selectedSegment = null
                                onNavigateToSurvey(segId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mulai Survei PM Tiang", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val segId = segment.id
                                selectedSegment = null
                                onNavigateToDetail(segId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lihat Detail Segmen & Tiang")
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}
