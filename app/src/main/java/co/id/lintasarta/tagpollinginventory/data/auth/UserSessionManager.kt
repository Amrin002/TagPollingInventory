package co.id.lintasarta.tagpollinginventory.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.id.lintasarta.tagpollinginventory.network.RnimsApiClient
import co.id.lintasarta.tagpollinginventory.network.RnimsUser
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserSession(
    val userId: Int = 1042,
    val username: String = "surveyor_mpo",
    val email: String = "surveyor.mpo@lintasarta.co.id",
    val displayName: String = "Field Engineer MPO",
    val areaId: Int = 18,
    val areaLabel: String = "MPO",
    val role: String = "SURVEYOR",
    val roleLabel: String = "Field Operations",
    val token: String? = "bearer_offline_token_mpo",
    val isLoggedIn: Boolean = true,
    val isOfflineSession: Boolean = true,
    val lastLoginTime: String = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
)

class UserSessionManager(private val context: Context) {

    private val gson = Gson()
    private val prefs: SharedPreferences = createSecurePrefs(context)

    private fun createSecurePrefs(ctx: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                ctx,
                "secure_user_session_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("UserSessionManager", "Android KeyStore / EncryptedSharedPreferences unavailable (${e.message}). Falling back to standard prefs.", e)
            ctx.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)
        }
    }

    private val _currentSession = MutableStateFlow(loadSession())
    val currentSession: StateFlow<UserSession> = _currentSession.asStateFlow()

    private fun loadSession(): UserSession {
        var json = prefs.getString("user_session_json", null)

        // Seamless migration: Check legacy cleartext SharedPreferences if not found in encrypted storage
        if (json == null) {
            try {
                val oldPrefs = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)
                val oldJson = oldPrefs.getString("user_session_json", null)
                if (!oldJson.isNullOrBlank()) {
                    json = oldJson
                    prefs.edit().putString("user_session_json", oldJson).apply()
                    oldPrefs.edit().remove("user_session_json").apply()
                    Log.i("UserSessionManager", "Migrated legacy user session to EncryptedSharedPreferences successfully.")
                }
            } catch (e: Exception) {
                Log.w("UserSessionManager", "Error checking legacy user_session_prefs: ${e.message}")
            }
        }

        if (json != null) {
            try {
                val parsed = gson.fromJson(json, UserSession::class.java)
                if (parsed != null) {
                    return parsed.copy(isLoggedIn = true, isOfflineSession = true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Mode Mandiri Offline: Default langsung aktif ke sesi surveyor tanpa blokir login
        return UserSession(
            userId = 1042,
            username = "surveyor_offline",
            email = "surveyor@lintasarta.co.id",
            displayName = "Field Surveyor",
            areaId = 18,
            areaLabel = "MPO",
            role = "SURVEYOR",
            roleLabel = "Field Operations",
            token = "bearer_offline_token",
            isLoggedIn = true,
            isOfflineSession = true,
            lastLoginTime = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        )
    }

    fun hasActiveSession(): Boolean = true

    private fun saveSessionToPrefs(session: UserSession) {
        prefs.edit().putString("user_session_json", gson.toJson(session)).apply()
    }

    suspend fun loginOnline(baseUrl: String, emailOrUsername: String, pass: String): Result<UserSession> {
        val result = RnimsApiClient.login(baseUrl, emailOrUsername, pass)
        return result.map { rnimsUser ->
            val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
            val effectiveToken = rnimsUser.token?.takeIf { it.isNotBlank() } ?: "bearer_token_online_${rnimsUser.userId}"
            val session = UserSession(
                userId = rnimsUser.userId,
                username = rnimsUser.username ?: if (emailOrUsername.contains("@")) emailOrUsername.substringBefore("@") else emailOrUsername,
                email = rnimsUser.email ?: if (emailOrUsername.contains("@")) emailOrUsername else "$emailOrUsername@lintasarta.co.id",
                displayName = rnimsUser.displayName.ifEmpty { "Field Engineer ${rnimsUser.areaLabel}" },
                areaId = rnimsUser.areaId,
                areaLabel = rnimsUser.areaLabel,
                role = rnimsUser.role ?: "Field Engineer MPO",
                roleLabel = rnimsUser.roleLabel ?: "Field Operations",
                token = effectiveToken,
                isLoggedIn = true,
                isOfflineSession = false,
                lastLoginTime = now
            )
            _currentSession.value = session
            saveSessionToPrefs(session)
            session
        }
    }

    fun updateArea(areaId: Int, areaLabel: String) {
        val current = _currentSession.value
        val updated = current.copy(
            areaId = areaId,
            areaLabel = areaLabel,
            displayName = if (current.displayName.startsWith("Field Engineer")) "Field Engineer $areaLabel" else current.displayName
        )
        _currentSession.value = updated
        saveSessionToPrefs(updated)
    }

    fun loginOffline(emailOrUser: String = "surveyor.mpo@lintasarta.co.id", pin: String = "1234", areaId: Int = 18, areaLabel: String = "MPO"): UserSession {
        val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        val (calcRole, calcRoleLabel) = RnimsApiClient.resolveUserRole(emailOrUser, null, 3)
        val session = UserSession(
            userId = 1042,
            username = if (emailOrUser.contains("@")) emailOrUser.substringBefore("@") else emailOrUser,
            email = if (emailOrUser.contains("@")) emailOrUser else "$emailOrUser@lintasarta.co.id",
            displayName = "Offline Surveyor ($areaLabel)",
            areaId = areaId,
            areaLabel = areaLabel,
            role = calcRole,
            roleLabel = calcRoleLabel,
            token = "bearer_offline_token_$areaId",
            isLoggedIn = true,
            isOfflineSession = true,
            lastLoginTime = now
        )
        _currentSession.value = session
        saveSessionToPrefs(session)
        return session
    }

    fun logout() {
        // Mode Standalone: Reset sesi tetap dalam kondisi surveyor offline aktif
        val resetSession = UserSession(
            userId = 1042,
            username = "surveyor_offline",
            email = "surveyor@lintasarta.co.id",
            displayName = "Field Surveyor",
            areaId = _currentSession.value.areaId,
            areaLabel = _currentSession.value.areaLabel,
            role = "SURVEYOR",
            roleLabel = "Field Operations",
            token = "bearer_offline_token",
            isLoggedIn = true,
            isOfflineSession = true,
            lastLoginTime = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
        )
        _currentSession.value = resetSession
        saveSessionToPrefs(resetSession)
    }

    fun getGDriveWebhookUrl(): String {
        return prefs.getString("gdrive_webhook_url", RnimsApiClient.DEFAULT_GDRIVE_WEBHOOK_URL)
            ?: RnimsApiClient.DEFAULT_GDRIVE_WEBHOOK_URL
    }

    fun setGDriveWebhookUrl(url: String) {
        prefs.edit().putString("gdrive_webhook_url", url.trim()).apply()
    }
}
