package co.id.lintasarta.tagpollinginventory.data.auth

import android.content.Context
import android.content.SharedPreferences
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
    val role: String = "Field Engineer MPO",
    val roleLabel: String = "Field Operations",
    val token: String? = "bearer_offline_token_mpo",
    val isLoggedIn: Boolean = true,
    val isOfflineSession: Boolean = true,
    val lastLoginTime: String = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
)

class UserSessionManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _currentSession = MutableStateFlow(loadSession())
    val currentSession: StateFlow<UserSession> = _currentSession.asStateFlow()

    private fun loadSession(): UserSession {
        val json = prefs.getString("user_session_json", null)
        if (json != null) {
            try {
                return gson.fromJson(json, UserSession::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // First-time install or after logout: Requires login online once
        return UserSession(
            userId = 0,
            username = "",
            email = "",
            displayName = "",
            areaId = 18,
            areaLabel = "MPO",
            role = "Field Engineer MPO",
            roleLabel = "Field Operations",
            token = null,
            isLoggedIn = false,
            isOfflineSession = false,
            lastLoginTime = ""
        )
    }

    fun hasActiveSession(): Boolean {
        return _currentSession.value.isLoggedIn && (_currentSession.value.username.isNotBlank() || _currentSession.value.email.isNotBlank())
    }

    private fun saveSessionToPrefs(session: UserSession) {
        prefs.edit().putString("user_session_json", gson.toJson(session)).apply()
    }

    suspend fun loginOnline(baseUrl: String, emailOrUsername: String, pass: String): Result<UserSession> {
        val result = RnimsApiClient.login(baseUrl, emailOrUsername, pass)
        return result.map { rnimsUser ->
            val now = SimpleDateFormat("dd MMM yyyy — HH:mm", Locale.US).format(Date())
            val session = UserSession(
                userId = rnimsUser.userId,
                username = rnimsUser.username ?: if (emailOrUsername.contains("@")) emailOrUsername.substringBefore("@") else emailOrUsername,
                email = rnimsUser.email ?: if (emailOrUsername.contains("@")) emailOrUsername else "$emailOrUsername@lintasarta.co.id",
                displayName = rnimsUser.displayName.ifEmpty { "Field Engineer ${rnimsUser.areaLabel}" },
                areaId = rnimsUser.areaId,
                areaLabel = rnimsUser.areaLabel,
                role = rnimsUser.role ?: "Field Engineer MPO",
                roleLabel = rnimsUser.roleLabel ?: "Field Operations",
                token = "bearer_token_online_${rnimsUser.userId}",
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
        // Revert to offline fallback MPO
        val guest = UserSession(
            userId = 0,
            username = "",
            email = "",
            displayName = "Offline Surveyor",
            areaId = 18,
            areaLabel = "MPO",
            role = "GUEST",
            roleLabel = "Field Operations",
            token = null,
            isLoggedIn = false,
            isOfflineSession = true,
            lastLoginTime = ""
        )
        _currentSession.value = guest
        prefs.edit().remove("user_session_json").apply()
    }
}
