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
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringSet(value: String): Set<String> {
        if (value.isEmpty()) return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return gson.fromJson(value, type) ?: emptySet()
    }

    // --- Pair<Double, Double> for Coordinates ---
    @TypeConverter
    fun fromDoublePair(value: Pair<Double, Double>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDoublePair(value: String): Pair<Double, Double> {
        if (value.isEmpty()) return Pair(0.0, 0.0)
        val type = object : TypeToken<Pair<Double, Double>>() {}.type
        return gson.fromJson(value, type) ?: Pair(0.0, 0.0)
    }

    // --- List<Segment> for Project ---
    @TypeConverter
    fun fromSegmentList(value: List<Segment>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toSegmentList(value: String): List<Segment> {
        if (value.isEmpty()) return emptyList()
        val type = object : TypeToken<List<Segment>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
