package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.network.RnimsApiClient
import co.id.lintasarta.tagpollinginventory.ui.components.NetworkStatusDot
import co.id.lintasarta.tagpollinginventory.ui.components.OsmMultiSegmentMapView
import co.id.lintasarta.tagpollinginventory.ui.components.SegmentStatusBadge
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

/**
 * LAYAR PETA SEBARAN SEGMEN (Area Overview Map Screen)
 * Menu Utama di Tengah (Tab Peta)
 *
 * Fitur:
 * 1. Menampilkan seluruh 143 rute kabel fiber optic di Area 18 MPO (OpenStreetMap).
 * 2. Chip Filter di bagian atas: "Semua Rute", Rute Prioritas Alpaca (Sentani-Abepura, Manokwari, Timika, Sorong, Dobo, Tiakur), dan Kota-kota hub (Jayapura, Ambon, Merauke, Biak, Nabire, Tual).
 * 3. Interaktif: Klik rute/marker untuk membuka Bottom Sheet ringkasan segmen & 1-tap "Mulai Quick Tagging Tiang".
 * 4. Kontrol Mengambang: Tombol Lokasi Saya (GPS Auto-Center) & Zoom Controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaOverviewMapScreen(
    viewModel: MainViewModel,
    onNavigateToSurvey: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.project.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startLocationUpdates()
    }

    var selectedFilterKey by remember { mutableStateOf("SEMUA") }
    var selectedSegment by remember { mutableStateOf<Segment?>(null) }
    var osmMapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Seluruh segmen: Ambil dari Room project.segments dengan fallback parsing mpo_segments.json
    val allSegments = remember(project.segments) {
        if (project.segments.isNotEmpty()) {
            project.segments
        } else {
            val bundledDtos = RnimsApiClient.loadBundledMpoSegments(context)
            bundledDtos.map { dto ->
                val coords = dto.route_path?.map { RouteCoordinate(it.lng, it.lat) } ?: emptyList()
                val cityCode = dto.org_site?.take(3)?.uppercase() ?: "TMK"
                val locationCode = dto.dst_site?.take(3)?.uppercase() ?: "PMK"
                val cityFilter = when {
                    dto.org_city?.contains("Jayapura", true) == true || dto.segment_name.contains("Sentani", true) || dto.segment_name.contains("Abepura", true) -> "JAYAPURA"
                    dto.org_city?.contains("Mimika", true) == true || dto.segment_name.contains("Timika", true) -> "TIMIKA"
                    dto.org_city?.contains("Sorong", true) == true || dto.segment_name.contains("Sorong", true) -> "SORONG"
                    dto.org_city?.contains("Manokwari", true) == true || dto.segment_name.contains("Manokwari", true) -> "MANOKWARI"
                    dto.org_city?.contains("Ambon", true) == true || dto.segment_name.contains("Ambon", true) -> "AMBON"
                    dto.org_city?.contains("Merauke", true) == true || dto.segment_name.contains("Merauke", true) -> "MERAUKE"
                    dto.org_city?.contains("Biak", true) == true || dto.segment_name.contains("Biak", true) -> "BIAK"
                    dto.org_city?.contains("Nabire", true) == true || dto.segment_name.contains("Nabire", true) -> "NABIRE"
                    dto.org_city?.contains("Tual", true) == true || dto.segment_name.contains("Tual", true) -> "TUAL"
                    dto.segment_name.contains("Aru", true) || dto.segment_name.contains("Dobo", true) -> "DOBO"
                    dto.segment_name.contains("Tiakur", true) || dto.segment_name.contains("Tanimbar", true) -> "TIAKUR"
                    else -> "LAINNYA"
                }

                val priorityKey = when {
                    dto.segment_name.contains("Sentani", true) || dto.segment_name.contains("Abepura", true) -> "SENTANI_ABEPURA"
                    dto.segment_name.contains("Manokwari", true) -> "MANOKWARI"
                    dto.segment_name.contains("Timika", true) -> "TIMIKA"
                    dto.segment_name.contains("Sorong", true) -> "SORONG"
                    dto.segment_name.contains("Dobo", true) -> "DOBO"
                    dto.segment_name.contains("Tiakur", true) -> "TIAKUR"
                    else -> ""
                }

                Segment(
                    id = "SEG-${dto.id}",
                    name = dto.segment_name,
                    lengthMeters = dto.distance.toDouble(),
                    routeCoordinates = coords,
                    areaId = 18,
                    orgSite = dto.org_site ?: "",
                    dstSite = dto.dst_site ?: "",
                    orgCity = dto.org_city ?: "",
                    dstCity = dto.dst_city ?: "",
                    cityFilterKey = cityFilter,
                    priorityRouteKey = priorityKey,
                    isAlpacaProject = true,
                    route = "${dto.org_site} -> ${dto.dst_site}",
                    projectId = "PRJ-MPO-18",
                    description = "Segmen FO ${dto.org_site} - ${dto.dst_site}",
                    status = SegmentStatus.NOT_STARTED
                )
            }
        }
    }

    // Normalisasi kota & rute untuk mengelompokkan segmen
    fun matchesFilter(seg: Segment, filterKey: String): Boolean {
        if (filterKey == "SEMUA") return true
        if (seg.priorityRouteKey.equals(filterKey, ignoreCase = true)) return true
        if (seg.cityFilterKey.equals(filterKey, ignoreCase = true)) return true

        val text = "${seg.name} ${seg.description} ${seg.route} ${seg.cityFilterKey} ${seg.priorityRouteKey}".uppercase()
        return when (filterKey) {
            "SENTANI_ABEPURA" -> text.contains("SENTANI") || text.contains("ABEPURA")
            "TIMIKA" -> text.contains("TIMIKA") || text.contains("MIMIKA") || text.contains("TMK")
            "MANOKWARI" -> text.contains("MANOKWARI") || text.contains("MWR")
            "SORONG" -> text.contains("SORONG") || text.contains("SRN")
            "DOBO" -> text.contains("DOBO") || text.contains("ARU")
            "TIAKUR" -> text.contains("TIAKUR") || text.contains("MBD") || text.contains("TANIMBAR")
            "JAYAPURA" -> text.contains("JAYAPURA") || text.contains("JPR")
            "AMBON" -> text.contains("AMBON") || text.contains("AMB")
            "MERAUKE" -> text.contains("MERAUKE") || text.contains("MRK")
            "BIAK" -> text.contains("BIAK") || text.contains("BIK")
            "NABIRE" -> text.contains("NABIRE") || text.contains("NBR")
            "TUAL" -> text.contains("TUAL") || text.contains("TLA")
            else -> false
        }
    }

    // Filter Chips: Rute Prioritas Alpaca & Kota Utama MPO
    val filterChipOptions = remember {
        listOf(
            "SEMUA" to "Semua Rute",
            "SENTANI_ABEPURA" to "Sentani - Abepura",
            "TIMIKA" to "Timika",
            "MANOKWARI" to "Manokwari",
            "SORONG" to "Sorong",
            "JAYAPURA" to "Jayapura",
            "AMBON" to "Ambon",
            "MERAUKE" to "Merauke",
            "BIAK" to "Biak",
            "NABIRE" to "Nabire",
            "TUAL" to "Tual",
            "DOBO" to "Dobo (Aru)",
            "TIAKUR" to "Tiakur"
        )
    }

    val filteredSegments = remember(allSegments, selectedFilterKey) {
        allSegments.filter { matchesFilter(it, selectedFilterKey) }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Peta Sebaran Rute FO",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Area ${currentUser.areaId} ${currentUser.areaLabel.uppercase()} • ${filteredSegments.size} dari ${allSegments.size} Rute Segmen",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        NetworkStatusDot(networkStatus = networkStatus, size = 9.dp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (currentUser.isOfflineSession || !networkStatus.isOnline) "Offline" else "Online",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. KANVAS OPENSTREETMAP MULTI-SEGMEN
            OsmMultiSegmentMapView(
                segments = filteredSegments,
                selectedSegmentId = selectedSegment?.id,
                selectedCity = selectedFilterKey,
                userLocation = currentLocation,
                onSegmentClick = { seg ->
                    selectedSegment = seg
                },
                onMapReady = { mv ->
                    osmMapViewInstance = mv
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. CHIP FILTER DI BAGIAN ATAS PETA (HORIZONTAL SCROLL)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    filterChipOptions.forEach { (key, label) ->
                        val count = if (key == "SEMUA") allSegments.size else allSegments.count { matchesFilter(it, key) }
                        if (count > 0 || key == "SEMUA") {
                            val isSelected = selectedFilterKey == key

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (selectedFilterKey == key) {
                                        // Ketuk ulang chip yang sedang aktif -> fokuskan kembali kamera ke area filter ini
                                        val points = filteredSegments.flatMap { it.routeCoordinates }
                                            .filter { it.latitude in -11.0..6.0 && it.longitude in 120.0..142.0 }
                                        if (points.isNotEmpty()) {
                                            val minLat = points.minOf { it.latitude }
                                            val maxLat = points.maxOf { it.latitude }
                                            val minLng = points.minOf { it.longitude }
                                            val maxLng = points.maxOf { it.longitude }
                                            val latSpan = maxLat - minLat
                                            val lngSpan = maxLng - minLng
                                            if (latSpan > 0.005 || lngSpan > 0.005) {
                                                val padLat = latSpan.coerceAtLeast(0.01) * 0.15
                                                val padLng = lngSpan.coerceAtLeast(0.01) * 0.15
                                                val bbox = BoundingBox(maxLat + padLat, maxLng + padLng, minLat - padLat, minLng - padLng)
                                                osmMapViewInstance?.zoomToBoundingBox(bbox, true, 80)
                                            } else {
                                                osmMapViewInstance?.controller?.setCenter(GeoPoint((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0))
                                                osmMapViewInstance?.controller?.setZoom(14.0)
                                            }
                                        }
                                    } else {
                                        selectedFilterKey = key
                                    }
                                    selectedSegment = null
                                },
                                label = {
                                    Text(
                                        text = "$label ($count)",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = if (key == "SENTANI_ABEPURA" || key == "TIMIKA" || key == "MANOKWARI" || key == "SORONG") {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Prioritas Alpaca",
                                            tint = if (isSelected) Color.White else Color(0xFFFFB300),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelecomPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 3. MINI LEGENDA STATUS PETA (Pojok Kiri Bawah)
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

            // 4. TOMBOL KONTROL PETA MENGAMBANG (Pojok Kanan Bawah)
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
                            osmMapViewInstance?.controller?.setZoom(16.5)
                            android.widget.Toast.makeText(context, "Peta dipusatkan ke lokasi Anda", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.startLocationUpdates()
                            android.widget.Toast.makeText(context, "Mencari sinyal GPS surveyor...", android.widget.Toast.LENGTH_SHORT).show()
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

            // 5. BOTTOM SHEET RINGKASAN SEGMEN SAAT RUTE DIKETUK
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
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TelecomPrimary
                                )
                                Text(
                                    text = segment.route.ifEmpty { "Rute Fiber Optic" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.DarkGray,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val coords = segment.routeCoordinates
                                        if (coords.isNotEmpty()) {
                                            val validCoords = coords.filter { it.latitude in -11.0..6.0 && it.longitude in 120.0..142.0 }
                                            if (validCoords.isNotEmpty()) {
                                                val minLat = validCoords.minOf { it.latitude }
                                                val maxLat = validCoords.maxOf { it.latitude }
                                                val minLng = validCoords.minOf { it.longitude }
                                                val maxLng = validCoords.maxOf { it.longitude }
                                                val latSpan = maxLat - minLat
                                                val lngSpan = maxLng - minLng
                                                if (latSpan > 0.002 || lngSpan > 0.002) {
                                                    val padLat = latSpan.coerceAtLeast(0.005) * 0.25
                                                    val padLng = lngSpan.coerceAtLeast(0.005) * 0.25
                                                    val segBbox = BoundingBox(maxLat + padLat, maxLng + padLng, minLat - padLat, minLng - padLng)
                                                    osmMapViewInstance?.zoomToBoundingBox(segBbox, true, 100)
                                                } else {
                                                    osmMapViewInstance?.controller?.animateTo(GeoPoint(validCoords.first().latitude, validCoords.first().longitude))
                                                    osmMapViewInstance?.controller?.setZoom(16.0)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CenterFocusStrong,
                                        contentDescription = "Fokuskan Kamera ke Rute Ini",
                                        tint = TelecomPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                SegmentStatusBadge(status = segment.status)
                            }
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
                                    Text("Wilayah", fontSize = 11.sp, color = Color.Gray)
                                    Text(
                                        text = segment.cityFilterKey.ifEmpty { "Area ${currentUser.areaId}" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Panjang Rute", fontSize = 11.sp, color = Color.Gray)
                                    val dist = String.format(Locale.US, "%.1f", (segment.routeCoordinates.size * 0.05).coerceAtLeast(1.0))
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

                        // TOMBOL AKSI 1: MULAI QUICK TAGGING TIANG
                        Button(
                            onClick = {
                                val segId = segment.id
                                selectedSegment = null
                                onNavigateToSurvey(segId)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mulai Quick Tagging Tiang (< 1 Menit)", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }

                        // TOMBOL AKSI 2: LIHAT DETAIL SEGMEN
                        OutlinedButton(
                            onClick = {
                                val segId = segment.id
                                selectedSegment = null
                                onNavigateToDetail(segId)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lihat Detail Segmen & Tiang", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}
