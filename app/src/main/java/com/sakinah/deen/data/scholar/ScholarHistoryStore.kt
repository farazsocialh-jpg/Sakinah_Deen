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
    val hanbaliStance: String? = null,
    /** Set when the answer could not be tied to a verifiable source. */
    val sourcesUnverified: String? = null
)

@Serializable
data class ScholarChatSession(
    val id: String,
    val title: String,
    val updatedAtMs: Long,
    val messages: List<ScholarChatMessage>,
    /** Pinned chats are never pruned by retention and sort to the top of the list. */
    val pinned: Boolean = false
)

/**
 * Small JSON-backed conversation store. Deliberately avoids Room so no schema
 * migration is required; the file lives in the app's private files directory.
 *
 * Retention is enforced on every save: pinned chats plus the most recent
 * [MAX_SESSIONS] are kept, and each chat is capped at [MAX_MESSAGES_PER_SESSION]
 * messages (newest kept) so the history cannot grow without bound on device.
 */
class ScholarHistoryStore(context: Context) {

    private val directory = context.applicationContext.filesDir
    private val file = File(directory, FILE_NAME)
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
            val retained = prune(sessions)
            file.parentFile?.mkdirs()
            val tmp = File(file.absolutePath + ".tmp")
            tmp.writeText(json.encodeToString(retained))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    /** Pinned chats survive; the rest are trimmed to the newest [MAX_SESSIONS]. */
    fun prune(sessions: List<ScholarChatSession>): List<ScholarChatSession> =
        pruneRetained(sessions, MAX_SESSIONS, MAX_MESSAGES_PER_SESSION)

    companion object {
        const val MAX_SESSIONS = 30
        const val MAX_MESSAGES_PER_SESSION = 200

        private const val FILE_NAME = "scholar_history.json"

        /** Pure retention rule: pinned chats first, then the newest, messages capped. */
        fun pruneRetained(
            sessions: List<ScholarChatSession>,
            maxSessions: Int = MAX_SESSIONS,
            maxMessages: Int = MAX_MESSAGES_PER_SESSION
        ): List<ScholarChatSession> {
            val sorted = sessions.sortedWith(
                compareByDescending<ScholarChatSession> { it.pinned }
                    .thenByDescending { it.updatedAtMs }
            )
            val pinned = sorted.filter { it.pinned }
            val unpinned = sorted.filterNot { it.pinned }
            val budget = (maxSessions - pinned.size).coerceAtLeast(0)
            return (pinned + unpinned.take(budget)).map { session ->
                if (session.messages.size <= maxMessages) {
                    session
                } else {
                    session.copy(messages = session.messages.takeLast(maxMessages))
                }
            }
        }

        /** Case-insensitive search over the chat title and every message body. */
        fun search(sessions: List<ScholarChatSession>, query: String): List<ScholarChatSession> {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return sessions
            return sessions.filter { session ->
                session.title.contains(trimmed, ignoreCase = true) ||
                    session.messages.any { it.text.contains(trimmed, ignoreCase = true) }
            }
        }
    }
}
