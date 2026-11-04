package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.auth.UserSession
import co.id.lintasarta.tagpollinginventory.data.model.Project
import co.id.lintasarta.tagpollinginventory.location.LocationData
import co.id.lintasarta.tagpollinginventory.network.ConnectionTestResult
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.network.RnimsApiClient
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
    val rnimsBaseUrl by viewModel.rnimsBaseUrl.collectAsState()
    val rnimsServerConnected by viewModel.rnimsServerConnected.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val mpoSegments by viewModel.mpoSegments.collectAsState()
    val isSyncingRnims by viewModel.isSyncingRnims.collectAsState()
    val rnimsSyncMessage by viewModel.rnimsSyncMessage.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isSyncingPoles by viewModel.isSyncingPoles.collectAsState()
    val syncPolesMessage by viewModel.syncPolesMessage.collectAsState()

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
        rnimsBaseUrl = rnimsBaseUrl,
        rnimsServerConnected = rnimsServerConnected,
        connectionTestResult = connectionTestResult,
        mpoSegmentsCount = mpoSegments.size,
        isSyncingRnims = isSyncingRnims,
        rnimsSyncMessage = rnimsSyncMessage,
        currentUser = currentUser,
        isSyncingPoles = isSyncingPoles,
        syncPolesMessage = syncPolesMessage,
        onUpdateBaseUrl = { viewModel.setRnimsBaseUrl(it) },
        onTestConnection = { viewModel.testRnimsConnection() },
        onSyncMpoSegments = { viewModel.syncMpoSegmentsFromServer() },
        onSyncPoles = { viewModel.syncPolesToServer() },
        onLoginRnims = { user, pass, cb -> viewModel.loginRnims(user, pass, cb) },
        onLoginOffline = { pin -> viewModel.loginOffline(pin) },
        onLogout = { viewModel.logout() },
        onUpdateArea = { id, label -> viewModel.setOperationalArea(id, label) },
        onCancelMapDownload = { viewModel.cancelMapDownload() },
        onStartMapDownload = { lat, lng, radius -> viewModel.startMapDownload(lat, lng, radius) },
        onClearMapCache = { viewModel.clearMapCache() },
        onClearAllData = { viewModel.clearAllData() },
        onNavigateToCameraSettings = onNavigateToCameraSettings,
        onNavigateToLogin = onNavigateToLogin
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
    rnimsBaseUrl: String = RnimsApiClient.DEFAULT_USB_URL,
    rnimsServerConnected: Boolean? = null,
    connectionTestResult: ConnectionTestResult? = null,
    mpoSegmentsCount: Int = 143,
    isSyncingRnims: Boolean = false,
    rnimsSyncMessage: String? = null,
    currentUser: UserSession = UserSession(),
    isSyncingPoles: Boolean = false,
    syncPolesMessage: String? = null,
    onUpdateBaseUrl: (String) -> Unit = {},
    onTestConnection: () -> Unit = {},
    onSyncMpoSegments: () -> Unit = {},
    onSyncPoles: () -> Unit = {},
    onLoginRnims: (String, String, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onLoginOffline: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    onUpdateArea: (Int, String) -> Unit = { _, _ -> },
    onCancelMapDownload: () -> Unit,
    onStartMapDownload: (lat: Double, lng: Double, radius: Double) -> Unit,
    onClearMapCache: () -> Unit,
    onClearAllData: () -> Unit,
    onNavigateToCameraSettings: () -> Unit,
    onNavigateToLogin: () -> Unit = {}
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showAdvancedServerConfig by remember { mutableStateOf(false) }

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
                        text = "AKUN SURVEYOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (currentUser.isOfflineSession) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = if (currentUser.isOfflineSession) "Mode Offline" else "Sesi Online",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (currentUser.isOfflineSession) Color(0xFFE65100) else Color(0xFF2E7D32)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFE3F2FD), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TelecomPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser.displayName.ifEmpty { "Field Surveyor" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${currentUser.username} • Role: ${currentUser.role}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Area Operasional Surveyor:", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                "Area ${currentUser.areaId} - ${currentUser.areaLabel}",
                                fontWeight = FontWeight.ExtraBold,
                                color = TelecomPrimary
                            )
                            Text(
                                "Sesuai profil akun surveyor yang login",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TelecomPrimary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "AKUN PROFIL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TelecomPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigateToLogin() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ganti Akun")
                    }

                    TextButton(
                        onClick = { onLogout() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC62828))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Keluar")
                    }
                }
            }
        }

        // 3. Sinkronisasi Data & Server RNIMS
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
                        text = "SINKRONISASI SERVER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )

                    val statusText = when {
                        rnimsServerConnected == true -> "Terhubung (${connectionTestResult?.latencyMs ?: 0}ms)"
                        rnimsServerConnected == false -> "Server Offline"
                        else -> "Belum Diperiksa"
                    }
                    val statusColor = when {
                        rnimsServerConnected == true -> Color(0xFF2E7D32)
                        rnimsServerConnected == false -> Color(0xFFC62828)
                        else -> Color.Gray
                    }
                    val statusBg = when {
                        rnimsServerConnected == true -> Color(0xFFE8F5E9)
                        rnimsServerConnected == false -> Color(0xFFFFEBEE)
                        else -> Color(0xFFF5F5F5)
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                // Action Buttons for Technicians
                Button(
                    onClick = { onSyncPoles() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSyncingPoles
                ) {
                    if (isSyncingPoles) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mengirim Data...")
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kirim Data Survei ke Server", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { onSyncMpoSegments() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSyncingRnims
                ) {
                    if (isSyncingRnims) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Memperbarui Segmen...")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Perbarui Data Segmen (143 Rute)")
                    }
                }

                if (rnimsSyncMessage != null || syncPolesMessage != null) {
                    val msg = syncPolesMessage ?: rnimsSyncMessage ?: ""
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                }

                // Collapsible Advanced Server Configuration (Cleaner & hidden from regular technicians)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedServerConfig = !showAdvancedServerConfig }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pengaturan Alamat Server",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (showAdvancedServerConfig) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showAdvancedServerConfig) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8F9FA), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = rnimsBaseUrl == RnimsApiClient.DEFAULT_USB_URL,
                                onClick = { onUpdateBaseUrl(RnimsApiClient.DEFAULT_USB_URL) },
                                label = { Text("USB", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = rnimsBaseUrl == RnimsApiClient.DEFAULT_WIFI_URL,
                                onClick = { onUpdateBaseUrl(RnimsApiClient.DEFAULT_WIFI_URL) },
                                label = { Text("Wi-Fi", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = rnimsBaseUrl == RnimsApiClient.DEFAULT_PROD_URL,
                                onClick = { onUpdateBaseUrl(RnimsApiClient.DEFAULT_PROD_URL) },
                                label = { Text("Prod", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        var inputUrl by remember(rnimsBaseUrl) { mutableStateOf(rnimsBaseUrl) }
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = {
                                inputUrl = it
                                onUpdateBaseUrl(it)
                            },
                            label = { Text("URL Server", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { onTestConnection() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Test")
                                }
                            }
                        )
                    }
                }
            }
        }

        // 4. Pengaturan Kamera Geotag SOP
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

        // 5. Peta & Penyimpanan Offline
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

        // 6. Simple Footer
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
