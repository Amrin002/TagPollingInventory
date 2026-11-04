package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.ui.components.NetworkStatusDot
import co.id.lintasarta.tagpollinginventory.ui.components.SegmentStatusBadge
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.ScreenFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentListScreen(
    viewModel: MainViewModel,
    onSegmentClick: (String) -> Unit
) {
    val project by viewModel.project.collectAsState()
    val filter by viewModel.segmentFilter.collectAsState()
    val searchQuery by viewModel.segmentSearchQuery.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    var selectedSegmentIds by remember { mutableStateOf(setOf<String>()) }
    val isMultiSelectionMode = selectedSegmentIds.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var activeSegmentForAction by remember { mutableStateOf<Segment?>(null) }

    val filteredSegments = remember(project.segments, filter, searchQuery) {
        project.segments.filter { seg ->
            val matchesFilter = when (filter) {
                "IN_PROGRESS" -> seg.status == SegmentStatus.IN_PROGRESS
                "COMPLETED" -> seg.status == SegmentStatus.COMPLETED
                "CONFLICT" -> seg.conflictPoles > 0
                else -> true
            }
            val matchesSearch = seg.name.contains(searchQuery, ignoreCase = true) ||
                    seg.route.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isMultiSelectionMode) {
            // Contextual Action Bar for Multi-Select
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF37474F),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedSegmentIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Batalkan Seleksi", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedSegmentIds.size} Dipilih",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus yang Dipilih", tint = Color(0xFFEF5350))
                    }
                }
            }
        } else {
            // Compact Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = TelecomPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Segment (${project.segments.size} Total)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    NetworkStatusDot(networkStatus = networkStatus, size = 11.dp)
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSegmentSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari segment...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filterOptions = listOf(
                "ALL" to "Semua",
                "IN_PROGRESS" to "Sedang Berjalan",
                "COMPLETED" to "Selesai",
                "CONFLICT" to "Konflik"
            )

            filterOptions.forEach { (key, label) ->
                FilterChip(
                    selected = filter == key,
                    onClick = { viewModel.setSegmentFilter(key) },
                    label = { Text(label, fontWeight = if (filter == key) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelecomPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Segment List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredSegments, key = { it.id }) { segment ->
                    val isSelected = selectedSegmentIds.contains(segment.id)
                    SegmentCard(
                        segment = segment,
                        isSelected = isSelected,
                        isMultiSelectionMode = isMultiSelectionMode,
                        onLongClick = {
                            if (!isMultiSelectionMode) {
                                selectedSegmentIds = setOf(segment.id)
                            }
                        },
                        onClick = {
                            if (isMultiSelectionMode) {
                                selectedSegmentIds = if (isSelected) {
                                    selectedSegmentIds - segment.id
                                } else {
                                    selectedSegmentIds + segment.id
                                }
                            } else {
                                activeSegmentForAction = segment
                            }
                        }
                    )
                }
            }

            FloatingActionButton(
                onClick = { viewModel.navigateTo(ScreenFlow.CREATE_SEGMENT) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp),
                containerColor = TelecomPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Buat Segment")
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false 
                if (selectedSegmentIds.size == 1) selectedSegmentIds = emptySet()
            },
            title = { Text("Hapus Segment", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin menghapus ${selectedSegmentIds.size} segment? Semua tiang dan foto yang terikat pada segment ini juga akan dihapus secara permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        Log.d("DeleteTrace", "[UI] User clicked delete confirm for ${selectedSegmentIds.size} segments: $selectedSegmentIds")
                        viewModel.deleteSelectedSegments(selectedSegmentIds.toList())
                        selectedSegmentIds = emptySet()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmDialog = false 
                    if (selectedSegmentIds.size == 1) selectedSegmentIds = emptySet()
                }) {
                    Text("Batal")
                }
            }
        )
    }

    // Modal Choice Bottom Sheet: Quick Tagging vs Tag Polling PM vs Detail
    activeSegmentForAction?.let { segment ->
        ModalBottomSheet(
            onDismissRequest = { activeSegmentForAction = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text(
                        text = "PILIH MODE SURVEI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = segment.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TelecomPrimary
                    )
                    Text(
                        text = "${segment.route.ifEmpty { "Segment Manual" }} • ${segment.completedPoles}/${segment.totalPoles} tiang selesai",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                }

                HorizontalDivider()

                // Option 1: Quick Tagging
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val segId = segment.id
                            activeSegmentForAction = null
                            viewModel.startQuickTaggingForSegment(segId)
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF80CBC4))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00897B),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Quick Tagging",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF004D40)
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF00897B)
                                ) {
                                    Text(
                                        text = "Cepat",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "1x Fix GPS + 1 Foto Tiang. Ringkas & efisien untuk inventarisasi tiang rute panjang.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF00695C)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF004D40)
                        )
                    }
                }

                // Option 2: Tag Polling PM (Preventive Maintenance)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val segId = segment.id
                            activeSegmentForAction = null
                            viewModel.startPmSurveyForSegment(segId)
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF90CAF9))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = TelecomPrimary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tag Polling PM (Lengkap)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TelecomPrimary
                            )
                            Text(
                                text = "Peta deviasi rute FO, GNSS telemetri 10 sampel, 10 foto SOP, atribut fisik tiang, kabel & aksesoris.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0D47A1)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TelecomPrimary
                        )
                    }
                }

                // Option 3: Buka Detail Segmen & Tiang
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val segId = segment.id
                            activeSegmentForAction = null
                            onSegmentClick(segId)
                        },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCFD8DC))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFECEFF1),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF455A64),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lihat Detail Segmen & Daftar Tiang",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF37474F)
                            )
                            Text(
                                text = "Tinjau statistik, peta sebaran tiang, dan daftar tiang pada segmen ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SegmentCard(
    segment: Segment,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isMultiSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) TelecomPrimary else Color.LightGray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = segment.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )
                        Text(
                            text = segment.route.ifEmpty { "Segment Manual" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    SegmentStatusBadge(status = segment.status)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar & Counts
                val progressFraction = if (segment.totalPoles > 0) segment.completedPoles.toFloat() / segment.totalPoles else 0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${segment.completedPoles} / ${segment.totalPoles} selesai",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (segment.conflictPoles > 0) {
                        Text(
                            text = "⚠ ${segment.conflictPoles} Konflik",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color(0xFF2E7D32),
                    trackColor = Color(0xFFE0E0E0),
                )
            }
        }
    }
}
