package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.id.lintasarta.tagpollinginventory.ui.components.FieldMapCanvas
import co.id.lintasarta.tagpollinginventory.ui.components.TagStatusBadge
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@Composable
fun SegmentDetailScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onStartTagging: (String) -> Unit
) {
    val project by viewModel.project.collectAsState()
    val segmentId by viewModel.selectedSegmentId.collectAsState()
    val polesMap by viewModel.poles.collectAsState()
    val targetPoleId by viewModel.targetPoleId.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    val activeSegment = remember(project, segmentId) {
        project.segments.find { it.id == segmentId } ?: project.segments.first()
    }

    val segmentPoles = remember(polesMap, segmentId) {
        polesMap.values.filter { it.segmentId == activeSegment.id }.sortedBy { it.sequence }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = activeSegment.name,
                subtitle = activeSegment.route,
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
        ) {
            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("${activeSegment.totalPoles}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Completed", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                    Text("${activeSegment.completedPoles}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2E7D32))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Remaining", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("${activeSegment.totalPoles - activeSegment.completedPoles}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Conflict", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                    Text("${activeSegment.conflictPoles}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFC62828))
                }
            }

            HorizontalDivider()

            // Map Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                FieldMapCanvas(
                    poles = segmentPoles,
                    selectedPoleId = targetPoleId,
                    onPoleClick = { pole -> viewModel.startTaggingPole(pole.id) }
                )
            }

            // Primary Start Tagging Action Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (targetPoleId.isNotEmpty()) "Target Pole: $targetPoleId" else "Belum Ada Tiang",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )
                        Text(
                            text = if (targetPoleId.isNotEmpty()) "Stand under pole to capture GPS" else "Tambah tiang baru untuk survey rute ini",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Button(
                        onClick = {
                            if (targetPoleId.isNotEmpty()) {
                                onStartTagging(targetPoleId)
                            } else {
                                viewModel.createAndStartNewPole(activeSegment.id)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (targetPoleId.isNotEmpty()) Icons.Default.PlayArrow else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (targetPoleId.isNotEmpty()) "Start Tagging" else "Tambah Tiang",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider()

            // Pole Inventory List for Segment
            if (segmentPoles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Belum Ada Data Tiang",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Klik tombol 'Tambah Tiang' di atas untuk mulai melakukan survey dan tagging tiang baru pada segmen ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "POLES IN THIS SEGMENT",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF546E7A)
                            )

                            TextButton(onClick = { viewModel.createAndStartNewPole(activeSegment.id) }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah Tiang", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(segmentPoles) { pole ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.startTaggingPole(pole.id) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (pole.id == targetPoleId) TelecomPrimary else Color(0xFFECEFF1)
                                    ) {
                                        Text(
                                            text = "#${pole.sequence}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (pole.id == targetPoleId) Color.White else Color.DarkGray
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = pole.id,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${pole.type.displayName} • ${pole.height}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TagStatusBadge(status = pole.status)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
