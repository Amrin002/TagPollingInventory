package co.id.lintasarta.tagpollinginventory.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import java.util.Locale

private class SegmentOverlays(
    val segment: Segment,
    val polyline: Polyline,
    val startMarker: Marker?
)

/**
 * Komponen OpenStreetMap untuk menampilkan seluruh segmen Fiber Optic (Area Overview Map)
 * dalam satu area operasional (misal: 143 rute Area 18 MPO).
 *
 * Responsif & Interaktif seperti Google Maps:
 * - Kamera TIDAK AKAN reset saat segmen/rute diklik (posisi pan & zoom dipertahankan).
 * - Perubahan gaya seleksi (highlight cyan) diperbarui secara instan tanpa membangun ulang overlay.
 * - Zoom to bounding box HANYA dijalankan saat filter kota berubah atau pemuatan awal.
 * - Marker lokasi GPS surveyor diperbarui secara independen tanpa mengganggu segmen.
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
    val lifecycleOwner = LocalLifecycleOwner.current

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
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            controller.setZoom(8.5)
            // Default center: Papua / Maluku midpoint
            controller.setCenter(GeoPoint(-3.5, 136.0))
            isTilesScaledToDpi = true
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(mapView) {
        onMapReady?.invoke(mapView)
    }

    // Reference map untuk polylines & markers per segment
    val overlayHolders = remember { mutableMapOf<String, SegmentOverlays>() }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    var userCircle by remember { mutableStateOf<org.osmdroid.views.overlay.Polygon?>(null) }
    var lastZoomedCity by remember { mutableStateOf<String?>(null) }

    // 1. Bangun overlay segmen saat daftar segments atau filter selectedCity berubah
    LaunchedEffect(segments, selectedCity) {
        // Hapus overlay lama milik segmen
        overlayHolders.values.forEach { holder ->
            mapView.overlays.remove(holder.polyline)
            holder.startMarker?.let { mapView.overlays.remove(it) }
        }
        overlayHolders.clear()

        val allGeoPoints = mutableListOf<GeoPoint>()

        segments.forEach { segment ->
            val coords = segment.routeCoordinates
            if (coords.size >= 2) {
                val geoPoints = coords.map { GeoPoint(it.latitude, it.longitude) }
                allGeoPoints.addAll(geoPoints)

                val isSelected = segment.id == selectedSegmentId
                val strokeColor = getSegmentColor(segment.status, isSelected)
                val strokeWidth = if (isSelected) 20f else 10f

                val polyline = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = strokeColor
                    outlinePaint.strokeWidth = strokeWidth
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    title = segment.name
                    snippet = "${segment.route} (${String.format(Locale.US, "%.1f", (coords.size * 0.05))} km)"
                    relatedObject = segment
                    setOnClickListener { _, _, _ ->
                        onSegmentClick(segment)
                        true
                    }
                }
                mapView.overlays.add(polyline)

                val startPoint = geoPoints.first()
                val marker = Marker(mapView).apply {
                    position = startPoint
                    title = segment.name
                    snippet = segment.route
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createMarkerIcon(strokeColor, isSelected)
                    setOnMarkerClickListener { _, _ ->
                        onSegmentClick(segment)
                        true
                    }
                }
                mapView.overlays.add(marker)

                overlayHolders[segment.id] = SegmentOverlays(
                    segment = segment,
                    polyline = polyline,
                    startMarker = marker
                )
            }
        }

        // Zoom to Bounding Box HANYA jika filter kota berubah atau load awal
        val currentFilterKey = selectedCity ?: "SEMUA"
        if (currentFilterKey != lastZoomedCity && allGeoPoints.isNotEmpty()) {
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
            lastZoomedCity = currentFilterKey
        }

        // Pastikan overlay user tetap di lapisan paling atas
        userCircle?.let {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }
        userMarker?.let {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }

        mapView.postInvalidate()
    }

    // 2. Perbarui highlight segmen yang dipilih TANPA mereset kamera
    LaunchedEffect(selectedSegmentId) {
        overlayHolders.forEach { (id, holder) ->
            val isSelected = (id == selectedSegmentId)
            val strokeColor = getSegmentColor(holder.segment.status, isSelected)
            val strokeWidth = if (isSelected) 20f else 10f

            holder.polyline.outlinePaint.color = strokeColor
            holder.polyline.outlinePaint.strokeWidth = strokeWidth
            holder.startMarker?.icon = createMarkerIcon(strokeColor, isSelected)

            if (isSelected) {
                // Pindahkan segmen dan marker terpilih ke layer paling atas agar terlihat jelas
                mapView.overlays.remove(holder.polyline)
                holder.startMarker?.let { mapView.overlays.remove(it) }
                mapView.overlays.add(holder.polyline)
                holder.startMarker?.let { mapView.overlays.add(it) }
            }
        }

        // Overlay user selalu paling atas
        userCircle?.let {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }
        userMarker?.let {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }

        mapView.postInvalidate()
    }

    // 3. Perbarui posisi GPS pengguna secara mandiri menggunakan UserLocationOverlayHelper
    LaunchedEffect(userLocation) {
        val (m, c) = UserLocationOverlayHelper.updateUserLocationOverlay(
            mapView = mapView,
            userLocation = userLocation,
            existingMarker = userMarker,
            existingCircle = userCircle,
            context = context
        )
        userMarker = m
        userCircle = c
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun getSegmentColor(status: SegmentStatus, isSelected: Boolean): Int {
    return when {
        isSelected -> AndroidColor.parseColor("#00E5FF") // Bright Cyan Highlight
        status == SegmentStatus.COMPLETED -> AndroidColor.parseColor("#2E7D32") // Green
        status == SegmentStatus.IN_PROGRESS -> AndroidColor.parseColor("#FF9800") // Orange
        else -> AndroidColor.parseColor("#1976D2") // Telecom Blue (Belum Mulai)
    }
}

private fun createMarkerIcon(strokeColor: Int, isSelected: Boolean): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(strokeColor)
        setStroke(if (isSelected) 4 else 2, AndroidColor.WHITE)
        val size = if (isSelected) 36 else 24
        setSize(size, size)
    }
}

