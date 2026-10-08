package co.id.lintasarta.tagpollinginventory.data.local

import androidx.room.TypeConverter
import co.id.lintasarta.tagpollinginventory.data.model.Segment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // --- Set<String> for Equipment ---
    @TypeConverter
    fun fromStringSet(value: Set<String>?): String {
        return gson.toJson(value ?: emptySet<String>())
    }

    @TypeConverter
    fun toStringSet(value: String?): Set<String> {
        if (value.isNullOrEmpty()) return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    // --- List<String> for Photo Paths ---
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            listOf(value)
        }
    }

    // --- Pair<Double, Double> for Coordinates ---
    @TypeConverter
    fun fromDoublePair(value: Pair<Double, Double>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDoublePair(value: String?): Pair<Double, Double> {
        if (value.isNullOrEmpty()) return Pair(0.0, 0.0)
        val type = object : TypeToken<Pair<Double, Double>>() {}.type
        return try {
            gson.fromJson(value, type) ?: Pair(0.0, 0.0)
        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }
    }

    // --- List<Segment> for Project ---
    @TypeConverter
    fun fromSegmentList(value: List<Segment>?): String {
        return gson.toJson(value ?: emptyList<Segment>())
    }

    @TypeConverter
    fun toSegmentList(value: String?): List<Segment> {
        if (value.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<Segment>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
