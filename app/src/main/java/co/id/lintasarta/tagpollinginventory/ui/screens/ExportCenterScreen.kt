package co.id.lintasarta.tagpollinginventory.ui.screens

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
                name = "No Segment Selected", 
                route = "N/A", 
                startPoint = Pair(0.0, 0.0), 
                endPoint = Pair(0.0, 0.0), 
                status = SegmentStatus.NOT_STARTED, 
                totalPoles = 0, 
                completedPoles = 0, 
                conflictPoles = 0
            )
    }

    var showOptionsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        text = "Export Inventory",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "${project.name} • ${activeSegment.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                IconButton(onClick = onViewHistoryClick) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Export History",
                        tint = Color.White
                    )
                }
            }
        }

        // Summary Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CURRENT SCOPE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = activeSegment.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )
                        Text(
                            text = activeSegment.route,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    OutlinedButton(
                        onClick = onViewHistoryClick,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export History", fontSize = 12.sp)
                    }
                }

                HorizontalDivider()

                // Record Summary Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("${activeSegment.totalPoles}", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Completed", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                        Text("${activeSegment.completedPoles}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Conflict", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                        Text("${activeSegment.conflictPoles}", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Local Records", style = MaterialTheme.typography.labelSmall, color = TelecomPrimary)
                        Text("${activeSegment.completedPoles + activeSegment.conflictPoles}", fontWeight = FontWeight.Bold, color = TelecomPrimary)
                    }
                }
            }
        }

        Text(
            text = "SELECT EXPORT FORMAT",
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
                        text = "CSV Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Export pole inventory attributes and coordinates into spreadsheet.",
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
                        Text("Export CSV", fontWeight = FontWeight.Bold)
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
                        text = "KML Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Export pole locations and attributes as KML for Google Earth & GIS.",
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
                        Text("Export KML", fontWeight = FontWeight.Bold)
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
                        text = "KMZ Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Export compressed KML package with photo attachments and metadata.",
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
                        Text("Export KMZ", fontWeight = FontWeight.Bold)
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
                text = "Export Options (${format.displayName})",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Export Scope",
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
                    text = "Data Status",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TelecomPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeCompleted,
                        onCheckedChange = { options = options.copy(includeCompleted = it) }
                    )
                    Text("Completed Records")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeConflict,
                        onCheckedChange = { options = options.copy(includeConflict = it) }
                    )
                    Text("Conflict Records")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeIncomplete,
                        onCheckedChange = { options = options.copy(includeIncomplete = it) }
                    )
                    Text("Incomplete Records")
                }

                HorizontalDivider()

                Text(
                    text = "Included Fields",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TelecomPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeCoordinates,
                        onCheckedChange = { options = options.copy(includeCoordinates = it) }
                    )
                    Text("Coordinates (Lat / Long)")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = options.includeAttributes,
                        onCheckedChange = { options = options.copy(includeAttributes = it) }
                    )
                    Text("Pole Attributes & Equipment")
                }

                if (format == ExportFormat.KMZ || format == ExportFormat.KML) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = options.includePhotoReferences,
                            onCheckedChange = { options = options.copy(includePhotoReferences = it) }
                        )
                        Text("Include Photo References")
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
                Text("Generate Export", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
