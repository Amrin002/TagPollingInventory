package co.id.lintasarta.tagpollinginventory.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.id.lintasarta.tagpollinginventory.data.model.ExportFile
import co.id.lintasarta.tagpollinginventory.data.model.ExportFormat
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

@Composable
fun FileManagementScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val exportFiles by viewModel.exportFiles.collectAsState()

    Scaffold(
        topBar = {
            TopBar(
                title = "Export File History",
                subtitle = "${exportFiles.size} Saved Files",
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(NeutralBackground)
        ) {
            if (exportFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No export files generated yet.",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(exportFiles) { file ->
                        ExportFileCard(
                            file = file,
                            onShare = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_SUBJECT, file.fileName)
                                    putExtra(Intent.EXTRA_TEXT, "Export File: ${file.fileName}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share File"))
                            },
                            onDelete = { viewModel.deleteExportFile(file.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExportFileCard(
    file: ExportFile,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, iconBg, iconTint) = when (file.format) {
        ExportFormat.CSV -> Triple(Icons.Default.GridOn, Color(0xFFE8F5E9), Color(0xFF2E7D32))
        ExportFormat.KML -> Triple(Icons.Default.Public, Color(0xFFE3F2FD), TelecomPrimary)
        ExportFormat.KMZ -> Triple(Icons.Default.Archive, Color(0xFFFFF3E0), Color(0xFFEF6C00))
    }

    val sizeKb = file.sizeBytes / 1024.0
    val sizeText = if (sizeKb >= 1024) {
        String.format("%.1f MB", sizeKb / 1024.0)
    } else {
        String.format("%.0f KB", sizeKb)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconBg, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )
                    Text(
                        text = "${file.createdAt} • ${file.recordCount} records",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFECEFF1)
                ) {
                    Text(
                        text = sizeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TelecomPrimary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFC62828))
                }
            }
        }
    }
}
