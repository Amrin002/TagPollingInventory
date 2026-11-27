package co.id.lintasarta.tagpollinginventory.export

import co.id.lintasarta.tagpollinginventory.data.model.Pole
import co.id.lintasarta.tagpollinginventory.data.model.TagStatus
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Kategori legenda tiang pada file KMZ/KML Google Earth untuk membedakan
 * tinggi tiang (T7/T9) dan aksesoris lapangan (JB, Splitter, Slakkan, Kombinasi, Semuanya).
 */
enum class KmzPoleCategory(
    val folderName: String,
    val badgeText: String,
    val fillRgb: Int,
    val textRgb: Int,
    val borderRgb: Int,
    val kmlAbgrColor: String,
    val fallbackKmlHref: String,
    val kmlScale: Double
) {
    POLE_7(
        folderName = "Tiang 7M (T7)",
        badgeText = "7",
        fillRgb = 0xEF6C00,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ff006cef",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-circle.png",
        kmlScale = 1.1
    ),
    POLE_9(
        folderName = "Tiang 9M (T9)",
        badgeText = "9",
        fillRgb = 0x1565C0,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ffc06515",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-circle.png",
        kmlScale = 1.15
    ),
    JB(
        folderName = "Tiang + JB",
        badgeText = "JB",
        fillRgb = 0x8E24AA,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ffaa248e",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-circle.png",
        kmlScale = 1.2
    ),
    SPLITTER(
        folderName = "Tiang + Splitter",
        badgeText = "SP",
        fillRgb = 0x2E7D32,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ff327d2e",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-circle.png",
        kmlScale = 1.2
    ),
    SLEKAN(
        folderName = "Tiang + Slakkan (Slekan)",
        badgeText = "SL",
        fillRgb = 0xFDD835,
        textRgb = 0x1A1A1A,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ff35d8fd",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-circle.png",
        kmlScale = 1.2
    ),
    JB_SPLITTER(
        folderName = "Tiang + JB & Splitter",
        badgeText = "JS",
        fillRgb = 0x00838F,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ff8f8300",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-stars.png",
        kmlScale = 1.25
    ),
    JB_SLEKAN(
        folderName = "Tiang + JB & Slakkan",
        badgeText = "JL",
        fillRgb = 0x6D4C41,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ff414c6d",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-stars.png",
        kmlScale = 1.25
    ),
    SPLITTER_SLEKAN(
        folderName = "Tiang + Splitter & Slakkan",
        badgeText = "PS",
        fillRgb = 0x689F38,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ff389f68",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-stars.png",
        kmlScale = 1.25
    ),
    ALL_EQUIPMENT(
        folderName = "Tiang + Semua Aksesoris (JB, Splitter, Slakkan)",
        badgeText = "ALL",
        fillRgb = 0xD81B60,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFD54F,
        kmlAbgrColor = "ff601bd8",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-stars.png",
        kmlScale = 1.3
    ),
    SAFETY_DEFERRED(
        folderName = "Safety Deferral",
        badgeText = "!",
        fillRgb = 0xC62828,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ff2828c6",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-diamond.png",
        kmlScale = 1.2
    ),
    MISSING(
        folderName = "Tiang Nihil (Missing)",
        badgeText = "X",
        fillRgb = 0x616161,
        textRgb = 0xFFFFFF,
        borderRgb = 0xFFFFFF,
        kmlAbgrColor = "ff616161",
        fallbackKmlHref = "http://maps.google.com/mapfiles/kml/paddle/wht-blank.png",
        kmlScale = 0.95
    )
}

enum class IconShape {
    CIRCLE,
    ROUNDED_SQUARE,
    DIAMOND
}

data class KmzStyleSpec(
    val styleId: String,
    val category: KmzPoleCategory,
    val iconRelativePath: String,
    val shape: IconShape
)

/**
 * Generator ikon PNG 64x64 pure-Kotlin (tanpa ketergantungan android.graphics)
 * agar ekspor KMZ offline memiliki legenda visual yang tajam di Google Earth dan
 * dapat diuji langsung lewat unit test JVM.
 */
object KmzIconGenerator {

    private const val ICON_SIZE = 64

