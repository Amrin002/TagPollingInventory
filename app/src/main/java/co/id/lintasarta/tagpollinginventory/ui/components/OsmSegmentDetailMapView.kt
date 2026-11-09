package co.id.lintasarta.tagpollinginventory.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import co.id.lintasarta.tagpollinginventory.data.model.Segment
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
import java.util.Locale

/**
 * Minimap interaktif OpenStreetMap untuk Layar Detail Segmen.
 *
 * Fitur:
 * 1. Menampilkan polyline rute kabel Fiber Optic untuk segmen yang dipilih.
 * 2. Menampilkan titik awal (Site Asal) dan titik akhir (Site Tujuan) rute.
 * 3. Menampilkan seluruh tiang (PL 1, PL 2, dst.) sebagai pin penanda berlabel angka/urutan tiang.
 * 4. Tiang yang dipilih disorot dengan ring cyan terang.
 * 5. Menampilkan posisi GPS surveyor saat ini secara mandiri.
 * 6. Kontrol mengambang: Fokus ke Rute/Tiang, Lokasi Saya, Zoom In & Zoom Out.
 */
@Composable
fun OsmSegmentDetailMapView(
    segment: Segment,
    routeCoordinates: List<RouteCoordinate>,
    poles: List<Pole>,
    selectedPoleId: String? = null,
    userLocation: LocationData? = null,
    onPoleClick: (Pole) -> Unit,
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
            controller.setZoom(14.0)
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

    // Reference untuk overlay rute, marker tiang, dan user location
    val poleMarkers = remember { mutableMapOf<String, Marker>() }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    var userCircle by remember { mutableStateOf<org.osmdroid.views.overlay.Polygon?>(null) }
    var lastFittedSegmentId by remember { mutableStateOf<String?>(null) }

    // Fungsi helper untuk menghitung bounding box dan animasi fokus
    fun fitRouteAndPoles() {
        val allPoints = mutableListOf<GeoPoint>()
        routeCoordinates.forEach { allPoints.add(GeoPoint(it.latitude, it.longitude)) }
        poles.filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .forEach { allPoints.add(GeoPoint(it.latitude, it.longitude)) }

        val validPoints = allPoints.filter { it.latitude in -11.0..6.0 && it.longitude in 120.0..142.0 }
        if (validPoints.isNotEmpty()) {
            val minLat = validPoints.minOf { it.latitude }
            val maxLat = validPoints.maxOf { it.latitude }
            val minLng = validPoints.minOf { it.longitude }
            val maxLng = validPoints.maxOf { it.longitude }

            val latSpan = maxLat - minLat
            val lngSpan = maxLng - minLng

            if (latSpan > 0.001 || lngSpan > 0.001) {
                val padLat = latSpan.coerceAtLeast(0.002) * 0.25
                val padLng = lngSpan.coerceAtLeast(0.002) * 0.25
                val bbox = BoundingBox(
                    maxLat + padLat,
                    maxLng + padLng,
                    minLat - padLat,
                    minLng - padLng
                )
                mapView.zoomToBoundingBox(bbox, true, 80)
            } else {
                mapView.controller.animateTo(GeoPoint(minLat, minLng))
                mapView.controller.setZoom(16.0)
            }
        }
    }

    // 1. Render Polyline Rute Segmen & Titik Site Asal / Tujuan
    LaunchedEffect(segment.id, routeCoordinates) {
        // Bersihkan seluruh overlay lama kecuali marker user
        mapView.overlays.clear()
        poleMarkers.clear()

        val allPoints = mutableListOf<GeoPoint>()

        if (routeCoordinates.size >= 2) {
            val geoPoints = routeCoordinates.map { GeoPoint(it.latitude, it.longitude) }
            allPoints.addAll(geoPoints)

            // Polyline Rute FO
            val polyline = Polyline(mapView).apply {
                setPoints(geoPoints)
                outlinePaint.color = AndroidColor.parseColor("#1976D2") // Telecom Blue
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                title = segment.name
                snippet = "${segment.route.ifEmpty { "Rute FO" }} (${String.format(Locale.US, "%.1f", (routeCoordinates.size * 0.05))} km)"
            }
            mapView.overlays.add(polyline)

            // Marker Titik Awal (Site Asal)
            val startPoint = geoPoints.first()
            val startMarker = Marker(mapView).apply {
                position = startPoint
                title = "Asal: ${segment.orgSite.ifEmpty { "Start Site" }}"
                snippet = segment.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createSiteNodeIcon(context, segment.orgSite.ifEmpty { "A" }, AndroidColor.parseColor("#2E7D32"))
            }
            mapView.overlays.add(startMarker)

            // Marker Titik Akhir (Site Tujuan)
            val endPoint = geoPoints.last()
            val endMarker = Marker(mapView).apply {
                position = endPoint
                title = "Tujuan: ${segment.dstSite.ifEmpty { "End Site" }}"
                snippet = segment.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createSiteNodeIcon(context, segment.dstSite.ifEmpty { "B" }, AndroidColor.parseColor("#C62828"))
            }
            mapView.overlays.add(endMarker)
        }

        // Render tiang-tiang pada segmen ini
        poles.forEach { pole ->
            if (pole.latitude != 0.0 && pole.longitude != 0.0) {
                val polePoint = GeoPoint(pole.latitude, pole.longitude)
                allPoints.add(polePoint)

                val isSelected = (pole.id == selectedPoleId)
                val marker = Marker(mapView).apply {
                    position = polePoint
                    title = "PL ${pole.sequence} • ${pole.effectiveAssetUid}"
                    snippet = "${pole.assetClass.uppercase()} (${pole.height}) • ${pole.status.displayName}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createPoleBadgeDrawable(context, pole.sequence, isSelected, pole.status)
                    setOnMarkerClickListener { _, _ ->
                        onPoleClick(pole)
                        true
                    }
                }
                mapView.overlays.add(marker)
                poleMarkers[pole.id] = marker
            }
        }

        // Pastikan marker user tetap di atas jika ada
        userMarker?.let {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }

        // Auto-fit kamera pada pemuatan pertama segmen ini
        if (lastFittedSegmentId != segment.id) {
            fitRouteAndPoles()
            lastFittedSegmentId = segment.id
        }

        mapView.postInvalidate()
    }

    // 2. Pembaruan seleksi tiang (highlight tanpa mereset kamera)
    LaunchedEffect(selectedPoleId, poles) {
        poles.forEach { pole ->
            poleMarkers[pole.id]?.let { marker ->
                val isSelected = (pole.id == selectedPoleId)
                marker.icon = createPoleBadgeDrawable(context, pole.sequence, isSelected, pole.status)
                if (isSelected) {
                    mapView.overlays.remove(marker)
                    mapView.overlays.add(marker) // Bring to front
                }
            }
        }
        userMarker?.let {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }
        mapView.postInvalidate()
    }

    // 3. Pembaruan posisi GPS surveyor menggunakan UserLocationOverlayHelper
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
        // Tampilan Peta OSM
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Info Badge: Panjang Rute & Jumlah Tiang (Pojok Kiri Atas)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            color = Color.White.copy(alpha = 0.92f),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF1976D2), CircleShape)
                )
                val distKm = String.format(Locale.US, "%.1f", (routeCoordinates.size * 0.05).coerceAtLeast(segment.lengthMeters / 1000.0).coerceAtLeast(0.5))
                Text(
                    text = "$distKm km • ${poles.size} Tiang",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF263238)
                )
            }
        }

        // Floating Action Buttons: Zoom & Re-Center Controls (Pojok Kanan Bawah)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tombol Fokuskan Peta ke Seluruh Rute & Tiang
            SmallFloatingActionButton(
                onClick = { fitRouteAndPoles() },
                containerColor = Color.White,
                contentColor = TelecomPrimary,
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Fokus Rute & Tiang",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Tombol Fokus Lokasi GPS Surveyor (Selalu Tampil)
            SmallFloatingActionButton(
                onClick = {
                    if (userLocation != null && userLocation.isAvailable && userLocation.latitude != 0.0) {
                        mapView.controller.animateTo(GeoPoint(userLocation.latitude, userLocation.longitude))
                        mapView.controller.setZoom(17.0)
                    } else {
                        android.widget.Toast.makeText(context, "Mencari sinyal GPS surveyor...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = Color.White,
                contentColor = Color(0xFF0288D1),
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Lokasi Saya",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Tombol Zoom In
            SmallFloatingActionButton(
                onClick = { mapView.controller.zoomIn() },
                containerColor = Color.White,
                contentColor = Color.DarkGray,
                shape = CircleShape,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Perbesar", modifier = Modifier.size(18.dp))
            }

            // Tombol Zoom Out
            SmallFloatingActionButton(
                onClick = { mapView.controller.zoomOut() },
                containerColor = Color.White,
                contentColor = Color.DarkGray,
                shape = CircleShape,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Perkecil", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Membuat icon badge melingkar berlabel "PL 1", "PL 2", dst. untuk tiang pada minimap.
 */
private fun createPoleBadgeDrawable(
    context: Context,
    sequence: Int,
    isSelected: Boolean,
    status: TagStatus
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val diameterDp = if (isSelected) 38 else 30
    val diameterPx = (diameterDp * density).toInt()

    val bitmap = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = diameterPx / 2f

    // 1. Warna latar belakang berdasarkan status tiang
    val bgColor = when (status) {
        TagStatus.COMPLETED -> AndroidColor.parseColor("#2E7D32") // Hijau Selesai
        TagStatus.CONFLICT -> AndroidColor.parseColor("#C62828")  // Merah Konflik / Safety Deferral
        TagStatus.NOT_TAGGED -> AndroidColor.parseColor("#0288D1") // Biru Belum Tagging / Baru
    }
    paint.color = bgColor
    paint.style = Paint.Style.FILL
    canvas.drawCircle(radius, radius, radius - (3 * density), paint)

    // 2. Stroke / Border Ring
    paint.style = Paint.Style.STROKE
    paint.color = if (isSelected) AndroidColor.parseColor("#00E5FF") else AndroidColor.WHITE
    paint.strokeWidth = if (isSelected) 4f * density else 2f * density
    canvas.drawCircle(radius, radius, radius - (3 * density), paint)

    // 3. Teks Nomor Tiang (e.g. "PL 1", "PL 2", "P1")
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = (if (isSelected) 12f else 10f) * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val label = if (sequence > 99) "$sequence" else "P$sequence"
    val yOffset = (radius - ((textPaint.descent() + textPaint.ascent()) / 2))
    canvas.drawText(label, radius, yOffset, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Membuat icon node untuk titik Site Asal atau Site Tujuan rute kabel FO.
 */
private fun createSiteNodeIcon(
    context: Context,
    siteCode: String,
    circleColor: Int
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val diameterPx = (32 * density).toInt()

    val bitmap = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = diameterPx / 2f

    paint.color = circleColor
    paint.style = Paint.Style.FILL
    canvas.drawCircle(radius, radius, radius - (2 * density), paint)

    paint.style = Paint.Style.STROKE
    paint.color = AndroidColor.WHITE
    paint.strokeWidth = 2.5f * density
    canvas.drawCircle(radius, radius, radius - (2 * density), paint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 9f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val shortLabel = siteCode.take(3).uppercase()
    val yOffset = (radius - ((textPaint.descent() + textPaint.ascent()) / 2))
    canvas.drawText(shortLabel, radius, yOffset, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
