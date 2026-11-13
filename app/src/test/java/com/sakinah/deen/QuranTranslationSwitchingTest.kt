package com.sakinah.deen

import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.fakes.FakeQuranDao
import com.sakinah.deen.fakes.FakeHadithDao
import com.sakinah.deen.fakes.FakeTafsirDao
import com.sakinah.deen.network.IslamicDataRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranTranslationSwitchingTest {

    @Test
    fun syncSurah_fetchesRequestedTranslationAndCachesIt() = kotlinx.coroutines.runBlocking {
        val quranDao = FakeQuranDao()
        val client = HttpClient(MockEngine { request ->
            val url = request.url.toString()
            val body = when {
                url.contains("chapters?language=en") -> """{"chapters":[{"id":1,"name_simple":"Al-Fatihah","name_arabic":"الفاتحة","translated_name":{"name":"The Opening"},"verses_count":1,"revelation_place":"makkah"}]}"""
                url.contains("verses/by_chapter/1") -> {
                    assertTrue(url.contains("translations=131"))
                    """{"verses":[{"id":1,"verse_number":1,"verse_key":"1:1","text_uthmani":"بِسْمِ","translations":[{"text":"In the name of Allah"}]}]}"""
                }
                url.contains("chapter_recitations") -> """{"audio_file":{"audio_url":"https://audio/1.mp3"}}"""
                else -> "{}"
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        })

        val repo = IslamicDataRepository(
            quranDao = quranDao,
            hadithDao = FakeHadithDao(),
            tafsirDao = FakeTafsirDao(),
            client = client,
            json = Json { ignoreUnknownKeys = true; isLenient = true }
        )

        val result = repo.syncSurah(1, translationId = 131, withAudio = false)

        assertEquals(1, result.getOrThrow())
        assertEquals(1, quranDao.translationWrites)
        assertTrue(quranDao.translations.values.any { it.translationId == 131 && it.translation == "In the name of Allah" })
        assertTrue(quranDao.ayahs.values.any { it.surahNumber == 1 && it.ayahNumber == 1 })
    }
}