    val allStyleSpecs: List<KmzStyleSpec> = listOf(
        KmzStyleSpec("p7Pole", KmzPoleCategory.POLE_7, "icons/pole_t7.png", IconShape.ROUNDED_SQUARE),
        KmzStyleSpec("p9Pole", KmzPoleCategory.POLE_9, "icons/pole_t9.png", IconShape.CIRCLE),
        KmzStyleSpec("poleJbP7", KmzPoleCategory.JB, "icons/pole_jb_p7.png", IconShape.ROUNDED_SQUARE),
        KmzStyleSpec("poleJbP9", KmzPoleCategory.JB, "icons/pole_jb_p9.png", IconShape.CIRCLE),
        KmzStyleSpec("poleSplitterP7", KmzPoleCategory.SPLITTER, "icons/pole_splitter_p7.png", IconShape.ROUNDED_SQUARE),
        KmzStyleSpec("poleSplitterP9", KmzPoleCategory.SPLITTER, "icons/pole_splitter_p9.png", IconShape.CIRCLE),
        KmzStyleSpec("poleSlekanP7", KmzPoleCategory.SLEKAN, "icons/pole_slekan_p7.png", IconShape.ROUNDED_SQUARE),
        KmzStyleSpec("poleSlekanP9", KmzPoleCategory.SLEKAN, "icons/pole_slekan_p9.png", IconShape.CIRCLE),
        KmzStyleSpec("poleJbSplitterP7", KmzPoleCategory.JB_SPLITTER, "icons/pole_jb_splitter_p7.png", IconShape.ROUNDED_SQUARE),
        KmzStyleSpec("poleJbSplitterP9", KmzPoleCategory.JB_SPLITTER, "icons/pole_jb_splitter_p9.png", IconShape.CIRCLE),
        KmzStyleSpec("poleJbSlekanP7", KmzPoleCategory.JB_SLEKAN, "icons/pole_jb_slekan_p7.png", IconShape.ROUNDED_SQUARE),
        KmzStyleSpec("poleJbSlekanP9", KmzPoleCategory.JB_SLEKAN, "icons/pole_jb_slekan_p9.png", IconShape.CIRCLE),
        KmzStyleSpec("poleSplitterSlekanP7", KmzPoleCategory.SPLITTER_SLEKAN, "icons/pole_splitter_slekan_p7.png", IconShape.ROUNDED_SQUARE),
        KmzStyleSpec("poleSplitterSlekanP9", KmzPoleCategory.SPLITTER_SLEKAN, "icons/pole_splitter_slekan_p9.png", IconShape.CIRCLE),
        KmzStyleSpec("poleAllP7", KmzPoleCategory.ALL_EQUIPMENT, "icons/pole_all_p7.png", IconShape.ROUNDED_SQUARE),
        KmzStyleSpec("poleAllP9", KmzPoleCategory.ALL_EQUIPMENT, "icons/pole_all_p9.png", IconShape.CIRCLE),
        KmzStyleSpec("safetyDeferredPole", KmzPoleCategory.SAFETY_DEFERRED, "icons/pole_safety.png", IconShape.DIAMOND),
        KmzStyleSpec("missingPole", KmzPoleCategory.MISSING, "icons/pole_missing.png", IconShape.CIRCLE)
    )

    fun classifyPoleCategory(pole: Pole): KmzPoleCategory {
        if (pole.status == TagStatus.MISSING || pole.matchStatus == "MISSING") {
            return KmzPoleCategory.MISSING
        }
        if (pole.isSafetyDeferred) {
            return KmzPoleCategory.SAFETY_DEFERRED
        }

        val eq = pole.equipment
        val hasLegacyDefaultPair = eq.contains("ODP") && eq.contains("Closure")
        val hasJb = eq.any { item ->
            val lower = item.trim().lowercase(Locale.ROOT)
            lower == "jb" ||
                lower.contains("(jb)") ||
                lower.contains("joint") ||
                (lower.contains("closure") && !hasLegacyDefaultPair)
        }
        val hasSplitter = eq.any { item ->
            val lower = item.trim().lowercase(Locale.ROOT)
            lower.contains("splitter") || lower.contains("spliter")
        }
        val hasSlekan = eq.any { item ->
            val lower = item.trim().lowercase(Locale.ROOT)
            lower.contains("slakkan") ||
                lower.contains("slekan") ||
                lower.contains("slk") ||
                lower.contains("slack")
        }

        return when {
            hasJb && hasSplitter && hasSlekan -> KmzPoleCategory.ALL_EQUIPMENT
            hasJb && hasSplitter -> KmzPoleCategory.JB_SPLITTER
            hasJb && hasSlekan -> KmzPoleCategory.JB_SLEKAN
            hasSplitter && hasSlekan -> KmzPoleCategory.SPLITTER_SLEKAN
            hasJb -> KmzPoleCategory.JB
            hasSplitter -> KmzPoleCategory.SPLITTER
            hasSlekan -> KmzPoleCategory.SLEKAN
            pole.assetClass == "pole_7" -> KmzPoleCategory.POLE_7
            else -> KmzPoleCategory.POLE_9
        }
    }

