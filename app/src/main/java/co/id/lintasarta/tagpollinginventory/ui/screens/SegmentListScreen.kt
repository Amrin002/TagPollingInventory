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
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.ui.components.SegmentStatusBadge
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@Composable
fun SegmentListScreen(
    viewModel: MainViewModel,
    onSegmentClick: (String) -> Unit
) {
    val project by viewModel.project.collectAsState()
    val filter by viewModel.segmentFilter.collectAsState()
    val searchQuery by viewModel.segmentSearchQuery.collectAsState()

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
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "OFFLINE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
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

        // Segment List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filteredSegments) { segment ->
                SegmentCard(
                    segment = segment,
                    onClick = { onSegmentClick(segment.id) }
                )
            }
        }
    }
}

@Composable
fun SegmentCard(
    segment: Segment,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                        text = segment.route,
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

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View Segment",
                    style = MaterialTheme.typography.labelMedium,
                    color = TelecomPrimary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TelecomPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
