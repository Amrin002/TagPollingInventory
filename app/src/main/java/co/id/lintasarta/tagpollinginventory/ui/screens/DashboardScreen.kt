package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
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
import co.id.lintasarta.tagpollinginventory.ui.components.NetworkStatusDot
import co.id.lintasarta.tagpollinginventory.ui.components.OfflineStatusCard
import co.id.lintasarta.tagpollinginventory.ui.components.StatCounterBox
import co.id.lintasarta.tagpollinginventory.ui.theme.*
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onContinueFieldWork: () -> Unit,
    onViewProject: () -> Unit,
    onCreateSegmentClick: () -> Unit,
    onQuickTaggingClick: () -> Unit = { viewModel.startQuickTaggingFlow() }
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
                            text = "Tag Polling Inventory",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Survei Lapangan Fiber Optic",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NetworkStatusDot(networkStatus = networkStatus, size = 12.dp)
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = "Telekomunikasi",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(16.dp))

                // Current Project Box
                Text(
                    text = "PROYEK SAAT INI",
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
                        text = "Progres",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${project.completedPoles} / ${project.totalPoles} Tiang",
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

        // Statistics Grid
        Text(
            text = "STATISTIK LAPANGAN",
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
                title = "DATA LOKAL",
                count = project.completedPoles + project.conflictPoles,
                color = TelecomPrimary,
                bgColor = TelecomPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Tagging Button (Entry Point Terpisah)
        Button(
            onClick = onQuickTaggingClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Quick Tagging (Tagging Cepat)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Main Action Buttons (Branching: New Field Work -> Create Segment vs Continue Field Work)
        Button(
            onClick = {
                if (hasWorkData) {
                    onContinueFieldWork()
                } else {
                    onCreateSegmentClick()
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
                text = if (hasWorkData) "Tag Polling PM (Survei Lengkap)" else "Pekerjaan Lapangan Baru (PM)",
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
                text = "Lihat Proyek",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
