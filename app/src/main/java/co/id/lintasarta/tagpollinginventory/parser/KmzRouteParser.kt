package co.id.lintasarta.tagpollinginventory.parser

import co.id.lintasarta.tagpollinginventory.data.model.ImportPreviewData
import java.io.InputStream
import java.util.zip.ZipInputStream

class KmzRouteParser {

    private val kmlParser = KmlRouteParser()

    fun parseKmz(inputStream: InputStream, fileName: String): ImportPreviewData {
        try {
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                var primaryKmlBytes: ByteArray? = null
                var primaryKmlName = ""

                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name.lowercase().endsWith(".kml")) {
                        // Prioritize doc.kml if found
                        val bytes = zip.readBytes()
                        if (name.equals("doc.kml", ignoreCase = true) || primaryKmlBytes == null) {
                            primaryKmlBytes = bytes
                            primaryKmlName = name
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }

                if (primaryKmlBytes != null) {
                    val kmlStream = primaryKmlBytes.inputStream()
                    return kmlParser.parseKml(kmlStream, fileName, fileType = "KMZ")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Return empty preview if no valid KML found inside KMZ
        return ImportPreviewData(
            fileName = fileName,
            fileType = "KMZ",
            routeName = fileName.substringBeforeLast("."),
            coordinateCount = 0,
            referencePoleCount = 0,
            detectedProject = "",
            detectedSegment = "",
            coordinates = emptyList(),
            referencePoles = emptyList()
        )
    }
}
