package co.id.lintasarta.tagpollinginventory.parser

import android.util.Xml
import co.id.lintasarta.tagpollinginventory.data.model.ImportPreviewData
import co.id.lintasarta.tagpollinginventory.data.model.ImportedPole
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

class KmlRouteParser {

    fun parseKml(inputStream: InputStream, fileName: String, fileType: String = "KML"): ImportPreviewData {
        var routeName = ""
        var documentName = ""
        var detectedProject = ""
        var detectedSegment = ""

        val routeCoordinates = mutableListOf<RouteCoordinate>()
        val referencePoles = mutableListOf<ImportedPole>()

        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(inputStream, null)
            }

            var eventType = parser.eventType
            var currentTag = ""
            var currentPlacemarkName = ""
            var isInsidePlacemark = false
            var isInsideLineString = false
            var isInsidePoint = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase()
                        if (currentTag == "placemark") {
                            isInsidePlacemark = true
                            currentPlacemarkName = ""
                        } else if (currentTag == "linestring") {
                            isInsideLineString = true
                        } else if (currentTag == "point") {
                            isInsidePoint = true
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "name" -> {
                                    if (isInsidePlacemark) {
                                        currentPlacemarkName = text
                                    } else if (documentName.isEmpty()) {
                                        documentName = text
                                    }
                                }
                                "coordinates" -> {
                                    if (isInsideLineString) {
                                        val parsedCoords = parseCoordinatesString(text)
                                        routeCoordinates.addAll(parsedCoords)
                                        if (routeName.isEmpty() && currentPlacemarkName.isNotEmpty()) {
                                            routeName = currentPlacemarkName
                                        }
                                    } else if (isInsidePoint) {
                                        val parsedPoint = parseSingleCoordinate(text)
                                        if (parsedPoint != null) {
                                            val poleId = if (currentPlacemarkName.isNotEmpty()) currentPlacemarkName else "Pole-${referencePoles.size + 1}"
                                            referencePoles.add(
                                                ImportedPole(
                                                    id = poleId,
                                                    routeId = "",
                                                    name = currentPlacemarkName,
                                                    latitude = parsedPoint.latitude,
                                                    longitude = parsedPoint.longitude,
                                                    altitude = parsedPoint.altitude
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.lowercase()
                        if (tag == "placemark") {
                            isInsidePlacemark = false
                            currentPlacemarkName = ""
                        } else if (tag == "linestring") {
                            isInsideLineString = false
                        } else if (tag == "point") {
                            isInsidePoint = false
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val finalRouteName = if (routeName.isNotEmpty()) routeName else if (documentName.isNotEmpty()) documentName else fileName.substringBeforeLast(".")

        // Detect project / segment from document name or route name
        if (finalRouteName.contains("-")) {
            val parts = finalRouteName.split("-")
            detectedProject = parts.getOrNull(0)?.trim() ?: ""
            detectedSegment = parts.getOrNull(1)?.trim() ?: ""
        } else if (finalRouteName.contains("_")) {
            val parts = finalRouteName.split("_")
            detectedProject = parts.getOrNull(0)?.trim() ?: ""
            detectedSegment = parts.getOrNull(1)?.trim() ?: ""
        }

        return ImportPreviewData(
            fileName = fileName,
            fileType = fileType,
            routeName = finalRouteName,
            coordinateCount = routeCoordinates.size,
            referencePoleCount = referencePoles.size,
            detectedProject = detectedProject,
            detectedSegment = detectedSegment,
            coordinates = routeCoordinates,
            referencePoles = referencePoles
        )
    }

    private fun parseCoordinatesString(coordinatesStr: String): List<RouteCoordinate> {
        val list = mutableListOf<RouteCoordinate>()
        val tokens = coordinatesStr.split(Regex("\\s+"))

        for (token in tokens) {
            val coord = parseSingleCoordinate(token)
            if (coord != null) {
                list.add(coord)
            }
        }
        return list
    }

    private fun parseSingleCoordinate(token: String): RouteCoordinate? {
        val parts = token.split(",")
        if (parts.size >= 2) {
            try {
                // KML format is longitude,latitude,altitude
                val lng = parts[0].trim().toDouble()
                val lat = parts[1].trim().toDouble()
                val alt = if (parts.size >= 3) parts[2].trim().toDoubleOrNull() ?: 0.0 else 0.0

                // Validate reasonable latitude/longitude bounds
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    return RouteCoordinate(longitude = lng, latitude = lat, altitude = alt)
                }
            } catch (e: Exception) {
                // Skip invalid coordinate token
            }
        }
        return null
    }
}
