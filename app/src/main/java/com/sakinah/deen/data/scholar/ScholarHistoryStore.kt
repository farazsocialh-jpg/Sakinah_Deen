package com.sakinah.deen.data.scholar

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ScholarChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val timestampMs: Long
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
            file.writeText(json.encodeToString(sessions))
        }
    }

    private companion object {
        const val FILE_NAME = "scholar_history.json"
    }
}
