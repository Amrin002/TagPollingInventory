package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.model.CameraSettings
import co.id.lintasarta.tagpollinginventory.data.model.TimezoneOption
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GeotagCameraSettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val networkStatus by viewModel.networkStatus.collectAsState()

    Scaffold(
        topBar = {
            TopBar(
                title = "Pengaturan Kamera Geotag",
                subtitle = "SOP Lapangan Fiber Optik",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GeotagCameraSettingsCard(viewModel)
        }
    }
}

@Composable
fun GeotagCameraSettingsCard(viewModel: MainViewModel) {
    val cameraSettings by viewModel.cameraSettings.collectAsState()
    var showTimezoneDialog by remember { mutableStateOf(false) }

    if (showTimezoneDialog) {
        AlertDialog(
            onDismissRequest = { showTimezoneDialog = false },
            title = {
                Text(
                    text = "Pilih Zona Waktu Watermark",
                    fontWeight = FontWeight.Bold,
                    color = TelecomPrimary,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimezoneOption.values().forEach { option ->
                        val isSelected = option == cameraSettings.timezoneOption
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateCameraSettings(cameraSettings.copy(timezoneOption = option))
                                        showTimezoneDialog = false
                                    }
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updateCameraSettings(cameraSettings.copy(timezoneOption = option))
                                    showTimezoneDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = TelecomPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = option.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = if (isSelected) TelecomPrimary else Color.Black
                                )
                                Text(
                                    text = if (option == TimezoneOption.AUTO) {
                                        "Mengikuti jam di HP saat pengambilan foto"
                                    } else {
                                        "Zona waktu resmi: ${option.code} (${option.zoneId})"
                                    },
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimezoneDialog = false }) {
                    Text("Tutup", color = TelecomPrimary)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Title Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(TelecomPrimary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraEnhance,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "PENGATURAN KAMERA GEOTAG (SOP LAPANGAN)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                    Text(
                        text = "Konfigurasi geotagging, watermark, dan kualitas resolusi foto kamera.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            HorizontalDivider()

            // 1. Lokasi / GPS
            GeotagSettingRow(
                icon = Icons.Default.LocationOn,
                title = "Lokasi / GPS",
                subtitle = "Aktifkan pencatatan koordinat GPS pada foto",
                isChecked = cameraSettings.isGpsActive,
                onCheckedChange = { viewModel.updateCameraSettings(cameraSettings.copy(isGpsActive = it)) },
                statusBadgeText = if (cameraSettings.isGpsActive) "✓ GPS Aktif" else "GPS Non-Aktif"
            )

            // 2. Akurasi Lokasi
            GeotagSettingRow(
                icon = Icons.Default.GpsFixed,
                title = "Akurasi Lokasi Tinggi",
                subtitle = "Format koordinat presisi tinggi (6 desimal: DD.dddddd°)",
                isChecked = cameraSettings.isHighAccuracy,
                onCheckedChange = { viewModel.updateCameraSettings(cameraSettings.copy(isHighAccuracy = it)) },
                statusBadgeText = if (cameraSettings.isHighAccuracy) "✓ Presisi 6 Desimal (±10cm)" else "Presisi Standar (4 Desimal)"
            )

            // 3. Watermark / Timestamp
            GeotagSettingRow(
                icon = Icons.Default.AccessTime,
                title = "Watermark Teks Visual",
                subtitle = "Cetak banner tiang, koordinat & waktu langsung pada foto",
                isChecked = cameraSettings.isWatermarkActive,
                onCheckedChange = { viewModel.updateCameraSettings(cameraSettings.copy(isWatermarkActive = it)) },
                statusBadgeText = if (cameraSettings.isWatermarkActive) "✓ Watermark Banner Aktif" else "Watermark Non-Aktif (Foto Polos)"
            )

            // 4. Zona Waktu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimezoneDialog = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = TelecomPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Zona Waktu Watermark", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = cameraSettings.timezoneOption.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE3F2FD),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "Zona Terpilih: ${cameraSettings.timezoneOption.code}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TelecomPrimary
                            )
                        }
                    }
                }

                IconButton(onClick = { showTimezoneDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Pilih Zona Waktu",
                        tint = TelecomPrimary
                    )
                }
            }

            // 5. Simpan Metadata EXIF
            GeotagSettingRow(
                icon = Icons.Default.PhotoCamera,
                title = "Simpan Metadata EXIF",
                subtitle = "Tanam koordinat GPS dan SHA-256 hash ke metadata JPEG",
                isChecked = cameraSettings.isExifSaveActive,
                onCheckedChange = { viewModel.updateCameraSettings(cameraSettings.copy(isExifSaveActive = it)) },
                statusBadgeText = if (cameraSettings.isExifSaveActive) "✓ EXIF GPS & Hash SHA-256 Tersimpan" else "EXIF Non-Aktif"
            )

            // 6. Kompresi Foto (Default: OFF / Kualitas Penuh 100%)
            GeotagSettingRow(
                icon = Icons.Default.HighQuality,
                title = "Kompresi Foto",
                subtitle = "Kompres resolusi maks 1920px & JPEG 80% (Hemat memori)",
                isChecked = cameraSettings.isCompressionEnabled,
                onCheckedChange = { viewModel.updateCameraSettings(cameraSettings.copy(isCompressionEnabled = it)) },
                statusBadgeText = if (cameraSettings.isCompressionEnabled) "Mode Kompresi Aktif (Maks 1920px, ~500 KB)" else "✓ Kualitas Penuh 100% (Resolusi Asli Kamera)"
            )

            HorizontalDivider()

            // CONTOH WATERMARK PADA FOTO PREVIEW BOX
            Text(
                text = "PRATINJAU HASIL FOTO & WATERMARK",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TelecomPrimary
            )

            val previewCoord = if (!cameraSettings.isGpsActive) {
                "Koor: GPS Nonaktif"
            } else if (cameraSettings.isHighAccuracy) {
                "Koor: -6.200123, 106.816456"
            } else {
                "Koor: -6.2001, 106.8164"
            }

            val previewTime = remember(cameraSettings.timezoneOption) {
                val tz = if (cameraSettings.timezoneOption == TimezoneOption.AUTO) {
                    TimeZone.getDefault()
                } else {
                    TimeZone.getTimeZone(cameraSettings.timezoneOption.zoneId)
                }
                val sdf = SimpleDateFormat("dd MMM yyyy — HH:mm:ss", Locale.US).apply {
                    timeZone = tz
                }
                val tzCode = if (cameraSettings.timezoneOption == TimezoneOption.AUTO) {
                    tz.getDisplayName(tz.inDaylightTime(Date()), TimeZone.SHORT, Locale.US)
                } else {
                    cameraSettings.timezoneOption.code
                }
                "${sdf.format(Date())} $tzCode"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dark Photo Watermark Preview Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    color = Color(0xFF212121),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        if (cameraSettings.isWatermarkActive) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("TIANG: TNG-001/MPO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("$previewCoord", color = Color(0xFFEEEEEE), fontSize = 10.sp)
                                Text("Waktu: $previewTime", color = Color(0xFFEEEEEE), fontSize = 10.sp)
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Foto Polos / Bersih", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Watermark visual dinonaktifkan", color = Color.Gray, fontSize = 9.sp)
                            }
                        }
                    }
                }

                // Info Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    color = Color(0xFFE3F2FD),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TelecomPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (cameraSettings.isCompressionEnabled) "Kualitas Kompresi" else "Kualitas Penuh (100%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TelecomPrimary
                            )
                        }
                        Text(
                            text = if (cameraSettings.isCompressionEnabled) {
                                "• Resolusi: Maks 1920x1080 px\n• Kualitas: 80% JPEG (~400KB)"
                            } else {
                                "• Resolusi: Asli Sensor Kamera\n• Kualitas: 100% JPEG Maksimal"
                            },
                            fontSize = 9.sp,
                            color = Color.DarkGray
                        )
                        Text(
                            text = if (cameraSettings.isExifSaveActive) "• Metadata: GPS & SHA-256 Aktif" else "• Metadata: Nonaktif",
                            fontSize = 9.sp,
                            color = if (cameraSettings.isExifSaveActive) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // TIPS PENTING BANNER
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFEF6C00), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TIPS PENTING SOP LAPANGAN:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
                    }
                    Text("1. Kualitas Penuh 100% menghasilkan foto resolusi tinggi sangat tajam untuk audit kabel & tiang.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100))
                    Text("2. Gunakan Watermark & EXIF aktif untuk bukti otentik survei MPO as-built berstandar ISO-27001.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100))
                }
            }
        }
    }
}

@Composable
fun GeotagSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    statusBadgeText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = TelecomPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isChecked) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = statusBadgeText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isChecked) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}