    fun resolveStyleId(pole: Pole): String {
        val category = classifyPoleCategory(pole)
        val isP7 = pole.assetClass == "pole_7"
        return when (category) {
            KmzPoleCategory.MISSING -> "missingPole"
            KmzPoleCategory.SAFETY_DEFERRED -> "safetyDeferredPole"
            KmzPoleCategory.POLE_7 -> "p7Pole"
            KmzPoleCategory.POLE_9 -> "p9Pole"
            KmzPoleCategory.JB -> if (isP7) "poleJbP7" else "poleJbP9"
            KmzPoleCategory.SPLITTER -> if (isP7) "poleSplitterP7" else "poleSplitterP9"
            KmzPoleCategory.SLEKAN -> if (isP7) "poleSlekanP7" else "poleSlekanP9"
            KmzPoleCategory.JB_SPLITTER -> if (isP7) "poleJbSplitterP7" else "poleJbSplitterP9"
            KmzPoleCategory.JB_SLEKAN -> if (isP7) "poleJbSlekanP7" else "poleJbSlekanP9"
            KmzPoleCategory.SPLITTER_SLEKAN -> if (isP7) "poleSplitterSlekanP7" else "poleSplitterSlekanP9"
            KmzPoleCategory.ALL_EQUIPMENT -> if (isP7) "poleAllP7" else "poleAllP9"
        }
    }

    fun generateAllLegendIcons(): Map<String, ByteArray> {
        return allStyleSpecs.associate { spec ->
            spec.iconRelativePath to renderBadgeIconPng(
                label = spec.category.badgeText,
                fillRgb = spec.category.fillRgb,
                textRgb = spec.category.textRgb,
                borderRgb = spec.category.borderRgb,
                shape = spec.shape
            )
        }
    }

