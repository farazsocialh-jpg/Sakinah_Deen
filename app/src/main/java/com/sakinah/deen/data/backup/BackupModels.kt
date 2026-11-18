package com.sakinah.deen.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class PrefEntry(
    val key: String,
    val type: String, // string, int, long, float, boolean, stringset
    val value: String
)

@Serializable
data class BackupData(
    val prefs: List<PrefEntry> = emptyList(),
    val qadha: List<QadhaDto> = emptyList(),
    val hifz: List<HifzDto> = emptyList(),
    val sadaqah: List<SadaqahDto> = emptyList(),
    val bookmarks: List<BookmarkDto> = emptyList()
)

@Serializable
data class QadhaDto(val prayerType: String, val totalMissed: Int, val totalMadeUp: Int, val updatedTimestamp: Long)

@Serializable
data class HifzDto(val surahNumber: Int, val ayahNumber: Int, val wordPosition: Int, val mistakeType: String, val lastReviewedTimestamp: Long)

@Serializable
data class SadaqahDto(val id: String, val amount: Double, val currency: String, val note: String?, val timestamp: Long, val category: String)

@Serializable
data class BookmarkDto(val surahNumber: Int, val ayahNumber: Int)

@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = 1,
    val checksum: String,
    val data: BackupData
)
