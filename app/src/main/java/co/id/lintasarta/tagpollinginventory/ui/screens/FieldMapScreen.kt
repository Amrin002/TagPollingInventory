package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
                title = "Field Map",
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
                                    text = if (networkStatus.isOnline) "GPS + Cell/WiFi (${networkStatus.connectionType})" else "GPS: ${realLocation.providerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (networkStatus.isOnline) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                )
                                Text(
                                    text = "Accuracy ±${String.format("%.1f", realLocation.accuracy)} m",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (networkStatus.isOnline) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
                        ) {
                            Text(
                                text = if (networkStatus.isOnline) "ONLINE MAP" else "OFFLINE MAP",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (networkStatus.isOnline) Color(0xFF2E7D32) else TelecomPrimary
                            )
                        }
                    }
                }

                // Route Deviation HUD Card (if reference route exists)
                if (activeRoute != null) {
                    val (devColor, devBg) = when (routeDeviation.status) {
                        DeviationStatus.NORMAL -> Pair(Color(0xFF2E7D32), Color(0xFFE8F5E9))
                        DeviationStatus.WARNING -> Pair(Color(0xFFEF6C00), Color(0xFFFFF3E0))
                        DeviationStatus.CHECK_ROUTE -> Pair(Color(0xFFC62828), Color(0xFFFFEBEE))
                    }

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = devColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "FO Route: ${activeRoute?.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Deviasi: ${String.format("%.1f", routeDeviation.distanceMeters)} m dari rute",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = devBg
                            ) {
                                Text(
                                    text = routeDeviation.status.displayName,
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
                        if (targetPole == null) "No Pole Selected"
                        else targetPole.poleCode.ifEmpty { "Pole #${targetPole.sequence}" }
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
                                    text = "TARGET POLE",
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
                                    text = "Distance: ${String.format(Locale.US, "%.0f", realDistanceMeters)} m",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF6C00)
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "“Stand directly under the pole to capture accurate coordinates.”",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF455A64),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onTagThisPoleClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tag This Pole",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
