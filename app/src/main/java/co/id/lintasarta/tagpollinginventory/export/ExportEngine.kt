package co.id.lintasarta.tagpollinginventory.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import co.id.lintasarta.tagpollinginventory.data.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class KmzUploadBatch(
    val file: File,
    val poles: List<Pole>,
    val partIndex: Int,
    val totalParts: Int
)

class ExportEngine(
    private val context: Context? = null,
    customExportDir: File? = null
) {

    companion object {
        const val DEFAULT_MAX_GDRIVE_KMZ_BYTES = 5L * 1024L * 1024L // 5 MB aman untuk Apps Script & seluler
        const val DEFAULT_KMZ_PHOTO_MAX_DIM_PX = 1440
        const val COMPACT_KMZ_PHOTO_MAX_DIM_PX = 1024
        const val DEFAULT_KMZ_PHOTO_JPEG_QUALITY = 78
        const val COMPACT_KMZ_PHOTO_JPEG_QUALITY = 72
        const val COMPACT_PHOTO_COUNT_THRESHOLD = 40
        private const val PHOTO_PASSTHROUGH_BYTES = 180_000L
    }

    private val exportDir: File by lazy {
        (customExportDir ?: File(context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "."), "exports"))
            .apply { if (!exists()) mkdirs() }
    }

    /**
     * Menghasilkan Ekspor File Resmi Alpaca Master CSV: Alpaca_Master_Poles.csv
     * Kompatibel dengan controller eNims dan script backend Alpaca_kmz.py.
     */
    fun generateAlpacaMasterCsv(poles: List<Pole>, targetFile: File? = null, surveyorInfo: String? = null): File {
        val file = targetFile ?: File(exportDir, "Alpaca_Master_Poles.csv")
        val sb = StringBuilder()
        sb.append("asset_uid,segment_id,route_name,po_id,asset_class,asset_type,latitude,longitude,raw_latitude,raw_longitude,accuracy,photo_filename,ownership,condition,is_safety_deferred,match_status,missing_reason,is_existing_pole,sync_status,timestamp,photo_sha256,equipment,tagged_by\n")

        for (pole in poles) {
            val photoFileName = if (!pole.photoPath.isNullOrEmpty()) {
                File(pole.photoPath).name
            } else {
                ""
            }
            val rawLat = pole.rawLatitude ?: pole.latitude
            val rawLng = pole.rawLongitude ?: pole.longitude

            sb.append("\"${pole.effectiveAssetUid}\",")
            sb.append("\"${pole.segmentId}\",")
            sb.append("\"${pole.routeName.replace("\"", "'")}\",")
            sb.append("\"${pole.poId}\",")
            sb.append("\"${pole.assetClass}\",")
            sb.append("\"${pole.assetType}\",")
            sb.append("${pole.latitude},")
            sb.append("${pole.longitude},")
            sb.append("$rawLat,")
            sb.append("$rawLng,")
            sb.append("${pole.accuracy},")
            sb.append("\"$photoFileName\",")
            sb.append("\"${pole.ownership.displayName}\",")
            sb.append("\"${pole.condition.displayName}\",")
            sb.append("${pole.isSafetyDeferred},")
            sb.append("\"${pole.matchStatus}\",")
            sb.append("\"${pole.missingReason ?: ""}\",")
            sb.append("${pole.isExistingPole},")
            sb.append("\"${pole.syncStatus}\",")
            sb.append("\"${pole.capturedTimestamp}\",")
            val eqStr = pole.equipment.filter { it.isNotBlank() }.joinToString("; ").replace("\"", "'")
            sb.append("\"${pole.photoSha256 ?: ""}\",")
            sb.append("\"$eqStr\",")
            sb.append("\"${(surveyorInfo ?: "").replace("\"", "'")}\"\n")
        }

        file.writeText(sb.toString())
        return file
    }

    /**
     * Menghasilkan Ekspor File Resmi Alpaca Master KMZ: Alpaca_Master.kmz
     * Berisi placemarks T7/T9, rute polyline kabel FO, ikon legenda, dan bundle foto tiang di folder images/.
     * Foto berukuran besar otomatis dioptimasi di dalam KMZ tanpa mengubah file asli di penyimpanan HP.
     */
    fun generateAlpacaMasterKmz(
        poles: List<Pole>,
        segmentName: String,
        importedRoute: ImportedRoute? = null,
        surveyorInfo: String? = null,
        targetFile: File? = null,
        optimizePhotosForKmz: Boolean = true,
        maxPhotoDimensionPx: Int = DEFAULT_KMZ_PHOTO_MAX_DIM_PX,
        photoJpegQuality: Int = DEFAULT_KMZ_PHOTO_JPEG_QUALITY
    ): File {
        val file = targetFile ?: File(exportDir, "Alpaca_Master.kmz")
        val kmlContent = generateAlpacaKmlContent(
            poles = poles,
            segmentName = segmentName,
            importedRoute = importedRoute,
            surveyorInfo = surveyorInfo,
            useBundledIcons = true
        )

        val uniquePhotoFiles = poles.mapNotNull { pole ->
            val path = pole.photoPath
            if (path.isNullOrBlank()) null else File(path).takeIf { it.exists() && it.length() > 0L }
        }.distinctBy { it.name }

        val useCompactPreset = uniquePhotoFiles.size > COMPACT_PHOTO_COUNT_THRESHOLD
        val effectiveMaxDim = if (useCompactPreset) minOf(maxPhotoDimensionPx, COMPACT_KMZ_PHOTO_MAX_DIM_PX) else maxPhotoDimensionPx
        val effectiveQuality = if (useCompactPreset) minOf(photoJpegQuality, COMPACT_KMZ_PHOTO_JPEG_QUALITY) else photoJpegQuality

        ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
            // 1. Masukkan file KML utama doc.kml
            val kmlEntry = ZipEntry("doc.kml")
            zip.putNextEntry(kmlEntry)
            zip.write(kmlContent.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 2. Masukkan seluruh ikon legenda PNG ke folder icons/ (offline-ready di Google Earth)
            for ((iconPath, pngBytes) in KmzIconGenerator.generateAllLegendIcons()) {
                val iconEntry = ZipEntry(iconPath)
                zip.putNextEntry(iconEntry)
                zip.write(pngBytes)
                zip.closeEntry()
            }

            // 3. Masukkan file foto aktual tiang ke dalam folder images/ (stream aman-memori & teroptimasi)
            for (photoSrc in uniquePhotoFiles) {
                val photoEntry = ZipEntry("images/${photoSrc.name}")
                zip.putNextEntry(photoEntry)
                writePhotoEntryToZip(
                    zip = zip,
                    photoSrc = photoSrc,
                    optimize = optimizePhotosForKmz,
                    maxDimPx = effectiveMaxDim,
                    jpegQuality = effectiveQuality
                )
                zip.closeEntry()
            }
        }
        return file
    }

    /**
     * Membangun paket KMZ untuk upload ke Google Drive.
     * Jika seluruh survei muat dalam <= maxBatchBytes (default 5 MB), dikemas menjadi 1 file KMZ tunggal.
     * Jika sangat banyak tiang hingga melebihi maxBatchBytes, otomatis dipecah menjadi beberapa bagian berurutan.
     */
    fun buildGDriveKmzBatches(
        poles: List<Pole>,
        segmentName: String,
        importedRoute: ImportedRoute? = null,
        surveyorInfo: String? = null,
        workDir: File = exportDir,
        baseFileNamePrefix: String,
        maxBatchBytes: Long = DEFAULT_MAX_GDRIVE_KMZ_BYTES
    ): List<KmzUploadBatch> {
        require(poles.isNotEmpty()) { "Daftar tiang tidak boleh kosong" }
        if (!workDir.exists()) workDir.mkdirs()

        val singleFile = File(workDir, "$baseFileNamePrefix.kmz")
        generateAlpacaMasterKmz(
            poles = poles,
            segmentName = segmentName,
            importedRoute = importedRoute,
            surveyorInfo = surveyorInfo,
            targetFile = singleFile,
            optimizePhotosForKmz = true
        )

        val singleSize = singleFile.length()
        if (singleSize <= maxBatchBytes || poles.size <= 1) {
            return listOf(KmzUploadBatch(file = singleFile, poles = poles, partIndex = 1, totalParts = 1))
        }

        // Survei sangat besar (> maxBatchBytes bahkan setelah optimasi foto): pecah otomatis per batch aman
        singleFile.delete()
        val rawPartitions = mutableListOf<Pair<File, List<Pole>>>()
        try {
            var cursor = 0
            val avgBytesPerPole = (singleSize.toDouble() / poles.size).coerceAtLeast(1.0)
            while (cursor < poles.size) {
                val remaining = poles.size - cursor
                var chunkCount = ((maxBatchBytes * 0.85) / avgBytesPerPole).toInt().coerceIn(1, remaining)

                while (true) {
                    val end = (cursor + chunkCount).coerceAtMost(poles.size)
                    val slice = poles.subList(cursor, end)
                    val tmpFile = File(workDir, "${baseFileNamePrefix}_tmp_part_${rawPartitions.size + 1}.kmz")
                    generateAlpacaMasterKmz(
                        poles = slice,
                        segmentName = segmentName,
                        importedRoute = importedRoute,
                        surveyorInfo = surveyorInfo,
                        targetFile = tmpFile,
                        optimizePhotosForKmz = true
                    )
                    if (tmpFile.length() <= maxBatchBytes || chunkCount == 1) {
                        rawPartitions.add(tmpFile to slice.toList())
                        cursor = end
                        break
                    } else {
                        tmpFile.delete()
                        chunkCount = maxOf(1, chunkCount / 2)
                    }
                }
            }

            val totalParts = rawPartitions.size
            return rawPartitions.mapIndexed { idx, (tmpFile, slice) ->
                val finalName = if (totalParts == 1) {
                    "$baseFileNamePrefix.kmz"
                } else {
                    "${baseFileNamePrefix}_Part${idx + 1}of$totalParts.kmz"
                }
                val finalFile = File(workDir, finalName)
                if (finalFile.exists()) finalFile.delete()
                val targetFile = if (tmpFile.renameTo(finalFile)) finalFile else tmpFile
                KmzUploadBatch(
                    file = targetFile,
                    poles = slice,
                    partIndex = idx + 1,
                    totalParts = totalParts
                )
            }
        } catch (t: Throwable) {
            rawPartitions.forEach { (tmpFile, _) -> tmpFile.delete() }
            throw t
        }
    }

    private fun writePhotoEntryToZip(
        zip: ZipOutputStream,
        photoSrc: File,
        optimize: Boolean,
        maxDimPx: Int,
        jpegQuality: Int
    ) {
        if (optimize && photoSrc.length() > PHOTO_PASSTHROUGH_BYTES) {
            val optimizedBytes = encodeOptimizedJpegForKmz(photoSrc, maxDimPx, jpegQuality)
            if (optimizedBytes != null && optimizedBytes.isNotEmpty() && optimizedBytes.size.toLong() < photoSrc.length()) {
                zip.write(optimizedBytes)
                return
            }
        }
        photoSrc.inputStream().buffered().use { input ->
            input.copyTo(zip, bufferSize = 16384)
        }
    }

    private fun encodeOptimizedJpegForKmz(
        photoSrc: File,
        maxDimPx: Int,
        jpegQuality: Int
    ): ByteArray? {
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photoSrc.absolutePath, boundsOpts)
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            var sampleSize = 1
            val rawMax = maxOf(rawW, rawH)
            while (rawMax / (sampleSize * 2) >= maxDimPx) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decoded = BitmapFactory.decodeFile(photoSrc.absolutePath, decodeOpts) ?: return null

            val orientation = try {
                ExifInterface(photoSrc.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_NORMAL
            }

            oriented = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(decoded, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(decoded, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(decoded, 270f)
                else -> decoded
            } ?: decoded

            val curW = oriented.width
            val curH = oriented.height
            scaled = if (curW > maxDimPx || curH > maxDimPx) {
                val ratio = minOf(maxDimPx.toFloat() / curW, maxDimPx.toFloat() / curH)
                val targetW = (curW * ratio).toInt().coerceAtLeast(1)
                val targetH = (curH * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
            } else {
                oriented
            }

            val baos = ByteArrayOutputStream(131072)
            val compressedOk = scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(50, 95), baos)
            if (compressedOk) baos.toByteArray() else null
        } catch (_: Throwable) {
            null
        } finally {
            if (scaled != null && scaled !== oriented && scaled !== decoded) scaled.recycle()
            if (oriented != null && oriented !== decoded) oriented.recycle()
            decoded?.recycle()
        }
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap? {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun escapeXml(input: String?): String {
        if (input == null) return ""
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    internal fun generateAlpacaKmlContent(
        poles: List<Pole>,
        segmentName: String,
        importedRoute: ImportedRoute?,
        surveyorInfo: String? = null,
        useBundledIcons: Boolean = false
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>Alpaca Master Poles - ${escapeXml(segmentName)}</name>\n")
        sb.append("    <description>Project Alpaca PRA Survey &amp; As-Built Poles (Regional MPO)</description>\n\n")

        // Definisi Style Legenda: T7, T9, JB, Splitter, Slakkan, Kombinasi, Semuanya, Safety, Missing
        for (spec in KmzIconGenerator.allStyleSpecs) {
            sb.append("    <Style id=\"${spec.styleId}\">\n")
            sb.append("      <IconStyle>\n")
            if (!useBundledIcons) {
                sb.append("        <color>${spec.category.kmlAbgrColor}</color>\n")
            }
            sb.append("        <scale>${spec.category.kmlScale}</scale>\n")
            val href = if (useBundledIcons) spec.iconRelativePath else spec.category.fallbackKmlHref
            sb.append("        <Icon><href>$href</href></Icon>\n")
            sb.append("      </IconStyle>\n")
            sb.append("    </Style>\n")
        }
        sb.append("\n")

        sb.append("    <Style id=\"fiberRouteLine\">\n")
        sb.append("      <LineStyle>\n")
        sb.append("        <color>ffc06515</color>\n") // Telecom Blue
        sb.append("        <width>5.0</width>\n")
        sb.append("      </LineStyle>\n")
        sb.append("    </Style>\n\n")

        // 1. Garis Polyline Jalur Fiber Optik
        if (importedRoute != null && importedRoute.coordinates.isNotEmpty()) {
            sb.append("    <Folder>\n")
            sb.append("      <name>Rute Kabel FO</name>\n")
            sb.append("      <open>1</open>\n")
            sb.append("      <Placemark>\n")
            sb.append("        <name>Rute Kabel FO - ${escapeXml(importedRoute.name)}</name>\n")
            sb.append("        <styleUrl>#fiberRouteLine</styleUrl>\n")
            sb.append("        <LineString>\n")
            sb.append("          <coordinates>\n")
            importedRoute.coordinates.forEach { c ->
                sb.append("            ${c.longitude},${c.latitude},${c.altitude}\n")
            }
            sb.append("          </coordinates>\n")
            sb.append("        </LineString>\n")
            sb.append("      </Placemark>\n")
            sb.append("    </Folder>\n\n")
        }

        // 2. Placemarks Tiang dikelompokkan per Folder kategori legenda Google Earth
        val groupedByCategory = poles.groupBy { KmzIconGenerator.classifyPoleCategory(it) }
        for (category in KmzPoleCategory.entries) {
            val categoryPoles = groupedByCategory[category] ?: continue
            if (categoryPoles.isEmpty()) continue

            sb.append("    <Folder>\n")
            sb.append("      <name>${escapeXml(category.folderName)} (${categoryPoles.size})</name>\n")
            sb.append("      <open>1</open>\n")

            for (pole in categoryPoles) {
                appendPolePlacemark(
                    sb = sb,
                    pole = pole,
                    category = category,
                    surveyorInfo = surveyorInfo
                )
            }

            sb.append("    </Folder>\n\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    private fun appendPolePlacemark(
        sb: StringBuilder,
        pole: Pole,
        category: KmzPoleCategory,
        surveyorInfo: String?
    ) {
        val styleId = KmzIconGenerator.resolveStyleId(pole)
        val photoFile = if (!pole.photoPath.isNullOrEmpty()) File(pole.photoPath) else null
        val photoFileName = if (photoFile != null && photoFile.exists() && photoFile.length() > 0) photoFile.name else null
        val categoryHex = String.format(Locale.US, "#%06X", category.fillRgb)

        sb.append("      <Placemark>\n")
        sb.append("        <name>${escapeXml(pole.effectiveAssetUid)}</name>\n")
        sb.append("        <styleUrl>#$styleId</styleUrl>\n")

        // HTML Description CDATA
        sb.append("        <description><![CDATA[\n")
        sb.append("          <div style=\"font-family: Arial, sans-serif; padding: 10px; max-width: 320px;\">\n")
        sb.append("            <h3 style=\"margin:0 0 6px 0; color:#1565C0;\">${pole.effectiveAssetUid}</h3>\n")
        sb.append("            <p style=\"margin:0 0 8px 0; color:#546E7A; font-size:12px;\"><b>Segmen:</b> ${pole.routeName}</p>\n")
        sb.append("            <table border=\"1\" cellpadding=\"4\" cellspacing=\"0\" style=\"border-collapse:collapse; width:100%; font-size:12px;\">\n")
        sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>Legenda</b></td><td><span style=\"display:inline-block;padding:2px 6px;border-radius:4px;background:$categoryHex;color:#FFFFFF;font-weight:bold;\">${escapeXml(category.badgeText)}</span> ${escapeXml(category.folderName)}</td></tr>\n")
        sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>Klasifikasi</b></td><td>${if (pole.assetClass == "pole_7") "Tiang 7M (T7)" else "Tiang 9M (T9)"}</td></tr>\n")
        sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>Material</b></td><td>${pole.assetType.uppercase()}</td></tr>\n")
        sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>Koordinat</b></td><td>${pole.latitude}, ${pole.longitude} (±${pole.accuracy}m)</td></tr>\n")
        if (pole.rawLatitude != null && pole.rawLongitude != null) {
            sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>GNSS Raw</b></td><td>${pole.rawLatitude}, ${pole.rawLongitude}</td></tr>\n")
        }
        sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>Kepemilikan</b></td><td>${pole.ownership.displayName}</td></tr>\n")
        sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>Kondisi</b></td><td>${pole.condition.displayName}</td></tr>\n")
        val eqList = pole.equipment.filter { it.isNotBlank() }
        if (eqList.isNotEmpty()) {
            sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>Aksesoris</b></td><td>${escapeXml(eqList.joinToString(", "))}</td></tr>\n")
        }
        if (pole.status == TagStatus.MISSING || pole.matchStatus == "MISSING") {
            sb.append("              <tr><td style=\"background:#ECEFF1; color:#37474F;\"><b>Status Lapangan</b></td><td style=\"color:#C62828;\"><b>NIHIL / TIDAK DITEMUKAN</b> (${escapeXml(pole.missingReason ?: "Nihil")})</td></tr>\n")
        }
        if (pole.isSafetyDeferred) {
            sb.append("              <tr><td style=\"background:#FFEBEE; color:#C62828;\"><b>Safety Deferral</b></td><td style=\"color:#C62828;\">${pole.safetyDeferralReason ?: "Terhalang"}</td></tr>\n")
        }
        sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>Waktu Survei</b></td><td>${pole.capturedTimestamp}</td></tr>\n")
        if (!surveyorInfo.isNullOrBlank()) {
            sb.append("              <tr><td style=\"background:#E8F5E9; color:#1B5E20;\"><b>Di-tag oleh</b></td><td><b>${escapeXml(surveyorInfo)}</b></td></tr>\n")
        }
        if (!pole.photoSha256.isNullOrEmpty()) {
            sb.append("              <tr><td style=\"background:#F5F5F5;\"><b>SHA-256</b></td><td style=\"font-family:monospace; font-size:10px;\">${pole.photoSha256.take(16)}...</td></tr>\n")
        }
        sb.append("            </table>\n")

        if (photoFileName != null) {
            sb.append("            <br/>\n")
            sb.append("            <img src=\"images/$photoFileName\" width=\"300\" style=\"border-radius:6px;\"/>\n")
        }
        sb.append("          </div>\n")
        sb.append("        ]]></description>\n")

        // ExtendedData untuk integrasi sistem Alpaca / eNims
        sb.append("        <ExtendedData>\n")
        sb.append("          <Data name=\"asset_uid\"><value>${escapeXml(pole.effectiveAssetUid)}</value></Data>\n")
        sb.append("          <Data name=\"asset_class\"><value>${escapeXml(pole.assetClass)}</value></Data>\n")
        sb.append("          <Data name=\"asset_type\"><value>${escapeXml(pole.assetType)}</value></Data>\n")
        sb.append("          <Data name=\"legend_category\"><value>${escapeXml(category.folderName)}</value></Data>\n")
        sb.append("          <Data name=\"po_id\"><value>${escapeXml(pole.poId)}</value></Data>\n")
        sb.append("          <Data name=\"segment_id\"><value>${escapeXml(pole.segmentId)}</value></Data>\n")
        sb.append("          <Data name=\"accuracy\"><value>${pole.accuracy}</value></Data>\n")
        sb.append("          <Data name=\"is_safety_deferred\"><value>${pole.isSafetyDeferred}</value></Data>\n")
        sb.append("          <Data name=\"match_status\"><value>${escapeXml(pole.matchStatus)}</value></Data>\n")
        sb.append("          <Data name=\"missing_reason\"><value>${escapeXml(pole.missingReason ?: "")}</value></Data>\n")
        sb.append("          <Data name=\"is_existing_pole\"><value>${pole.isExistingPole}</value></Data>\n")
        sb.append("          <Data name=\"sync_status\"><value>${escapeXml(pole.syncStatus)}</value></Data>\n")
        if (pole.equipment.isNotEmpty()) {
            sb.append("          <Data name=\"equipment\"><value>${escapeXml(pole.equipment.joinToString(", "))}</value></Data>\n")
        }
        if (!surveyorInfo.isNullOrBlank()) {
            sb.append("          <Data name=\"tagged_by\"><value>${escapeXml(surveyorInfo)}</value></Data>\n")
        }
        sb.append("        </ExtendedData>\n")

        sb.append("        <Point>\n")
        sb.append("          <coordinates>${pole.longitude},${pole.latitude},0</coordinates>\n")
        sb.append("        </Point>\n")
        sb.append("      </Placemark>\n")
    }

    /**
     * Kompatibilitas dengan alur ekspor generik aplikasi
     */
    fun generateExport(
        projectName: String,
        segmentName: String,
        format: ExportFormat,
        poles: List<Pole>,
        options: ExportOptions,
        importedRoute: ImportedRoute? = null,
        surveyorInfo: String? = null
    ): ExportFile {
        val filteredPoles = poles.filter { pole ->
            (options.includeCompleted && pole.status == TagStatus.COMPLETED) ||
                    (options.includeConflict && pole.status == TagStatus.CONFLICT) ||
                    (options.includeIncomplete && pole.status == TagStatus.NOT_TAGGED) ||
                    ((options.includeIncomplete || options.includeConflict) && pole.status == TagStatus.MISSING)
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val dateDisplay = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        val cleanProject = projectName.replace(" ", "_")
        val cleanSegment = segmentName.replace(" ", "_")
        val fileName = "${cleanProject}_${cleanSegment}_${timestamp}.${format.extension}"
        val targetFile = File(exportDir, fileName)

        when (format) {
            ExportFormat.CSV -> generateAlpacaMasterCsv(filteredPoles, targetFile, surveyorInfo)
            ExportFormat.KML -> {
                val kmlText = generateAlpacaKmlContent(filteredPoles, segmentName, importedRoute, surveyorInfo)
                targetFile.writeText(kmlText)
            }
            ExportFormat.KMZ -> generateAlpacaMasterKmz(filteredPoles, segmentName, importedRoute, surveyorInfo, targetFile)
        }

        return ExportFile(
            id = "EXP-" + System.currentTimeMillis().toString().takeLast(6),
            fileName = fileName,
            format = format,
            sizeBytes = targetFile.length(),
            recordCount = filteredPoles.size,
            createdAt = dateDisplay,
            filePath = targetFile.absolutePath,
            segmentName = segmentName
        )
    }

    private fun createDummyJpgBytes(poleId: String): ByteArray {
        val header = "ALPACA_MASTER_IMAGE_DATA_FOR_$poleId"
        return header.toByteArray(Charsets.UTF_8)
    }
}
