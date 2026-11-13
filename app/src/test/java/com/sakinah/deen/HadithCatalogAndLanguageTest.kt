package com.sakinah.deen

import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.fakes.FakeHadithDao
import com.sakinah.deen.network.IslamicDataRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HadithCatalogAndLanguageTest {

    @Test
    fun parseHadithCatalog_readsBooksLanguagesAndSections() {
        val editions = """
            {"bukhari":{"name":"Sahih al-Bukhari","collection":[
              {"name":"eng-bukhari","book":"bukhari","language":"English","has_sections":true,"direction":"ltr","linkmin":"x"},
              {"name":"ara-bukhari","book":"bukhari","language":"Arabic","has_sections":true,"direction":"rtl","linkmin":"y"}
            ]}}
        """.trimIndent()
        val info = """
            {"bukhari":{"metadata":{"name":"Sahih al-Bukhari","sections":{"1":"Revelation","2":"Belief"}}}}
        """.trimIndent()

        val parsed = IslamicDataRepository.Companion.parseHadithCatalog(editions, info, Json { ignoreUnknownKeys = true })

        assertEquals(1, parsed.size)
        assertEquals("bukhari", parsed[0].bookKey)
        assertEquals(2, parsed[0].editions.size)
        assertEquals("eng", parsed[0].editions[0].languageCode)
        assertEquals("Revelation", parsed[0].sections[1])
    }

    @Test
    fun syncHadithBook_usesRequestedLanguageAndJoinsArabic() = runBlocking {
        val hadithDao = FakeHadithDao()
        val client = HttpClient(MockEngine { request ->
            val url = request.url.toString()
            val body = when {
                url.contains("editions/fra-bukhari.min.json") -> """
                    {"metadata":{"name":"Sahih al-Bukhari","sections":{"1":"Revelation"}},"hadiths":[
                      {"hadithnumber":1,"arabicnumber":1,"text":"French text","grades":[],"reference":{"book":1,"hadith":1}}
                    ]}
                """.trimIndent()
                url.contains("editions/ara-bukhari.min.json") -> """
                    {"metadata":{"name":"Sahih al-Bukhari","sections":{"1":"Revelation"}},"hadiths":[
                      {"hadithnumber":1,"arabicnumber":1,"text":"Arabic text","grades":[],"reference":{"book":1,"hadith":1}}
                    ]}
                """.trimIndent()
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })

        val repo = IslamicDataRepository(
            quranDao = com.sakinah.deen.fakes.FakeQuranDao(),
            hadithDao = hadithDao,
            tafsirDao = com.sakinah.deen.fakes.FakeTafsirDao(),
            client = client,
            json = Json { ignoreUnknownKeys = true; isLenient = true }
        )

        val result = repo.syncHadithBook("bukhari", "fra")

        assertEquals(1, result.getOrThrow())
        val cached = hadithDao.observeHadiths("bukhari", "fra").first()
        assertEquals(1, cached.size)
        assertEquals("French text", cached[0].englishText)
        assertEquals("Arabic text", cached[0].arabicText)
    }

    @Test
    fun sectionedRoomQuery_returnsOnlyRequestedSection() = runBlocking {
        val dao = FakeHadithDao()
        dao.upsertAll(listOf(
            HadithEntryEntity(bookKey = "bukhari", languageCode = "eng", hadithNumber = 1, bookTitle = "Bukhari", sectionNumber = 1, englishText = "one"),
            HadithEntryEntity(bookKey = "bukhari", languageCode = "eng", hadithNumber = 2, bookTitle = "Bukhari", sectionNumber = 2, englishText = "two")
        ))

        val sectionOne = dao.observeHadithsBySection("bukhari", "eng", 1).first()

        assertEquals(1, sectionOne.size)
        assertEquals(1, sectionOne[0].hadithNumber)
    }
}
