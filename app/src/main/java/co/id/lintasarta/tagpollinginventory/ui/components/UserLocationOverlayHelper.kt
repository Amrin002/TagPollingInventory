package co.id.lintasarta.tagpollinginventory.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import co.id.lintasarta.tagpollinginventory.location.LocationData
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.Locale

object UserLocationOverlayHelper {

    private var cachedUserMarkerDrawable: Drawable? = null

    /**
     * Membuat ikon penanda posisi GPS Surveyor modern ala Google Maps / GIS (di-cache untuk efisiensi memori)
     */
    fun createUserLocationMarkerDrawable(context: Context): Drawable {
        if (cachedUserMarkerDrawable != null) {
            return cachedUserMarkerDrawable!!
        }

        val density = context.resources.displayMetrics.density
        val sizeDp = 38
        val sizePx = (sizeDp * density).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Outer Soft Glow / Shadow
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.parseColor("#331A73E8")
        canvas.drawCircle(center, center, center, paint)

        // 2. White Border
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(center, center, center - (3f * density), paint)

        // 3. Electric Blue Center
        paint.color = AndroidColor.parseColor("#1A73E8")
        canvas.drawCircle(center, center, center - (5.5f * density), paint)

        // 4. Center White Pinpoint
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(center, center, 3.5f * density, paint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cachedUserMarkerDrawable = drawable
        return drawable
    }

    /**
     * Memperbarui atau menambahkan overlay titik lokasi user dan lingkaran radius akurasi
     * pada MapView OpenStreetMap secara ringan (tanpa alokasi ulang memori berlebih).
     */
    fun updateUserLocationOverlay(
        mapView: MapView,
        userLocation: LocationData?,
        existingMarker: Marker?,
        existingCircle: Polygon?,
        context: Context
    ): Pair<Marker?, Polygon?> {
        if (userLocation != null && userLocation.isAvailable && userLocation.latitude != 0.0) {
            val userPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

            // Optimisasi: Jika posisi dan akurasi tidak berubah signifikan (< 0.8 meter), lewati redraw
            if (existingMarker != null && existingCircle != null) {
                val prev = existingMarker.position
                val latDiff = kotlin.math.abs(prev.latitude - userPoint.latitude)
                val lngDiff = kotlin.math.abs(prev.longitude - userPoint.longitude)
                if (latDiff < 0.00001 && lngDiff < 0.00001) {
                    return Pair(existingMarker, existingCircle)
                }
            }

            // 1. Lingkaran Radius Akurasi GPS (Translucent Blue Halo)
            val accuracyMeters = userLocation.accuracy.toDouble().coerceIn(8.0, 300.0)
            val circle = existingCircle ?: Polygon(mapView).apply {
                fillPaint.color = AndroidColor.parseColor("#261A73E8") // 15% alpha
                outlinePaint.color = AndroidColor.parseColor("#661A73E8") // 40% alpha
                outlinePaint.strokeWidth = 2.5f
            }
            circle.points = Polygon.pointsAsCircle(userPoint, accuracyMeters)
            if (!mapView.overlays.contains(circle)) {
                mapView.overlays.add(circle)
            }

            // 2. Pin Penanda Surveyor (Gunakan cache Drawable)
            val marker = existingMarker ?: Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createUserLocationMarkerDrawable(context)
            }
            marker.position = userPoint
            marker.title = "Lokasi Anda (Surveyor)"
            marker.snippet = "Akurasi ±${String.format(Locale.US, "%.1f", userLocation.accuracy)}m • ${userLocation.providerName}"

            // Selalu pastikan marker user berada di lapisan teratas
            mapView.overlays.remove(marker)
            mapView.overlays.add(marker)

            mapView.postInvalidate()
            return Pair(marker, circle)
        } else {
            // Hapus overlay jika lokasi tidak tersedia
            existingCircle?.let { mapView.overlays.remove(it) }
            existingMarker?.let { mapView.overlays.remove(it) }
            mapView.postInvalidate()
            return Pair(null, null)
        }
    }
}
