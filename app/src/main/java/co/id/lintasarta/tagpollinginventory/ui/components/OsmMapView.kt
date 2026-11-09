package co.id.lintasarta.tagpollinginventory.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import co.id.lintasarta.tagpollinginventory.data.model.ImportedRoute
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import co.id.lintasarta.tagpollinginventory.data.model.TagStatus
import co.id.lintasarta.tagpollinginventory.location.LocationData
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

@Composable
fun OsmMapView(
    poles: List<Pole>,
    selectedPoleId: String?,
    userLocation: LocationData?,
    importedRoute: ImportedRoute? = null,
    segmentCoordinates: List<RouteCoordinate> = emptyList(),
    segmentName: String = "",
    onPoleClick: (Pole) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val mapView = remember {
        val osmdroidDir = File(context.filesDir, "osmdroid").apply { if (!exists()) mkdirs() }
        val tilesDir = File(osmdroidDir, "tiles").apply { if (!exists()) mkdirs() }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidBasePath = osmdroidDir
        Configuration.getInstance().osmdroidTileCache = tilesDir

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.5)
            isTilesScaledToDpi = true
        }
    }

    LaunchedEffect(poles, selectedPoleId, userLocation, importedRoute, segmentCoordinates) {
        mapView.overlays.clear()

        val allGeoPoints = mutableListOf<GeoPoint>()

        // 1. Gather all coordinates for Fiber Optic Route
        val activeRouteCoords = when {
            importedRoute != null && importedRoute.coordinates.isNotEmpty() -> importedRoute.coordinates
            segmentCoordinates.isNotEmpty() -> segmentCoordinates
            else -> emptyList()
        }

        if (activeRouteCoords.isNotEmpty()) {
            val routeGeoPoints = activeRouteCoords.map { GeoPoint(it.latitude, it.longitude) }
            allGeoPoints.addAll(routeGeoPoints)

            // Draw FO Route Polyline
            val routePolyline = Polyline(mapView).apply {
                setPoints(routeGeoPoints)
                outlinePaint.color = AndroidColor.parseColor("#0D47A1") // Bold Telecom Blue
                outlinePaint.strokeWidth = 14f
            }
            mapView.overlays.add(routePolyline)

            // Start (Origin) Point Marker
            val startPt = routeGeoPoints.first()
            val startMarker = Marker(mapView).apply {
                position = startPt
                title = "Titik Awal Segmen FO"
                snippet = segmentName.ifEmpty { "Origin Site" }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                val startShape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(AndroidColor.parseColor("#2E7D32")) // Green
                    setStroke(4, AndroidColor.WHITE)
                    setSize(36, 36)
                }
                icon = startShape
            }
            mapView.overlays.add(startMarker)

            // End (Destination) Point Marker
            if (routeGeoPoints.size > 1) {
                val endPt = routeGeoPoints.last()
                val endMarker = Marker(mapView).apply {
                    position = endPt
                    title = "Titik Akhir Segmen FO"
                    snippet = segmentName.ifEmpty { "Destination Site" }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    val endShape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(AndroidColor.parseColor("#D32F2F")) // Red
                        setStroke(4, AndroidColor.WHITE)
                        setSize(36, 36)
                    }
                    icon = endShape
                }
                mapView.overlays.add(endMarker)
            }

            // Draw Imported Reference Poles if available
            importedRoute?.referencePoles?.forEach { refPole ->
                val refMarker = Marker(mapView).apply {
                    position = GeoPoint(refPole.latitude, refPole.longitude)
                    title = "Ref: ${refPole.name}"
                    snippet = "Titik Referensi Jalur FO"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(AndroidColor.parseColor("#EF6C00"))
                        setStroke(2, AndroidColor.WHITE)
                        setSize(28, 28)
                    }
                    icon = shape
                }
                mapView.overlays.add(refMarker)
            }
        }

        // 2. Draw Surveyed Fiber Optic Route Poles
        if (poles.isNotEmpty()) {
            val geoPoints = poles.map { GeoPoint(it.latitude, it.longitude) }
            allGeoPoints.addAll(geoPoints)

            val polyline = Polyline(mapView).apply {
                setPoints(geoPoints)
                outlinePaint.color = AndroidColor.parseColor("#1565C0")
                outlinePaint.strokeWidth = 10f
            }
            mapView.overlays.add(polyline)

            poles.forEach { pole ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(pole.latitude, pole.longitude)
                    title = "${pole.poleCode.ifEmpty { pole.id }} (${pole.tagNumber})"
                    snippet = "${pole.type.displayName} • ${pole.condition.displayName} • ${pole.status.displayName}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                    val colorInt = when (pole.status) {
                        TagStatus.COMPLETED -> AndroidColor.parseColor("#2E7D32")
                        TagStatus.CONFLICT -> AndroidColor.parseColor("#C62828")
                        TagStatus.NOT_TAGGED -> AndroidColor.parseColor("#757575")
                    }

                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(colorInt)
                        setStroke(if (pole.id == selectedPoleId) 8 else 3, AndroidColor.WHITE)
                        val size = if (pole.id == selectedPoleId) 48 else 32
                        setSize(size, size)
                    }

                    icon = shape

                    setOnMarkerClickListener { _, _ ->
                        onPoleClick(pole)
                        true
                    }
                }
                mapView.overlays.add(marker)
            }
        }

        // 3. User Real Location Marker & Accuracy Halo
        UserLocationOverlayHelper.updateUserLocationOverlay(
            mapView = mapView,
            userLocation = userLocation,
            existingMarker = null,
            existingCircle = null,
            context = context
        )

        // 4. MAP CENTERING: Always center directly on the segment centroid / bounding box
        val validRoutePoints = allGeoPoints.filter { it.latitude != 0.0 && it.longitude != 0.0 }
        if (validRoutePoints.isNotEmpty()) {
            val minLat = validRoutePoints.minOf { it.latitude }
            val maxLat = validRoutePoints.maxOf { it.latitude }
            val minLng = validRoutePoints.minOf { it.longitude }
            val maxLng = validRoutePoints.maxOf { it.longitude }
            val centerLat = (minLat + maxLat) / 2.0
            val centerLng = (minLng + maxLng) / 2.0
            val centerGeoPoint = GeoPoint(centerLat, centerLng)

            mapView.controller.setCenter(centerGeoPoint)

            val latSpan = maxLat - minLat
            val lngSpan = maxLng - minLng
            if (validRoutePoints.size > 1 && (latSpan > 0.0008 || lngSpan > 0.0008)) {
                val padLat = latSpan.coerceAtLeast(0.005) * 0.2
                val padLng = lngSpan.coerceAtLeast(0.005) * 0.2
                val box = BoundingBox(
                    maxLat + padLat,
                    maxLng + padLng,
                    minLat - padLat,
                    minLng - padLng
                )
                mapView.zoomToBoundingBox(box, false, 80)
            } else {
                mapView.controller.setZoom(16.5)
            }
        } else {
            val targetPole = poles.find { it.id == selectedPoleId } ?: poles.firstOrNull()
            if (targetPole != null && targetPole.latitude != 0.0) {
                mapView.controller.setCenter(GeoPoint(targetPole.latitude, targetPole.longitude))
                mapView.controller.setZoom(16.5)
            } else if (userLocation != null && userLocation.isAvailable &&
                userLocation.latitude in -11.5..6.5 && userLocation.longitude in 95.0..142.0
            ) {
                mapView.controller.setCenter(GeoPoint(userLocation.latitude, userLocation.longitude))
                mapView.controller.setZoom(16.5)
            } else {
                // Default fallback: Pusat Operasional Area 18 MPO (Timika, Papua)
                mapView.controller.setCenter(GeoPoint(-4.76609, 136.76967))
                mapView.controller.setZoom(15.5)
            }
        }

        mapView.invalidate()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 220.dp, end = 16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            // 1. Tombol Fokus ke Lokasi Surveyor
            FloatingActionButton(
                onClick = {
                    if (userLocation != null && userLocation.isAvailable && userLocation.latitude != 0.0) {
                        mapView.controller.animateTo(GeoPoint(userLocation.latitude, userLocation.longitude), 17.0, 500L)
                        Toast.makeText(context, "Peta dipusatkan ke lokasi Anda", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Mencari sinyal GPS surveyor...", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = Color.White,
                contentColor = Color(0xFF1A73E8)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Lokasi Saya")
            }

            // 2. Tombol Fokus ke Rute Segmen FO
            FloatingActionButton(
                onClick = {
                    val validRoutePoints = when {
                        importedRoute != null && importedRoute.coordinates.isNotEmpty() ->
                            importedRoute.coordinates.map { GeoPoint(it.latitude, it.longitude) }
                        segmentCoordinates.isNotEmpty() ->
                            segmentCoordinates.map { GeoPoint(it.latitude, it.longitude) }
                        poles.isNotEmpty() ->
                            poles.map { GeoPoint(it.latitude, it.longitude) }
                        else -> emptyList()
                    }.filter { it.latitude != 0.0 && it.longitude != 0.0 }

                    if (validRoutePoints.isNotEmpty()) {
                        val centerLat = validRoutePoints.map { it.latitude }.average()
                        val centerLng = validRoutePoints.map { it.longitude }.average()
                        mapView.controller.animateTo(GeoPoint(centerLat, centerLng), 16.5, 600L)
                        Toast.makeText(context, "Peta dipusatkan ke rute segmen", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Belum ada titik rute segmen", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = TelecomPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Pusatkan Rute Segmen")
            }
        }
    }
}

fun calculateRealDistanceMeters(
    userLat: Double,
    userLng: Double,
    targetLat: Double,
    targetLng: Double
): Float {
    val results = FloatArray(1)
    Location.distanceBetween(userLat, userLng, targetLat, targetLng, results)
    return results[0]
}
