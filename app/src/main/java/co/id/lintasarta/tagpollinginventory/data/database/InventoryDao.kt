package co.id.lintasarta.tagpollinginventory.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM poles")
    fun getAllPoles(): Flow<List<PoleEntity>>

    @Query("SELECT * FROM poles WHERE segmentId = :segmentId")
    fun getPolesBySegment(segmentId: String): Flow<List<PoleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPole(pole: PoleEntity)

    @Update
    suspend fun updatePole(pole: PoleEntity)

    @Delete
    suspend fun deletePole(pole: PoleEntity)

    @Query("SELECT * FROM segments")
    fun getAllSegments(): Flow<List<SegmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: SegmentEntity)
}
