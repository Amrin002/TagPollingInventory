package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.model.SegmentEntity
import co.id.lintasarta.tagpollinginventory.ui.components.NetworkStatusDot
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentExplorerScreen(
    viewModel: MainViewModel,
    onSegmentSelected: (String) -> Unit
) {
    val project by viewModel.project.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCityChip by remember { mutableStateOf("SEMUA") }
    var showAddManualRouteDialog by remember { mutableStateOf(false) }

    // Daftar Kota Dinamis berdasarkan segmen yang ada pada Area aktif
    val cityChips = remember(project.segments) {
        val extracted = project.segments.map { it.cityFilterKey }
            .filter { it.isNotBlank() && it != "LAINNYA" }
            .distinct()
            .sorted()
        if (extracted.isNotEmpty()) {
            listOf("SEMUA" to "Semua") + extracted.map { key ->
                val label = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                key to label
            }
        } else {
            listOf(
                "SEMUA" to "Semua",
                "AMBON" to "Ambon",
                "JAYAPURA" to "Jayapura",
                "TIMIKA" to "Timika",
                "SORONG" to "Sorong",
                "MANOKWARI" to "Manokwari",
                "MERAUKE" to "Merauke"
            )
        }
    }

    // Kuantisasi koordinat GPS (~25 meter) agar perhitungan jarak segmen
    // tidak dijalankan terus-menerus saat surveyor diam/berjalan pelan.
    val quantizedLat = remember(currentLocation.latitude) {
        (currentLocation.latitude * 4000).roundToLong() / 4000.0
    }
    val quantizedLng = remember(currentLocation.longitude) {
        (currentLocation.longitude * 4000).roundToLong() / 4000.0
    }

    // Hitung jarak ke seluruh segmen di background thread (Dispatchers.Default)
    // agar UI thread 100% bebas dari lag dan frame drop saat scrolling
    val segmentsWithDistance by produceState(
        initialValue = project.segments.map { Pair(it, Double.MAX_VALUE) },
        key1 = project.segments,
        key2 = quantizedLat,
        key3 = quantizedLng
    ) {
        val hasGps = quantizedLat != 0.0 && quantizedLng != 0.0
        val result = withContext(Dispatchers.Default) {
            project.segments.map { segment ->
                val distMeters = if (hasGps) {
                    calculateDistanceToSegment(quantizedLat, quantizedLng, segment)
                } else {
                    Double.MAX_VALUE
                }
                Pair(segment, distMeters)
            }
        }
        value = result
    }

    // Segmen terdekat (< 2 km dari posisi GPS surveyor)
    val nearestSegmentPair = remember(segmentsWithDistance) {
        if (quantizedLat != 0.0 && quantizedLng != 0.0) {
            val nearest = segmentsWithDistance.minByOrNull { it.second }
            if (nearest != null && nearest.second <= 2000.0) nearest else null
        } else null
    }

    // Filter Segmen berdasarkan Search & City Chip
    val filteredSegments = remember(segmentsWithDistance, selectedCityChip, searchQuery) {
        segmentsWithDistance.filter { (segment, _) ->
            // Filter Kota
            val matchesCity = if (selectedCityChip == "SEMUA") {
                true
            } else {
                segment.cityFilterKey.equals(selectedCityChip, ignoreCase = true) ||
                segment.orgCity.contains(selectedCityChip, ignoreCase = true) ||
                segment.dstCity.contains(selectedCityChip, ignoreCase = true) ||
                segment.name.contains(selectedCityChip, ignoreCase = true)
            }

            // Filter Pencarian Text
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                segment.name.contains(searchQuery, ignoreCase = true) ||
                segment.orgSite.contains(searchQuery, ignoreCase = true) ||
                segment.dstSite.contains(searchQuery, ignoreCase = true) ||
                segment.orgCity.contains(searchQuery, ignoreCase = true) ||
                segment.dstCity.contains(searchQuery, ignoreCase = true) ||
                segment.id.contains(searchQuery, ignoreCase = true)
            }

            matchesCity && matchesSearch
        }.sortedWith(
            compareByDescending<Pair<SegmentEntity, Double>> { it.first.isPinned }
                .thenBy { it.second }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBackground)
    ) {
        // TOP APP BAR
        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                        text = "Daftar Segmen",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Area ${currentUser.areaId} ${currentUser.areaLabel} • ${filteredSegments.size} Rute",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { showAddManualRouteDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Tambah Rute",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    NetworkStatusDot(networkStatus = networkStatus, size = 10.dp)
                }
            }
        }

        // PENCARIAN & FILTER KOTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari segmen atau kode site...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TelecomPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TelecomPrimary,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )

            // SINGLE ROW: FILTER KOTA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                cityChips.forEach { (key, label) ->
                    val isSelected = selectedCityChip.equals(key, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCityChip = key },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelecomPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) TelecomPrimary else Color(0xFFCFD8DC)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // DAFTAR SEGMEN
        // Pastikan list selalu berada di posisi paling atas saat layar dibuka, filter/pencarian berubah,
        // atau ketika hasil hitung jarak GPS & kartu terdekat masuk di urutan awal sebelum user menggeser manual.
        val listState = rememberLazyListState()
        var userHasDraggedList by remember(currentUser.areaId, selectedCityChip, searchQuery) {
            mutableStateOf(false)
        }
        LaunchedEffect(listState, currentUser.areaId, selectedCityChip, searchQuery) {
            listState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) {
                    userHasDraggedList = true
                }
            }
        }
        val topOrderSignature = remember(filteredSegments, nearestSegmentPair) {
            Triple(
                nearestSegmentPair?.first?.id,
                filteredSegments.firstOrNull()?.first?.id,
                filteredSegments.size
            )
        }
        LaunchedEffect(topOrderSignature, currentUser.areaId, selectedCityChip, searchQuery) {
            if (!userHasDraggedList) {
                listState.scrollToItem(0)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // SEGMEN TERDEKAT (SLIM CARD)
            nearestSegmentPair?.let { (nearestSeg, distMeters) ->
                item(key = "nearest_segment_banner") {
                    val distText = if (distMeters < 1000.0) {
                        "${distMeters.roundToInt()}m"
                    } else {
                        String.format(Locale.US, "%.1f km", distMeters / 1000.0)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSegmentSelected(nearestSeg.id) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF81C784)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = nearestSeg.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Terdekat ($distText)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF388E3C)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { onSegmentSelected(nearestSeg.id) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Pilih", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // LIST SELURUH SEGMEN
            items(filteredSegments, key = { it.first.id }) { (segment, _) ->
                SegmentExplorerCard(
                    segment = segment,
                    onPinToggle = { viewModel.repository.togglePinSegment(segment.id) },
                    onClick = { onSegmentSelected(segment.id) }
                )
            }
        }
    }

    // DIALOG TAMBAH RUTE BARU
    if (showAddManualRouteDialog) {
        var newRouteName by remember { mutableStateOf("") }
        var newOrgSite by remember { mutableStateOf("") }
        var newDstSite by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddManualRouteDialog = false },
            title = { Text("Tambah Rute Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newRouteName,
                        onValueChange = { newRouteName = it },
                        label = { Text("Nama Rute / Segmen *") },
                        placeholder = { Text("misal: Sentani - Genyem") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newOrgSite,
                            onValueChange = { newOrgSite = it.uppercase() },
                            label = { Text("Site Asal") },
                            placeholder = { Text("STN") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newDstSite,
                            onValueChange = { newDstSite = it.uppercase() },
                            label = { Text("Site Tujuan") },
                            placeholder = { Text("GNY") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRouteName.isNotBlank()) {
                            viewModel.createSegment(
                                name = newRouteName,
                                description = "Rute: $newOrgSite -> $newDstSite",
                                cityCode = newOrgSite.take(3),
                                locationCode = newDstSite.take(3),
                                startingSequence = 1
                            )
                            showAddManualRouteDialog = false
                        }
                    },
                    enabled = newRouteName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddManualRouteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

/**
 * Kartu Tiap Segmen dalam Segment Explorer (Clean & Ringkas)
 */
@Composable
fun SegmentExplorerCard(
    segment: SegmentEntity,
    onPinToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (segment.isPinned) Color(0xFFFFFDE7) else Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (segment.isPinned) Color(0xFFFFD54F) else Color(0xFFE8ECEF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Baris 1: Nama Segmen
                Text(
                    text = segment.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Baris 2: Rute Site & Kota
                val routeText = if (segment.orgSite.isNotBlank() && segment.dstSite.isNotBlank()) {
                    "${segment.orgSite} ➔ ${segment.dstSite} • ${segment.orgCity}"
                } else {
                    segment.orgCity.ifBlank { "Rute Operasional" }
                }
                Text(
                    text = routeText,
                    fontSize = 11.sp,
                    color = Color(0xFF546E7A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Baris 3: Jarak & Status Tiang (hanya jika ada)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (segment.lengthMeters > 0) {
                        Text(
                            text = String.format(Locale.US, "%.1f km", segment.lengthMeters / 1000.0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                    }
                    if (segment.completedPoles > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = "${segment.completedPoles} tiang",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Tombol Pin & Arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (segment.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin Segmen",
                        tint = if (segment.isPinned) Color(0xFFF57F17) else Color(0xFFCFD8DC),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFB0BEC5),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Utilitas Penghitungan Jarak GPS ke Garis Rute Segmen (Haversine Formula)
 */
fun calculateDistanceToSegment(userLat: Double, userLng: Double, segment: SegmentEntity): Double {
    if (userLat == 0.0 && userLng == 0.0) return Double.MAX_VALUE
    if (segment.routeCoordinates.isEmpty()) {
        val (sLat, sLng) = segment.startPoint
        return if (sLat != 0.0 && sLng != 0.0) {
            calculateDistanceMeters(userLat, userLng, sLat, sLng)
        } else Double.MAX_VALUE
    }

    var minDist = Double.MAX_VALUE
    val step = maxOf(1, segment.routeCoordinates.size / 20)
    for (i in segment.routeCoordinates.indices step step) {
        val coord = segment.routeCoordinates[i]
        val dist = calculateDistanceMeters(userLat, userLng, coord.latitude, coord.longitude)
        if (dist < minDist) {
            minDist = dist
        }
    }
    return minDist
}

fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Radius bumi dalam meter
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
