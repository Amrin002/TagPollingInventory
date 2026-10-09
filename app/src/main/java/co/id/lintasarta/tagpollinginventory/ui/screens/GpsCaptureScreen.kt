package co.id.lintasarta.tagpollinginventory.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GpsCaptureScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    val draftPole by viewModel.currentDraftPole.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val gpsAccuracy by viewModel.gpsAccuracy.collectAsState()
    val gpsIsStable by viewModel.gpsIsStable.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    var hasPermission by remember { mutableStateOf(viewModel.locationProvider.hasLocationPermission()) }
    var isTreeObstructed by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasPermission) {
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            viewModel.startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Pulse animation for GPS reticle
    val infiniteTransition = rememberInfiniteTransition(label = "gpsPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val (qualityText, qualityColor, qualityBg) = when {
        gpsAccuracy <= 5.0f -> Triple("GOOD FIX", Color(0xFF2E7D32), Color(0xFFE8F5E9))
        gpsAccuracy <= 15.0f -> Triple("FAIR FIX", Color(0xFFEF6C00), Color(0xFFFFF3E0))
        else -> Triple("POOR FIX", Color(0xFFC62828), Color(0xFFFFEBEE))
    }

    val timestampStr = remember(currentLocation.timestamp) {
        SimpleDateFormat("dd MMM yyyy — HH:mm:ss", Locale.US).format(Date(currentLocation.timestamp))
    }

    val dynamicProvider = if (networkStatus.isOnline) {
        "GPS + Cell/WiFi Triangulation (${networkStatus.connectionType})"
    } else {
        "GPS Hardware Only (Offline Local)"
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "GPS Capture",
                subtitle = draftPole?.id ?: "Target Pole",
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFEF6C00), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Location Permission Required", fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Application needs GPS location permission to capture accurate pole coordinates.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00))
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Target Pole Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = TelecomPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("TARGET POLE ID", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(draftPole?.id ?: "P-019-019", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = TelecomPrimary)
                        }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = qualityBg) {
                        Text(qualityText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = qualityColor)
                    }
                }
            }

            // Tree Canopy Obstruction Field Guidance Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Forest,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Petunjuk Lapangan: Terhalang Pohon / Kanopi Rimbun",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    Text(
                        text = "Jika tiang berada di semak/terhalang pohon rimbun, Anda dapat berdiri di area terbuka terdekat (1–3 meter). Koordinat GPS dan batas toleransi akurasi tetap tersimpan aman.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1B5E20)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isTreeObstructed,
                            onCheckedChange = { isTreeObstructed = it }
                        )
                        Text(
                            text = "Tandai: Lokasi Tiang Terhalang Pohon / Semak",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // Animated Visual GPS Reticle / Target Radar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.White, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(if (gpsIsStable) pulseScale else 1.0f)
                        .background(qualityBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(qualityColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = qualityColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // Real-time Coordinate & Telemetry Display Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("LIVE GPS COORDINATES & TELEMETRY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TelecomPrimary)

                    // Sampling Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sampling Progress", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("${currentLocation.sampleCount} / ${currentLocation.targetSamples} Samples", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TelecomPrimary)
                        }
                        LinearProgressIndicator(
                            progress = { currentLocation.sampleCount.toFloat() / currentLocation.targetSamples.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = qualityColor,
                            trackColor = qualityBg
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFEEEEEE))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Latitude (Centroid)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(String.format(Locale.US, "%.6f°", currentLocation.latitude), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Longitude (Centroid)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(String.format(Locale.US, "%.6f°", currentLocation.longitude), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Accuracy (p68)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(String.format(Locale.US, "±%.1f meters", gpsAccuracy), fontWeight = FontWeight.Bold, color = qualityColor)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sample Dispersion", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(String.format(Locale.US, "±%.2f meters", currentLocation.sampleDispersionMeters), fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFEEEEEE))

                    // GnssStatus Telemetry Section
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Satellites (Used / Visible)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("${currentLocation.satellitesUsed} / ${currentLocation.satellitesVisible} Satellites", fontWeight = FontWeight.Bold, color = TelecomPrimary)
                    }

                    if (currentLocation.constellations.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Constellations", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(currentLocation.constellations.joinToString(", "), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                        }
                    }

                    if (currentLocation.avgCn0DbHz > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Signal Strength (C/N0)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(String.format(Locale.US, "%.1f dBHz", currentLocation.avgCn0DbHz), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Provider", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(dynamicProvider, fontWeight = FontWeight.Medium, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fix Timestamp", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(timestampStr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Button(
                onClick = {
                    if (isTreeObstructed) {
                        val currentDraft = viewModel.currentDraftPole.value
                        if (currentDraft != null) {
                            val updatedNotes = if (currentDraft.notes.isEmpty()) {
                                "Lokasi tiang terhalang pohon / kanopi rimbun (Toleransi akurasi ±${String.format(Locale.US, "%.1f", gpsAccuracy)}m)"
                            } else {
                                "${currentDraft.notes} [Terhalang Pohon/Kanopi]"
                            }
                            viewModel.repository.updateDraftAttributes(
                                type = currentDraft.type,
                                condition = currentDraft.condition,
                                ownership = currentDraft.ownership,
                                height = currentDraft.height,
                                tagNumber = currentDraft.tagNumber,
                                hasFoCable = currentDraft.hasFoCable,
                                cableCondition = currentDraft.cableCondition,
                                equipment = currentDraft.equipment,
                                notes = updatedNotes
                            )
                        }
                    }
                    viewModel.captureCoordinatesAndContinue()
                    onContinueClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Capture GPS Location", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }

            OutlinedButton(
                onClick = { viewModel.retryGpsCapture() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TelecomPrimary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh / Retry GPS Fix", fontWeight = FontWeight.Bold)
            }
        }
    }
}
