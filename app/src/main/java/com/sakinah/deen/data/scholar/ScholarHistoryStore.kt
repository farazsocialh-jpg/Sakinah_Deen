package com.sakinah.deen.data.scholar

import android.content.Context
import com.sakinah.deen.data.model.Citation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

typealias Citation = com.sakinah.deen.data.model.Citation

@Serializable
data class ScholarChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val timestampMs: Long,
    val citations: List<Citation> = emptyList(),
    val hanafiStance: String? = null,
    val malikiStance: String? = null,
    val shafiiStance: String? = null,
    val hanbaliStance: String? = null
)

@Serializable
data class ScholarChatSession(
    val id: String,
    val title: String,
    val updatedAtMs: Long,
    val messages: List<ScholarChatMessage>
)

/**
 * Small JSON-backed conversation store. Deliberately avoids Room so no schema
 * migration is required; the file lives in the app's private files directory.
 */
class ScholarHistoryStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Synchronized
    fun load(): List<ScholarChatSession> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ScholarChatSession>>(file.readText())
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(sessions: List<ScholarChatSession>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.absolutePath + ".tmp")
            tmp.writeText(json.encodeToString(sessions))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val FILE_NAME = "scholar_history.json"
    }
}
