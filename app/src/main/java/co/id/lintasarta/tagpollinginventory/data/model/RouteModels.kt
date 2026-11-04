package co.id.lintasarta.tagpollinginventory.data.model

import com.google.gson.annotations.SerializedName

data class RouteCoordinate(
    val longitude: Double,
    val latitude: Double,
    val altitude: Double = 0.0
)

/**
 * Model Aset Tiang Eksisting dari API Dashboard Alpaca:
 * GET /api/otdr/asset-assurance/map?po_id={poId}
 */
data class AlpacaPraAssetDto(
    @SerializedName(value = "asset_uid", alternate = ["assetUid", "id", "pole_code"])
    val asset_uid: String,
    @SerializedName(value = "asset_class", alternate = ["assetClass", "pole_class", "type"])
    val asset_class: String = "pole_9", // "pole_7" atau "pole_9"
    @SerializedName(value = "asset_type", alternate = ["assetType", "material"])
    val asset_type: String? = "steel", // "concrete" atau "steel"
    @SerializedName(value = "po_id", alternate = ["poId", "segment_id", "segmentId"])
    val po_id: String? = "",
    val geometry: List<Double> = emptyList(), // [lng, lat]
    @SerializedName(value = "match_status", alternate = ["matchStatus", "status"])
    val match_status: String = "MATCHED",
    val anomaly_reason: String? = null
) {
    val longitude: Double
        get() = if (geometry.size >= 2) geometry[0] else 0.0

    val latitude: Double
        get() = if (geometry.size >= 2) geometry[1] else 0.0
}

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
