package com.sakinah.deen

import com.sakinah.deen.data.model.QuranTranslationResource
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.network.model.QuranTranslationCatalogDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranTranslationCatalogTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parseQuranComTranslations_readsIdNameLanguageAndIsoCode() {
        val body = """
            {"translations":[
              {"id":20,"name":"Saheeh International","author_name":"Saheeh International","language_name":"English","iso_code":"en"},
              {"id":131,"name":"Dr. Mustafa Khattab","author_name":"Dr. Mustafa Khattab","language_name":"English","iso_code":"en"}
            ]}
        """.trimIndent()

        val parsed = IslamicDataRepository.Companion.parseQuranComTranslations(body, json)

        assertEquals(2, parsed.size)
        assertEquals(20, parsed[0].id)
        assertEquals("English", parsed[0].languageName)
        assertEquals("en", parsed[0].isoCode)
        assertEquals("20", parsed[0].editionKey)
        assertEquals(QuranTranslationResource.SOURCE_QURAN_COM, parsed[0].source)
    }

    @Test
    fun parseAlQuranCloudTranslations_filtersToTextTranslations() {
        val body = """
            {"data":[
              {"identifier":"en.sahih","language":"en","name":"Saheeh International","englishName":"Saheeh International","format":"text","type":"translation"},
              {"identifier":"ar.muyassar","language":"ar","name":"Tafsir Muyassar","englishName":"Tafsir Muyassar","format":"text","type":"tafsir"}
            ]}
        """.trimIndent()

        val parsed = IslamicDataRepository.Companion.parseAlQuranCloudTranslations(body, json)

        assertEquals(1, parsed.size)
        assertEquals("en", parsed[0].isoCode)
        assertEquals("en.sahih", parsed[0].editionKey)
        assertEquals(QuranTranslationResource.SOURCE_ALQURAN_CLOUD, parsed[0].source)
        assertNotEquals(0, parsed[0].id)
    }

    @Test
    fun parseAlQuranCloudTranslations_assignsUniqueStableIds() {
        val body = """
            {"data":[
              {"identifier":"en.sahih","language":"en","name":"Saheeh International","englishName":"Saheeh International","format":"text","type":"translation"},
              {"identifier":"ur.jalandhry","language":"ur","name":"Jalandhry","englishName":"Fateh Muhammad Jalandhry","format":"text","type":"translation"}
            ]}
        """.trimIndent()

        val parsed = IslamicDataRepository.Companion.parseAlQuranCloudTranslations(body, json)

        assertEquals(2, parsed.size)
        assertNotEquals(0, parsed[0].id)
        assertNotEquals(parsed[0].id, parsed[1].id)
        assertEquals("Urdu", parsed[1].languageName)
    }

    @Test
    fun parseQuranApiTranslations_readsEditionsAndSourceKeys() {
        val body = """
            {
              "eng_abdelhaleem":{"name":"eng-abdelhaleem","author":"Abdul Haleem","language":"English"},
              "urd_jalandhry":{"name":"urd-jalandhry","author":"Fateh Muhammad Jalandhry","language":"Urdu"}
            }
        """.trimIndent()

        val parsed = IslamicDataRepository.Companion.parseQuranApiTranslations(body, json)

        assertEquals(2, parsed.size)
        assertEquals("eng-abdelhaleem", parsed[0].editionKey)
        assertEquals("Abdul Haleem", parsed[0].name)
        assertEquals("English", parsed[0].languageName)
        assertEquals(QuranTranslationResource.SOURCE_QURAN_API, parsed[0].source)
        assertNotEquals(0, parsed[0].id)
        assertNotEquals(parsed[0].id, parsed[1].id)
    }

    @Test
    fun defaultTranslationId_isSaheehInternational() {
        assertEquals(20, IslamicDataRepository.DEFAULT_TRANSLATION_ID)
    }
}