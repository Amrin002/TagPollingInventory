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
import java.util.Locale

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

    var userEditedCityCode by remember { mutableStateOf(false) }
    var userEditedLocCode by remember { mutableStateOf(false) }

    val (autoCity, autoLoc) = remember(segmentName) { deriveCodesFromSegmentName(segmentName) }
    val effectiveCityCode = if (userEditedCityCode) cityCode else autoCity
    val effectiveLocCode = if (userEditedLocCode) locationCode else autoLoc
    val previewSeq = startingSequence.toIntOrNull() ?: 1
    val previewPoleCode = "PL-${effectiveCityCode.ifEmpty { "JPR" }}-${effectiveLocCode.ifEmpty { "CTR" }}-${String.format(
        Locale.US, "%03d", previewSeq)}"

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
                        text = "Pole Naming Configuration (Otomatis)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (userEditedCityCode) cityCode else autoCity,
                            onValueChange = {
                                cityCode = it.take(3).uppercase()
                                userEditedCityCode = true
                            },
                            label = { Text("City Code (Auto/3 chars)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = if (userEditedLocCode) locationCode else autoLoc,
                            onValueChange = {
                                locationCode = it.take(3).uppercase()
                                userEditedLocCode = true
                            },
                            label = { Text("Loc Code (Auto/3 chars)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = startingSequence,
                        onValueChange = { if (it.all { char -> char.isDigit() }) startingSequence = it },
                        label = { Text("Starting Sequence (e.g. 1)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Live Architecture-Compliant Pole Code Preview
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE3F2FD),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Otomatis Format Kode Tiang (Architecture Sec. 9):",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelecomPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = previewPoleCode,
                                style = MaterialTheme.typography.titleMedium,
                                color = TelecomPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
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
                        val seqInt = startingSequence.toIntOrNull() ?: 1
                        val finalCity = if (userEditedCityCode) cityCode else autoCity
                        val finalLoc = if (userEditedLocCode) locationCode else autoLoc
                        onCreateSegment(segmentName, description, finalCity, finalLoc, seqInt)
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

fun deriveCodesFromSegmentName(name: String): Pair<String, String> {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return Pair("JPR", "CTR")
    if (words.size == 1) {
        val w = words[0].uppercase().filter { it.isLetterOrDigit() }
        val city = if (w.length >= 3) w.take(3) else w.padEnd(3, 'X')
        return Pair(city, "CTR")
    }
    val w1 = words[0].uppercase().filter { it.isLetterOrDigit() }
    val w2 = words[1].uppercase().filter { it.isLetterOrDigit() }
    val city = if (w1.length >= 3) w1.take(3) else w1.padEnd(3, 'X')
    val loc = if (w2.length >= 3) w2.take(3) else w2.padEnd(3, 'X')
    return Pair(city, loc)
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

