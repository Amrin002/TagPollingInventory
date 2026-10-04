package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.data.model.*
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoleInformationScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    val draftPole by viewModel.currentDraftPole.collectAsState()

    var poleType by remember(draftPole) { mutableStateOf(draftPole?.type ?: PoleType.CONCRETE) }
    var poleCondition by remember(draftPole) { mutableStateOf(draftPole?.condition ?: PoleCondition.GOOD) }
    var ownership by remember(draftPole) { mutableStateOf(draftPole?.ownership ?: PoleOwnership.LINTASARTA) }
    var height by remember(draftPole) { mutableStateOf(draftPole?.height ?: "9m") }
    var tagNumber by remember(draftPole) { mutableStateOf(draftPole?.tagNumber ?: "TAG-LTA-${draftPole?.id ?: "019"}") }
    var hasFoCable by remember(draftPole) { mutableStateOf(draftPole?.hasFoCable ?: true) }
    var cableCondition by remember(draftPole) { mutableStateOf(draftPole?.cableCondition ?: CableCondition.GOOD) }
    var selectedEquipment by remember(draftPole) { mutableStateOf(draftPole?.equipment ?: setOf("ODP", "Closure")) }
    var notes by remember(draftPole) { mutableStateOf(draftPole?.notes ?: "") }

    Scaffold(
        topBar = {
            TopBar(
                title = "Pole Information",
                subtitle = draftPole?.id ?: "P-019-019",
                onBackClick = onBackClick
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
            // Pole ID Header Card
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
                    Column {
                        Text(
                            text = "POLE IDENTIFIER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = draftPole?.id ?: "P-019-019",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = TelecomPrimary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE3F2FD)
                    ) {
                        Text(
                            text = "Seq #${draftPole?.sequence ?: 19}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )
                    }
                }
            }

            // 1. Pole Type
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Pole Type",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PoleType.values().forEach { type ->
                            FilterChip(
                                selected = poleType == type,
                                onClick = { poleType = type },
                                label = { Text(type.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelecomPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 2. Pole Condition
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Pole Condition",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PoleCondition.values().forEach { cond ->
                            val chipBg = when (cond) {
                                PoleCondition.GOOD -> Color(0xFF2E7D32)
                                PoleCondition.FAIR -> Color(0xFFEF6C00)
                                PoleCondition.DAMAGED -> Color(0xFFD84315)
                                PoleCondition.CRITICAL -> Color(0xFFC62828)
                            }
                            FilterChip(
                                selected = poleCondition == cond,
                                onClick = { poleCondition = cond },
                                label = { Text(cond.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipBg,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 3. Ownership
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Ownership",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PoleOwnership.values().forEach { owner ->
                            FilterChip(
                                selected = ownership == owner,
                                onClick = { ownership = owner },
                                label = { Text(owner.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelecomPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 4. Pole Height & Tag Number
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
                        text = "Pole Attributes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Pole Height") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = tagNumber,
                        onValueChange = { tagNumber = it },
                        label = { Text("Pole Tag Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // 5. FO Cable & Cable Condition
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Fiber Optic Cable Present",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasFoCable) "FO cable attached" else "No cable attached",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = hasFoCable,
                            onCheckedChange = { hasFoCable = it }
                        )
                    }

                    if (hasFoCable) {
                        Text(
                            text = "Cable Condition",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CableCondition.values().forEach { cableCond ->
                                FilterChip(
                                    selected = cableCondition == cableCond,
                                    onClick = { cableCondition = cableCond },
                                    label = { Text(cableCond.displayName, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TelecomPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 6. Additional Equipment Checkboxes
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Additional Equipment",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val equipmentList = listOf("ODP", "Closure", "Slack", "Grounding", "Other")

                    equipmentList.forEach { eq ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedEquipment.contains(eq),
                                onCheckedChange = { checked ->
                                    selectedEquipment = if (checked) {
                                        selectedEquipment + eq
                                    } else {
                                        selectedEquipment - eq
                                    }
                                }
                            )
                            Text(
                                text = eq,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // 7. Notes
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Field Notes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter observations, access conditions, or issues...") },
                        minLines = 3,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // Action Button
            Button(
                onClick = {
                    viewModel.savePoleInfoAndContinue(
                        poleType,
                        poleCondition,
                        ownership,
                        height,
                        tagNumber,
                        hasFoCable,
                        cableCondition,
                        selectedEquipment,
                        notes
                    )
                    onContinueClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Continue to Photo Capture",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
