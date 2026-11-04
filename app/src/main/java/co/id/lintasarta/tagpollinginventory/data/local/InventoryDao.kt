package co.id.lintasarta.tagpollinginventory.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import co.id.lintasarta.tagpollinginventory.data.model.ExportFile
import co.id.lintasarta.tagpollinginventory.data.model.PoleEntity
import co.id.lintasarta.tagpollinginventory.data.model.SegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    // --- Segments (Master Data MSFO RNIMS & Alpaca PRA) ---
    @Query("SELECT * FROM segments ORDER BY isPinned DESC, id ASC")
    fun getAllSegmentsSync(): List<SegmentEntity>

    @Query("SELECT * FROM segments ORDER BY isPinned DESC, id ASC")
    fun getAllSegmentsFlow(): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE id = :id LIMIT 1")
    suspend fun getSegmentById(id: String): SegmentEntity?

    @Query("SELECT * FROM segments WHERE id = :id LIMIT 1")
    fun getSegmentByIdFlow(id: String): Flow<SegmentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: SegmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Update
    suspend fun updateSegment(segment: SegmentEntity)

    @Query("UPDATE segments SET isPinned = :isPinned WHERE id = :id")
    suspend fun updateSegmentPin(id: String, isPinned: Boolean)

    @Query("UPDATE segments SET status = :status WHERE id = :id")
    suspend fun updateSegmentStatus(id: String, status: String)

    @Query("DELETE FROM segments WHERE id IN (:segmentIds)")
    suspend fun deleteSegments(segmentIds: List<String>)

    @Query("DELETE FROM segments")
    suspend fun clearSegments()

    // --- Poles (Quick Tagging Hasil Lapangan & Alpaca PRA) ---
    @Query("SELECT * FROM poles ORDER BY sequence ASC")
    fun getAllPolesSync(): List<PoleEntity>

    @Query("SELECT * FROM poles ORDER BY sequence ASC")
    fun getAllPolesFlow(): Flow<List<PoleEntity>>

    @Query("SELECT * FROM poles WHERE segmentId = :segmentId ORDER BY sequence ASC")
    fun getPolesBySegmentFlow(segmentId: String): Flow<List<PoleEntity>>

    @Query("SELECT * FROM poles WHERE segmentId = :segmentId ORDER BY sequence ASC")
    fun getPolesBySegmentSync(segmentId: String): List<PoleEntity>

    @Query("SELECT * FROM poles WHERE id = :id LIMIT 1")
    suspend fun getPoleById(id: String): PoleEntity?

    @Query("SELECT * FROM poles WHERE assetUid = :assetUid LIMIT 1")
    suspend fun getPoleByAssetUid(assetUid: String): PoleEntity?

    @Query("SELECT COUNT(*) FROM poles WHERE assetClass = 'pole_7'")
    suspend fun countPolesP7(): Int

    @Query("SELECT COUNT(*) FROM poles WHERE assetClass = 'pole_9'")
    suspend fun countPolesP9(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPole(pole: PoleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoles(poles: List<PoleEntity>)

    @Update
    suspend fun updatePole(pole: PoleEntity)

    @Delete
    suspend fun deletePole(pole: PoleEntity)

    @Query("UPDATE poles SET syncStatus = :syncStatus WHERE id = :id")
    suspend fun updatePoleSyncStatus(id: String, syncStatus: String)

    @Query("DELETE FROM poles WHERE segmentId IN (:segmentIds)")
    suspend fun deletePolesBySegmentIds(segmentIds: List<String>)

    @Query("DELETE FROM poles WHERE id = :id")
    suspend fun deletePoleById(id: String)

    @Query("DELETE FROM poles")
    suspend fun clearPoles()

    // --- Export Files ---
    @Query("SELECT * FROM export_files ORDER BY createdAt DESC")
    fun getAllExportFilesSync(): List<ExportFile>

    @Query("SELECT * FROM export_files ORDER BY createdAt DESC")
    fun getAllExportFilesFlow(): Flow<List<ExportFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportFile(file: ExportFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportFiles(files: List<ExportFile>)

    @Query("DELETE FROM export_files WHERE id = :id")
    suspend fun deleteExportFile(id: String)

    @Query("DELETE FROM export_files")
    suspend fun clearExportFiles()
}
