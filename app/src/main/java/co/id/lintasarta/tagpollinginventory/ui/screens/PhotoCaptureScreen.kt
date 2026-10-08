package co.id.lintasarta.tagpollinginventory.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.ui.tooling.preview.Preview
import co.id.lintasarta.tagpollinginventory.camera.CameraPreview
import co.id.lintasarta.tagpollinginventory.camera.takePhoto
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.location.LocationData
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.ui.components.TopBar
import co.id.lintasarta.tagpollinginventory.ui.components.calculateRealDistanceMeters
import co.id.lintasarta.tagpollinginventory.ui.theme.TagPollingInventoryTheme
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import java.io.File
import java.util.Locale

@Composable
fun PhotoCaptureScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onUsePhotoClick: () -> Unit
) {
    val draftPole by viewModel.currentDraftPole.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()

    PhotoCaptureScreenContent(
        draftPole = draftPole,
        networkStatus = networkStatus,
        currentLocation = currentLocation,
        onBackClick = onBackClick,
        onUsePhotoClick = onUsePhotoClick,
        onRemovePhoto = { photoPath -> viewModel.removePhoto(photoPath) },
        onAddPhotoAndContinue = { photoPath -> viewModel.addPhotoAndContinue(photoPath) }
    )
}

@Composable
fun PhotoCaptureScreenContent(
    draftPole: Pole?,
    networkStatus: NetworkStatus,
    currentLocation: LocationData,
    onBackClick: () -> Unit,
    onUsePhotoClick: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onAddPhotoAndContinue: (String) -> Unit
) {
    val context = LocalContext.current
    val capturedPhotos = draftPole?.photoPaths ?: emptyList()
    val maxPhotos = 3

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isCapturing by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var imageCaptureInstance by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { ContextCompat.getMainExecutor(context) }

    Scaffold(
        topBar = {
            TopBar(
                title = "Capture Pole Photos",
                subtitle = draftPole?.poleCode?.ifEmpty { draftPole.id } ?: "P-019-019",
                onBackClick = onBackClick,
                networkStatus = networkStatus
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black)
            ) {
            // Instruction Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E1E)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val label = when(capturedPhotos.size) {
                        0 -> "Photo 1: Pole Foundation & Surroundings"
                        1 -> "Photo 2: Tag Label & Serial Number"
                        2 -> "Photo 3: Top Closure / ODP / Cables"
                        else -> "Maximum photos reached ($maxPhotos/3)"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (capturedPhotos.size < maxPhotos) Color.White else Color(0xFF4CAF50)
                    )
                    Text(
                        text = "${capturedPhotos.size} of $maxPhotos photos taken",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            // Viewfinder / Photo Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF121212)),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    // Real CameraX Live Preview Feed!
                    if (capturedPhotos.size < maxPhotos) {
                        CameraPreview(
                            cameraSelector = cameraSelector,
                            flashMode = flashMode,
                            onImageCaptureCreated = { imageCapture ->
                                imageCaptureInstance = imageCapture
                            }
                        )

                        // Camera Framing Overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val boxW = w * 0.7f
                            val boxH = h * 0.8f
                            drawRect(
                                color = TelecomPrimary.copy(alpha = 0.5f),
                                topLeft = Offset((w - boxW) / 2, (h - boxH) / 2),
                                size = Size(boxW, boxH),
                                style = Stroke(width = 4f)
                            )
                        }

                        // GPS & Distance Overlay Badges
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GpsFixed,
                                        contentDescription = null,
                                        tint = if (currentLocation.accuracy <= 5f) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("GPS ±${String.format(Locale.US, "%.1f", currentLocation.accuracy)}m", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }

                            if (draftPole != null) {
                                val distance = calculateRealDistanceMeters(
                                    currentLocation.latitude, currentLocation.longitude,
                                    draftPole.latitude, draftPole.longitude
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Straighten,
                                            contentDescription = null,
                                            tint = if (distance <= 15f) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Distance ~${String.format(Locale.US, "%.0f", distance)}m", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                }
                            }
                        }
                    } else {
                        // Max photos reached preview
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0xFF263238)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("All required photos captured", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary)
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }

                // Remove the loading overlay from the main Viewfinder box 
                // because it forces full recomposition of the CameraPreview, which can interrupt capture.
            }

            // Photo Gallery Strip (Bottom)
            if (capturedPhotos.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2C2C2C))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(capturedPhotos) { photoPath ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = File(photoPath),
                                contentDescription = "Thumb",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Delete button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .clickable { onRemovePhoto(photoPath) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Controls
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E1E)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash Toggle
                    IconButton(
                        onClick = {
                            flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        },
                        enabled = capturedPhotos.size < maxPhotos
                    ) {
                        Icon(
                            imageVector = if (flashMode == ImageCapture.FLASH_MODE_ON) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (capturedPhotos.size < maxPhotos) Color.White else Color.DarkGray
                        )
                    }

                    // Shutter Button
                    if (capturedPhotos.size < maxPhotos) {
                        IconButton(
                            onClick = {
                                if (isCapturing) return@IconButton
                                isCapturing = true
                                val poleId = draftPole?.id ?: "P-019-019"
                                takePhoto(
                                    context = context,
                                    imageCapture = imageCaptureInstance,
                                    executor = cameraExecutor,
                                    poleId = poleId,
                                    lat = draftPole?.latitude ?: 0.0,
                                    lng = draftPole?.longitude ?: 0.0,
                                    onPhotoSaved = { file ->
                                        onAddPhotoAndContinue(file.absolutePath)
                                        isCapturing = false
                                    },
                                    onError = { e ->
                                        isCapturing = false
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(4.dp, TelecomPrimary, CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(TelecomPrimary)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(72.dp)) // Maintain spacing when shutter is hidden
                    }

                    // Finish / Next Button
                    Button(
                        onClick = onUsePhotoClick,
                        enabled = true,
//                            capturedPhotos.isNotEmpty() && !isCapturing,
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomPrimary)
                    ) {
                        Text(if (capturedPhotos.size >= maxPhotos) "Finish" else "Next", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        
        // Overlay directly inside the Box, avoids Dialog which can destroy SurfaceView
        if (isCapturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // Intercept clicks
                contentAlignment = Alignment.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TelecomPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Processing...", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        } // Close the Box
    } // Close the Scaffold
}

@Preview(showBackground = true)
@Composable
fun PhotoCaptureScreenPreview() {
    TagPollingInventoryTheme {
        PhotoCaptureScreenContent(
            draftPole = Pole(
                id = "P-019-019",
                segmentId = "SEG-01",
                sequence = 1,
                latitude = -3.6954,
                longitude = 128.1814,
                poleCode = "P-019-019"
            ),
            networkStatus = NetworkStatus(
                isOnline = true,
                connectionType = "WiFi"
            ),
            currentLocation = LocationData(
                latitude = -3.6954,
                longitude = 128.1814,
                accuracy = 2.8f,
                altitude = 15.0,
                isAvailable = true,
                providerName = "GPS"
            ),
            onBackClick = {},
            onUsePhotoClick = {},
            onRemovePhoto = {},
            onAddPhotoAndContinue = {}
        )
    }
}