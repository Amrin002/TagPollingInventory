package co.id.lintasarta.tagpollinginventory.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * Klasifikasi Tiang Resmi Project Alpaca PRA (Poles Rental Agreement):
 * pole_7 (T7, 7 meter) dan pole_9 (T9, 9 meter).
 */
enum class AssetClass(val code: String, val displayName: String, val defaultHeight: String) {
    POLE_7("pole_7", "Tiang 7M (T7)", "7m"),
    POLE_9("pole_9", "Tiang 9M (T9)", "9m");

    companion object {
        fun fromCode(code: String?): AssetClass {
            return when (code?.lowercase()?.trim()) {
                "pole_7", "t7", "7m", "7" -> POLE_7
                else -> POLE_9 // Default Alpaca PRA adalah pole_9 (9m)
            }
        }
    }
}

/**
 * Status Penyelarasan Aset Lapangan vs Master Dashboard Alpaca PRA
 */
enum class MatchStatus(val code: String, val displayName: String) {
    MATCHED("MATCHED", "Matched"),
    ANOMALY("ANOMALY", "Anomaly"),
    UNMATCHED("UNMATCHED", "Unmatched"),
    PROVISIONAL("PROVISIONAL", "Provisional New");

    companion object {
        fun fromCode(code: String?): MatchStatus {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: MATCHED
        }
    }
}

enum class PoleType(val displayName: String) {
    STEEL("Steel"),
    CONCRETE("Concrete"),
    WOODEN("Wooden"),
    OTHER("Other")
}

enum class PoleCondition(val displayName: String) {
    GOOD("Good"),
    FAIR("Fair"),
    DAMAGED("Damaged"),
    CRITICAL("Critical")
}

enum class PoleOwnership(val displayName: String) {
    LINTASARTA("Lintasarta"),
    PLN("PLN"),
    TELKOM("Telkom"),
    CUSTOMER("Customer")
}

enum class CableCondition(val displayName: String) {
    GOOD("Good"),
    DAMAGED("Damaged"),
    SAGGING("Sagging"),
    UNKNOWN("Unknown")
}

enum class TagStatus(val displayName: String) {
    NOT_TAGGED("Not Tagged"),
    COMPLETED("Completed"),
    CONFLICT("Conflict")
}

enum class SegmentStatus(val displayName: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

enum class ExportFormat(val displayName: String, val extension: String) {
    CSV("CSV", "csv"),
    KML("KML", "kml"),
    KMZ("KMZ", "kmz")
}

/**
 * Entity SegmentEntity: Menyimpan data master segmen FO dari RNIMS (Area 18 MPO)
 * dan Project Alpaca PRA.
 */
@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lengthMeters: Double = 0.0,
    val routeCoordinates: List<RouteCoordinate> = emptyList(),
    val areaId: Int = 18,
    val orgSite: String = "",
    val dstSite: String = "",
    val orgCity: String = "",
    val dstCity: String = "",
    val cityFilterKey: String = "", // "Timika", "Sorong", "Jayapura", "Ambon", "Merauke", dll.
    val route: String = "",
    val projectId: String = "PRJ-MPO-18",
    val poId: String = "", // Purchase Order Alpaca PRA
    val isAlpacaProject: Boolean = true, // Flag Project Alpaca PRA
    val priorityRouteKey: String = "", // Misal: "SENTANI_ABEPURA", "MANOKWARI", "TIMIKA", "SORONG", "DOBO", "TIAKUR"
    val description: String = "",
    val startPoint: Pair<Double, Double> = Pair(0.0, 0.0),
    val endPoint: Pair<Double, Double> = Pair(0.0, 0.0),
    val referenceRouteFileName: String? = null,
    val status: SegmentStatus = SegmentStatus.NOT_STARTED,
    val totalPoles: Int = 0,
    val completedPoles: Int = 0,
    val conflictPoles: Int = 0,
    val isPinned: Boolean = false,
    val cityCode: String = "",
    val locationCode: String = "",
    val currentSequence: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
)

typealias Segment = SegmentEntity

/**
 * Entity PoleEntity: Menyimpan data tiang hasil survei lapangan
 * mengadopsi standar Alpaca PRA & POLE_INVENTORY_ARCHITECTURE.md (v2.3).
 *
 * Standar Alpaca:
 * - assetUid: POL-MPO-[KOTA]-[SEQ:04d]
 * - assetClass: pole_7 / pole_9
 * - matchStatus: MATCHED / ANOMALY / UNMATCHED / PROVISIONAL
 *
 * Standar Field Architecture v2.3:
 * - rawLatitude / rawLongitude: titik fix GNSS saat Validate Position di dasar tiang
 * - latitude / longitude: titik koordinat terverifikasi (averaged centroid)
 * - isSafetyDeferred: opsi penanda tiang terhalang bahaya (Approach Safety Deferral)
 */
