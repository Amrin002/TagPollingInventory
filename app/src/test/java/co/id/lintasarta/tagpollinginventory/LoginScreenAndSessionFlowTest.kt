package co.id.lintasarta.tagpollinginventory

import co.id.lintasarta.tagpollinginventory.data.auth.UserSession
import co.id.lintasarta.tagpollinginventory.location.CityLocationHelper
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.ScreenFlow
import org.junit.Assert.*
import org.junit.Test

class LoginScreenAndSessionFlowTest {

    @Test
    fun testCityLocationHelper_ambonCoordinateReturnsAmbon() {
        val lat = -3.6954
        val lng = 128.1814
        val resolved = CityLocationHelper.resolveCityName(context = null, latitude = lat, longitude = lng)
        assertEquals("Ambon", resolved)
    }

    @Test
    fun testCityLocationHelper_tualCoordinateReturnsTual() {
        val lat = -5.6288
        val lng = 132.7484
        val resolved = CityLocationHelper.resolveCityName(context = null, latitude = lat, longitude = lng)
        assertEquals("Tual", resolved)
    }

    @Test
    fun testCityLocationHelper_timikaCoordinateReturnsTimika() {
        val lat = -4.5467
        val lng = 136.8889
        val resolved = CityLocationHelper.resolveCityName(context = null, latitude = lat, longitude = lng)
        assertEquals("Timika", resolved)
    }

    @Test
    fun testCityLocationHelper_jayapuraCoordinateReturnsJayapura() {
        val lat = -2.5381
        val lng = 140.7054
        val resolved = CityLocationHelper.resolveCityName(context = null, latitude = lat, longitude = lng)
        assertEquals("Jayapura", resolved)
    }

    @Test
    fun testCityLocationHelper_defaultZeroReturnsAmbonRegionalCenter() {
        val resolved = CityLocationHelper.resolveCityName(context = null, latitude = 0.0, longitude = 0.0)
        assertEquals("Ambon", resolved)
    }

    @Test
    fun testUserSession_authenticationStateProperties() {
        val unauthenticated = UserSession(
            userId = 0,
            username = "",
            isLoggedIn = false
        )
        assertFalse(unauthenticated.isLoggedIn)
        assertTrue(unauthenticated.username.isEmpty())

        val authenticated = UserSession(
            userId = 1042,
            username = "surveyor_mpo",
            displayName = "Field Engineer MPO",
            isLoggedIn = true,
            isOfflineSession = false
        )
        assertTrue(authenticated.isLoggedIn)
        assertFalse(authenticated.isOfflineSession)
        assertEquals("surveyor_mpo", authenticated.username)
    }

    @Test
    fun testScreenFlow_containsLoginDestination() {
        val allFlows = ScreenFlow.values().map { it.name }
        assertTrue("ScreenFlow harus memuat LOGIN", allFlows.contains("LOGIN"))
        assertTrue("ScreenFlow harus memuat TAB_ROOT", allFlows.contains("TAB_ROOT"))
    }
}
