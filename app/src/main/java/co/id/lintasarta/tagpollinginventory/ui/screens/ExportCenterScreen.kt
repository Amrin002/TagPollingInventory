package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import co.id.lintasarta.tagpollinginventory.data.model.ExportFormat
import co.id.lintasarta.tagpollinginventory.data.model.ExportOptions
import co.id.lintasarta.tagpollinginventory.data.model.ExportScope
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportCenterScreen(
    viewModel: MainViewModel,
    onViewHistoryClick: () -> Unit
) {
    val project by viewModel.project.collectAsState()
    val segmentId by viewModel.selectedSegmentId.collectAsState()

    val activeSegment = remember(project, segmentId) {
        project.segments.find { it.id == segmentId } 
            ?: project.segments.firstOrNull() 
            ?: Segment(
                id = "", 
                name = "Tidak Ada Segment Dipilih", 
                route = "T/A", 
                startPoint = Pair(0.0, 0.0), 
                endPoint = Pair(0.0, 0.0), 
                status = SegmentStatus.NOT_STARTED, 
                totalPoles = 0, 
                completedPoles = 0, 
                conflictPoles = 0
            )
    }

    var showOptionsDialog by remember { mutableStateOf(false) }
    var isSegmentDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with History Button
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ekspor Inventaris",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "${project.name} • ${project.segments.size} Segment",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                OutlinedButton(
                    onClick = onViewHistoryClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Riwayat Ekspor",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Riwayat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Summary & Segment Dropdown Scope Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PILIH CAKUPAN EKSPOR / SEGMENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TelecomPrimary
                )

                // Interactive Segment Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = isSegmentDropdownExpanded,
                    onExpandedChange = { isSegmentDropdownExpanded = !isSegmentDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = activeSegment.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cakupan Segment Aktif") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSegmentDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(8.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = isSegmentDropdownExpanded,
                        onDismissRequest = { isSegmentDropdownExpanded = false }
                    ) {
                        project.segments.forEach { segment ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(segment.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${segment.totalPoles} tiang • ${segment.route.ifEmpty { "Tidak Ada Rute" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.selectSegment(segment.id, navigateToDetail = false)
                                    isSegmentDropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Record Summary Metrics for Selected Segment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("${activeSegment.totalPoles}", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Selesai", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                        Text("${activeSegment.completedPoles}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Konflik", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                        Text("${activeSegment.conflictPoles}", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Catatan Lokal", style = MaterialTheme.typography.labelSmall, color = TelecomPrimary)
                        Text("${activeSegment.completedPoles + activeSegment.conflictPoles}", fontWeight = FontWeight.Bold, color = TelecomPrimary)
                    }
                }
            }
        }

        Text(
            text = "PILIH FORMAT EKSPOR",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF546E7A)
        )

        // 1. CSV Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Format CSV",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ekspor atribut dan koordinat inventaris tiang ke spreadsheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.initiateExportFormat(ExportFormat.CSV)
                            showOptionsDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Ekspor CSV", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. KML Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFE3F2FD), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = TelecomPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Format KML",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ekspor lokasi dan atribut tiang sebagai KML untuk Google Earth & GIS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.initiateExportFormat(ExportFormat.KML)
                            showOptionsDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Ekspor KML", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. KMZ Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFFFF3E0), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        tint = Color(0xFFEF6C00),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Format KMZ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ekspor paket KML terkompresi dengan lampiran foto dan metadata.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.initiateExportFormat(ExportFormat.KMZ)
                            showOptionsDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Ekspor KMZ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Export Options Modal Dialog
    if (showOptionsDialog) {
        ExportOptionsModal(
            viewModel = viewModel,
            onDismiss = { showOptionsDialog = false },
            onGenerateClick = {
                showOptionsDialog = false
                viewModel.startGeneratingExport()
            }
        )
    }
}

@Composable
fun ExportOptionsModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onGenerateClick: () -> Unit
) {
    val format by viewModel.selectedExportFormat.collectAsState()
    var options by remember { mutableStateOf(viewModel.exportOptions.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Opsi Ekspor (${format.displayName})",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cakupan Ekspor",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TelecomPrimary
                )

                ExportScope.values().forEach { scope ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = options.scope == scope,
                            onClick = { options = options.copy(scope = scope) }
                        )
                        Text(scope.displayName)
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Status Data",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TelecomPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeCompleted,
                        onCheckedChange = { options = options.copy(includeCompleted = it) }
                    )
                    Text("Catatan Selesai")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeConflict,
                        onCheckedChange = { options = options.copy(includeConflict = it) }
                    )
                    Text("Catatan Konflik")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeIncomplete,
                        onCheckedChange = { options = options.copy(includeIncomplete = it) }
                    )
                    Text("Catatan Belum Selesai")
                }

                HorizontalDivider()

                Text(
                    text = "Kolom yang Disertakan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TelecomPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeCoordinates,
                        onCheckedChange = { options = options.copy(includeCoordinates = it) }
                    )
                    Text("Koordinat (Lintang / Bujur)")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeAttributes,
                        onCheckedChange = { options = options.copy(includeAttributes = it) }
                    )
                    Text("Atribut Tiang & Peralatan")
                }

                if (format == ExportFormat.KMZ || format == ExportFormat.KML) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = options.includePhotoReferences,
                            onCheckedChange = { options = options.copy(includePhotoReferences = it) }
                        )
                        Text("Sertakan Referensi Foto")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateExportOptions(options)
                    onGenerateClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary)
            ) {
                Text("Buat Ekspor", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
