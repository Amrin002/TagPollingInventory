package co.id.lintasarta.tagpollinginventory.service

import android.location.Location
import co.id.lintasarta.tagpollinginventory.data.model.DeviationStatus
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import co.id.lintasarta.tagpollinginventory.data.model.RouteDeviation

class RouteDeviationService {

    fun calculateDeviation(
        userLat: Double,
        userLng: Double,
        routeCoordinates: List<RouteCoordinate>
    ): RouteDeviation {
        if (routeCoordinates.isEmpty()) {
            return RouteDeviation(0f, DeviationStatus.NORMAL)
        }

        if (routeCoordinates.size == 1) {
            val dist = distanceBetweenMeters(userLat, userLng, routeCoordinates[0].latitude, routeCoordinates[0].longitude)
            return buildDeviation(dist)
        }

        var minDistance = Float.MAX_VALUE

        for (i in 0 until routeCoordinates.size - 1) {
            val p1 = routeCoordinates[i]
            val p2 = routeCoordinates[i + 1]

            val dist = distanceToSegmentMeters(
                userLat, userLng,
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude
            )

            if (dist < minDistance) {
                minDistance = dist
            }
        }

        val finalDistance = if (minDistance == Float.MAX_VALUE) 0f else minDistance
        return buildDeviation(finalDistance)
    }

    private fun buildDeviation(distanceMeters: Float): RouteDeviation {
        val status = when {
            distanceMeters <= 10.0f -> DeviationStatus.NORMAL
            distanceMeters <= 25.0f -> DeviationStatus.WARNING
            else -> DeviationStatus.CHECK_ROUTE
        }
        return RouteDeviation(distanceMeters = distanceMeters, status = status)
    }

    private fun distanceToSegmentMeters(
        px: Double, py: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1

        if (dx == 0.0 && dy == 0.0) {
            return distanceBetweenMeters(px, py, x1, y1)
        }

        // Project point p onto line segment (p1, p2)
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)

        val closestX: Double
        val closestY: Double

        if (t < 0.0) {
            closestX = x1
            closestY = y1
        } else if (t > 1.0) {
            closestX = x2
            closestY = y2
        } else {
            closestX = x1 + t * dx
            closestY = y1 + t * dy
        }

        return distanceBetweenMeters(px, py, closestX, closestY)
    }

    private fun distanceBetweenMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }
}
