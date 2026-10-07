package co.id.lintasarta.tagpollinginventory.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos

class MapDownloaderService(private val context: Context) {

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _progressPercent = MutableStateFlow(0f)
    val progressPercent: StateFlow<Float> = _progressPercent.asStateFlow()

    private val _downloadStatus = MutableStateFlow("")
    val downloadStatus: StateFlow<String> = _downloadStatus.asStateFlow()

    // 10 Km Radius roughly translates to 0.09 degrees in Latitude.
    // Longitude depends on the latitude, but we'll approximate.
    private fun getBoundingBoxFromCenter(lat: Double, lng: Double, radiusKm: Double): BoundingBox {
        val latRadian = Math.toRadians(lat)
        
        // 1 degree latitude is approx 111.32 km
        val latOffset = radiusKm / 111.32
        
        // 1 degree longitude is approx 111.32 * cos(latitude) km
        val lngOffset = radiusKm / (111.32 * cos(latRadian))
        
        return BoundingBox(
            lat + latOffset, // North
            lng + lngOffset, // East
            lat - latOffset, // South
            lng - lngOffset  // West
        )
    }

    // Helper to calculate total tiles
    private fun getTileNumber(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 1 shl zoom
        val x = ((lon + 180) / 360 * n).toInt()
        val latRad = Math.toRadians(lat)
        val y = ((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * n).toInt()
        return Pair(x, y)
    }

    suspend fun downloadMapArea(centerLat: Double, centerLng: Double, radiusKm: Double = 10.0) {
        if (_isDownloading.value) return
        
        _isDownloading.value = true
        _progressPercent.value = 0f
        _downloadStatus.value = "Kalkulasi wilayah..."

        withContext(Dispatchers.IO) {
            try {
                val bbox = getBoundingBoxFromCenter(centerLat, centerLng, radiusKm)
                val zoomMin = 10
                val zoomMax = 18
                
                val tileSource = TileSourceFactory.MAPNIK
                val writer = SqlTileWriter()

                // 1. Hitung total tiles
                var totalTiles = 0
                val tilesToDownload = mutableListOf<Triple<Int, Int, Int>>() // Zoom, X, Y

                for (zoom in zoomMin..zoomMax) {
                    val nw = getTileNumber(bbox.latNorth, bbox.lonWest, zoom)
                    val se = getTileNumber(bbox.latSouth, bbox.lonEast, zoom)
                    
                    val xMin = minOf(nw.first, se.first)
                    val xMax = maxOf(nw.first, se.first)
                    val yMin = minOf(nw.second, se.second)
                    val yMax = maxOf(nw.second, se.second)
                    
                    for (x in xMin..xMax) {
                        for (y in yMin..yMax) {
                            tilesToDownload.add(Triple(zoom, x, y))
                        }
                    }
                }
                
                totalTiles = tilesToDownload.size
                
                // Jika total > 15,000, potong agar memori tak terlalu penuh
                val maxLimit = 15000
                val safeTiles = if (totalTiles > maxLimit) {
                    _downloadStatus.value = "Terlalu banyak tiles ($totalTiles). Membatasi ke $maxLimit..."
                    tilesToDownload.take(maxLimit)
                } else {
                    tilesToDownload
                }
                
                val finalTotal = safeTiles.size
                var downloaded = 0
                var failed = 0
                
                _downloadStatus.value = "Mengunduh 0 dari $finalTotal tiles..."

                // 2. Download Tiles
                for (tile in safeTiles) {
                    if (!_isDownloading.value) break // Handle Cancellation

                    val (z, x, y) = tile
                    
                    // Check if already in cache
                    val p = MapTileIndex.getTileIndex(z, x, y)
                    if (writer.exists(tileSource, p)) {
                        downloaded++
                        updateProgress(downloaded, finalTotal)
                        continue
                    }

                    // Download the tile
                    val urlString = tileSource.getTileURLString(p)
                    try {
                        val url = URL(urlString)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.setRequestProperty("User-Agent", "TagPollingInventory/1.0")
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        
                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            val inputStream: InputStream = connection.inputStream
                            writer.saveFile(tileSource, p, inputStream, null)
                            downloaded++
                        } else {
                            failed++
                        }
                        connection.disconnect()
                    } catch (e: Exception) {
                        failed++
                    }

                    updateProgress(downloaded, finalTotal)
                    
                    // Sleep sedikit agar tidak diban oleh server OSM
                    delay(50)
                }

                if (_isDownloading.value) {
                    _downloadStatus.value = "Selesai! $downloaded berhasil, $failed gagal."
                    _progressPercent.value = 1f
                } else {
                    _downloadStatus.value = "Dibatalkan oleh pengguna."
                }

            } catch (e: Exception) {
                Log.e("MapDownloader", "Gagal unduh", e)
                _downloadStatus.value = "Gagal: ${e.message}"
            } finally {
                _isDownloading.value = false
            }
        }
    }

    private fun updateProgress(current: Int, total: Int) {
        val pct = current.toFloat() / total.toFloat()
        _progressPercent.value = pct
        _downloadStatus.value = "Mengunduh $current dari $total tiles (${(pct * 100).toInt()}%)"
    }

    fun cancelDownload() {
        _isDownloading.value = false
        _downloadStatus.value = "Membatalkan..."
    }

    suspend fun clearMapCache() {
        withContext(Dispatchers.IO) {
            try {
                val writer = SqlTileWriter()
                writer.purgeCache()
                _downloadStatus.value = "Cache peta berhasil dihapus."
            } catch (e: Exception) {
                _downloadStatus.value = "Gagal menghapus cache."
            }
        }
    }

    suspend fun getCacheSizeMB(): Double {
        return withContext(Dispatchers.IO) {
            try {
                // OsmDroid defaults to using sqlite db files inside osmdroid basePath or standard storage
                val basePath = File(context.filesDir, "osmdroid")
                val tilePath = File(basePath, "tiles")
                
                var totalBytes = 0L
                if (tilePath.exists() && tilePath.isDirectory) {
                    val files = tilePath.listFiles()
                    if (files != null) {
                        for (file in files) {
                            totalBytes += getFolderSize(file)
                        }
                    }
                }
                
                totalBytes / (1024.0 * 1024.0)
            } catch (e: Exception) {
                0.0
            }
        }
    }

    private fun getFolderSize(folder: File): Long {
        var length: Long = 0
        if (folder.isFile) {
            length = folder.length()
        } else {
            val files = folder.listFiles()
            if (files != null) {
                for (file in files) {
                    length += getFolderSize(file)
                }
            }
        }
        return length
    }
}