    fun renderBadgeIconPng(
        label: String,
        fillRgb: Int,
        textRgb: Int,
        borderRgb: Int,
        shape: IconShape
    ): ByteArray {
        val size = ICON_SIZE
        val center = (size - 1) / 2f
        val rgba = ByteArray(size * size * 4)

        val outerRimRgb = 0x263238

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dist = shapeSdf(x.toFloat(), y.toFloat(), center, shape)
                val coverage = (0.5f - dist).coerceIn(0f, 1f)
                if (coverage <= 0f) continue

                val baseRgb = when {
                    dist > -1.8f -> outerRimRgb
                    dist > -5.5f -> borderRgb
                    else -> fillRgb
                }

                val alpha = (coverage * 255f).toInt().coerceIn(0, 255)
                val idx = (y * size + x) * 4
                rgba[idx] = ((baseRgb shr 16) and 0xFF).toByte()
                rgba[idx + 1] = ((baseRgb shr 8) and 0xFF).toByte()
                rgba[idx + 2] = (baseRgb and 0xFF).toByte()
                rgba[idx + 3] = alpha.toByte()
            }
        }

        drawCenteredText(rgba, size, label, textRgb)
        return encodeRgbaPng(size, size, rgba)
    }

    private fun shapeSdf(x: Float, y: Float, center: Float, shape: IconShape): Float {
        val dx = abs(x - center)
        val dy = abs(y - center)
        return when (shape) {
            IconShape.CIRCLE -> hypot(dx, dy) - 27.5f
            IconShape.ROUNDED_SQUARE -> {
                val halfSize = 25.5f
                val cornerRadius = 8.5f
                val qx = dx - (halfSize - cornerRadius)
                val qy = dy - (halfSize - cornerRadius)
                hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - cornerRadius
            }
            IconShape.DIAMOND -> {
                val rx = (dx + dy) * 0.70710677f
                rx - 26.5f
            }
        }
    }

    private fun drawCenteredText(rgba: ByteArray, size: Int, text: String, textRgb: Int) {
        if (text.isEmpty()) return
        val glyphW = 5
        val glyphH = 7
        val scale = when (text.length) {
            1 -> 4
            2 -> 3
            else -> 2
        }
        val gap = when (text.length) {
            1 -> 0
            2 -> 3
            else -> 2
        }

        val totalW = text.length * glyphW * scale + (text.length - 1) * gap
        val totalH = glyphH * scale
        val startX = (size - totalW) / 2
        val startY = (size - totalH) / 2

        val tr = ((textRgb shr 16) and 0xFF).toByte()
        val tg = ((textRgb shr 8) and 0xFF).toByte()
        val tb = (textRgb and 0xFF).toByte()

        text.forEachIndexed { charIdx, ch ->
            val bitmap = GLYPHS[ch] ?: return@forEachIndexed
            val offsetX = startX + charIdx * (glyphW * scale + gap)
            for (row in 0 until glyphH) {
                val rowStr = bitmap[row]
                for (col in 0 until glyphW) {
                    if (rowStr[col] == '1') {
                        for (sy in 0 until scale) {
                            val py = startY + row * scale + sy
                            if (py !in 0 until size) continue
                            for (sx in 0 until scale) {
                                val px = offsetX + col * scale + sx
                                if (px !in 0 until size) continue
                                val idx = (py * size + px) * 4
                                rgba[idx] = tr
                                rgba[idx + 1] = tg
                                rgba[idx + 2] = tb
                                rgba[idx + 3] = 255.toByte()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun encodeRgbaPng(width: Int, height: Int, rgba: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val ihdr = ByteArrayOutputStream().apply {
            DataOutputStream(this).use { dos ->
                dos.writeInt(width)
                dos.writeInt(height)
                dos.writeByte(8) // bit depth
                dos.writeByte(6) // color type: RGBA
                dos.writeByte(0) // compression
                dos.writeByte(0) // filter
                dos.writeByte(0) // interlace
            }
        }.toByteArray()
        writePngChunk(out, "IHDR", ihdr)

        val rawScanlines = ByteArray(height * (1 + width * 4))
        val rowStride = width * 4
        for (y in 0 until height) {
            val destRowStart = y * (1 + rowStride)
            rawScanlines[destRowStart] = 0 // filter type 0 (None)
            System.arraycopy(rgba, y * rowStride, rawScanlines, destRowStart + 1, rowStride)
        }

        val compressed = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            deflater.setInput(rawScanlines)
            deflater.finish()
            val buf = ByteArray(4096)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                compressed.write(buf, 0, count)
            }
        } finally {
            deflater.end()
        }

        writePngChunk(out, "IDAT", compressed.toByteArray())
        writePngChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writePngChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value

        val dos = DataOutputStream(out)
        dos.writeInt(data.size)
        dos.write(typeBytes)
        dos.write(data)
        dos.writeInt(crc.toInt())
        dos.flush()
    }

    private val GLYPHS: Map<Char, List<String>> = mapOf(
        '7' to listOf(
            "11111",
            "00001",
            "00010",
            "00100",
            "01000",
            "01000",
            "01000"
        ),
        '9' to listOf(
            "01110",
            "10001",
            "10001",
            "01111",
            "00001",
            "00010",
            "01100"
        ),
        'J' to listOf(
            "00111",
            "00010",
            "00010",
            "00010",
            "10010",
            "10010",
            "01100"
        ),
        'B' to listOf(
            "11110",
            "10001",
            "10001",
            "11110",
            "10001",
            "10001",
            "11110"
        ),
        'S' to listOf(
            "01111",
            "10000",
            "10000",
            "01110",
            "00001",
            "00001",
            "11110"
        ),
        'P' to listOf(
            "11110",
            "10001",
            "10001",
            "11110",
            "10000",
            "10000",
            "10000"
        ),
        'L' to listOf(
            "10000",
            "10000",
            "10000",
            "10000",
            "10000",
            "10000",
            "11111"
        ),
        'A' to listOf(
            "01110",
            "10001",
            "10001",
            "11111",
            "10001",
            "10001",
            "10001"
        ),
        '!' to listOf(
            "00100",
            "00100",
            "00100",
            "00100",
            "00100",
            "00000",
            "00100"
        ),
        'X' to listOf(
            "10001",
            "10001",
            "01010",
            "00100",
            "01010",
            "10001",
            "10001"
        )
    )
}
