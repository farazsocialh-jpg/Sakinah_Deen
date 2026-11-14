package com.sakinah.deen.data.database.converter

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Stores string lists as JSON instead of a \"|||\" delimiter, so null, empty lists,
 * blanks and values containing the delimiter all round-trip losslessly.
 */
class SakinahTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(list: List<String>?): String =
        json.encodeToString(ListSerializer(String.serializer()), list.orEmpty())

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), data)
        }.getOrDefault(emptyList())
    }
}
