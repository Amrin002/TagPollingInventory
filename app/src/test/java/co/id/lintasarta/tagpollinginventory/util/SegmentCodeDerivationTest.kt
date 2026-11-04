package co.id.lintasarta.tagpollinginventory.util

import co.id.lintasarta.tagpollinginventory.ui.screens.deriveCodesFromSegmentName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SegmentCodeDerivationTest {

    @Test
    fun testDeriveCodes_withStandardTwoWords() {
        val (city, loc) = deriveCodesFromSegmentName("Jayapura Sentani")
        assertEquals("JAY", city)
        assertEquals("SEN", loc)
    }

    @Test
    fun testDeriveCodes_withSingleWordDefaultsLocationToCTR() {
        val (city, loc) = deriveCodesFromSegmentName("Jayapura")
        assertEquals("JAY", city)
        assertEquals("CTR", loc)
    }

    @Test
    fun testDeriveCodes_withEmptyOrBlankReturnsFallback() {
        val (cityEmpty, locEmpty) = deriveCodesFromSegmentName("")
        assertEquals("JPR", cityEmpty)
        assertEquals("CTR", locEmpty)

        val (cityBlank, locBlank) = deriveCodesFromSegmentName("   ")
        assertEquals("JPR", cityBlank)
        assertEquals("CTR", locBlank)
    }

    @Test
    fun testDeriveCodes_withShortWordsPadsWithX() {
        val (city, loc) = deriveCodesFromSegmentName("AB CD")
        assertEquals("ABX", city)
        assertEquals("CDX", loc)

        val (singleShort, singleLoc) = deriveCodesFromSegmentName("A")
        assertEquals("AXX", singleShort)
        assertEquals("CTR", singleLoc)
    }

    @Test
    fun testDeriveCodes_withSpecialCharactersFiltersNonAlphanumeric() {
        val (city, loc) = deriveCodesFromSegmentName("JKT-01 @Sector-9")
        assertEquals("JKT", city)
        assertEquals("SEC", loc)
    }

    @Test
    fun testDeriveCodes_withMoreThanTwoWordsUsesFirstTwo() {
        val (city, loc) = deriveCodesFromSegmentName("Ambon Central Network Fiber")
        assertEquals("AMB", city)
        assertEquals("CEN", loc)
    }

    @Test
    fun testBusinessPoleCodeFormatCompliance() {
        val (city, loc) = deriveCodesFromSegmentName("Jayapura Sentani")
        val seq = 7
        val poleCode = "PL-$city-$loc-${String.format(Locale.US, "%03d", seq)}"
        
        // Sesuai standar arsitektur Section 9: PL-[CITY_CODE]-[LOCATION_CODE]-[SEQUENCE]
        assertEquals("PL-JAY-SEN-007", poleCode)
    }
}
