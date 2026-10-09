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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

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
            GeotagCameraSettingsCard()
        }
    }
}

@Composable
fun GeotagCameraSettingsCard() {
    var isGpsActive by remember { mutableStateOf(true) }
    var isHighAccuracy by remember { mutableStateOf(true) }
    var isWatermarkActive by remember { mutableStateOf(true) }
    var isTimezoneSync by remember { mutableStateOf(true) }
    var isExifSaveActive by remember { mutableStateOf(true) }

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
                        text = "Pastikan pengaturan berikut aktif agar foto memiliki lokasi & waktu akurat.",
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
                subtitle = "Aktifkan akses lokasi untuk kamera",
                isChecked = isGpsActive,
                onCheckedChange = { isGpsActive = it },
                statusBadgeText = if (isGpsActive) "✓ GPS Aktif / High Accuracy" else "GPS Non-Aktif"
            )

            // 2. Akurasi Lokasi
            GeotagSettingRow(
                icon = Icons.Default.GpsFixed,
                title = "Akurasi Lokasi",
                subtitle = "Gunakan mode akurasi tertinggi (High Accuracy)",
                isChecked = isHighAccuracy,
                onCheckedChange = { isHighAccuracy = it },
                statusBadgeText = if (isHighAccuracy) "✓ Koordinat: DD.dddddd°" else "Mode Hemat Baterai"
            )

            // 3. Watermark / Timestamp
            GeotagSettingRow(
                icon = Icons.Default.AccessTime,
                title = "Watermark / Timestamp",
                subtitle = "Tampilkan tanggal & waktu pada foto (Next Update)",
                isChecked = isWatermarkActive,
                onCheckedChange = { isWatermarkActive = it },
                statusBadgeText = if (isWatermarkActive) "✓ Timestamp Lokal Aktif" else "Watermark Non-Aktif"
            )

            // 4. Zona Waktu
            GeotagSettingRow(
                icon = Icons.Default.Language,
                title = "Zona Waktu",
                subtitle = "Pilih WIB / GMT+7 (Asia/Jakarta)",
                isChecked = isTimezoneSync,
                onCheckedChange = { isTimezoneSync = it },
                statusBadgeText = if (isTimezoneSync) "✓ Waktu Sinkron: WIB / GMT+7" else "Waktu Lokal Perangkat"
            )

            // 5. Simpan Metadata
            GeotagSettingRow(
                icon = Icons.Default.PhotoCamera,
                title = "Simpan Metadata",
                subtitle = "Simpan informasi lokasi (EXIF GPS) pada setiap foto",
                isChecked = isExifSaveActive,
                onCheckedChange = { isExifSaveActive = it },
                statusBadgeText = if (isExifSaveActive) "✓ Metadata EXIF GPS Tersimpan" else "EXIF Non-Aktif"
            )

            HorizontalDivider()

            // CONTOH WATERMARK PADA FOTO PREVIEW BOX
            Text(
                text = "CONTOH PRATINJAU WATERMARK FOTO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TelecomPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dark Photo Watermark Preview Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                    color = Color(0xFF212121),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(10.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Text("Lat -6.200123°", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Lon 106.816456°", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("2026-07-08 09:15", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text("WIB (GMT+7)", color = Color(0xFF81C784), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Info Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                    color = Color(0xFFE3F2FD),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TelecomPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Decimal Degrees (DD.dddddd°)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TelecomPrimary)
                        }
                        Text("• Lat: Lintang (- South, + North)", fontSize = 10.sp, color = Color.DarkGray)
                        Text("• Lon: Bujur (- West, + East)", fontSize = 10.sp, color = Color.DarkGray)
                        Text("• Format Waktu: 24 Jam (WIB)", fontSize = 10.sp, color = Color.DarkGray)
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
                    Text("1. Pastikan berada di area terbuka untuk sinyal GPS yang optimal.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100))
                    Text("2. Periksa hasil foto untuk memastikan watermark & EXIF lokasi tersimpan.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100))
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
