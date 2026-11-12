package com.sakinah.deen

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.prayer.SunniAuthority
import com.sakinah.deen.data.prayer.SunniMethodMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/** Country → AlAdhan calculation-method mapping and manual-override precedence. */
class SunniMethodMapperTest {

    @Test
    fun countryCode_mapsToNationalSunniAuthority() {
        val expected = mapOf(
            "SA" to 4,   // Umm Al-Qura University, Makkah
            "QA" to 10,  // Qatar
            "AE" to 16,  // Dubai
            "KW" to 9,   // Kuwait
            "EG" to 5,   // Egyptian General Authority of Survey
            "TR" to 13,  // Diyanet İşleri Başkanlığı
            "MY" to 17,  // JAKIM
            "SG" to 11,  // MUIS
            "PK" to 1,   // University of Islamic Sciences, Karachi
            "IN" to 1,
            "BD" to 1,
            "US" to 2,   // ISNA
            "CA" to 2
        )

        expected.forEach { (code, methodId) ->
            assertEquals("country $code", methodId, SunniMethodMapper.fromCountryCode(code).methodId)
        }
    }

    @Test
    fun unknownOrMissingCountry_fallsBackToMuslimWorldLeague() {
        listOf(null, "", "  ", "XX", "GB", "ZZ").forEach { code ->
            assertEquals(
                "country ${code ?: "<null>"}",
                SunniAuthority.MUSLIM_WORLD_LEAGUE,
                SunniMethodMapper.fromCountryCode(code)
            )
        }
        assertEquals(3, SunniMethodMapper.fromCountryCode(null).methodId)
    }

    @Test
    fun countryCode_isCaseInsensitiveAndTrimmed() {
        assertEquals(SunniAuthority.QATAR, SunniMethodMapper.fromCountryCode(" qa "))
        assertEquals(SunniAuthority.ISNA, SunniMethodMapper.fromCountryCode("us"))
    }

    @Test
    fun manualSelection_overridesTheCountryMapping() {
        assertEquals(
            SunniAuthority.ISNA,
            SunniMethodMapper.resolve("SA", CalculationMethod.NORTH_AMERICA)
        )
        assertEquals(
            SunniAuthority.EGYPTIAN,
            SunniMethodMapper.resolve("QA", CalculationMethod.EGYPTIAN)
        )
    }

    @Test
    fun withoutOverrides_theCountryAuthorityApplies() {
        assertEquals(SunniAuthority.UMM_AL_QURA, SunniMethodMapper.resolve("SA", null))
        assertEquals(SunniAuthority.JAKIM, SunniMethodMapper.resolve("MY", null))
        assertEquals(SunniAuthority.MUSLIM_WORLD_LEAGUE, SunniMethodMapper.resolve(null, null))
    }

    @Test
    fun asrSchool_followsTheMadhhab() {
        assertEquals(0, SunniMethodMapper.schoolId(Madhhab.SHAFI_HANBALI_MALIKI))
        assertEquals(1, SunniMethodMapper.schoolId(Madhhab.HANAFI))
    }

    @Test
    fun localFallbackMethod_roundTripsEveryManualChoice() {
        CalculationMethod.entries.forEach { method ->
            val authority = SunniMethodMapper.fromOverride(method)
            assertEquals(
                "round trip for $method",
                method,
                SunniMethodMapper.localCalculationMethod(authority)
            )
        }
    }

    @Test
    fun authoritiesWithoutALocalEquivalent_reuseTheClosestEngineMethod() {
        assertEquals(
            CalculationMethod.MUSLIM_WORLD_LEAGUE,
            SunniMethodMapper.localCalculationMethod(SunniAuthority.DIYANET)
        )
        assertEquals(
            CalculationMethod.KARACHI,
            SunniMethodMapper.localCalculationMethod(SunniAuthority.JAKIM)
        )
        assertEquals(
            CalculationMethod.MUSLIM_WORLD_LEAGUE,
            SunniMethodMapper.localCalculationMethod(SunniAuthority.KUWAIT)
        )
    }
}
