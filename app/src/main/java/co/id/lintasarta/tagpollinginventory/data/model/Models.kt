package co.id.lintasarta.tagpollinginventory.data.model

enum class PoleType(val displayName: String) {
    CONCRETE("Concrete"),
    STEEL("Steel"),
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
    CUSTOMER("Customer"),
    UNKNOWN("Unknown")
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

data class Pole(
    val id: String,
    val segmentId: String,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 2.8f,
    val capturedTimestamp: String = "",
    val type: PoleType = PoleType.CONCRETE,
    val condition: PoleCondition = PoleCondition.GOOD,
    val ownership: PoleOwnership = PoleOwnership.LINTASARTA,
    val height: String = "9m",
    val tagNumber: String = "",
    val hasFoCable: Boolean = true,
    val cableCondition: CableCondition = CableCondition.GOOD,
    val equipment: Set<String> = setOf("ODP", "Closure"),
    val notes: String = "",
    val photoPath: String? = null,
    val status: TagStatus = TagStatus.NOT_TAGGED
)

data class Segment(
    val id: String,
    val name: String,
    val route: String,
    val startPoint: Pair<Double, Double>,
    val endPoint: Pair<Double, Double>,
    val status: SegmentStatus,
    val totalPoles: Int,
    val completedPoles: Int,
    val conflictPoles: Int
)

data class Project(
    val id: String,
    val name: String,
    val location: String,
    val totalPoles: Int,
    val completedPoles: Int,
    val conflictPoles: Int,
    val uncompletedPoles: Int,
    val segments: List<Segment>
)

data class ExportFile(
    val id: String,
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
