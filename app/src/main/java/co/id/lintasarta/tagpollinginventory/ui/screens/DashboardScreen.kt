package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.ui.components.OfflineStatusCard
import co.id.lintasarta.tagpollinginventory.ui.components.StatCounterBox
import co.id.lintasarta.tagpollinginventory.ui.theme.*
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onContinueFieldWork: () -> Unit,
    onViewProject: () -> Unit,
    onImportRouteClick: () -> Unit
) {
    val project by viewModel.project.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val polesMap by viewModel.poles.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    val hasWorkData = activeRoute != null || polesMap.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title & Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = TelecomPrimary,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tag Poling Inventory",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Fiber Optic Field Survey",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.CellTower,
                        contentDescription = "Telecom",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(16.dp))

                // Current Project Box
                Text(
                    text = "CURRENT PROJECT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = project.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar
                val progressFraction = if (project.totalPoles > 0) project.completedPoles.toFloat() / project.totalPoles else 0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${project.completedPoles} / ${project.totalPoles} Poles",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = Color(0xFF4CAF50),
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }

        // Import FO Route Action Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onImportRouteClick),
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
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = Color(0xFFEF6C00),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeRoute != null) "Active Route: ${activeRoute?.name}" else "Import FO Route (KML/KMZ)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (activeRoute != null) TelecomPrimary else Color.Black
                    )
                    Text(
                        text = if (activeRoute != null) "${activeRoute?.coordinates?.size ?: 0} route points • ${activeRoute?.sourceFileName}" else "Import reference KML/KMZ route file from engineer",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }

        // Dynamic Online / Offline Status Banner
        OfflineStatusCard(networkStatus = networkStatus)

        // Statistics Grid
        Text(
            text = "FIELD STATISTICS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF546E7A)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCounterBox(
                title = "BELUM",
                count = project.uncompletedPoles,
                color = StatusNotTagged,
                bgColor = StatusNotTaggedContainer,
                modifier = Modifier.weight(1f)
            )
            StatCounterBox(
                title = "SELESAI",
                count = project.completedPoles,
                color = StatusCompleted,
                bgColor = StatusCompletedContainer,
                modifier = Modifier.weight(1f)
            )
            StatCounterBox(
                title = "KONFLIK",
                count = project.conflictPoles,
                color = StatusConflict,
                bgColor = StatusConflictContainer,
                modifier = Modifier.weight(1f)
            )
            StatCounterBox(
                title = "LOCAL DATA",
                count = project.completedPoles + project.conflictPoles,
                color = TelecomPrimary,
                bgColor = TelecomPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Action Buttons (Branching: New Field Work vs Continue Field Work)
        Button(
            onClick = {
                if (hasWorkData) {
                    onContinueFieldWork()
                } else {
                    onImportRouteClick()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = if (hasWorkData) Icons.Default.PlayArrow else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasWorkData) "Continue Field Work" else "New Field Work",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onViewProject,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TelecomPrimary)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ListAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "View Project",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
