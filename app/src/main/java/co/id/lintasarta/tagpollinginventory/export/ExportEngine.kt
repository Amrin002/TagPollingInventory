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
            ExportFormat.CSV -> generateCsv(targetFile, filteredPoles, options)
            ExportFormat.KML -> generateKml(targetFile, filteredPoles, segmentName, options, importedRoute)
            ExportFormat.KMZ -> generateKmz(targetFile, filteredPoles, segmentName, options, importedRoute)
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

    private fun generateCsv(file: File, poles: List<Pole>, options: ExportOptions) {
        val sb = StringBuilder()
        sb.append("Pole Code,Internal ID,Segment,Latitude,Longitude,GPS Accuracy (m),Pole Type,Condition,Ownership,Height,Tag Number,FO Cable,Cable Condition,Equipment,Notes,Timestamp,Photo\n")

        for (pole in poles) {
            sb.append("\"${pole.poleCode}\",")
            sb.append("\"${pole.id}\",")
            sb.append("\"${pole.segmentId}\",")
            sb.append("${if (options.includeCoordinates) pole.latitude else ""},")
            sb.append("${if (options.includeCoordinates) pole.longitude else ""},")
            sb.append("${if (options.includeCoordinates) pole.accuracy else ""},")
            sb.append("\"${if (options.includeAttributes) pole.type.displayName else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.condition.displayName else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.ownership.displayName else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.height else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.tagNumber else ""}\",")
            sb.append("\"${if (options.includeAttributes) if (pole.hasFoCable) "Yes" else "No" else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.cableCondition.displayName else ""}\",")
            sb.append("\"${if (options.includeAttributes) pole.equipment.joinToString("; ") else ""}\",")
            sb.append("\"${if (options.includeNotes) pole.notes.replace("\"", "'") else ""}\",")
            sb.append("\"${if (options.includeTimestamp) pole.capturedTimestamp else ""}\",")
            sb.append("\"${if (options.includePhotos) pole.photoPaths.joinToString("; ") else ""}\"\n")
        }

        file.writeText(sb.toString())
    }

    private fun generateKmlContent(
        poles: List<Pole>,
        segmentName: String,
        options: ExportOptions,
        isKmz: Boolean,
        importedRoute: ImportedRoute?
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>Tag Polling Inventory - $segmentName</name>\n")
        sb.append("    <description>Exported Fiber Optic Pole Survey Data</description>\n\n")

        // Styles
        sb.append("    <Style id=\"completedPole\">\n")
        sb.append("      <IconStyle><color>ff008000</color><scale>1.1</scale></IconStyle>\n")
        sb.append("    </Style>\n")
        sb.append("    <Style id=\"conflictPole\">\n")
        sb.append("      <IconStyle><color>ff0000ff</color><scale>1.2</scale></IconStyle>\n")
        sb.append("    </Style>\n")
        sb.append("    <Style id=\"uncompletedPole\">\n")
        sb.append("      <IconStyle><color>ff808080</color><scale>0.9</scale></IconStyle>\n")
        sb.append("    </Style>\n\n")

        // 1. Reference FO Route LineString if available
        if (importedRoute != null && importedRoute.coordinates.isNotEmpty()) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>FO Reference Route - ${importedRoute.name}</name>\n")
            sb.append("      <description>Reference FO Route imported from ${importedRoute.sourceFileName}</description>\n")
            sb.append("      <LineString>\n")
            sb.append("        <coordinates>\n")
            importedRoute.coordinates.forEach { c ->
                sb.append("          ${c.longitude},${c.latitude},${c.altitude}\n")
            }
            sb.append("        </coordinates>\n")
            sb.append("      </LineString>\n")
            sb.append("    </Placemark>\n\n")
        }

        // 2. Surveyed Poles Placemarks
        for (pole in poles) {
            val style = when (pole.status) {
                TagStatus.COMPLETED -> "#completedPole"
                TagStatus.CONFLICT -> "#conflictPole"
                TagStatus.NOT_TAGGED -> "#uncompletedPole"
            }

            sb.append("    <Placemark>\n")
            sb.append("      <name>${pole.poleCode}</name>\n")
            sb.append("      <styleUrl>$style</styleUrl>\n")

            // HTML Description
            sb.append("      <description><![CDATA[\n")
            sb.append("        <div style=\"font-family: Arial, sans-serif; padding: 8px;\">\n")
            sb.append("          <h3 style=\"margin:0 0 8px 0; color:#1565C0;\">Pole Code: ${pole.poleCode}</h3>\n")
            sb.append("          <table border=\"1\" cellpadding=\"4\" cellspacing=\"0\" style=\"border-collapse:collapse;\">\n")
            sb.append("            <tr><td><b>Internal ID</b></td><td>${pole.id}</td></tr>\n")
            sb.append("            <tr><td><b>Status</b></td><td>${pole.status.displayName}</td></tr>\n")
            if (options.includeCoordinates) {
                sb.append("            <tr><td><b>Coordinates</b></td><td>${pole.latitude}, ${pole.longitude} (±${pole.accuracy}m)</td></tr>\n")
            }
            if (options.includeAttributes) {
                sb.append("            <tr><td><b>Pole Type</b></td><td>${pole.type.displayName} (${pole.height})</td></tr>\n")
                sb.append("            <tr><td><b>Condition</b></td><td>${pole.condition.displayName}</td></tr>\n")
                sb.append("            <tr><td><b>Ownership</b></td><td>${pole.ownership.displayName}</td></tr>\n")
                sb.append("            <tr><td><b>Tag Number</b></td><td>${pole.tagNumber}</td></tr>\n")
                sb.append("            <tr><td><b>FO Cable</b></td><td>${if (pole.hasFoCable) "Yes" else "No"} (${pole.cableCondition.displayName})</td></tr>\n")
                sb.append("            <tr><td><b>Equipment</b></td><td>${pole.equipment.joinToString(", ")}</td></tr>\n")
            }
            if (options.includeNotes && pole.notes.isNotEmpty()) {
                sb.append("            <tr><td><b>Notes</b></td><td>${pole.notes}</td></tr>\n")
            }
            if (options.includeTimestamp && pole.capturedTimestamp.isNotEmpty()) {
                sb.append("            <tr><td><b>Timestamp</b></td><td>${pole.capturedTimestamp}</td></tr>\n")
            }
            sb.append("          </table>\n")

            if (isKmz && options.includePhotoReferences && pole.photoPaths.isNotEmpty()) {
                sb.append("          <br/>\n")
                pole.photoPaths.forEachIndexed { index, _ ->
                    val photoName = "photo_${pole.id}_$index.jpg"
                    sb.append("          <img src=\"images/$photoName\" width=\"300\" style=\"border-radius:4px; margin-right:8px;\"/>\n")
                }
            }
            sb.append("        </div>\n")
            sb.append("      ]]></description>\n")

            // ExtendedData
            sb.append("      <ExtendedData>\n")
            sb.append("        <Data name=\"segment\"><value>${pole.segmentId}</value></Data>\n")
            sb.append("        <Data name=\"pole_type\"><value>${pole.type.displayName}</value></Data>\n")
            sb.append("        <Data name=\"condition\"><value>${pole.condition.displayName}</value></Data>\n")
            sb.append("        <Data name=\"ownership\"><value>${pole.ownership.displayName}</value></Data>\n")
            sb.append("        <Data name=\"status\"><value>${pole.status.displayName}</value></Data>\n")
            sb.append("      </ExtendedData>\n")

            // Coordinates (KML order is longitude,latitude,altitude)
            sb.append("      <Point>\n")
            sb.append("        <coordinates>${pole.longitude},${pole.latitude},0</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    private fun generateKml(file: File, poles: List<Pole>, segmentName: String, options: ExportOptions, importedRoute: ImportedRoute?) {
        val content = generateKmlContent(poles, segmentName, options, isKmz = false, importedRoute = importedRoute)
        file.writeText(content)
    }

    private fun generateKmz(file: File, poles: List<Pole>, segmentName: String, options: ExportOptions, importedRoute: ImportedRoute?) {
        val kmlContent = generateKmlContent(poles, segmentName, options, isKmz = true, importedRoute = importedRoute)

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            // 1. Add doc.kml
            val kmlEntry = ZipEntry("doc.kml")
            zip.putNextEntry(kmlEntry)
            zip.write(kmlContent.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 2. Add actual photos if file exists, else placeholder
            for (pole in poles) {
                if (options.includePhotos && pole.photoPaths.isNotEmpty()) {
                    pole.photoPaths.forEachIndexed { index, path ->
                        val photoEntry = ZipEntry("images/photo_${pole.id}_$index.jpg")
                        zip.putNextEntry(photoEntry)
                        val photoFile = File(path)
                        if (photoFile.exists()) {
                            zip.write(photoFile.readBytes())
                        } else {
                            val dummyImageBytes = createDummyJpgBytes(pole.id)
                            zip.write(dummyImageBytes)
                        }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    private fun createDummyJpgBytes(poleId: String): ByteArray {
        val header = "TAG_POLLING_INVENTORY_IMAGE_DATA_FOR_$poleId"
        return header.toByteArray(Charsets.UTF_8)
    }
}
