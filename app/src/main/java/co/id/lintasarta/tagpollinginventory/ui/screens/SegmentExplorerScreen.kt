package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.model.SegmentEntity
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.ui.components.NetworkStatusDot
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.ScreenFlow
import java.util.Locale
import kotlin.math.*

/**
 * LAYAR 2: HYBRID ROUTE & SEGMENT EXPLORER (Alpaca PRA Standard)
 *
 * Menerapkan 3 Lapisan Seleksi Rute:
 * a) Quick Filter Chips: Rute prioritas Alpaca MPO (Sentani-Abepura, Manokwari, Timika, Sorong, Dobo, Tiakur).
 * b) Katalog Lengkap 143 Segmen MPO: Terbaca offline dari mpo_segments.json.
 * c) Input Manual Rute/PO Baru: Tombol "Tambah Rute / PO" untuk jalur baru yang belum terdaftar di Alpaca.
 *
 * Auto-Suggest Proximity: Kartu pintar menampilkan segmen terdekat (< 500m) dari posisi GPS surveyor.
 * Peta Rute Offline: Menampilkan ringkasan polyline rute kabel optik.
 */
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
    var selectedPriorityRouteKey by remember { mutableStateOf<String?>(null) }
    var showAddManualRouteDialog by remember { mutableStateOf(false) }

    // Rute Prioritas Alpaca MPO Resmi
    val alpacaPriorityRoutes = remember {
        listOf(
            "ALL" to "Semua Prioritas",
            "SENTANI_ABEPURA" to "Sentani - Abepura",
            "MANOKWARI" to "Manokwari",
            "TIMIKA" to "Timika",
            "SORONG" to "Sorong",
            "DOBO" to "Dobo",
            "TIAKUR" to "Tiakur"
        )
    }

    // Daftar Kota Dinamis berdasarkan segmen yang ada pada Area aktif
    val cityChips = remember(project.segments) {
        val extracted = project.segments.map { it.cityFilterKey }
            .filter { it.isNotBlank() && it != "LAINNYA" }
            .distinct()
            .sorted()
        if (extracted.isNotEmpty()) {
            listOf("SEMUA" to "Semua Kota") + extracted.map { key ->
                val label = when (key.uppercase()) {
                    "TIMIKA" -> "Timika (Mimika)"
                    else -> key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
                key to label
            }
        } else {
            listOf(
                "SEMUA" to "Semua Kota",
                "TIMIKA" to "Timika (Mimika)",
                "SORONG" to "Sorong",
                "JAYAPURA" to "Jayapura",
                "AMBON" to "Ambon",
                "MERAUKE" to "Merauke",
                "MANOKWARI" to "Manokwari",
                "BIAK" to "Biak",
                "NABIRE" to "Nabire"
            )
        }
    }

    // Hitung jarak ke seluruh segmen berdasarkan GPS Surveyor saat ini
    val segmentsWithDistance = remember(project.segments, currentLocation) {
        val userLat = currentLocation.latitude
        val userLng = currentLocation.longitude
        val hasGps = userLat != 0.0 && userLng != 0.0

        project.segments.map { segment ->
            val distMeters = if (hasGps) {
                calculateDistanceToSegment(userLat, userLng, segment)
            } else {
                Double.MAX_VALUE
            }
            Pair(segment, distMeters)
        }
    }

    // Segmen terdekat (Lapisan Proximity Auto-Detect)
    val nearestSegmentPair = remember(segmentsWithDistance) {
        if (currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0) {
            segmentsWithDistance.minByOrNull { it.second }
        } else null
    }

    // Filter Segmen berdasarkan Search, Priority Route & City Chip
    val filteredSegments = remember(segmentsWithDistance, selectedCityChip, selectedPriorityRouteKey, searchQuery) {
        segmentsWithDistance.filter { (segment, _) ->
            // Filter Rute Prioritas Alpaca MPO
            val matchesPriority = if (selectedPriorityRouteKey == null || selectedPriorityRouteKey == "ALL") {
                true
            } else {
                val segName = segment.name.lowercase(Locale.ROOT)
                val orgSite = segment.orgSite.lowercase(Locale.ROOT)
                val dstSite = segment.dstSite.lowercase(Locale.ROOT)
                val priority = segment.priorityRouteKey.uppercase(Locale.ROOT)

                when (selectedPriorityRouteKey) {
                    "SENTANI_ABEPURA" -> priority == "SENTANI_ABEPURA" || (segName.contains("sentani") || segName.contains("abepura"))
                    "MANOKWARI" -> priority == "MANOKWARI" || segName.contains("manokwari") || orgSite.contains("mkw") || dstSite.contains("mkw")
                    "TIMIKA" -> priority == "TIMIKA" || segName.contains("timika") || segName.contains("mimika") || orgSite.contains("tmk") || dstSite.contains("tmk")
                    "SORONG" -> priority == "SORONG" || segName.contains("sorong") || orgSite.contains("srg") || dstSite.contains("soq")
                    "DOBO" -> priority == "DOBO" || segName.contains("dobo") || orgSite.contains("dbo")
                    "TIAKUR" -> priority == "TIAKUR" || segName.contains("tiakur") || orgSite.contains("tkr")
                    else -> true
                }
            }

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
                segment.id.contains(searchQuery, ignoreCase = true) ||
                segment.poId.contains(searchQuery, ignoreCase = true)
            }

            matchesPriority && matchesCity && matchesSearch
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
        // --- TOP CONTEXT BAR: Area 18 MPO & Surveyor ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TelecomPrimary,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00C853),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PROJECT ALPACA PRA • AREA ${currentUser.areaId} (${currentUser.areaLabel.uppercase()})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NetworkStatusDot(networkStatus = networkStatus, size = 10.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentUser.isOfflineSession) "100% Offline" else "Online Alpaca",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hybrid Route Explorer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${project.segments.size} Segmen MPO • 100% Siap Survei Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Tombol Tambah Rute / PO Baru
                        FilledTonalButton(
                            onClick = { showAddManualRouteDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF00E676),
                                contentColor = Color(0xFF1B5E20)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Rute/PO", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        // Tombol Sinkronisasi / Reload Master Segmen
                        FilledTonalIconButton(
                            onClick = { viewModel.syncMpoSegmentsFromServer() },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Muat Ulang Segmen",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- PENCARIAN & 3 LAPIS FILTER ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Kotak Pencarian Cepat
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari segmen, site (TMK, JYP, AMB) atau rute PO...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TelecomPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TelecomPrimary,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )

            // LAPIS 1: QUICK FILTER CHIPS — RUTE PRIORITAS ALPACA MPO
            Text(
                text = "Rute Prioritas Alpaca MPO:",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF37474F)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                alpacaPriorityRoutes.forEach { (key, label) ->
                    val isSelected = if (key == "ALL") {
                        selectedPriorityRouteKey == null || selectedPriorityRouteKey == "ALL"
                    } else {
                        selectedPriorityRouteKey == key
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF00695C) else Color.White,
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF004D40) else Color(0xFFB2DFDB)),
                        modifier = Modifier.clickable {
                            selectedPriorityRouteKey = if (key == "ALL") null else key
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (key != "ALL") {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFFFFD54F) else Color(0xFF00897B),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF004D40)
                            )
                        }
                    }
                }
            }

            // LAPIS 2: FILTER CHIP KOTA AREA 18
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

        // --- DAFTAR SEGMEN & KARTU TERDEKAT ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // PROXIMITY AUTO-SUGGEST: KARTU GPS AUTO-DETECT (< 500m)
            nearestSegmentPair?.let { (nearestSeg, distMeters) ->
                item {
                    val isWithin500m = distMeters <= 500.0
                    val distText = if (distMeters < 1000.0) {
                        "${distMeters.roundToInt()} meter"
                    } else {
                        String.format(Locale.US, "%.1f km", distMeters / 1000.0)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSegmentSelected(nearestSeg.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isWithin500m) Color(0xFFE8F5E9) else Color(0xFFE1F5FE)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (isWithin500m) Color(0xFF4CAF50) else Color(0xFF29B6F6)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isWithin500m) Icons.Default.Bolt else Icons.Default.NearMe,
                                        contentDescription = null,
                                        tint = if (isWithin500m) Color(0xFF2E7D32) else Color(0xFF0277BD),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isWithin500m) "⚡ SEGMEN TERDEKAT (< 500m)" else "📍 SEGMEN TERDEKAT DARI POSISI ANDA",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isWithin500m) Color(0xFF2E7D32) else Color(0xFF0277BD),
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isWithin500m) Color(0xFF2E7D32) else Color(0xFF0277BD)
                                ) {
                                    Text(
                                        text = distText,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = nearestSeg.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF263238)
                                )
                                Text(
                                    text = "${nearestSeg.orgSite} ➔ ${nearestSeg.dstSite} (${nearestSeg.orgCity}) • ${nearestSeg.poId.ifEmpty { "Alpaca PRA" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF455A64)
                                )
                            }

                            Button(
                                onClick = { onSegmentSelected(nearestSeg.id) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isWithin500m) Color(0xFF2E7D32) else Color(0xFF0288D1)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mulai Survei Segmen Ini (Quick Tagging)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Subheader Jumlah Hasil
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Katalog Segmen MPO (${filteredSegments.size} Rute)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF546E7A)
                    )

                    if (selectedPriorityRouteKey != null) {
                        Text(
                            text = "Filter: $selectedPriorityRouteKey",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00695C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // LIST SELURUH SEGMEN MPO
            items(filteredSegments, key = { it.first.id }) { (segment, distMeters) ->
                SegmentExplorerCard(
                    segment = segment,
                    distMeters = distMeters,
                    onPinToggle = { viewModel.repository.togglePinSegment(segment.id) },
                    onClick = { onSegmentSelected(segment.id) }
                )
            }
        }
    }

    // DIALOG TAMBAH RUTE / PO BARU MANUAL
    if (showAddManualRouteDialog) {
        var newRouteName by remember { mutableStateOf("") }
        var newCityCode by remember { mutableStateOf("TMK") }
        var newLocationCode by remember { mutableStateOf("LOC") }
        var newPoId by remember { mutableStateOf("PO-MPO-NEW") }
        var newDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddManualRouteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = TelecomPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Rute / PO Baru", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Registrasi rute atau Purchase Order (PO) Alpaca PRA baru yang belum ada di katalog MPO:",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )

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
                            value = newCityCode,
                            onValueChange = { newCityCode = it.take(3).uppercase() },
                            label = { Text("Kode Kota") },
                            placeholder = { Text("TMK / JAP") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newLocationCode,
                            onValueChange = { newLocationCode = it.take(3).uppercase() },
                            label = { Text("Kode Lokasi") },
                            placeholder = { Text("KCC") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = newPoId,
                        onValueChange = { newPoId = it },
                        label = { Text("Nomor PO / IFT ID") },
                        placeholder = { Text("misal: PO-MPO-2026-001") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Catatan Rute (Opsional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRouteName.isNotBlank()) {
                            viewModel.createSegment(
                                name = newRouteName,
                                description = newDesc.ifEmpty { "Rute PO Baru: $newPoId" },
                                cityCode = newCityCode,
                                locationCode = newLocationCode,
                                startingSequence = 1
                            )
                            showAddManualRouteDialog = false
                        }
                    },
                    enabled = newRouteName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary)
                ) {
                    Text("Simpan & Lanjut Tagging", fontWeight = FontWeight.Bold)
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
 * Kartu Tiap Segmen dalam Segment Explorer
 */
@Composable
fun SegmentExplorerCard(
    segment: SegmentEntity,
    distMeters: Double,
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
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (segment.isPinned) Color(0xFFFFD54F) else Color(0xFFE0E0E0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (segment.isPinned) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Baris 1: ID, Pin, dan Badge Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFECEFF1)
                    ) {
                        Text(
                            text = segment.id,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF455A64),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE0F2F1)
                    ) {
                        Text(
                            text = if (segment.poId.isNotBlank()) segment.poId else "Alpaca PRA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00695C),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (segment.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFECB3)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Color(0xFFF57F17),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "PINNED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF57F17)
                                )
                            }
                        }
                    }
                }

                // Tombol Pin / Unpin
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (segment.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin Segmen",
                        tint = if (segment.isPinned) Color(0xFFF57F17) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Nama Segmen & Rute Site
            Column {
                Text(
                    text = segment.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${segment.orgSite.ifEmpty { "SITE" }} ➔ ${segment.dstSite.ifEmpty { "DEST" }} • ${segment.orgCity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = Color(0xFFF0F0F0))

            // Baris Bawah: Jarak Segmen, Tiang Terdata, dan Tombol Pilih
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Panjang Kabel FO
                    val km = String.format(Locale.US, "%.1f km", segment.lengthMeters / 1000.0)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = TelecomPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = km,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF37474F)
                        )
                    }

                    // Tiang Selesai
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (segment.completedPoles > 0) Color(0xFF2E7D32) else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${segment.completedPoles} tiang",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF37474F)
                        )
                    }
                }

                // Tombol Pilih Segmen (1-Tap ke Peta & Quick Tagging)
                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = TelecomPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Pilih Rute", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                }
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
