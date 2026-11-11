package com.sakinah.deen.data.database.converter

import androidx.room.TypeConverter

class SakinahTypeConverters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return ""
        return list.joinToString(separator = "|||")
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split("|||").filter { it.isNotBlank() }
    }
}
