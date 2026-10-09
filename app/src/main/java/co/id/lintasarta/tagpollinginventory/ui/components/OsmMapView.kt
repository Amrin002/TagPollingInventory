package co.id.lintasarta.tagpollinginventory.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.data.model.ImportedRoute
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.data.model.TagStatus
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

@Composable
fun OsmMapView(
    poles: List<Pole>,
    selectedPoleId: String?,
    userLocation: LocationData?,
    importedRoute: ImportedRoute? = null,
    onPoleClick: (Pole) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val osmdroidDir = File(context.filesDir, "osmdroid").apply { if (!exists()) mkdirs() }
        val tilesDir = File(osmdroidDir, "tiles").apply { if (!exists()) mkdirs() }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = "TagPollingInventory/1.0 (Android Field Application)"
        Configuration.getInstance().osmdroidBasePath = osmdroidDir
        Configuration.getInstance().osmdroidTileCache = tilesDir
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true) // Will fallback to local SQLite cache / MBTiles if offline
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.5)
            isTilesScaledToDpi = true
        }
    }
    
    var initialCenterDone by remember { mutableStateOf(false) }

    LaunchedEffect(poles, selectedPoleId, userLocation, importedRoute) {
        mapView.overlays.clear()

        val allGeoPoints = mutableListOf<GeoPoint>()

        // 1. Draw Imported FO Reference Route Polyline (Amber/Orange)
        if (importedRoute != null && importedRoute.coordinates.isNotEmpty()) {
            val routeGeoPoints = importedRoute.coordinates.map { GeoPoint(it.latitude, it.longitude) }
            allGeoPoints.addAll(routeGeoPoints)

            val refPolyline = Polyline(mapView).apply {
                setPoints(routeGeoPoints)
                outlinePaint.color = AndroidColor.parseColor("#E65100")
                outlinePaint.strokeWidth = 14f
            }
            mapView.overlays.add(refPolyline)

            // Draw Imported Reference Poles
            importedRoute.referencePoles.forEach { refPole ->
                val refMarker = Marker(mapView).apply {
                    position = GeoPoint(refPole.latitude, refPole.longitude)
                    title = "Ref: ${refPole.name}"
                    snippet = "Imported KML Reference Pole"
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

        // 2. Draw Surveyed Fiber Optic Route Polyline (Blue)
        if (poles.isNotEmpty()) {
            val geoPoints = poles.map { GeoPoint(it.latitude, it.longitude) }
            allGeoPoints.addAll(geoPoints)

            val polyline = Polyline(mapView).apply {
                setPoints(geoPoints)
                outlinePaint.color = AndroidColor.parseColor("#1565C0")
                outlinePaint.strokeWidth = 10f
            }
            mapView.overlays.add(polyline)

            // Add Surveyed Pole Markers
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

        // 3. User Real Location Marker
        if (userLocation != null && userLocation.isAvailable) {
            val userPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
            val userMarker = Marker(mapView).apply {
                position = userPoint
                title = "Lokasi Saya (${userLocation.providerName})"
                snippet = "Akurasi ±${String.format("%.1f", userLocation.accuracy)}m"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                val userShape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(AndroidColor.parseColor("#1976D2"))
                    setStroke(6, AndroidColor.WHITE)
                    setSize(44, 44)
                }
                icon = userShape
            }
            mapView.overlays.add(userMarker)
        }

        var didCenter = false
        if (!initialCenterDone && mapView.zoomLevelDouble < 10.0) {
            if (userLocation != null && userLocation.isAvailable && userLocation.latitude != 0.0) {
                mapView.controller.setCenter(GeoPoint(userLocation.latitude, userLocation.longitude))
                mapView.controller.setZoom(18.0)
                initialCenterDone = true
                didCenter = true
            } else {
                val targetPole = poles.find { it.id == selectedPoleId } ?: poles.firstOrNull()
                if (targetPole != null && targetPole.latitude != 0.0) {
                    mapView.controller.setCenter(GeoPoint(targetPole.latitude, targetPole.longitude))
                    mapView.controller.setZoom(17.0)
                    initialCenterDone = true
                    didCenter = true
                } else {
                    val validPoints = allGeoPoints.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                    if (validPoints.isNotEmpty()) {
                        val box = BoundingBox.fromGeoPoints(validPoints)
                        mapView.zoomToBoundingBox(box, true, 80)
                        initialCenterDone = true
                        didCenter = true
                    }
                }
            }
            
            // Fallback to center of Indonesia if no other location is available
            // but DO NOT set initialCenterDone to true so that when GPS arrives, it can center.
            if (!didCenter) {
                mapView.controller.setCenter(GeoPoint(-0.7893, 113.9213))
                mapView.controller.setZoom(5.0)
            }
        }

        mapView.invalidate()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )
        
        FloatingActionButton(
            onClick = {
                if (userLocation != null && userLocation.isAvailable && userLocation.latitude != 0.0) {
                    mapView.controller.animateTo(GeoPoint(userLocation.latitude, userLocation.longitude), 18.0, 1000L)
                } else {
                    val target = poles.find { it.id == selectedPoleId } ?: poles.firstOrNull()
                    if (target != null && target.latitude != 0.0) {
                        mapView.controller.animateTo(GeoPoint(target.latitude, target.longitude), 17.0, 1000L)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 220.dp, end = 16.dp),
            containerColor = TelecomPrimary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Center Map")
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
