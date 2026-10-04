package co.id.lintasarta.tagpollinginventory.data.model

data class RouteCoordinate(
    val longitude: Double,
    val latitude: Double,
    val altitude: Double = 0.0
)

data class ImportedPole(
    val id: String,
    val routeId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val metadata: Map<String, String> = emptyMap()
)

data class ImportedRoute(
    val id: String,
    val name: String,
    val sourceFileName: String,
    val sourceFileType: String,
    val projectId: String,
    val segmentId: String,
    val coordinates: List<RouteCoordinate>,
    val referencePoles: List<ImportedPole> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = ""
)

enum class DeviationStatus(val displayName: String) {
    NORMAL("Normal"),
    WARNING("Warning"),
    CHECK_ROUTE("Check Route")
}

data class RouteDeviation(
    val distanceMeters: Float,
    val status: DeviationStatus
)

data class ImportPreviewData(
    val fileName: String,
    val fileType: String,
    val routeName: String,
    val coordinateCount: Int,
    val referencePoleCount: Int,
    val detectedProject: String,
    val detectedSegment: String,
    val coordinates: List<RouteCoordinate>,
    val referencePoles: List<ImportedPole>
)
