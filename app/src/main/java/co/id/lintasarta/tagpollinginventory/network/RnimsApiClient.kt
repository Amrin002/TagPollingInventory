package co.id.lintasarta.tagpollinginventory.network

import android.content.Context
import co.id.lintasarta.tagpollinginventory.data.model.AlpacaPraAssetDto
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RnimsUser(
    @SerializedName(value = "userId", alternate = ["user_id", "id"])
    val userId: Int = 1042,
    @SerializedName(value = "username", alternate = ["user_name"])
    val username: String? = "surveyor",
    val email: String? = "surveyor@lintasarta.co.id",
    @SerializedName(value = "displayName", alternate = ["display_name", "name", "full_name"])
    val displayName: String = "Field Engineer",
    @SerializedName(value = "areaId", alternate = ["area_id", "area"])
    val areaId: Int = 18,
    @SerializedName(value = "areaLabel", alternate = ["area_label", "area_name", "region"])
    val areaLabel: String = "MPO",
    val role: String? = "SURVEYOR",
    val roleLabel: String? = "Field Operations"
)

data class RnimsSessionResponse(
    val token: String? = null,
    val tokenType: String? = null,
    val user: RnimsUser,
    val csrfToken: String? = null,
    val expiresAt: String? = null
)

data class RnimsRouteCoordinateDto(
    val lat: Double,
    val lng: Double
)

data class RnimsSegmentDto(
    val id: Int,
    val segment_name: String,
    val area_id: Int,
    val org_site: String? = "",
    val dst_site: String? = "",
    val org_city: String? = "",
    val dst_city: String? = "",
    val distance: Double = 0.0,
    val route_path: List<RnimsRouteCoordinateDto>? = emptyList()
)

data class RnimsRoutesData(
    val area_id: Int,
    val area_name: String,
    val total_segments: Int,
    val routes: List<RnimsSegmentDto>?
)

data class RnimsRoutesApiResponse(
    val status: String,
    val code: Int,
    val message: String? = null,
    val data: RnimsRoutesData? = null
)

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val message: String
)

object RnimsApiClient {

    const val DEFAULT_USB_URL = "http://127.0.0.1:8000"
    const val DEFAULT_EMULATOR_URL = "http://10.0.2.2:8000"
    const val DEFAULT_WIFI_URL = "http://192.168.1.6:8000"
    const val DEFAULT_PROD_URL = "https://rnims.lintasarta.co.id/api"

    private val gson = Gson()

    /**
     * Uji koneksi ke endpoint /health bawaan backend produksi RNIMS
     */
    suspend fun testConnection(baseUrl: String): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val cleanUrl = baseUrl.trimEnd('/')
        val rootUrl = cleanUrl.removeSuffix("/api")

        val endpointsToTry = listOf(
            "$rootUrl/health",
            "$cleanUrl/health",
            if (cleanUrl.endsWith("/api")) "$cleanUrl/health" else "$cleanUrl/api/health"
        ).distinct()

