package com.sakinah.deen.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "salah_records",
    primaryKeys = ["dateEpochDay", "prayerType"]
)
data class SalahRecordEntity(
    val dateEpochDay: Long,
    val prayerType: String,
    val isCompleted: Boolean,
    val isJamaah: Boolean,
    val isQadha: Boolean,
    val completedTimestamp: Long? = null,
    val nawafilCompleted: List<String> = emptyList()
)

@Entity(tableName = "qadha_ledger")
data class QadhaLedgerEntity(
    @PrimaryKey
    val prayerType: String,
    val totalMissed: Int,
    val totalMadeUp: Int,
    val updatedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "hifz_mistakes",
    primaryKeys = ["surahNumber", "ayahNumber", "wordPosition"]
)
data class HifzMistakeEntity(
    val surahNumber: Int,
    val ayahNumber: Int,
    val wordPosition: Int,
    val mistakeType: String, // RED_MEMORY, YELLOW_TAJWEED, GREEN_MASTERED
    val lastReviewedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sadaqah_logs")
data class SadaqahLogEntity(
    @PrimaryKey
    val id: String,
    val amount: Double,
    val currency: String = "USD",
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
