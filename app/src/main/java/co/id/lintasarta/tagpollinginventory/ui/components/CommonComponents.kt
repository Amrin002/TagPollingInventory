package co.id.lintasarta.tagpollinginventory.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.data.model.TagStatus
import co.id.lintasarta.tagpollinginventory.network.NetworkStatus
import co.id.lintasarta.tagpollinginventory.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    networkStatus: NetworkStatus = NetworkStatus(),
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Dynamic Online / Offline Pill Indicator
                val (pillDotColor, pillLabel) = if (networkStatus.isOnline) {
                    Pair(Color(0xFF4CAF50), "ONLINE (${networkStatus.connectionType})")
                } else {
                    Pair(Color(0xFFFF9800), "OFFLINE (Local)")
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(pillDotColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = pillLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                actions()
            }
        },
        windowInsets = WindowInsets(0.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TelecomPrimary
        )
    )
}

@Composable
fun OfflineStatusCard(
    networkStatus: NetworkStatus = NetworkStatus(),
    modifier: Modifier = Modifier
) {
    val isOnline = networkStatus.isOnline
    val containerBg = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
    val statusColor = if (isOnline) Color(0xFF2E7D32) else TelecomOnPrimaryContainer
    val dotColor = if (isOnline) Color(0xFF4CAF50) else Color(0xFF2E7D32)

    val titleText = if (isOnline) "⚡ Online Mode (${networkStatus.connectionType})" else "● Offline Mode (Local Storage)"
    val messageText = if (isOnline) {
        "Connected via ${networkStatus.connectionType}. High precision GPS triangulation & fast tile loading active."
    } else {
        "Your field survey data is stored safely in local storage with hardware GPS."
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = titleText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor
                )
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF37474F)
                )
            }
        }
    }
}

@Composable
fun TagStatusBadge(status: TagStatus) {
    val (color, bg, text) = when (status) {
        TagStatus.COMPLETED -> Triple(StatusCompleted, StatusCompletedContainer, "Completed")
        TagStatus.CONFLICT -> Triple(StatusConflict, StatusConflictContainer, "Conflict")
        TagStatus.NOT_TAGGED -> Triple(StatusNotTagged, StatusNotTaggedContainer, "Not Tagged")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun SegmentStatusBadge(status: SegmentStatus) {
    val (color, bg, text) = when (status) {
        SegmentStatus.COMPLETED -> Triple(StatusCompleted, StatusCompletedContainer, "Completed")
        SegmentStatus.IN_PROGRESS -> Triple(StatusWarning, StatusWarningContainer, "In Progress")
        SegmentStatus.NOT_STARTED -> Triple(StatusNotTagged, StatusNotTaggedContainer, "Not Started")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun StatCounterBox(
    title: String,
    count: Int,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

@Composable
fun FieldMapCanvas(
    poles: List<Pole>,
    selectedPoleId: String?,
    onPoleClick: (Pole) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEAEFF5))
            .border(1.dp, NeutralBorder)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(poles) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (poles.isEmpty()) return@detectTapGestures

                        val padding = 100f
                        var minLat = poles.minOf { it.latitude }
                        var maxLat = poles.maxOf { it.latitude }
                        var minLng = poles.minOf { it.longitude }
                        var maxLng = poles.maxOf { it.longitude }

                        if (maxLat == minLat) { maxLat += 0.001; minLat -= 0.001 }
                        if (maxLng == minLng) { maxLng += 0.001; minLng -= 0.001 }

                        val clickedPole = poles.minByOrNull { pole ->
                            val x = padding + ((pole.longitude - minLng) / (maxLng - minLng) * (w - 2 * padding)).toFloat()
                            val y = padding + ((maxLat - pole.latitude) / (maxLat - minLat) * (h - 2 * padding)).toFloat()
                            val dx = x - tapOffset.x
                            val dy = y - tapOffset.y
                            dx * dx + dy * dy
                        }

                        if (clickedPole != null) {
                            onPoleClick(clickedPole)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val padding = 100f

            // 1. Draw Map Grid lines (Offline topographical look)
            val gridStep = 80f
            var gx = 0f
            while (gx < w) {
                drawLine(
                    color = Color(0xFFD0D7DE),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1f
                )
                gx += gridStep
            }
            var gy = 0f
            while (gy < h) {
                drawLine(
                    color = Color(0xFFD0D7DE),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 1f
                )
                gy += gridStep
            }

            if (poles.isEmpty()) return@Canvas

            var minLat = poles.minOf { it.latitude }
            var maxLat = poles.maxOf { it.latitude }
            var minLng = poles.minOf { it.longitude }
            var maxLng = poles.maxOf { it.longitude }

            if (maxLat == minLat) { maxLat += 0.001; minLat -= 0.001 }
            if (maxLng == minLng) { maxLng += 0.001; minLng -= 0.001 }

            val points = poles.map { pole ->
                val x = padding + ((pole.longitude - minLng) / (maxLng - minLng) * (w - 2 * padding)).toFloat()
                val y = padding + ((maxLat - pole.latitude) / (maxLat - minLat) * (h - 2 * padding)).toFloat()
                Pair(pole, Offset(x, y))
            }

            // 2. Draw Fiber Optic Segment Route Line
            val path = Path()
            points.forEachIndexed { idx, pair ->
                if (idx == 0) path.moveTo(pair.second.x, pair.second.y)
                else path.lineTo(pair.second.x, pair.second.y)
            }

            // Route casing
            drawPath(
                path = path,
                color = TelecomPrimary.copy(alpha = 0.3f),
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
            // Route line
            drawPath(
                path = path,
                color = TelecomPrimary,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            // 3. User Location Blue Circle
            val userLocationPole = points.find { it.first.id == selectedPoleId } ?: points.getOrNull(18)
            if (userLocationPole != null) {
                val userOffset = Offset(userLocationPole.second.x - 30f, userLocationPole.second.y + 40f)

                // Accuracy circle
                drawCircle(
                    color = LocationBlue.copy(alpha = 0.2f),
                    radius = 45f,
                    center = userOffset
                )
                drawCircle(
                    color = LocationBlue,
                    radius = 10f,
                    center = userOffset
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = userOffset
                )

                // Distance indicator line to target pole
                drawLine(
                    color = LocationBlue,
                    start = userOffset,
                    end = userLocationPole.second,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // 4. Draw Pole Markers
            points.forEach { (pole, offset) ->
                val isSelected = pole.id == selectedPoleId

                val markerColor = when (pole.status) {
                    TagStatus.COMPLETED -> StatusCompleted
                    TagStatus.CONFLICT -> StatusConflict
                    TagStatus.NOT_TAGGED -> StatusNotTagged
                }

                if (isSelected) {
                    // Pulsing ring around selected target pole
                    drawCircle(
                        color = markerColor.copy(alpha = 0.35f / pulseScale),
                        radius = 24f * pulseScale,
                        center = offset
                    )
                    drawCircle(
                        color = TelecomPrimary,
                        radius = 18f,
                        center = offset,
                        style = Stroke(width = 3f)
                    )
                }

                // Marker background fill
                drawCircle(
                    color = Color.White,
                    radius = if (isSelected) 14f else 10f,
                    center = offset
                )

                // Inner marker dot
                drawCircle(
                    color = markerColor,
                    radius = if (isSelected) 10f else 7f,
                    center = offset
                )
            }
        }

        // Compass / Map Overlay Overlay Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Compass",
                        tint = TelecomPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Center Location",
                        tint = LocationBlue
                    )
                }
            }
        }
    }
}