        var lastError: Exception? = null
        for (endpoint in endpointsToTry) {
            try {
                val url = URL(endpoint)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 2500
                    requestMethod = "GET"
                }
                val responseCode = connection.responseCode
                val latency = System.currentTimeMillis() - startTime
                connection.disconnect()
                if (responseCode in 200..299) {
                    return@withContext Result.success(ConnectionTestResult(true, latency, "Terhubung (HTTP $responseCode) • ${latency}ms"))
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        val latency = System.currentTimeMillis() - startTime
        Result.failure(Exception("Gagal terhubung (${lastError?.localizedMessage ?: "Timeout"}) • ${latency}ms"))
    }

    /**
     * Login autentikasi resmi RNIMS: POST /auth/login atau /api/auth/login
     */
    suspend fun login(baseUrl: String, usernameOrEmail: String, password: String): Result<RnimsUser> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trimEnd('/')
                val rootUrl = cleanUrl.removeSuffix("/api")

                val loginEndpoints = listOf(
                    "$rootUrl/auth/login",
                    "$cleanUrl/auth/login",
                    if (cleanUrl.endsWith("/api")) "$cleanUrl/auth/login" else "$cleanUrl/api/auth/login"
                ).distinct()

                var lastResponseCode = 0
                var lastErrText = ""
                var lastException: Exception? = null

                for (endpoint in loginEndpoints) {
                    try {
                        val url = URL(endpoint)
                        val payload = mapOf(
                            "username" to usernameOrEmail,
                            "password" to password
                        )
                        val jsonBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)

                        val connection = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 4000
                            readTimeout = 4000
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                            setRequestProperty("Accept", "application/json")
                            setFixedLengthStreamingMode(jsonBytes.size)
                            doOutput = true
                        }

                        connection.outputStream.use { os ->
                            os.write(jsonBytes)
                            os.flush()
                        }

                        val responseCode = connection.responseCode
                        lastResponseCode = responseCode
                        if (responseCode in 200..299) {
                            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                            connection.disconnect()

                            val rawMap = try {
                                gson.fromJson(responseText, Map::class.java) as? Map<*, *>
                            } catch (e: Exception) {
                                null
                            }

                            val userFromJson = try {
                                val userObj = rawMap?.get("user")
                                if (userObj != null) {
                                    gson.fromJson(gson.toJson(userObj), RnimsUser::class.java)
                                } else null
                            } catch (e: Exception) {
                                null
                            }

                            val detectedAreaId = userFromJson?.areaId
                                ?: (rawMap?.get("area_id") as? Number)?.toInt()
                                ?: (rawMap?.get("areaId") as? Number)?.toInt()
                                ?: ((rawMap?.get("user") as? Map<*, *>)?.get("area_id") as? Number)?.toInt()
                                ?: ((rawMap?.get("user") as? Map<*, *>)?.get("areaId") as? Number)?.toInt()
                                ?: 18

                            val detectedAreaLabel = userFromJson?.areaLabel?.takeIf { it.isNotBlank() }
                                ?: (rawMap?.get("area_name") as? String)
                                ?: (rawMap?.get("areaLabel") as? String)
                                ?: ((rawMap?.get("user") as? Map<*, *>)?.get("area_name") as? String)
                                ?: ((rawMap?.get("user") as? Map<*, *>)?.get("areaLabel") as? String)
                                ?: "Area $detectedAreaId"

                            val detectedUserId = userFromJson?.userId
                                ?: (rawMap?.get("user_id") as? Number)?.toInt()
                                ?: (rawMap?.get("userId") as? Number)?.toInt()
                                ?: ((rawMap?.get("user") as? Map<*, *>)?.get("user_id") as? Number)?.toInt()
                                ?: ((rawMap?.get("user") as? Map<*, *>)?.get("userId") as? Number)?.toInt()
                                ?: 1042

                            val (calcRole, calcRoleLabel) = resolveUserRole(usernameOrEmail, userFromJson?.role, detectedUserId)
                            val calcDisplayName = resolveDisplayName(usernameOrEmail, userFromJson?.displayName, detectedAreaLabel)

                            val finalUser = userFromJson?.copy(
                                userId = detectedUserId,
                                areaId = detectedAreaId,
                                areaLabel = detectedAreaLabel,
                                role = calcRole,
                                roleLabel = calcRoleLabel,
                                displayName = calcDisplayName
                            ) ?: RnimsUser(
                                userId = detectedUserId,
                                username = if (usernameOrEmail.contains("@")) usernameOrEmail.substringBefore("@") else usernameOrEmail.ifBlank { "surveyor" },
                                email = if (usernameOrEmail.contains("@")) usernameOrEmail else "$usernameOrEmail@lintasarta.co.id",
                                displayName = calcDisplayName,
                                areaId = detectedAreaId,
                                areaLabel = detectedAreaLabel,
                                role = calcRole,
                                roleLabel = calcRoleLabel
                            )

                            return@withContext Result.success(finalUser)
                        } else {
                            lastErrText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                            connection.disconnect()
                        }
                    } catch (e: Exception) {
                        lastException = e
                    }
                }

                if (lastResponseCode != 0) {
                    Result.failure(Exception("Login gagal (Kode: $lastResponseCode): $lastErrText"))
                } else {
                    Result.failure(lastException ?: Exception("Gagal terhubung ke endpoint login"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun resolveUserRole(emailOrUsername: String, backendRole: String?, userId: Int? = null): Pair<String, String> {
        val clean = emailOrUsername.lowercase().trim()
        return when {
            userId == 1 || clean.startsWith("admin") || clean.contains("admin") ->
                "System Administrator" to "Super Admin"
            userId == 2 || clean.startsWith("manager") || clean.contains("manager") ->
                "Manager Operasional" to "Operations Management"
            userId == 3 || clean.contains("surveyor") || clean.contains("amrin") || clean.contains("engineer") || clean.startsWith("employee") ->
                "Field Engineer MPO" to "Field Operations"
            !backendRole.isNullOrBlank() && !backendRole.equals("SURVEYOR", true) ->
                backendRole to "Corporate User"
            else ->
                "Field Engineer MPO" to "Field Operations"
        }
    }

    fun resolveDisplayName(emailOrUsername: String, backendName: String?, areaLabel: String): String {
        val clean = emailOrUsername.lowercase().trim()
        if (!backendName.isNullOrBlank() && !backendName.equals("Field Engineer", true) && !backendName.equals("Field Surveyor", true) && !backendName.startsWith("Surveyor", true) && !backendName.contains("mpo", ignoreCase = true)) {
            return backendName
        }
        return when {
            clean.contains("admin") -> "System Administrator"
            clean.contains("manager") -> "Manager Operasional"
            clean.contains("amrin") -> "Amrin (Field Engineer)"
            clean.contains("surveyor") -> "Surveyor MPO (Area 18)"
            clean.contains("employee") -> "Field Engineer Employee"
            else -> backendName?.takeIf { it.isNotBlank() } ?: "Field Engineer $areaLabel"
        }
    }

    /**
     * Tarik rute & segmen area MPO resmi RNIMS: GET /api/msfo/ta/routes?area_id=18
     */
    suspend fun fetchRoutesByArea(baseUrl: String, areaId: Int = 18): Result<List<RnimsSegmentDto>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trimEnd('/')
                val endpoint = if (cleanUrl.endsWith("/api")) {
                    "$cleanUrl/msfo/ta/routes?area_id=$areaId"
                } else {
                    "$cleanUrl/api/msfo/ta/routes?area_id=$areaId"
                }

                val url = URL(endpoint)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val segments = try {
                        val apiResponse = gson.fromJson(responseText, RnimsRoutesApiResponse::class.java)
                        apiResponse.data?.routes ?: emptyList()
                    } catch (e: Exception) {
                        val type = object : TypeToken<List<RnimsSegmentDto>>() {}.type
                        gson.fromJson<List<RnimsSegmentDto>>(responseText, type) ?: emptyList()
                    }
                    connection.disconnect()
                    Result.success(segments)
                } else {
                    val errDetail = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    connection.disconnect()
                    Result.failure(Exception("HTTP $responseCode: $errDetail"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Tarik Tiang Eksisting Project Alpaca PRA:
     * GET /api/otdr/asset-assurance/map?po_id={poId}
     * Menghasilkan daftar aset tiang eksisting: asset_uid, asset_class, geometry: [lng, lat], match_status
     */
    suspend fun fetchAlpacaExistingPoles(baseUrl: String, poId: String): Result<List<AlpacaPraAssetDto>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trimEnd('/')
                val endpoint = if (cleanUrl.endsWith("/api")) {
                    "$cleanUrl/otdr/asset-assurance/map?po_id=$poId"
                } else {
                    "$cleanUrl/api/otdr/asset-assurance/map?po_id=$poId"
                }

                val url = URL(endpoint)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    val type = object : TypeToken<List<AlpacaPraAssetDto>>() {}.type
                    val poles: List<AlpacaPraAssetDto> = try {
                        gson.fromJson(responseText, type) ?: emptyList()
                    } catch (_: Exception) {
                        // In case response is wrapped in { "data": [...] }
                        val rawMap = gson.fromJson(responseText, Map::class.java)
                        val dataObj = rawMap["data"] ?: rawMap["assets"]
                        gson.fromJson(gson.toJson(dataObj), type) ?: emptyList()
                    }
                    Result.success(poles)
                } else {
                    connection.disconnect()
                    Result.failure(Exception("Gagal mengambil aset Alpaca PRA (HTTP $responseCode)"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Tarik Segmen Khusus Alpaca Project:
     * GET /api/dev_pgsql/segments-simple?alpacaProject=Yes atau /api/otdr/sia.geojson?area=MPO
     */
    suspend fun fetchAlpacaSegments(baseUrl: String, area: String = "MPO"): Result<List<RnimsSegmentDto>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trimEnd('/')
                val endpoint = if (cleanUrl.endsWith("/api")) {
                    "$cleanUrl/dev_pgsql/segments-simple?alpacaProject=Yes&area=$area"
                } else {
                    "$cleanUrl/api/dev_pgsql/segments-simple?alpacaProject=Yes&area=$area"
                }

                val url = URL(endpoint)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    val type = object : TypeToken<List<RnimsSegmentDto>>() {}.type
                    val list: List<RnimsSegmentDto> = try {
                        gson.fromJson(responseText, type) ?: emptyList()
                    } catch (_: Exception) {
                        val apiResponse = gson.fromJson(responseText, RnimsRoutesApiResponse::class.java)
                        apiResponse.data?.routes ?: emptyList()
                    }
                    Result.success(list)
                } else {
                    connection.disconnect()
                    Result.failure(Exception("Gagal mengambil segmen Alpaca (HTTP $responseCode)"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Kirim data tiang survei ke server (Sync Batch Payload ke eNims / Alpaca PRA)
     */
    suspend fun syncPolesBatch(
        baseUrl: String,
        token: String?,
        areaId: Int,
        polesPayload: List<Map<String, Any?>>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = baseUrl.trimEnd('/')
            val endpointsToTry = listOf(
                // 1. Endpoint Utama: Redis Realtime Data Storage (port 8000 test-api)
                if (cleanUrl.endsWith("/api")) "$cleanUrl/redis/data" else "$cleanUrl/api/redis/data",
                // 2. Fallbacks: MSFO TA & OTDR Asset Assurance
                if (cleanUrl.endsWith("/api")) "$cleanUrl/msfo/ta/sync" else "$cleanUrl/api/msfo/ta/sync",
                if (cleanUrl.endsWith("/api")) "$cleanUrl/otdr/asset-assurance/sync" else "$cleanUrl/api/otdr/asset-assurance/sync"
            )

            var lastCode = 0
            var lastErr = ""

            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

            for (endpoint in endpointsToTry) {
                try {
                    val url = URL(endpoint)
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6000
                        readTimeout = 10000
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                        if (!token.isNullOrEmpty()) {
                            setRequestProperty("Authorization", "Bearer $token")
                        }
                        doOutput = true
                    }

                    val body: Any = if (endpoint.contains("/redis/data")) {
                        mapOf(
                            "path" to "survey:poles:area:$areaId",
                            "data" to mapOf(
                                "area_id" to areaId,
                                "total_poles" to polesPayload.size,
                                "timestamp" to nowIso,
                                "poles" to polesPayload
                            ),
                            "type" to "json"
                        )
                    } else {
                        mapOf(
                            "area_id" to areaId,
                            "total_poles" to polesPayload.size,
                            "poles" to polesPayload
                        )
                    }
                    val jsonPayload = gson.toJson(body)

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonPayload)
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    lastCode = responseCode
                    if (responseCode in 200..299) {
                        connection.disconnect()
                        return@withContext Result.success(polesPayload.size)
                    } else {
                        lastErr = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        connection.disconnect()
                    }
                } catch (_: Exception) {
                    // Try next endpoint
                }
            }

            Result.failure(Exception("Sync server menolak data (HTTP $lastCode): $lastErr"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fallback Offline: Membaca 143 segmen MPO dari assets lokal bawaan APK
     */
    fun loadBundledMpoSegments(context: Context): List<RnimsSegmentDto> {
        return try {
            context.assets.open("mpo_segments.json").use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                val type = object : TypeToken<List<RnimsSegmentDto>>() {}.type
                gson.fromJson(reader, type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
