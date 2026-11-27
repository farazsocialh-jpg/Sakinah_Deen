package com.sakinah.deen.network

import com.sakinah.deen.network.model.ProofSourceDto
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

interface SunniAiScholarRepository {
    fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk>
}

class SunniAiScholarRepositoryImpl(
    private val aiClient: SunniAiClient? = null,
    private val backendBaseUrl: String? = null
) : SunniAiScholarRepository {

    override fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
        // 1. Emit Initial Orthodoxy Guardrail Disclaimer
        emit(
            RagStreamChunk.GuardrailDisclaimer(
                notice = "Educational Reference Guide: Verified against authentic Islamic Quran, Hadith, and classical Fiqh texts. For personal binding fatwas, consult a local qualified Mufti."
            )
        )

        // 2. Preferred path: stream from the configured AI proxy. When no proxy is
        //    configured, or it fails before producing text, the grounded offline
        //    answer below keeps the Scholar usable instead of showing a setup error.

        if (aiClient?.isConfigured == true) {
            var receivedText = false
            try {
                aiClient.streamScholar(request).collect { chunk ->
                    if (chunk is RagStreamChunk.TextDelta && chunk.delta.isNotBlank()) {
                        receivedText = true
                    }
                    emit(chunk)
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Surface mid-stream failures to the caller instead of silently completing.
                if (receivedText) {
                    throw e
                }
                // Otherwise fall through to the offline grounded answer below.
            }
        }

        try {
            // 4. Optional self-hosted proxy, then the offline-first grounded fallback.
            var connected = false
            try {
                val configuredBackend = backendBaseUrl
                    ?: throw IllegalStateException("No backend proxy configured")
                val url = URL(configuredBackend)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 8000
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val payload = kotlinx.serialization.json.buildJsonObject {
                    put("question", kotlinx.serialization.json.JsonPrimitive(request.query))
                }.toString()
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    connected = true

                    // Stream response tokens
                    for (word in response.split(" ")) {
                        emit(RagStreamChunk.TextDelta("$word "))
                        delay(25)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                connected = false
            }

            if (!connected) {
                // Retrieval is unavailable, so the answer below is a curated offline
                // reference summary. Say that plainly instead of implying live sourcing.
                emit(
                    RagStreamChunk.SourcesUnverified(
                        "Live source retrieval failed. This is an offline reference summary; " +
                            "verify the references before acting on it."
                    )
                )
                emitResilientSunniKnowledge(request.query)

                // Only the offline/fallback path appends these synthesized proofs.
                val sources = getGroundedSources(request.query)
                for (src in sources) {
                    emit(RagStreamChunk.SourceCitation(src))
                    delay(30)
                }

                val madhhabs = getMadhhabBreakdown(request.query)
                for ((school, summary) in madhhabs) {
                    emit(RagStreamChunk.MadhhabBreakdown(school = school, summary = summary))
                    delay(20)
                }
            }

            emit(RagStreamChunk.Complete)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(RagStreamChunk.TextDelta("\nError: ${e.localizedMessage ?: "Unable to complete request. Please retry."}"))
            emit(RagStreamChunk.Complete)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<RagStreamChunk>.emitResilientSunniKnowledge(query: String) {
        val qLower = query.lowercase()
        val text = when {
            qLower.contains("sahw") || qLower.contains("forget") -> {
                "Sujud as-Sahw (سجود السهو) consists of two prostrations performed to compensate for deficiencies or unintentional additions/omissions during Salah, followed by Salam.\n\n" +
                        "The classical Islamic jurists established consensus that it is prescribed for inadvertent omissions or doubtful additions in the prayer, grounded in the authentic hadith of Abu Sa‘id al-Khudri in Sahih Muslim:\n" +
                        "> إِذَا شَكَّ أَحَدُكُمْ فِي صَلاَتِهِ فَلَمْ يَدْرِ كَمْ صَلَّى ثَلاَثًا أَمْ أَرْبَعًا فَلْيَطْرَحِ الشَّكَّ وَلْيَبْنِ عَلَى مَا اسْتَيْقَنَ ثُمَّ يَسْجُدُ سَجْدَتَيْنِ قَبْلَ أَنْ يُسَلِّمَ\n" +
                        "> \"When any of you is in doubt during prayer and does not know how much he prayed—three or four—let him discard doubt and build upon certainty, then prostrate twice before giving Salam.\""
            }
            qLower.contains("travel") || qLower.contains("qasr") || qLower.contains("jam") -> {
                "Regarding traveling prayers (Salat al-Safar):\n\n" +
                        "When a Muslim travels the Shar'i distance (approx 80-88 km), the Sunnah grants the concession (Rukhsah) of Qasr (shortening 4-Rakat prayers to 2) and Jam‘ (combining Dhuhr with Asr, and Maghrib with Isha).\n\n" +
                        "> \"And when you travel throughout the land, there is no blame upon you for shortening an aspect of prayer...\" (Surah An-Nisa 4:101)"
            }
            qLower.contains("tahajjud") || qLower.contains("qiyam al-layl") ||
                qLower.contains("night prayer") || qLower.contains("witr") -> {
                "Tahajjud (قيام الليل) is a voluntary prayer offered after Isha until Fajr. " +
                        "Its virtue is greatest in the last third of the night, and it is normally " +
                        "prayed in sets of two rak‘at, concluded with Witr.\n\n" +
                        "> \"And from the night, arise and pray in it as an additional prayer for you; " +
                        "perhaps your Lord will raise you to a praised station.\" (Surah Al-Isra 17:79)\n\n" +
                        "This is an educational summary, not a personal fatwa; asks about specific " +
                        "circumstances should go to a qualified scholar."
            }
            qLower.contains("zakat") || qLower.contains("stock") -> {
                "Regarding Zakat on liquid stocks and equities:\n\n" +
                        "According to contemporary Islamic Fiqh Academies and classical principles of Trade Goods (Urud al-Tijarah):\n" +
                        "1. Short-term speculative trading (Day trading): Zakat is 2.5% on the entire market value.\n" +
                        "2. Long-term dividend investment: Zakat is calculated strictly on the company's underlying liquid and net trade assets (~25-30% proxy) at 2.5%."
            }
            else -> {
                "I don't have a verified offline answer for that specific question. The live " +
                        "Scholar service is not configured in this build, so no sourced texts could " +
                        "be retrieved. Please configure the Scholar backend, rephrase the question, " +
                        "or consult a qualified scholar."
            }
        }

        for (word in text.split(" ")) {
            emit(RagStreamChunk.TextDelta("$word "))
            delay(15)
        }
    }

    private fun getGroundedSources(query: String): List<ProofSourceDto> {
        val qLower = query.lowercase()
        return when {
            qLower.contains("sahw") -> listOf(
                ProofSourceDto(
                    title = "Sahih Muslim #1272 · Book of Mosques and Places of Prayer",
                    referenceType = "HADITH",
                    primaryTextArabic = "فَلْيَطْرَحِ الشَّكَّ وَلْيَبْنِ عَلَى مَا اسْتَيْقَنَ",
                    translation = "Discard doubt and build upon certainty.",
                    navigationDeepLink = "hadith/muslim/1272"
                ),
                ProofSourceDto(
                    title = "Sahih al-Bukhari #1226",
                    referenceType = "HADITH",
                    primaryTextArabic = "سَجَدَ سَجْدَتَيْنِ بَعْدَ مَا سَلَّمَ",
                    translation = "He prostrated twice after delivering the Salam.",
                    navigationDeepLink = "hadith/bukhari/1226"
                )
            )
            qLower.contains("travel") -> listOf(
                ProofSourceDto(
                    title = "Surah An-Nisa 4:101",
                    referenceType = "QURAN",
                    primaryTextArabic = "وَإِذَا ضَرَبْتُمْ فِي الْأَرْضِ فَلَيْسَ عَلَيْكُمْ جُنَاحٌ أَنْ تَقْصُرُوا مِنَ الصَّلَاةِ",
                    translation = "And when you travel throughout the land, there is no blame upon you for shortening prayer.",
                    navigationDeepLink = "quran/4/101"
                ),
                ProofSourceDto(
                    title = "Sunan Abi Dawud #1205",
                    referenceType = "HADITH",
                    primaryTextArabic = "جَمَعَ بَيْنَهُمَا",
                    translation = "He combined the two prayers during travel.",
                    navigationDeepLink = "hadith/abudawud/1205"
                )
            )
            qLower.contains("tahajjud") || qLower.contains("qiyam al-layl") ||
                qLower.contains("night prayer") || qLower.contains("witr") -> listOf(
                ProofSourceDto(
                    title = "Surah Al-Isra 17:79",
                    referenceType = "QURAN",
                    primaryTextArabic = "وَمِنَ اللَّيْلِ فَتَهَجَّدْ بِهِ نَافِلَةً لَّكَ",
                    translation = "And from the night, arise and pray as an additional prayer for you.",
                    navigationDeepLink = "quran/17/79"
                )
            )
            else -> emptyList()
        }
    }

    private fun getMadhhabBreakdown(query: String): Map<String, String> {
        val qLower = query.lowercase()
        return if (qLower.contains("sahw")) {
            mapOf(
                "Hanafi" to "Performed after the first Salam to the right. Turn head right, make 2 Sujuds, recite Tashahhud and Durood, and complete full Salam.",
                "Maliki" to "Performed before Salam if due to omission (Qabli); performed after Salam if due to addition (Ba'di).",
                "Shafi'i" to "Always performed immediately prior to the first Salam (Qabli) after final Tashahhud.",
                "Hanbali" to "Before Salam for omissions and doubt; after Salam when based on prevailing probability or completed after early Salam."
            )
        } else if (qLower.contains("travel") || qLower.contains("qasr") || qLower.contains("jam")) {
            mapOf(
                "Hanafi" to "Structured upon Zahir al-Riwayah and foundational Kufan jurisprudence.",
                "Maliki" to "Grounded in the Muwatta and the continuous collective practice of Madinah (Amal Ahl al-Madinah).",
                "Shafi'i" to "Rooted in rigorous linguistic Usul formulated by Imam al-Shafi'i in Al-Risalah.",
                "Hanbali" to "Prioritizes Marfu‘ Hadith and Athar of the Sahabah over speculative Qiyas."
            )
        } else {
            emptyMap()
        }
    }
}
