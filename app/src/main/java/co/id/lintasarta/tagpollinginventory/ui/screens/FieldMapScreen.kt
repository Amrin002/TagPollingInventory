package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.model.DeviationStatus
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.ui.components.NetworkStatusDot
import co.id.lintasarta.tagpollinginventory.ui.components.OsmMapView
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.components.calculateRealDistanceMeters
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun FieldMapScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onQuickTagClick: (String) -> Unit = { poleId -> viewModel.startQuickTaggingForTargetPole(poleId) },
    onTagThisPoleClick: () -> Unit
) {
    val project by viewModel.project.collectAsState()
    val segmentId by viewModel.selectedSegmentId.collectAsState()
    val polesMap by viewModel.poles.collectAsState()
    val targetPoleId by viewModel.targetPoleId.collectAsState()
    val realLocation by viewModel.currentLocation.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val routeDeviation by viewModel.routeDeviation.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startLocationUpdates()
    }

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

    LaunchedEffect(activeSegment.id) {
        if (activeRoute == null && activeSegment.id.isNotEmpty()) {
            viewModel.selectSegment(activeSegment.id, navigateToDetail = false)
        }
    }

    val segmentCoordinates = remember(activeSegment, activeRoute) {
        if (activeRoute != null && activeRoute!!.coordinates.isNotEmpty()) {
            activeRoute!!.coordinates
        } else if (activeSegment.routeCoordinates.isNotEmpty()) {
            activeSegment.routeCoordinates
        } else {
            emptyList()
        }
    }

    val segmentPoles = remember(polesMap, segmentId) {
        polesMap.values.filter { it.segmentId == activeSegment.id }.sortedBy { it.sequence }
    }

    val targetPole = remember(segmentPoles, targetPoleId) {
        segmentPoles.find { it.id == targetPoleId } ?: segmentPoles.firstOrNull()
    }

    // Real distance calculation using Haversine formula on hardware GPS coordinates!
    val realDistanceMeters = remember(realLocation, targetPole) {
        if (targetPole != null) {
            calculateRealDistanceMeters(
                userLat = realLocation.latitude,
                userLng = realLocation.longitude,
                targetLat = targetPole.latitude,
                targetLng = targetPole.longitude
            )
        } else 12.0f
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "Peta Lapangan",
                subtitle = "${activeSegment.name} • ${activeSegment.route}",
                onBackClick = onBackClick,
                networkStatus = networkStatus
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Real OpenStreetMap View
            OsmMapView(
                poles = segmentPoles,
                selectedPoleId = targetPoleId,
                userLocation = realLocation,
                importedRoute = activeRoute,
                segmentCoordinates = segmentCoordinates,
                segmentName = activeSegment.name,
                onPoleClick = { pole -> viewModel.startTaggingPole(pole.id) }
            )

            // Top Status Overlay HUD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GPS Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = if (networkStatus.isOnline) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (networkStatus.isOnline) "GPS + Seluler/WiFi (${networkStatus.connectionType})" else "GPS: ${realLocation.providerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (networkStatus.isOnline) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                )
                                Text(
                                    text = "Akurasi ±${String.format("%.1f", realLocation.accuracy)} m",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        NetworkStatusDot(networkStatus = networkStatus, size = 11.dp)
                    }
                }

                // Route Deviation HUD Card (for active FO route)
                val hasRoute = activeRoute != null || segmentCoordinates.isNotEmpty()
                if (hasRoute) {
                    val isSimulatedOrFar = routeDeviation.distanceMeters > 50000f || realLocation.latitude !in -11.5..6.5
                    val devColor = if (isSimulatedOrFar) {
                        Color(0xFF2E7D32)
                    } else {
                        when (routeDeviation.status) {
                            DeviationStatus.NORMAL -> Color(0xFF2E7D32)
                            DeviationStatus.WARNING -> Color(0xFFEF6C00)
                            DeviationStatus.CHECK_ROUTE -> Color(0xFFC62828)
                        }
                    }
                    val devBg = if (isSimulatedOrFar) {
                        Color(0xFFE8F5E9)
                    } else {
                        when (routeDeviation.status) {
                            DeviationStatus.NORMAL -> Color(0xFFE8F5E9)
                            DeviationStatus.WARNING -> Color(0xFFFFF3E0)
                            DeviationStatus.CHECK_ROUTE -> Color(0xFFFFEBEE)
                        }
                    }
                    val devIcon = if (isSimulatedOrFar) Icons.Default.CheckCircle else Icons.Default.Navigation
                    val devTitle = "Rute Fiber Optic: ${activeRoute?.name ?: activeSegment.name}"
                    val devSubtitle = if (isSimulatedOrFar) {
                        "Peta difokuskan ke rute segmen Area 18 MPO"
                    } else {
                        "Deviasi: ${String.format("%.1f", routeDeviation.distanceMeters)} m dari rute"
                    }
                    val devBadge = if (isSimulatedOrFar) "SIAP SURVEI" else routeDeviation.status.displayName

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = devIcon,
                                    contentDescription = null,
                                    tint = devColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = devTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = devSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = devBg
                            ) {
                                Text(
                                    text = devBadge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = devColor
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Target Pole HUD & Action Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val displayTargetCode = remember(targetPole) {
                        if (targetPole == null) "Tidak Ada Tiang Dipilih"
                        else targetPole.poleCode.ifEmpty { "Tiang #${targetPole.sequence}" }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = TelecomPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "TIANG TARGET",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(
                                    text = displayTargetCode,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TelecomPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFFF3E0)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PinDrop,
                                    contentDescription = null,
                                    tint = Color(0xFFEF6C00),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Jarak: ${String.format(Locale.US, "%.0f", realDistanceMeters)} m",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF6C00)
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF1F8E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Pilih mode survei. Gunakan Quick Tagging (< 1 Menit) untuk alur cepat 2-tahap.",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF2E7D32),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Dual Action Survey Buttons: Quick Tagging (Diutamakan) & Tag Polling PM
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. UTAMA (PRIORITAS): Quick Tagging (< 1 Menit)
                        Button(
                            onClick = { onQuickTagClick(targetPole?.id ?: "") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)), // Teal Signature Quick Tagging
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Quick Tagging (< 1 Menit)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // 2. SEKUNDER: Tag Polling PM (Form Lengkap)
                        OutlinedButton(
                            onClick = onTagThisPoleClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, TelecomPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TelecomPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tag Polling PM (Form Lengkap)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
