package co.id.lintasarta.tagpollinginventory.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import co.id.lintasarta.tagpollinginventory.data.model.SegmentStatus
import co.id.lintasarta.tagpollinginventory.location.LocationData
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Komponen OpenStreetMap untuk menampilkan seluruh segmen Fiber Optic (Area Overview Map)
 * dalam satu area operasional (misal: 143 rute Area 18 MPO).
 * Mendukung pewarnaan garis berbasis status survei dan deteksi klik untuk Bottom Sheet.
 */
@Composable
fun OsmMultiSegmentMapView(
    segments: List<Segment>,
    selectedSegmentId: String? = null,
    selectedCity: String? = null,
    userLocation: LocationData? = null,
    onSegmentClick: (Segment) -> Unit,
    onMapReady: ((MapView) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val mapView = remember {
        val osmdroidDir = File(context.filesDir, "osmdroid").apply { if (!exists()) mkdirs() }
        val tilesDir = File(osmdroidDir, "tiles").apply { if (!exists()) mkdirs() }
        val prefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidBasePath = osmdroidDir
        Configuration.getInstance().osmdroidTileCache = tilesDir

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(8.5)
            // Default center: Papua / Maluku midpoint
            controller.setCenter(GeoPoint(-3.5, 136.0))
            isTilesScaledToDpi = true
        }
    }

    LaunchedEffect(mapView) {
        onMapReady?.invoke(mapView)
    }

    // Render all segments with status-based coloring
    LaunchedEffect(segments, selectedSegmentId, selectedCity, userLocation) {
        mapView.overlays.clear()

        val allGeoPoints = mutableListOf<GeoPoint>()

        segments.forEach { segment ->
            val coords = segment.routeCoordinates
            if (coords.size >= 2) {
                val geoPoints = coords.map { GeoPoint(it.latitude, it.longitude) }
                allGeoPoints.addAll(geoPoints)

                val isSelected = segment.id == selectedSegmentId

                // Determine color based on survey status and selection
                val strokeColor = when {
                    isSelected -> AndroidColor.parseColor("#00E5FF") // Bright Cyan Highlight
                    segment.status == SegmentStatus.COMPLETED -> AndroidColor.parseColor("#2E7D32") // Green
                    segment.status == SegmentStatus.IN_PROGRESS -> AndroidColor.parseColor("#FF9800") // Orange
                    else -> AndroidColor.parseColor("#1976D2") // Telecom Blue (Belum Mulai)
                }

                val strokeWidth = if (isSelected) 18f else 10f

                val polyline = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = strokeColor
                    outlinePaint.strokeWidth = strokeWidth
                    title = segment.name
                    snippet = "${segment.route} (${String.format(java.util.Locale.US, "%.1f", (coords.size * 0.05))} km)"
                    relatedObject = segment
                    setOnClickListener { _, _, _ ->
                        onSegmentClick(segment)
                        true
                    }
                }
                mapView.overlays.add(polyline)

                // Add start marker node for easy tapping
                val startPoint = geoPoints.first()
                val marker = Marker(mapView).apply {
                    position = startPoint
                    title = segment.name
                    snippet = segment.route
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(strokeColor)
                        setStroke(if (isSelected) 4 else 2, AndroidColor.WHITE)
                        val size = if (isSelected) 36 else 24
                        setSize(size, size)
                    }
                    icon = shape
                    setOnMarkerClickListener { _, _ ->
                        onSegmentClick(segment)
                        true
                    }
                }
                mapView.overlays.add(marker)
            }
        }

        // Add user location marker if available
        if (userLocation != null && userLocation.isAvailable && userLocation.latitude != 0.0) {
            val userGeoPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
            val userMarker = Marker(mapView).apply {
                position = userGeoPoint
                title = "Lokasi Saya"
                snippet = "Akurasi ±${userLocation.accuracy}m"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                val userShape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(AndroidColor.parseColor("#0288D1")) // Blue dot
                    setStroke(4, AndroidColor.WHITE)
                    setSize(36, 36)
                }
                icon = userShape
            }
            mapView.overlays.add(userMarker)
        }

        // Compute Bounding Box and animate view to fit all rendered segments
        if (allGeoPoints.isNotEmpty()) {
            val validPoints = allGeoPoints.filter { it.latitude in -11.0..6.0 && it.longitude in 120.0..142.0 }
            if (validPoints.isNotEmpty()) {
                val minLat = validPoints.minOf { it.latitude }
                val maxLat = validPoints.maxOf { it.latitude }
                val minLng = validPoints.minOf { it.longitude }
                val maxLng = validPoints.maxOf { it.longitude }

                val latSpan = maxLat - minLat
                val lngSpan = maxLng - minLng

                if (latSpan > 0.005 || lngSpan > 0.005) {
                    val padLat = latSpan.coerceAtLeast(0.01) * 0.15
                    val padLng = lngSpan.coerceAtLeast(0.01) * 0.15
                    val bbox = BoundingBox(
                        maxLat + padLat,
                        maxLng + padLng,
                        minLat - padLat,
                        minLng - padLng
                    )
                    mapView.zoomToBoundingBox(bbox, true, 80)
                } else {
                    mapView.controller.setCenter(GeoPoint((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0))
                    mapView.controller.setZoom(14.0)
                }
            }
        }

        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
