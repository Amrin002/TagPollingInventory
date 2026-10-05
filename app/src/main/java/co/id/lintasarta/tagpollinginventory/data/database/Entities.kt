package co.id.lintasarta.tagpollinginventory.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "poles")
data class PoleEntity(
    @PrimaryKey
    val id: String,
    val segmentId: String,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val capturedTimestamp: String,
    val type: String,
    val condition: String,
    val ownership: String,
    val height: String,
    val tagNumber: String,
    val hasFoCable: Boolean,
    val cableCondition: String,
    val equipment: String, // Stored as comma-separated string
    val notes: String,
    val photoPath: String?,
    val status: String
)

@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val route: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val status: String,
    val totalPoles: Int,
    val completedPoles: Int,
    val conflictPoles: Int
)
