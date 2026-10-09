package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.id.lintasarta.tagpollinginventory.data.model.TagStatus
import co.id.lintasarta.tagpollinginventory.ui.components.TagStatusBadge
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@Composable
fun PoleInventoryListScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onPoleClick: (String) -> Unit
) {
    val polesMap by viewModel.poles.collectAsState()
    val searchQuery by viewModel.poleSearchQuery.collectAsState()
    val statusFilter by viewModel.poleStatusFilter.collectAsState()

    val allPoles = remember(polesMap) { polesMap.values.toList().sortedBy { it.id } }

    val filteredPoles = remember(allPoles, searchQuery, statusFilter) {
        allPoles.filter { pole ->
            val matchesSearch = pole.poleCode.contains(searchQuery, ignoreCase = true) ||
                    pole.id.contains(searchQuery, ignoreCase = true) ||
                    pole.tagNumber.contains(searchQuery, ignoreCase = true) ||
                    pole.segmentId.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (statusFilter) {
                "COMPLETED" -> pole.status == TagStatus.COMPLETED
                "CONFLICT" -> pole.status == TagStatus.CONFLICT
                "NOT_TAGGED" -> pole.status == TagStatus.NOT_TAGGED
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    val networkStatus by viewModel.networkStatus.collectAsState()
    val project by viewModel.project.collectAsState()

    val total = allPoles.size
    val completed = allPoles.count { it.status == TagStatus.COMPLETED }
    val conflict = allPoles.count { it.status == TagStatus.CONFLICT }
    val localRecords = completed + conflict

    Scaffold(
        topBar = {
            TopBar(
                title = "Local Inventory List",
                subtitle = "${project.name} (${allPoles.size} Poles Total)",
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
            // Stats Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("$total", fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Completed", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                    Text("$completed", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Conflict", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                    Text("$conflict", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Local Records", style = MaterialTheme.typography.labelSmall, color = TelecomPrimary)
                    Text("$localRecords", fontWeight = FontWeight.Bold, color = TelecomPrimary)
                }
            }

            HorizontalDivider()

            // Search & Filter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setPoleSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by Pole ID or Tag Number...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        "ALL" to "All",
                        "COMPLETED" to "Completed",
                        "CONFLICT" to "Conflict",
                        "NOT_TAGGED" to "Not Tagged"
                    )

                    filterOptions.forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { viewModel.setPoleStatusFilter(key) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelecomPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Pole Cards List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredPoles) { pole ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPoleClick(pole.id) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = pole.poleCode.ifEmpty { pole.id },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TelecomPrimary
                                )
                                Text(
                                    text = "${pole.segmentId} • ${pole.type.displayName} (${pole.height})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                if (pole.capturedTimestamp.isNotEmpty()) {
                                    Text(
                                        text = "Saved: ${pole.capturedTimestamp}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.DarkGray
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
