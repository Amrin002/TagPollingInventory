package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.auth.UserSession
import co.id.lintasarta.tagpollinginventory.data.model.Project
import co.id.lintasarta.tagpollinginventory.location.LocationData
import co.id.lintasarta.tagpollinginventory.network.AreaDto
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.ui.components.NetworkStatusDot
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TagPollingInventoryTheme
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToCameraSettings: () -> Unit,
    onNavigateToLogin: () -> Unit = {}
) {
    val project by viewModel.project.collectAsState()
    val isDownloading by viewModel.isMapDownloading.collectAsState()
    val progress by viewModel.mapDownloadProgress.collectAsState()
    val status by viewModel.mapDownloadStatus.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val mapCacheSize by viewModel.mapCacheSizeMB.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

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
        currentUser = currentUser,
        availableAreas = viewModel.availableAreas,
        deviceModel = viewModel.getDeviceModel(),
        onUpdateSurveyorName = { viewModel.updateSurveyorName(it) },
        onUpdateArea = { id, label -> viewModel.setOperationalArea(id, label) },
        onCancelMapDownload = { viewModel.cancelMapDownload() },
        onStartMapDownload = { lat, lng, radius -> viewModel.startMapDownload(lat, lng, radius) },
        onClearMapCache = { viewModel.clearMapCache() },
        onClearAllData = { viewModel.clearAllData() },
        onNavigateToCameraSettings = onNavigateToCameraSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    project: Project,
    isDownloading: Boolean,
    progress: Float,
    status: String,
    currentLocation: LocationData,
    networkStatus: NetworkStatus,
    mapCacheSize: Double,
    currentUser: UserSession = UserSession(),
    availableAreas: List<AreaDto> = emptyList(),
    deviceModel: String = "Android Device",
    onUpdateSurveyorName: (String) -> Unit = {},
    onUpdateArea: (Int, String) -> Unit = { _, _ -> },
    onCancelMapDownload: () -> Unit,
    onStartMapDownload: (lat: Double, lng: Double, radius: Double) -> Unit,
    onClearMapCache: () -> Unit,
    onClearAllData: () -> Unit,
    onNavigateToCameraSettings: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameInput by remember(currentUser.displayName) { mutableStateOf(currentUser.displayName) }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Ubah Nama Surveyor Lapangan", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nama ini akan dicantumkan secara otomatis pada setiap pin Google Earth dan file KMZ survei.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF546E7A)
                    )
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Nama Lengkap") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            onUpdateSurveyorName(editNameInput)
                        }
                        showEditNameDialog = false
                    },
                    enabled = editNameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Compact Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TelecomPrimary,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pengaturan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Area ${currentUser.areaId} ${currentUser.areaLabel} • Wilayah Kerja",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                NetworkStatusDot(networkStatus = networkStatus, size = 11.dp)
            }
        }

        // 2. Profil Akun Surveyor
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
                    Text(
                        text = "PROFIL PENGGUNA RNIMS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val avatarBg = when {
                        currentUser.role.contains("Admin", ignoreCase = true) -> Color(0xFFF3E5F5)
                        currentUser.role.contains("Manager", ignoreCase = true) -> Color(0xFFE3F2FD)
                        else -> Color(0xFFE8F5E9)
                    }
                    val avatarIconColor = when {
                        currentUser.role.contains("Admin", ignoreCase = true) -> Color(0xFF6A1B9A)
                        currentUser.role.contains("Manager", ignoreCase = true) -> Color(0xFF1565C0)
                        else -> Color(0xFF2E7D32)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(avatarBg, RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = avatarIconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentUser.displayName.ifEmpty { "Field Surveyor" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    editNameInput = currentUser.displayName
                                    showEditNameDialog = true
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Nama Surveyor",
                                    tint = TelecomPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF1F8E9)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = deviceModel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text(
                                text = currentUser.email.ifEmpty { currentUser.username },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Pemilih Area Operasional (Dropdown Master Data 24 Area)
                var isAreaDropdownExpanded by remember { mutableStateOf(false) }
                val currentAreaObj = availableAreas.find { it.areaId == currentUser.areaId }
                val areaDisplayName = currentAreaObj?.let { "Area ${it.areaId} - ${it.code} (${it.name})" }
                    ?: "Area ${currentUser.areaId} - ${currentUser.areaLabel}"

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WILAYAH KERJA / AREA ID",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )
                        Text(
                            text = "${availableAreas.size} Area Tersedia",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = isAreaDropdownExpanded,
                        onExpandedChange = { isAreaDropdownExpanded = !isAreaDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = areaDisplayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pilih Wilayah Operasional") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAreaDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(8.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = isAreaDropdownExpanded,
                            onDismissRequest = { isAreaDropdownExpanded = false }
                        ) {
                            availableAreas.forEach { area ->
                                val isSelected = area.areaId == currentUser.areaId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Area ${area.areaId} - ${area.code}",
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                    color = if (isSelected) TelecomPrimary else Color.Unspecified
                                                )
                                                if (isSelected) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = TelecomPrimary.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "AKTIF",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TelecomPrimary,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = area.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    },
                                    onClick = {
                                        onUpdateArea(area.areaId, area.code)
                                        isAreaDropdownExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Pengaturan Kamera Geotag SOP
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToCameraSettings() },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Pengaturan Kamera Geotag",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SOP Watermark, GPS, dan resolusi foto",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 4. Peta & Penyimpanan Offline
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
                    Text(
                        text = "PETA & PENYIMPANAN OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "Cache: ${String.format(Locale.US, "%.1f", mapCacheSize)} MB",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                }

                if (isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(status, style = MaterialTheme.typography.bodySmall, color = TelecomPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = TelecomPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onCancelMapDownload() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Batalkan Unduhan", color = Color(0xFFC62828))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val lat = if (currentLocation.isAvailable && currentLocation.latitude != 0.0) currentLocation.latitude else -3.6954
                                val lng = if (currentLocation.isAvailable && currentLocation.longitude != 0.0) currentLocation.longitude else 128.1814
                                onStartMapDownload(lat, lng, 3.0)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unduh Peta (3 KM)", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { onClearMapCache() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC62828))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus Cache", color = Color(0xFFC62828), fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFEEEEEE))

                // Tombol Reset Data Lokal (Clean text button with confirmation)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reset data survei lokal di perangkat",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    TextButton(
                        onClick = { showClearDialog = true }
                    ) {
                        Text("Reset Data", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. Simple Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tag Polling Inventory • v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Text(
                text = "PT Aplikanusa Lintasarta",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray
            )
        }
    }

    // Dialog Konfirmasi Reset Data
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Reset Data Survei Lokal?", fontWeight = FontWeight.Bold) },
            text = { Text("Semua data tiang lokal yang belum diekspor akan dihapus dari perangkat ini. Lanjutkan?") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Hapus Data", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Batal")
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
                id = "PRJ-MPO-18",
                name = "Project MPO (Maluku Papua Operation)",
                location = "Papua & Maluku",
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
                latitude = -3.6954,
                longitude = 128.1814,
                accuracy = 3.5f,
                altitude = 12.0,
                isAvailable = true,
                providerName = "GPS",
                timestamp = System.currentTimeMillis()
            ),
            networkStatus = NetworkStatus(isOnline = false, connectionType = "Offline"),
            mapCacheSize = 0.0,
            onCancelMapDownload = {},
            onStartMapDownload = { _, _, _ -> },
            onClearMapCache = {},
            onClearAllData = {},
            onNavigateToCameraSettings = {}
        )
    }
}
