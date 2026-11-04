package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.network.RnimsSegmentDto
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TagPollingInventoryTheme
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.ScreenFlow
import java.util.Locale

@Composable
fun CreateSegmentScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val networkStatus by viewModel.networkStatus.collectAsState()
    val mpoSegments by viewModel.mpoSegments.collectAsState()

    CreateSegmentScreenContent(
        networkStatus = networkStatus,
        mpoSegments = mpoSegments,
        onBackClick = onBackClick,
        onCreateSegmentFromRnims = { segmentDto ->
            viewModel.createSegmentFromRnims(segmentDto)
        },
        onCreateSegment = { segmentName, description, cityCode, locationCode, seqInt ->
            viewModel.createSegment(segmentName, description, cityCode, locationCode, seqInt)
        }
    )
}

@Composable
fun CreateSegmentScreenContent(
    networkStatus: NetworkStatus,
    mpoSegments: List<RnimsSegmentDto> = emptyList(),
    onBackClick: () -> Unit,
    onCreateSegmentFromRnims: (RnimsSegmentDto) -> Unit = {},
    onCreateSegment: (String, String, String, String, Int) -> Unit
) {
    var segmentName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cityCode by remember { mutableStateOf("") }
    var locationCode by remember { mutableStateOf("") }
    var startingSequence by remember { mutableStateOf("") }

    var userEditedCityCode by remember { mutableStateOf(false) }
    var userEditedLocCode by remember { mutableStateOf(false) }

    var selectedMpoSegment by remember { mutableStateOf<RnimsSegmentDto?>(null) }
    var showMpoDialog by remember { mutableStateOf(false) }

    val (autoCity, autoLoc) = remember(segmentName) { deriveCodesFromSegmentName(segmentName) }
    val effectiveCityCode = if (userEditedCityCode) cityCode else autoCity
    val effectiveLocCode = if (userEditedLocCode) locationCode else autoLoc
    val previewSeq = startingSequence.toIntOrNull() ?: 1
    val previewPoleCode = "PL-${effectiveCityCode.ifEmpty { "TMK" }}-${effectiveLocCode.ifEmpty { "PMK" }}-${String.format(
        Locale.US, "%03d", previewSeq)}"

    if (showMpoDialog) {
        MpoSegmentPickerDialog(
            segments = mpoSegments,
            onDismiss = { showMpoDialog = false },
            onSelect = { seg ->
                selectedMpoSegment = seg
                segmentName = seg.segment_name
                cityCode = seg.org_site?.take(3)?.uppercase() ?: "TMK"
                locationCode = seg.dst_site?.take(3)?.uppercase() ?: "PMK"
                userEditedCityCode = true
                userEditedLocCode = true
                val km = String.format(Locale.US, "%.1f", seg.distance / 1000.0)
                description = "Segmen FO ${seg.org_site} -> ${seg.dst_site} (${seg.org_city} - ${seg.dst_city}, $km km)"
                showMpoDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "Buat Segment Baru",
                subtitle = "Mulai Segment Rute Fiber Optic Baru",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Master Database Segmen RNIMS (MPO)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MASTER SEGMEN RNIMS (MPO)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2E7D32)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2E7D32)
                        ) {
                            Text(
                                text = "${mpoSegments.size} RUTE FO",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedMpoSegment != null) {
                        val seg = selectedMpoSegment!!
                        Text(
                            text = "✓ Terpilih: ${seg.segment_name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            text = "${seg.org_site} ➔ ${seg.dst_site} (${seg.org_city} - ${seg.dst_city})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "Jarak: ${String.format(Locale.US, "%.1f", seg.distance / 1000.0)} km • Rute GPS langsung aktif di peta",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF388E3C)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showMpoDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Ganti Pilihan Segmen MPO")
                        }
                    } else {
                        Text(
                            text = "Pilih segmen langsung dari database master untuk otomatis memuat titik jalur kabel optik ke peta tanpa file KML.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF37474F)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showMpoDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pilih dari ${mpoSegments.size} Segmen MPO")
                        }
                    }
                }
            }

            // Card 2: Informasi Segment Detail & Penomoran Tiang
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Informasi Segment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = segmentName,
                        onValueChange = { segmentName = it },
                        label = { Text("Nama Segment *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Deskripsi (Opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Konfigurasi Penamaan Tiang (Otomatis)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (userEditedCityCode) cityCode else autoCity,
                            onValueChange = {
                                cityCode = it.take(3).uppercase()
                                userEditedCityCode = true
                            },
                            label = { Text("Kode Kota (3 Karakter)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = if (userEditedLocCode) locationCode else autoLoc,
                            onValueChange = {
                                locationCode = it.take(3).uppercase()
                                userEditedLocCode = true
                            },
                            label = { Text("Kode Lokasi (3 Karakter)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = startingSequence,
                        onValueChange = { if (it.all { char -> char.isDigit() }) startingSequence = it },
                        label = { Text("Nomor Urut Awal (Default: 1)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Live Architecture-Compliant Pole Code Preview
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE3F2FD),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Format Kode Tiang Otomatis (Architecture Sec. 9):",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelecomPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = previewPoleCode,
                                style = MaterialTheme.typography.titleMedium,
                                color = TelecomPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Button
            Button(
                onClick = {
                    if (selectedMpoSegment != null) {
                        onCreateSegmentFromRnims(selectedMpoSegment!!)
                    } else if (segmentName.isNotBlank()) {
                        val seqInt = startingSequence.toIntOrNull() ?: 1
                        val finalCity = if (userEditedCityCode) cityCode else autoCity
                        val finalLoc = if (userEditedLocCode) locationCode else autoLoc
                        onCreateSegment(segmentName, description, finalCity, finalLoc, seqInt)
                    }
                },
                enabled = segmentName.isNotBlank() || selectedMpoSegment != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMpoSegment != null) Color(0xFF2E7D32) else TelecomPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (selectedMpoSegment != null) "Mulai Survei Segmen Ini (Rute Otomatis)" else "Simpan Segment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MpoSegmentPickerDialog(
    segments: List<RnimsSegmentDto>,
    onDismiss: () -> Unit,
    onSelect: (RnimsSegmentDto) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, segments) {
        if (searchQuery.isBlank()) segments
        else segments.filter {
            it.segment_name.contains(searchQuery, ignoreCase = true) ||
                    (it.org_city ?: "").contains(searchQuery, ignoreCase = true) ||
                    (it.dst_city ?: "").contains(searchQuery, ignoreCase = true) ||
                    (it.org_site ?: "").contains(searchQuery, ignoreCase = true) ||
                    (it.dst_site ?: "").contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pilih Segmen MPO",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )
                        Text(
                            text = "${filtered.size} dari ${segments.size} Segmen Maluku & Papua",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Cari Kota, Site, atau Segmen...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { seg ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF5F5F5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(seg) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = seg.segment_name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TelecomPrimary
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", seg.distance / 1000.0)} km",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${seg.org_site ?: "N/A"} ➔ ${seg.dst_site ?: "N/A"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${seg.org_city ?: ""} - ${seg.dst_city ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun deriveCodesFromSegmentName(name: String): Pair<String, String> {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return Pair("JPR", "CTR")
    if (words.size == 1) {
        val w = words[0].uppercase().filter { it.isLetterOrDigit() }
        val city = if (w.length >= 3) w.take(3) else w.padEnd(3, 'X')
        return Pair(city, "CTR")
    }
    val w1 = words[0].uppercase().filter { it.isLetterOrDigit() }
    val w2 = words[1].uppercase().filter { it.isLetterOrDigit() }
    val city = if (w1.length >= 3) w1.take(3) else w1.padEnd(3, 'X')
    val loc = if (w2.length >= 3) w2.take(3) else w2.padEnd(3, 'X')
    return Pair(city, loc)
}

@Preview(showBackground = true)
@Composable
fun CreateSegmentScreenPreview() {
    TagPollingInventoryTheme {
        CreateSegmentScreenContent(
            networkStatus = NetworkStatus(isOnline = true, connectionType = "WiFi"),
            onBackClick = {},
            onCreateSegment = { _, _, _, _, _ -> }
        )
    }
}
