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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.ui.components.SegmentStatusBadge
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.ScreenFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentListScreen(
    viewModel: MainViewModel,
    onSegmentClick: (String) -> Unit
) {
    val project by viewModel.project.collectAsState()
    val filter by viewModel.segmentFilter.collectAsState()
    val searchQuery by viewModel.segmentSearchQuery.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    var selectedSegmentIds by remember { mutableStateOf(setOf<String>()) }
    val isMultiSelectionMode = selectedSegmentIds.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val filteredSegments = remember(project.segments, filter, searchQuery) {
        project.segments.filter { seg ->
            val matchesFilter = when (filter) {
                "IN_PROGRESS" -> seg.status == SegmentStatus.IN_PROGRESS
                "COMPLETED" -> seg.status == SegmentStatus.COMPLETED
                "CONFLICT" -> seg.conflictPoles > 0
                else -> true
            }
            val matchesSearch = seg.name.contains(searchQuery, ignoreCase = true) ||
                    seg.route.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isMultiSelectionMode) {
            // Contextual Action Bar for Multi-Select
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF37474F),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedSegmentIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedSegmentIds.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color(0xFFEF5350))
                    }
                }
            }
        } else {
            // Compact Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = TelecomPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Segments (${project.segments.size} Total)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (networkStatus.isOnline) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                    ) {
                        Text(
                            text = if (networkStatus.isOnline) "ONLINE" else "OFFLINE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSegmentSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search segments...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filterOptions = listOf(
                "ALL" to "All",
                "IN_PROGRESS" to "In Progress",
                "COMPLETED" to "Completed",
                "CONFLICT" to "Conflict"
            )

            filterOptions.forEach { (key, label) ->
                FilterChip(
                    selected = filter == key,
                    onClick = { viewModel.setSegmentFilter(key) },
                    label = { Text(label, fontWeight = if (filter == key) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelecomPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Segment List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredSegments, key = { it.id }) { segment ->
                    val isSelected = selectedSegmentIds.contains(segment.id)
                    SegmentCard(
                        segment = segment,
                        isSelected = isSelected,
                        isMultiSelectionMode = isMultiSelectionMode,
                        onLongClick = {
                            if (!isMultiSelectionMode) {
                                selectedSegmentIds = setOf(segment.id)
                            }
                        },
                        onClick = {
                            if (isMultiSelectionMode) {
                                selectedSegmentIds = if (isSelected) {
                                    selectedSegmentIds - segment.id
                                } else {
                                    selectedSegmentIds + segment.id
                                }
                            } else {
                                onSegmentClick(segment.id)
                            }
                        }
                    )
                }
            }

            FloatingActionButton(
                onClick = { viewModel.navigateTo(ScreenFlow.CREATE_SEGMENT) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp),
                containerColor = TelecomPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Segment")
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false 
                if (selectedSegmentIds.size == 1) selectedSegmentIds = emptySet()
            },
            title = { Text("Delete Segments", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${selectedSegmentIds.size} segment(s)? All poles and photos tied to these segments will also be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        Log.d("DeleteTrace", "[UI] User clicked delete confirm for ${selectedSegmentIds.size} segments: $selectedSegmentIds")
                        viewModel.deleteSelectedSegments(selectedSegmentIds.toList())
                        selectedSegmentIds = emptySet()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmDialog = false 
                    if (selectedSegmentIds.size == 1) selectedSegmentIds = emptySet()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SegmentCard(
    segment: Segment,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isMultiSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) TelecomPrimary else Color.LightGray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = segment.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )
                        Text(
                            text = segment.route.ifEmpty { "Manual Segment" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    SegmentStatusBadge(status = segment.status)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar & Counts
                val progressFraction = if (segment.totalPoles > 0) segment.completedPoles.toFloat() / segment.totalPoles else 0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${segment.completedPoles} / ${segment.totalPoles} completed",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (segment.conflictPoles > 0) {
                        Text(
                            text = "⚠ ${segment.conflictPoles} Conflict",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color(0xFF2E7D32),
                    trackColor = Color(0xFFE0E0E0),
                )
            }
        }
    }
}