@Entity(tableName = "poles")
data class PoleEntity(
    @PrimaryKey val id: String,
    val segmentId: String,
    val sequence: Int = 1,
    val assetUid: String = "", // POL-MPO-[KOTA]-[SEQ:04d]
    val poleCode: String = "", // Kompatibel: disamakan dengan assetUid
    val routeName: String = "", // Nama rute kabel FO
    val poId: String = "", // Purchase Order Alpaca
    val assetClass: String = "pole_9", // "pole_7" atau "pole_9"
    val assetType: String = "steel", // "steel" atau "concrete"
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 2.5f,
    val rawLatitude: Double? = null,
    val rawLongitude: Double? = null,
    val photoPath: String? = null,
    val photoSha256: String? = null,
    val type: PoleType = PoleType.STEEL,
    val condition: PoleCondition = PoleCondition.GOOD,
    val ownership: PoleOwnership = PoleOwnership.LINTASARTA,
    val syncStatus: String = "PENDING", // PENDING, SYNCED, DRAFT
    val capturedTimestamp: String = "",
    val timestamp: String = "",
    val height: String = "9m",
    val tagNumber: String = "",
    val hasFoCable: Boolean = true,
    val cableCondition: CableCondition = CableCondition.GOOD,
    val equipment: Set<String> = setOf("ODP", "Closure"),
    val notes: String = "",
    val isSafetyDeferred: Boolean = false, // Evaluasi keselamatan / tiang terhalang
    val safetyDeferralReason: String? = null,
    val matchStatus: String = "MATCHED", // MATCHED, ANOMALY, UNMATCHED, PROVISIONAL
    val isExistingPole: Boolean = false,
    val isLocationLocked: Boolean = false,
    val originalLatitude: Double? = null,
    val originalLongitude: Double? = null,
    val photoPaths: List<String> = if (!photoPath.isNullOrEmpty()) listOf(photoPath) else emptyList(),
    val status: TagStatus = if (isSafetyDeferred) {
        TagStatus.CONFLICT
    } else if (syncStatus == "SYNCED" || isExistingPole) {
        TagStatus.COMPLETED
    } else if (condition == PoleCondition.CRITICAL || condition == PoleCondition.DAMAGED) {
        TagStatus.CONFLICT
    } else if (latitude != 0.0 && !photoPath.isNullOrEmpty()) {
        TagStatus.COMPLETED
    } else {
        TagStatus.NOT_TAGGED
    }
) {
    val effectiveAssetUid: String
        get() = if (assetUid.isNotBlank()) assetUid else if (poleCode.isNotBlank()) poleCode else id

    companion object {
        fun formatAlpacaAssetUid(cityCode: String, sequence: Int): String {
            val cleanCity = cityCode.trim().uppercase().take(3).ifEmpty { "TMK" }
            return "POL-MPO-$cleanCity-${String.format(Locale.US, "%04d", sequence)}"
        }
    }
}

typealias Pole = PoleEntity

data class Project(
    val id: String = "PRJ-MPO-ALPACA",
    val name: String = "Alpaca PRA - Regional Fiber Optic Survey",
    val location: String = "Area 18 Maluku Papua Operation",
    val totalPoles: Int = 0,
    val completedPoles: Int = 0,
    val conflictPoles: Int = 0,
    val uncompletedPoles: Int = 0,
    val totalP7: Int = 0,
    val totalP9: Int = 0,
    val segments: List<SegmentEntity> = emptyList()
)

@Entity(tableName = "export_files")
data class ExportFile(
    @PrimaryKey val id: String,
    val fileName: String,
    val format: ExportFormat,
    val sizeBytes: Long,
    val recordCount: Int,
    val createdAt: String,
    val filePath: String,
    val segmentName: String
)

enum class ExportScope(val displayName: String) {
    CURRENT_SEGMENT("Current Segment"),
    CURRENT_PROJECT("Current Project"),
    ALL_LOCAL_DATA("All Local Data")
}

data class ExportOptions(
    val scope: ExportScope = ExportScope.CURRENT_SEGMENT,
    val includeCompleted: Boolean = true,
    val includeConflict: Boolean = true,
    val includeIncomplete: Boolean = false,
    val includeCoordinates: Boolean = true,
    val includeAttributes: Boolean = true,
    val includeTimestamp: Boolean = true,
    val includeNotes: Boolean = true,
    val includePhotos: Boolean = true,
    val includePhotoReferences: Boolean = true
)
