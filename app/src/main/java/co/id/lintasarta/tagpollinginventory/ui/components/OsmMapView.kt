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
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = "TagPollingInventory/1.0 (Android Field Application)"
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.5)
        }
    }

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

        // 4. Center map to target pole ONLY if it's the first time or location hasn't been set yet
        val targetPole = poles.find { it.id == selectedPoleId } ?: poles.firstOrNull()
        
        // Kita simpan status apakah map sudah pernah di-center sebelumnya
        // Untuk saat ini, asumsikan jika zoom masih rendah (default/awal), kita arahkan ke target.
        if (mapView.zoomLevelDouble < 10) {
            if (targetPole != null) {
                mapView.controller.setCenter(GeoPoint(targetPole.latitude, targetPole.longitude))
                mapView.controller.setZoom(16.5)
            } else if (allGeoPoints.isNotEmpty()) {
                val box = BoundingBox.fromGeoPoints(allGeoPoints)
                mapView.zoomToBoundingBox(box, true, 80)
            }
        }

        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
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
