package co.id.lintasarta.tagpollinginventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.theme.NeutralBackground
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.io.File
import java.util.Locale

@Composable
fun ReviewPoleScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val draftPole by viewModel.currentDraftPole.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    val displayPoleCode = draftPole?.poleCode?.ifEmpty { draftPole?.id } ?: "Target Pole"

    Scaffold(
        topBar = {
            TopBar(
                title = "Tinjau Data Tiang",
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
            // 1. LOCATION SECTION (LOCKED & FIXED)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LOKASI TERKUNCI",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TelecomPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Koordinat Berhasil Diambil & Dikunci",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Lintang", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(
                                text = String.format(Locale.US, "%.6f°", draftPole?.latitude ?: 0.0),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Column {
                            Text("Bujur", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(
                                text = String.format(Locale.US, "%.6f°", draftPole?.longitude ?: 0.0),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Column {
                            Text("Akurasi GPS", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(
                                text = String.format(Locale.US, "±%.1fm", draftPole?.accuracy ?: 2.8f),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    Text(
                        text = "Waktu Pengambilan: ${draftPole?.capturedTimestamp?.ifEmpty { "Baru saja" } ?: "Baru saja"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray
                    )
                }
            }

            // 2. POLE ATTRIBUTES SECTION
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
                        text = "DETAIL TIANG",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Jenis", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(draftPole?.type?.displayName ?: "Concrete", fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Kondisi", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(draftPole?.condition?.displayName ?: "Good", fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Kepemilikan", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(draftPole?.ownership?.displayName ?: "Lintasarta", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Tinggi", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(draftPole?.height ?: "9m", fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Nomor Tag", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(draftPole?.tagNumber ?: "TAG-LTA-019", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. FO CABLE & EQUIPMENT SECTION
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
                        text = "KABEL FO & PERALATAN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TelecomPrimary
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Kabel Fiber Optic Tersedia", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(if (draftPole?.hasFoCable != false) "Ya" else "Tidak", fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Kondisi Kabel", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(draftPole?.cableCondition?.displayName ?: "Good", fontWeight = FontWeight.Bold)
                        }
                    }

                    Column {
                        Text("Peralatan Terpasang", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(
                            text = draftPole?.equipment?.joinToString(", ") ?: "ODP, Closure",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!draftPole?.notes.isNullOrEmpty()) {
                        Column {
                            Text("Catatan", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(draftPole?.notes ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 4. PHOTO ATTACHMENT SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val photoCount = draftPole?.photoPaths?.size ?: 0
                            Text(
                                text = "✓ $photoCount Foto Terlampir",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    val paths = draftPole?.photoPaths ?: emptyList()
                    if (paths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(paths) { path ->
                                val file = File(path)
                                if (file.exists()) {
                                    AsyncImage(
                                        model = file,
                                        contentDescription = "Pratinjau Foto Tiang",
                                        modifier = Modifier
                                            .width(200.dp)
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // SAVE POLE BUTTON
            Button(
                onClick = {
                    viewModel.savePoleLocally()
                    onSaveSuccess()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SIMPAN TIANG SECARA LOKAL",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
