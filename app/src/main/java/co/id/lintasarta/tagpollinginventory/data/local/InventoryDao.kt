package co.id.lintasarta.tagpollinginventory.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import co.id.lintasarta.tagpollinginventory.data.model.ExportFile
import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.data.model.Project
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    // --- Projects ---
    @Query("SELECT * FROM projects LIMIT 1")
    fun getProjectSync(): Project?
    
    @Query("SELECT * FROM projects LIMIT 1")
    fun getProjectFlow(): Flow<Project?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    // --- Segments ---
    @Query("SELECT * FROM segments")
    fun getAllSegmentsSync(): List<Segment>

    @Query("SELECT * FROM segments")
    fun getAllSegmentsFlow(): Flow<List<Segment>>

    @Query("SELECT * FROM segments WHERE id = :id LIMIT 1")
    suspend fun getSegmentById(id: String): Segment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: Segment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<Segment>)

    @Update
    suspend fun updateSegment(segment: Segment)

    @Query("UPDATE segments SET status = :status WHERE id = :id")
    suspend fun updateSegmentStatus(id: String, status: String)

    // --- Poles ---
    @Query("SELECT * FROM poles")
    fun getAllPolesSync(): List<Pole>
    
    @Query("SELECT * FROM poles")
    fun getAllPolesFlow(): Flow<List<Pole>>

    @Query("SELECT * FROM poles WHERE id = :id LIMIT 1")
    suspend fun getPoleById(id: String): Pole?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPole(pole: Pole)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoles(poles: List<Pole>)

    @Update
    suspend fun updatePole(pole: Pole)

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

    // --- Danger Zone ---
    @Query("DELETE FROM projects")
    suspend fun clearProjects()

    @Query("DELETE FROM segments")
    suspend fun clearSegments()

    @Query("DELETE FROM poles")
    suspend fun clearPoles()

    @Query("DELETE FROM export_files")
    suspend fun clearExportFiles()
}
