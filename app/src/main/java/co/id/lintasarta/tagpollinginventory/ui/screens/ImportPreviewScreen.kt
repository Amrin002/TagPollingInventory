package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@Composable
fun ImportPreviewScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onConfirmImportClick: () -> Unit
) {
    val previewData by viewModel.importPreviewData.collectAsState()
    val project by viewModel.project.collectAsState()
    val segmentId by viewModel.selectedSegmentId.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    val activeSegment = remember(project, segmentId) {
        project.segments.find { it.id == segmentId } ?: project.segments.first()
    }

    var customProjectName by remember(previewData) {
        mutableStateOf(if (!previewData?.detectedProject.isNullOrEmpty()) previewData!!.detectedProject else project.name)
    }

    var customSegmentName by remember(previewData) {
        mutableStateOf(if (!previewData?.detectedSegment.isNullOrEmpty()) previewData!!.detectedSegment else activeSegment.name)
    }

    val isValidRoute = (previewData?.coordinateCount ?: 0) > 0 || (previewData?.referencePoleCount ?: 0) > 0

    Scaffold(
        topBar = {
            TopBar(
                title = "Import Route Preview",
                subtitle = previewData?.fileName ?: "File Preview",
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
            if (isValidRoute) {
                // Route Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ROUTE METADATA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("File Name", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(previewData?.fileName ?: "", fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Format", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(previewData?.fileType ?: "KML", fontWeight = FontWeight.Bold, color = TelecomPrimary)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Route Name", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(previewData?.routeName ?: "", fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("LineString Points", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text("${previewData?.coordinateCount ?: 0} coordinates", fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Reference Poles", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text("${previewData?.referencePoleCount ?: 0} poles", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Project & Segment Assignment Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ASSIGN TO PROJECT / SEGMENT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )

                        OutlinedTextField(
                            value = customProjectName,
                            onValueChange = { customProjectName = it },
                            label = { Text("Project Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = customSegmentName,
                            onValueChange = { customSegmentName = it },
                            label = { Text("Segment Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearImportPreview()
                            onBackClick()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            viewModel.confirmRouteImport(customProjectName, customSegmentName)
                            onConfirmImportClick()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm Import", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Invalid File Error Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Unable to import route",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The selected KML/KMZ does not contain supported LineString or Point geometry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF37474F)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Try Another File", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
