package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TagPollingInventoryTheme
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.ScreenFlow

@Composable
fun CreateSegmentScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val networkStatus by viewModel.networkStatus.collectAsState()

    CreateSegmentScreenContent(
        networkStatus = networkStatus,
        onBackClick = onBackClick,
        onImportRouteClick = { viewModel.navigateTo(ScreenFlow.IMPORT_ROUTE) },
        onCreateSegment = { segmentName, description, cityCode, locationCode, seqInt ->
            viewModel.createSegment(segmentName, description, cityCode, locationCode, seqInt)
        }
    )
}

@Composable
fun CreateSegmentScreenContent(
    networkStatus: NetworkStatus,
    onBackClick: () -> Unit,
    onImportRouteClick: () -> Unit,
    onCreateSegment: (String, String, String, String, Int) -> Unit
) {
    var segmentName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cityCode by remember { mutableStateOf("") }
    var locationCode by remember { mutableStateOf("") }
    var startingSequence by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopBar(
                title = "Create New Segment",
                subtitle = "Start a new FO Route Segment",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Segment Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = segmentName,
                        onValueChange = { segmentName = it },
                        label = { Text("Segment Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Pole Naming Configuration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cityCode,
                            onValueChange = { cityCode = it.take(3).uppercase() },
                            label = { Text("City Code (3 chars)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = locationCode,
                            onValueChange = { locationCode = it.take(3).uppercase() },
                            label = { Text("Loc Code (3 chars)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = startingSequence,
                        onValueChange = { if (it.all { char -> char.isDigit() }) startingSequence = it },
                        label = { Text("Starting Sequence (e.g. 0)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Reference Route (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You can import a KML/KMZ reference route later, or do it now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onImportRouteClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Import KML / KMZ Route")
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (segmentName.isNotBlank()) {
                        val seqInt = startingSequence.toIntOrNull() ?: 0
                        onCreateSegment(segmentName, description, cityCode, locationCode, seqInt)
                    }
                },
                enabled = segmentName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Save Segment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreateSegmentScreenPreview() {
    TagPollingInventoryTheme {
        CreateSegmentScreenContent(
            networkStatus = NetworkStatus(isOnline = true, connectionType = "WiFi"),
            onBackClick = {},
            onImportRouteClick = {},
            onCreateSegment = { _, _, _, _, _ -> }
        )
    }
}
