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
    val networkStatus by viewModel.networkStatus.collectAsState()

    var poleType by remember(draftPole?.id) { mutableStateOf(draftPole?.type ?: PoleType.CONCRETE) }
    var poleCondition by remember(draftPole?.id) { mutableStateOf(draftPole?.condition ?: PoleCondition.GOOD) }
    var ownership by remember(draftPole?.id) { mutableStateOf(draftPole?.ownership ?: PoleOwnership.LINTASARTA) }
    var height by remember(draftPole?.id) { mutableStateOf(draftPole?.height ?: "9m") }
    
    val defaultTagNumber = remember(draftPole?.id) {
        val code = draftPole?.poleCode?.ifEmpty { draftPole?.id } ?: "001"
        "TAG-${code}"
    }
    var tagNumber by remember(draftPole?.id) { mutableStateOf(draftPole?.tagNumber?.ifEmpty { defaultTagNumber } ?: defaultTagNumber) }
    var hasFoCable by remember(draftPole?.id) { mutableStateOf(draftPole?.hasFoCable ?: true) }
    var cableCondition by remember(draftPole?.id) { mutableStateOf(draftPole?.cableCondition ?: CableCondition.GOOD) }
    var selectedEquipment by remember(draftPole?.id) { mutableStateOf(draftPole?.equipment ?: setOf("ODP", "Closure")) }
    var notes by remember(draftPole?.id) { mutableStateOf(draftPole?.notes ?: "") }

    fun syncToDraft(
        t: PoleType = poleType,
        c: PoleCondition = poleCondition,
        o: PoleOwnership = ownership,
        h: String = height,
        tn: String = tagNumber,
        fo: Boolean = hasFoCable,
        cc: CableCondition = cableCondition,
        eq: Set<String> = selectedEquipment,
        n: String = notes
    ) {
        viewModel.repository.updateDraftAttributes(t, c, o, h, tn, fo, cc, eq, n)
    }

    val displayPoleCode = draftPole?.poleCode?.ifEmpty { draftPole?.id } ?: "Target Pole"
    val poleStatus = draftPole?.status ?: TagStatus.NOT_TAGGED

    val (statusColor, statusBg) = when (poleStatus) {
        TagStatus.COMPLETED -> Pair(Color(0xFF2E7D32), Color(0xFFE8F5E9))
        TagStatus.CONFLICT -> Pair(Color(0xFFC62828), Color(0xFFFFEBEE))
        TagStatus.NOT_TAGGED -> Pair(TelecomPrimary, Color(0xFFE3F2FD))
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "Pole Information",
                subtitle = displayPoleCode,
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
                            text = displayPoleCode,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = TelecomPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusBg
                        ) {
                            Text(
                                text = poleStatus.displayName,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                        Text(
                            text = "Seq #${draftPole?.sequence ?: 1}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
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
                                onClick = {
                                    poleType = type
                                    syncToDraft(t = type)
                                },
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
                                onClick = {
                                    poleCondition = cond
                                    syncToDraft(c = cond)
                                },
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
                                onClick = {
                                    ownership = owner
                                    syncToDraft(o = owner)
                                },
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
                        onValueChange = {
                            height = it
                            syncToDraft(h = it)
                        },
                        label = { Text("Pole Height") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = tagNumber,
                        onValueChange = {
                            tagNumber = it
                            syncToDraft(tn = it)
                        },
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
                            onCheckedChange = {
                                hasFoCable = it
                                syncToDraft(fo = it)
                            }
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
                                    onClick = {
                                        cableCondition = cableCond
                                        syncToDraft(cc = cableCond)
                                    },
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
                                    val newEq = if (checked) {
                                        selectedEquipment + eq
                                    } else {
                                        selectedEquipment - eq
                                    }
                                    selectedEquipment = newEq
                                    syncToDraft(eq = newEq)
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
                        onValueChange = {
                            notes = it
                            syncToDraft(n = it)
                        },
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
