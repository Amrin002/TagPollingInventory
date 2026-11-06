package co.id.lintasarta.tagpollinginventory.data.preferences

import android.content.Context
import android.content.SharedPreferences
import co.id.lintasarta.tagpollinginventory.data.model.CameraSettings
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraSettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("camera_settings_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<CameraSettings> = _settings.asStateFlow()

    private fun loadSettings(): CameraSettings {
        val json = prefs.getString("camera_settings_json", null)
        if (json != null) {
            try {
                return gson.fromJson(json, CameraSettings::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Default: Full resolution without compression, high accuracy, GPS on, EXIF on, AUTO timezone
        return CameraSettings()
    }

    fun updateSettings(newSettings: CameraSettings) {
        _settings.value = newSettings
        prefs.edit().putString("camera_settings_json", gson.toJson(newSettings)).apply()
    }
}
