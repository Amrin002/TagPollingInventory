package co.id.lintasarta.tagpollinginventory.data.model

enum class TimezoneOption(val code: String, val displayName: String, val zoneId: String) {
    AUTO("AUTO", "Waktu Lokal Perangkat (Otomatis)", ""),
    WIT("WIT", "WIT / GMT+9 (Maluku & Papua)", "Asia/Jayapura"),
    WITA("WITA", "WITA / GMT+8 (Sulawesi & Bali)", "Asia/Makassar"),
    WIB("WIB", "WIB / GMT+7 (Jawa & Sumatera)", "Asia/Jakarta")
}

data class CameraSettings(
    val isGpsActive: Boolean = true,
    val isHighAccuracy: Boolean = true,
    val isWatermarkActive: Boolean = true,
    val timezoneOption: TimezoneOption = TimezoneOption.AUTO,
    val isExifSaveActive: Boolean = true,
    val isCompressionEnabled: Boolean = false // Default: FALSE (Tanpa kompresi, Kualitas Penuh 100%)
)
