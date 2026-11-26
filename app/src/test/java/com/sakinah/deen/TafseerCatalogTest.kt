package com.sakinah.deen

import com.sakinah.deen.data.model.TafsirText
import com.sakinah.deen.data.repository.TafseerPackage
import com.sakinah.deen.network.IslamicDataRepository
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog parsing and language filtering for the multi-language tafsir library. */
class TafseerCatalogTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val catalogJson = """
        {
          "tafsirs": [
            {"id":169,"name":"Ibn Kathir (Abridged)","author_name":"Hafiz Ibn Kathir","slug":"en-tafisr-ibn-kathir","language_name":"english","translated_name":{"name":"Ibn Kathir (Abridged)","language_name":"english"}},
            {"id":160,"name":"Tafsir Ibn Kathir","author_name":"Hafiz Ibn Kathir","slug":"tafseer-ibn-e-kaseer-urdu","language_name":"urdu","translated_name":{"name":"Tafsir Ibn Kathir","language_name":"english"}},
            {"id":804,"name":"Rebar Kurdish Tafsir","author_name":"Rebar Kurdish Tafsir","slug":"kurd-tafsir-rebar","language_name":"Kurdish","translated_name":{"name":"Rebar Kurdish Tafsir","language_name":"english"}},
            {"id":165,"name":"Tafsir Ahsanul Bayaan","author_name":"Bayaan Foundation","slug":"bn-tafsir-ahsanul-bayaan","language_name":"bengali","translated_name":{"name":"Tafsir Ahsanul Bayaan","language_name":"english"}},
            {"id":170,"name":"Al-Sa'di","author_name":"Saddi","slug":"ru-tafseer-al-saddi","language_name":"russian","translated_name":{"name":"Al-Sa'di","language_name":"english"}},
            {"id":15,"name":"Tafsir al-Tabari","author_name":"Tabari","slug":"ar-tafsir-al-tabari","language_name":"arabic","translated_name":{"name":"Tafsir al-Tabari","language_name":"english"}}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesResourcesTafseersFields() {
        val catalog = IslamicDataRepository.parseTafseerCatalog(catalogJson, json)

        assertEquals(6, catalog.size)
        val urdu = catalog.first { it.id == 160 }
        assertEquals("Tafsir Ibn Kathir", urdu.name)
        assertEquals("Hafiz Ibn Kathir", urdu.authorName)
        assertEquals("tafseer-ibn-e-kaseer-urdu", urdu.slug)
        assertEquals("urdu", urdu.languageName)
        assertEquals("Tafsir Ibn Kathir", urdu.translatedName)
        assertEquals("ur", urdu.languageCode)

        val kurdish = catalog.first { it.id == 804 }
        assertEquals("Kurdish", kurdish.languageName)
        assertEquals("ku", kurdish.languageCode)
        assertEquals("Rebar Kurdish Tafsir", kurdish.authorName)
    }

    @Test
    fun languageCodesNormalizeAllRequestedLanguages() {
        assertEquals("en", IslamicDataRepository.tafsirLanguageCode("english"))
        assertEquals("ar", IslamicDataRepository.tafsirLanguageCode("Arabic"))
        assertEquals("ur", IslamicDataRepository.tafsirLanguageCode("urdu"))
        assertEquals("bn", IslamicDataRepository.tafsirLanguageCode("bengali"))
        assertEquals("ru", IslamicDataRepository.tafsirLanguageCode("russian"))
        assertEquals("ku", IslamicDataRepository.tafsirLanguageCode("Kurdish"))
        assertEquals("ur", IslamicDataRepository.tafsirLanguageCode("ur"))
    }

    @Test
    fun catalogAndFallbackFilterByLanguage() {
        val packages = (TafseerPackage.fallbackPackages() +
            TafseerPackage.fromCatalog(IslamicDataRepository.parseTafseerCatalog(catalogJson, json)))
            .distinctBy { it.tafsirId }

        val languages = packages.map { it.languageCode }.toSet()
        assertTrue(setOf("en", "ar", "ur", "bn", "ru", "ku").all(languages::contains))

        val urdu = packages.filter { it.languageCode == "ur" }
        assertTrue(urdu.any { it.tafsirId == 160 && it.title == "Tafsir Ibn Kathir" })
        assertTrue(urdu.any { it.tafsirId == 159 })

        val kurdish = packages.filter { it.languageCode == "ku" }
        assertEquals(listOf(804), kurdish.map { it.tafsirId })

        // Fallback editions keep their legacy package ids while new catalog editions use tafsir_<id>.
        assertTrue(packages.any { it.id == "ibn_kathir_en" && it.tafsirId == 169 })
        assertTrue(packages.any { it.id == "tafsir_804" && it.tafsirId == 804 })

        assertTrue(TafsirText.isRtl("ar"))
        assertTrue(TafsirText.isRtl("ur"))
        assertTrue(TafsirText.isRtl("ku"))
        assertFalse(TafsirText.isRtl("en"))
        assertEquals("Tafsir without markup", TafsirText.clean("<p>Tafsir <span>without</span> markup</p>"))
    }
}
