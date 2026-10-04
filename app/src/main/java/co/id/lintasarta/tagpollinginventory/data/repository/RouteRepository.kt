package co.id.lintasarta.tagpollinginventory.data.repository

import android.content.Context
import co.id.lintasarta.tagpollinginventory.data.model.ImportedPole
import co.id.lintasarta.tagpollinginventory.data.model.ImportedRoute
import co.id.lintasarta.tagpollinginventory.data.model.RouteCoordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RouteRepository(private val context: Context) {

    private val routeFile = File(context.filesDir, "imported_routes.json")

    private val _importedRoutes = MutableStateFlow<List<ImportedRoute>>(emptyList())
    val importedRoutes: StateFlow<List<ImportedRoute>> = _importedRoutes.asStateFlow()

    private val _activeRoute = MutableStateFlow<ImportedRoute?>(null)
    val activeRoute: StateFlow<ImportedRoute?> = _activeRoute.asStateFlow()

    init {
        loadRoutes()
    }

    private fun loadRoutes() {
        if (!routeFile.exists()) return

        try {
            val text = routeFile.readText()
            val arr = JSONArray(text)
            val list = mutableListOf<ImportedRoute>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val coordsArr = obj.getJSONArray("coordinates")
                val coords = mutableListOf<RouteCoordinate>()
                for (c in 0 until coordsArr.length()) {
                    val cObj = coordsArr.getJSONObject(c)
                    coords.add(
                        RouteCoordinate(
                            longitude = cObj.getDouble("longitude"),
                            latitude = cObj.getDouble("latitude"),
                            altitude = cObj.optDouble("altitude", 0.0)
                        )
                    )
                }

                val polesArr = obj.optJSONArray("referencePoles")
                val refPoles = mutableListOf<ImportedPole>()
                if (polesArr != null) {
                    for (p in 0 until polesArr.length()) {
                        val pObj = polesArr.getJSONObject(p)
                        refPoles.add(
                            ImportedPole(
                                id = pObj.getString("id"),
                                routeId = pObj.optString("routeId", ""),
                                name = pObj.optString("name", ""),
                                latitude = pObj.getDouble("latitude"),
                                longitude = pObj.getDouble("longitude"),
                                altitude = pObj.optDouble("altitude", 0.0)
                            )
                        )
                    }
                }

                val route = ImportedRoute(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    sourceFileName = obj.optString("sourceFileName", ""),
                    sourceFileType = obj.optString("sourceFileType", "KML"),
                    projectId = obj.optString("projectId", ""),
                    segmentId = obj.optString("segmentId", ""),
                    coordinates = coords,
                    referencePoles = refPoles,
                    createdAt = obj.optString("createdAt", ""),
                    updatedAt = obj.optString("updatedAt", "")
                )
                list.add(route)
            }

            _importedRoutes.value = list
            _activeRoute.value = list.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveRoutes() {
        try {
            val arr = JSONArray()
            _importedRoutes.value.forEach { route ->
                val obj = JSONObject()
                obj.put("id", route.id)
                obj.put("name", route.name)
                obj.put("sourceFileName", route.sourceFileName)
                obj.put("sourceFileType", route.sourceFileType)
                obj.put("projectId", route.projectId)
                obj.put("segmentId", route.segmentId)
                obj.put("createdAt", route.createdAt)
                obj.put("updatedAt", route.updatedAt)

                val coordsArr = JSONArray()
                route.coordinates.forEach { c ->
                    val cObj = JSONObject()
                    cObj.put("longitude", c.longitude)
                    cObj.put("latitude", c.latitude)
                    cObj.put("altitude", c.altitude)
                    coordsArr.put(cObj)
                }
                obj.put("coordinates", coordsArr)

                val polesArr = JSONArray()
                route.referencePoles.forEach { p ->
                    val pObj = JSONObject()
                    pObj.put("id", p.id)
                    pObj.put("routeId", p.routeId)
                    pObj.put("name", p.name)
                    pObj.put("latitude", p.latitude)
                    pObj.put("longitude", p.longitude)
                    pObj.put("altitude", p.altitude)
                    polesArr.put(pObj)
                }
                obj.put("referencePoles", polesArr)

                arr.put(obj)
            }
            routeFile.writeText(arr.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addAndActivateRoute(route: ImportedRoute) {
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        val finalRoute = route.copy(
            createdAt = if (route.createdAt.isEmpty()) now else route.createdAt,
            updatedAt = now
        )

        val list = _importedRoutes.value.toMutableList()
        list.removeAll { it.id == finalRoute.id }
        list.add(0, finalRoute)

        _importedRoutes.value = list
        _activeRoute.value = finalRoute
        saveRoutes()
    }

    fun setActiveRoute(routeId: String) {
        val found = _importedRoutes.value.find { it.id == routeId }
        if (found != null) {
            _activeRoute.value = found
        }
    }

    fun deleteRoute(routeId: String) {
        val list = _importedRoutes.value.filter { it.id != routeId }
        _importedRoutes.value = list
        if (_activeRoute.value?.id == routeId) {
            _activeRoute.value = list.firstOrNull()
        }
        saveRoutes()
    }

    fun clearAllRoutes() {
        routeFile.delete()
        _importedRoutes.value = emptyList()
        _activeRoute.value = null
    }
}
