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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.id.lintasarta.tagpollinginventory.data.model.Project
import co.id.lintasarta.tagpollinginventory.location.LocationData
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TagPollingInventoryTheme
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val project by viewModel.project.collectAsState()
    val isDownloading by viewModel.isMapDownloading.collectAsState()
    val progress by viewModel.mapDownloadProgress.collectAsState()
    val status by viewModel.mapDownloadStatus.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val mapCacheSize by viewModel.mapCacheSizeMB.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.updateMapCacheSize()
    }

    SettingsScreenContent(
        project = project,
        isDownloading = isDownloading,
        progress = progress,
        status = status,
        currentLocation = currentLocation,
        networkStatus = networkStatus,
        mapCacheSize = mapCacheSize,
        onCancelMapDownload = { viewModel.cancelMapDownload() },
        onStartMapDownload = { lat, lng, radius -> viewModel.startMapDownload(lat, lng, radius) },
        onClearMapCache = { viewModel.clearMapCache() },
        onClearAllData = { viewModel.clearAllData() }
    )
}

@Composable
fun SettingsScreenContent(
    project: Project,
    isDownloading: Boolean,
    progress: Float,
    status: String,
    currentLocation: LocationData,
    networkStatus: NetworkStatus,
    mapCacheSize: Double,
    onCancelMapDownload: () -> Unit,
    onStartMapDownload: (lat: Double, lng: Double, radius: Double) -> Unit,
    onClearMapCache: () -> Unit,
    onClearAllData: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

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
                        text = "Settings & Data Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Tag Poling Inventory v1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (networkStatus.isOnline) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                ) {
                    Text(
                        text = if (networkStatus.isOnline) "ONLINE" else "OFFLINE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // 1. Data Architecture Card (Offline First Visualizer)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "DATA ARCHITECTURE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TelecomPrimary
                )
                Text(
                    text = "FIELD DATA → LOCAL DATABASE → LOCAL INVENTORY → EXPORT ENGINE → CSV / KML / KMZ",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D47A1)
                )
                Text(
                    text = "This application operates 100% offline. All survey data is safely stored in local device storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF37474F)
                )
            }
        }

        // 2. Project Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PROJECT INFORMATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active Project", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(project.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TelecomPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Location", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(project.location, style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Segments", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${project.segments.size} Segments", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // 3. Storage & GPS Settings
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
                    text = "SYSTEM CONFIGURATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("GPS Accuracy Requirement", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Warn if accuracy is worse than ±5.0m", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text("±3.5m", fontWeight = FontWeight.Bold, color = TelecomPrimary)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Offline Map Canvas", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Pre-loaded vector route maps", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text("Enabled", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            }
        }

        // 4. Offline Map Management
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OFFLINE MAP MANAGEMENT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "Size: ${String.format(Locale.US, "%.1f", mapCacheSize)} MB",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                }
                
                if (isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(status, style = MaterialTheme.typography.bodySmall, color = TelecomPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = TelecomPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onCancelMapDownload() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                        ) {
                            Text("Cancel Download")
                        }
                    }
                } else {

                    Text(
                        text = status.ifEmpty { "Download detailed street and building background maps for offline use." },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.startsWith("Selesai") || status.startsWith("Cache")) Color(0xFF2E7D32) else Color.Gray
                    )
                    
                    var downloadRadius by remember { mutableFloatStateOf(2f) }
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Radius Area:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TelecomPrimary
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", downloadRadius)} KM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (downloadRadius > 5f) Color(0xFFEF6C00) else TelecomPrimary
                            )
                        }
                        Slider(
                            value = downloadRadius,
                            onValueChange = { downloadRadius = it },
                            valueRange = 1f..15f,
                            steps = 28, // Every 0.5 KM increments
                            colors = SliderDefaults.colors(
                                thumbColor = TelecomPrimary,
                                activeTrackColor = TelecomPrimary,
                                inactiveTrackColor = Color.LightGray
                            )
                        )
                        if (downloadRadius > 5f) {
                            Text(
                                text = "Warning: Large radius might take a long time to download.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFEF6C00)
                            )
                        }
                    }
                    
                    OutlinedButton(
                        onClick = {
                            val lat = if (currentLocation.isAvailable && currentLocation.latitude != 0.0) currentLocation.latitude else -3.6954
                            val lng = if (currentLocation.isAvailable && currentLocation.longitude != 0.0) currentLocation.longitude else 128.1814
                            onStartMapDownload(lat, lng, downloadRadius.toDouble())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Local Map Area")
                    }
                    
                    TextButton(
                        onClick = { onClearMapCache() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFC62828))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Map Cache", color = Color(0xFFC62828))
                    }
                }
            }
        }

        // 5. Data Management Actions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "DATA MANAGEMENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup Local Data")
                }

                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Local Data", fontWeight = FontWeight.Bold)
                }
            }
        }

        // App Version Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tag Poling Inventory v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Text(
                text = "Offline Field Survey Mobile Engine",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray
            )
        }
    }

    // Clear Data Dialog Confirmation
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Reset Local Data?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to clear all local survey data?") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Clear Data", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    TagPollingInventoryTheme {
        SettingsScreenContent(
            project = Project(
                id = "P-1234",
                name = "Sample Project",
                location = "Jakarta",
                totalPoles = 150,
                completedPoles = 45,
                conflictPoles = 5,
                uncompletedPoles = 100,
                segments = emptyList()
            ),
            isDownloading = false,
            progress = 0f,
            status = "Ready",
            currentLocation = LocationData(
                latitude = -6.200000,
                longitude = 106.816666,
                accuracy = 3.5f,
                altitude = 12.0,
                isAvailable = true,
                providerName = "GPS",
                timestamp = System.currentTimeMillis()
            ),
            onCancelMapDownload = {},
            onStartMapDownload = { _, _, _ -> },
            onClearMapCache = {},
            onClearAllData = {},
            networkStatus = NetworkStatus(isOnline = false, connectionType = "Offline"),
            mapCacheSize = 0.0
        )
    }
}
