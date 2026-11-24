package co.id.lintasarta.tagpollinginventory.data.model

import com.google.gson.annotations.SerializedName

data class RouteCoordinate(
    val longitude: Double,
    val latitude: Double,
    val altitude: Double = 0.0
)

/**
 * DTO Geometri Titik Tiang: Mendukung objek GeoJSON Point {"type": "Point", "coordinates": [lng, lat]}
 * secara polimorfik dan defensif.
 */
data class AlpacaAssetGeometryDto(
    @SerializedName("type")
    val type: String? = "Point",
    @SerializedName("coordinates")
    val coordinates: List<Double>? = emptyList()
) {
    val longitude: Double get() = coordinates?.getOrNull(0) ?: 0.0
    val latitude: Double get() = coordinates?.getOrNull(1) ?: 0.0
}

/**
 * Model Aset Tiang Eksisting dari API Dashboard Alpaca:
 * GET /api/otdr/asset-assurance/map?po_id={poId}
 */
data class AlpacaAssetItemDto(
    @SerializedName(value = "asset_uid", alternate = ["assetUid", "id", "pole_code"])
    val assetUid: String = "",
    @SerializedName(value = "feature_name", alternate = ["featureName", "name"])
    val featureName: String? = null,
    @SerializedName(value = "asset_type", alternate = ["assetType", "material"])
    val assetType: String? = "pole",
    @SerializedName(value = "asset_class", alternate = ["assetClass", "pole_class"])
    val assetClass: String = "pole_9", // "pole_7" atau "pole_9"
    @SerializedName(value = "source_proximity")
    val sourceProximity: String? = null,
    @SerializedName(value = "classification_confidence")
    val classificationConfidence: String? = null,
    @SerializedName(value = "match_status", alternate = ["matchStatus", "status"])
    val matchStatus: String = "MATCHED",
    @SerializedName(value = "matched_po_id", alternate = ["matchedPoId", "po_id", "poId"])
    val matchedPoId: String? = null,
    @SerializedName(value = "matched_po_distance_m", alternate = ["matchedPoDistanceM"])
    val matchedPoDistanceM: String? = null,
    @SerializedName(value = "geometry")
    val geometry: AlpacaAssetGeometryDto? = null,
    @SerializedName(value = "anomaly_reason")
    val anomalyReason: String? = null
) {
    // Properti Kompatibilitas untuk Kode Lama
    val asset_uid: String get() = assetUid
    val asset_class: String get() = assetClass
    val asset_type: String? get() = assetType
    val match_status: String get() = matchStatus
    val po_id: String? get() = matchedPoId
    val longitude: Double get() = geometry?.longitude ?: 0.0
    val latitude: Double get() = geometry?.latitude ?: 0.0
}

typealias AlpacaPraAssetDto = AlpacaAssetItemDto

/**
 * Geometri Rute IFT: MultiLineString berisi list garis, masing-masing garis memiliki koordinat [lng, lat].
 */
data class AlpacaIftGeometryDto(
    @SerializedName("type")
    val type: String? = "MultiLineString",
    @SerializedName("coordinates")
    val coordinates: List<List<List<Double>>>? = emptyList()
) {
    fun toFlatRouteCoordinates(): List<RouteCoordinate> {
        val result = mutableListOf<RouteCoordinate>()
        coordinates?.forEach { line ->
            line.forEach { pt ->
                if (pt.size >= 2) {
                    result.add(RouteCoordinate(longitude = pt[0], latitude = pt[1]))
                }
            }
        }
        return result
    }
}

/**
 * Metadata Rute Segmen IFT Alpaca PRA.
 */
data class AlpacaIftDto(
    @SerializedName(value = "po_id", alternate = ["poId"])
    val poId: String = "",
    @SerializedName(value = "po_name", alternate = ["poName", "name"])
    val poName: String = "",
    @SerializedName(value = "area")
    val area: String? = "MPO",
    @SerializedName(value = "region")
    val region: String? = "EIR",
    @SerializedName(value = "length_po", alternate = ["lengthPo", "length_m"])
    val lengthPo: Double = 0.0,
    @SerializedName(value = "geometry")
    val geometry: AlpacaIftGeometryDto? = null
)

/**
 * Respon Lengkap Endpoint /api/otdr/asset-assurance/map?po_id={poId}
 */
data class AlpacaMapResponseDto(
    val run: Map<String, Any?>? = null,
    val ift: AlpacaIftDto? = null,
    val assets: List<AlpacaAssetItemDto> = emptyList()
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
