package co.id.lintasarta.tagpollinginventory.export

import android.content.Context
import co.id.lintasarta.tagpollinginventory.data.model.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportEngine(private val context: Context) {

    private val exportDir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }

    /**
     * Menghasilkan Ekspor File Resmi Alpaca Master CSV: Alpaca_Master_Poles.csv
     * Kompatibel dengan controller eNims dan script backend Alpaca_kmz.py.
     */
    fun generateAlpacaMasterCsv(poles: List<Pole>, targetFile: File? = null): File {
        val file = targetFile ?: File(exportDir, "Alpaca_Master_Poles.csv")
        val sb = StringBuilder()
        sb.append("asset_uid,segment_id,route_name,po_id,asset_class,asset_type,latitude,longitude,raw_latitude,raw_longitude,accuracy,photo_filename,ownership,condition,is_safety_deferred,match_status,sync_status,timestamp,photo_sha256\n")

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
            sb.append("\"${pole.syncStatus}\",")
            sb.append("\"${pole.capturedTimestamp}\",")
            sb.append("\"${pole.photoSha256 ?: ""}\"\n")
        }

        file.writeText(sb.toString())
        return file
    }

    /**
     * Menghasilkan Ekspor File Resmi Alpaca Master KMZ: Alpaca_Master.kmz
     * Berisi placemarks T7/T9, rute polyline kabel FO, dan bundle foto tiang di folder images/.
     */
    fun generateAlpacaMasterKmz(
        poles: List<Pole>,
        segmentName: String,
        importedRoute: ImportedRoute? = null,
        targetFile: File? = null
    ): File {
        val file = targetFile ?: File(exportDir, "Alpaca_Master.kmz")
        val kmlContent = generateAlpacaKmlContent(poles, segmentName, importedRoute)

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            // 1. Masukkan file KML utama doc.kml
            val kmlEntry = ZipEntry("doc.kml")
            zip.putNextEntry(kmlEntry)
            zip.write(kmlContent.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 2. Masukkan file foto aktual tiang ke dalam folder images/
            for (pole in poles) {
                if (!pole.photoPath.isNullOrEmpty()) {
                    val photoSrc = File(pole.photoPath)
                    val photoName = photoSrc.name
                    val photoEntry = ZipEntry("images/$photoName")
                    zip.putNextEntry(photoEntry)
                    if (photoSrc.exists()) {
                        zip.write(photoSrc.readBytes())
                    } else {
                        val dummy = createDummyJpgBytes(pole.effectiveAssetUid)
                        zip.write(dummy)
                    }
                    zip.closeEntry()
                }
            }
        }
        return file
    }

    private fun generateAlpacaKmlContent(
        poles: List<Pole>,
        segmentName: String,
        importedRoute: ImportedRoute?
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>Alpaca Master Poles - $segmentName</name>\n")
        sb.append("    <description>Project Alpaca PRA Survey & As-Built Poles (Regional MPO)</description>\n\n")

        // Styles untuk P7 (Orange) & P9 (Cyan/Biru) & Safety Deferral (Merah)
        sb.append("    <Style id=\"p9Pole\">\n")
        sb.append("      <IconStyle>\n")
        sb.append("        <color>ffffa500</color>\n") // Cyan / Blueish
        sb.append("        <scale>1.2</scale>\n")
        sb.append("        <Icon><href>http://maps.google.com/mapfiles/kml/paddle/blu-circle.png</href></Icon>\n")
        sb.append("      </IconStyle>\n")
        sb.append("    </Style>\n")

        sb.append("    <Style id=\"p7Pole\">\n")
        sb.append("      <IconStyle>\n")
        sb.append("        <color>ff00a5ff</color>\n") // Orange / Amber
        sb.append("        <scale>1.1</scale>\n")
        sb.append("        <Icon><href>http://maps.google.com/mapfiles/kml/paddle/orange-circle.png</href></Icon>\n")
        sb.append("      </IconStyle>\n")
        sb.append("    </Style>\n")

        sb.append("    <Style id=\"safetyDeferredPole\">\n")
        sb.append("      <IconStyle>\n")
        sb.append("        <color>ff0000ff</color>\n") // Red
        sb.append("        <scale>1.2</scale>\n")
        sb.append("        <Icon><href>http://maps.google.com/mapfiles/kml/paddle/red-diamond.png</href></Icon>\n")
        sb.append("      </IconStyle>\n")
        sb.append("    </Style>\n")

        sb.append("    <Style id=\"fiberRouteLine\">\n")
        sb.append("      <LineStyle>\n")
        sb.append("        <color>ffc06515</color>\n") // Telecom Blue
        sb.append("        <width>5.0</width>\n")
        sb.append("      </LineStyle>\n")
        sb.append("    </Style>\n\n")

        // 1. Garis Polyline Jalur Fiber Optik
        if (importedRoute != null && importedRoute.coordinates.isNotEmpty()) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>Rute Kabel FO - ${importedRoute.name}</name>\n")
            sb.append("      <styleUrl>#fiberRouteLine</styleUrl>\n")
            sb.append("      <LineString>\n")
            sb.append("        <coordinates>\n")
            importedRoute.coordinates.forEach { c ->
                sb.append("          ${c.longitude},${c.latitude},${c.altitude}\n")
            }
            sb.append("        </coordinates>\n")
            sb.append("      </LineString>\n")
            sb.append("    </Placemark>\n\n")
        }

        // 2. Placemarks Tiang Alpaca PRA
        for (pole in poles) {
            val styleUrl = when {
                pole.isSafetyDeferred -> "#safetyDeferredPole"
                pole.assetClass == "pole_7" -> "#p7Pole"
                else -> "#p9Pole"
            }

            val photoFileName = if (!pole.photoPath.isNullOrEmpty()) File(pole.photoPath).name else null

            sb.append("    <Placemark>\n")
            sb.append("      <name>${pole.effectiveAssetUid}</name>\n")
            sb.append("      <styleUrl>$styleUrl</styleUrl>\n")

            // HTML Description CDATA
            sb.append("      <description><![CDATA[\n")
            sb.append("        <div style=\"font-family: Arial, sans-serif; padding: 10px; max-width: 320px;\">\n")
            sb.append("          <h3 style=\"margin:0 0 6px 0; color:#1565C0;\">${pole.effectiveAssetUid}</h3>\n")
            sb.append("          <p style=\"margin:0 0 10px 0; color:#546E7A; font-size:12px;\"><b>Segmen:</b> ${pole.routeName}</p>\n")
            sb.append("          <table border=\"1\" cellpadding=\"4\" cellspacing=\"0\" style=\"border-collapse:collapse; width:100%; font-size:12px;\">\n")
            sb.append("            <tr><td style=\"background:#F5F5F5;\"><b>Klasifikasi</b></td><td>${if (pole.assetClass == "pole_7") "Tiang 7M (T7)" else "Tiang 9M (T9)"}</td></tr>\n")
            sb.append("            <tr><td style=\"background:#F5F5F5;\"><b>Material</b></td><td>${pole.assetType.uppercase()}</td></tr>\n")
            sb.append("            <tr><td style=\"background:#F5F5F5;\"><b>Koordinat</b></td><td>${pole.latitude}, ${pole.longitude} (±${pole.accuracy}m)</td></tr>\n")
            if (pole.rawLatitude != null && pole.rawLongitude != null) {
                sb.append("            <tr><td style=\"background:#F5F5F5;\"><b>GNSS Raw</b></td><td>${pole.rawLatitude}, ${pole.rawLongitude}</td></tr>\n")
            }
            sb.append("            <tr><td style=\"background:#F5F5F5;\"><b>Kepemilikan</b></td><td>${pole.ownership.displayName}</td></tr>\n")
            sb.append("            <tr><td style=\"background:#F5F5F5;\"><b>Kondisi</b></td><td>${pole.condition.displayName}</td></tr>\n")
            if (pole.isSafetyDeferred) {
                sb.append("            <tr><td style=\"background:#FFEBEE; color:#C62828;\"><b>Safety Deferral</b></td><td style=\"color:#C62828;\">${pole.safetyDeferralReason ?: "Terhalang"}</td></tr>\n")
            }
            sb.append("            <tr><td style=\"background:#F5F5F5;\"><b>Waktu Survei</b></td><td>${pole.capturedTimestamp}</td></tr>\n")
            if (!pole.photoSha256.isNullOrEmpty()) {
                sb.append("            <tr><td style=\"background:#F5F5F5;\"><b>SHA-256</b></td><td style=\"font-family:monospace; font-size:10px;\">${pole.photoSha256.take(16)}...</td></tr>\n")
            }
            sb.append("          </table>\n")

            if (photoFileName != null) {
                sb.append("          <br/>\n")
                sb.append("          <img src=\"images/$photoFileName\" width=\"300\" style=\"border-radius:6px;\"/>\n")
            }
            sb.append("        </div>\n")
            sb.append("      ]]></description>\n")

            // ExtendedData untuk integrasi sistem Alpaca / eNims
            sb.append("      <ExtendedData>\n")
            sb.append("        <Data name=\"asset_uid\"><value>${pole.effectiveAssetUid}</value></Data>\n")
            sb.append("        <Data name=\"asset_class\"><value>${pole.assetClass}</value></Data>\n")
            sb.append("        <Data name=\"asset_type\"><value>${pole.assetType}</value></Data>\n")
            sb.append("        <Data name=\"po_id\"><value>${pole.poId}</value></Data>\n")
            sb.append("        <Data name=\"segment_id\"><value>${pole.segmentId}</value></Data>\n")
            sb.append("        <Data name=\"accuracy\"><value>${pole.accuracy}</value></Data>\n")
            sb.append("        <Data name=\"is_safety_deferred\"><value>${pole.isSafetyDeferred}</value></Data>\n")
            sb.append("        <Data name=\"match_status\"><value>${pole.matchStatus}</value></Data>\n")
            sb.append("        <Data name=\"sync_status\"><value>${pole.syncStatus}</value></Data>\n")
            sb.append("      </ExtendedData>\n")

            sb.append("      <Point>\n")
            sb.append("        <coordinates>${pole.longitude},${pole.latitude},0</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
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
        importedRoute: ImportedRoute? = null
    ): ExportFile {
        val filteredPoles = poles.filter { pole ->
            (options.includeCompleted && pole.status == TagStatus.COMPLETED) ||
                    (options.includeConflict && pole.status == TagStatus.CONFLICT) ||
                    (options.includeIncomplete && pole.status == TagStatus.NOT_TAGGED)
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val dateDisplay = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        val cleanProject = projectName.replace(" ", "_")
        val cleanSegment = segmentName.replace(" ", "_")
        val fileName = "${cleanProject}_${cleanSegment}_${timestamp}.${format.extension}"
        val targetFile = File(exportDir, fileName)

        when (format) {
            ExportFormat.CSV -> generateAlpacaMasterCsv(filteredPoles, targetFile)
            ExportFormat.KML -> {
                val kmlText = generateAlpacaKmlContent(filteredPoles, segmentName, importedRoute)
                targetFile.writeText(kmlText)
            }
            ExportFormat.KMZ -> generateAlpacaMasterKmz(filteredPoles, segmentName, importedRoute, targetFile)
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
