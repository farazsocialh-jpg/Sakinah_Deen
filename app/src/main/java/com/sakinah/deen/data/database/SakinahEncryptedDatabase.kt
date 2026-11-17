package com.sakinah.deen.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sakinah.deen.data.database.converter.SakinahTypeConverters
import com.sakinah.deen.data.database.dao.AdhkarDao
import com.sakinah.deen.data.database.dao.HadithDao
import com.sakinah.deen.data.database.dao.HifzDao
import com.sakinah.deen.data.database.dao.PrayerTimetableDao
import com.sakinah.deen.data.database.dao.QadhaDao
import com.sakinah.deen.data.database.dao.QuranDao
import com.sakinah.deen.data.database.dao.SadaqahDao
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.database.dao.TafsirDao
import com.sakinah.deen.data.database.entity.AdhkarEntity
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.database.entity.PrayerTimetableEntity
import com.sakinah.deen.data.database.entity.QadhaLedgerEntity
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.database.entity.QuranTranslationEntity
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import com.sakinah.deen.data.database.entity.TafsirEntryEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        SalahRecordEntity::class,
        QadhaLedgerEntity::class,
        HifzMistakeEntity::class,
        SadaqahLogEntity::class,
        QuranAyahEntity::class,
        QuranTranslationEntity::class,
        SurahMetadataEntity::class,
        HadithEntryEntity::class,
        TafsirEntryEntity::class,
        AdhkarEntity::class,
        PrayerTimetableEntity::class
    ],
    version = 11,
    exportSchema = true
)
@TypeConverters(SakinahTypeConverters::class)
abstract class SakinahEncryptedDatabase : RoomDatabase() {
    abstract fun salahDao(): SalahDao
    abstract fun qadhaDao(): QadhaDao
    abstract fun hifzDao(): HifzDao
    abstract fun sadaqahDao(): SadaqahDao
    abstract fun quranDao(): QuranDao
    abstract fun hadithDao(): HadithDao
    abstract fun tafsirDao(): TafsirDao
    abstract fun adhkarDao(): AdhkarDao
    abstract fun prayerTimetableDao(): PrayerTimetableDao

    companion object {
        private const val DB_NAME = "sakinah_encrypted.db"

        /**
         * v6 -> v7 language-aware hadith editions. Duplicate legacy rows are collapsed
         * by (bookKey, hadithNumber) before the new composite key is applied.
         */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hadith_entries_new` (`bookKey` TEXT NOT NULL, `languageCode` TEXT NOT NULL, `hadithNumber` INTEGER NOT NULL, `bookTitle` TEXT NOT NULL, `sectionNumber` INTEGER NOT NULL, `chapterTitle` TEXT NOT NULL, `arabicText` TEXT NOT NULL, `englishText` TEXT NOT NULL, `grade` TEXT NOT NULL, `gradingScholar` TEXT NOT NULL, `downloadedAt` INTEGER NOT NULL, PRIMARY KEY(`bookKey`, `languageCode`, `hadithNumber`))"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `hadith_entries_new` (`bookKey`,`languageCode`,`hadithNumber`,`bookTitle`,`sectionNumber`,`chapterTitle`,`arabicText`,`englishText`,`grade`,`gradingScholar`,`downloadedAt`) SELECT `bookKey`,'eng',`hadithNumber`,`bookTitle`,`sectionNumber`,`chapterTitle`,`arabicText`,`englishText`,`grade`,`gradingScholar`,`downloadedAt` FROM `hadith_entries` GROUP BY `bookKey`,`hadithNumber`"
                )
                db.execSQL("DROP TABLE `hadith_entries`")
                db.execSQL("ALTER TABLE `hadith_entries_new` RENAME TO `hadith_entries`")
            }
        }

        /**
         * v7 -> v8 adds Quran reader state (bookmark/last-read/juz) and sadaqah categories,
         * plus lookup indices for hadith sections.
         */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `hadith_entries` ADD COLUMN `arabicNumber` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v9 -> v10 adds the IndoPak and Imlaei Arabic scripts so the reader can switch
         * script without re-downloading a surah.
         */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `quran_ayahs` ADD COLUMN `textIndopak` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `quran_ayahs` ADD COLUMN `textImlaei` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v10 -> v11 gives adhkar rows their provenance: transliteration, grading, repeat
         * count and variant note, so the Adhkar cards can show a source chain instead of
         * bare text. Existing rows keep blank provenance and are replaced by the seed.
         */
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `adhkar_entries` ADD COLUMN `grading` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `adhkar_entries` ADD COLUMN `repeatCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `adhkar_entries` ADD COLUMN `transliteration` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `adhkar_entries` ADD COLUMN `variantNote` TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `quran_ayahs` ADD COLUMN `juzNumber` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `quran_ayahs` ADD COLUMN `isBookmarked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `quran_ayahs` ADD COLUMN `lastReadAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sadaqah_logs` ADD COLUMN `category` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quran_ayahs_juzNumber` ON `quran_ayahs` (`juzNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quran_ayahs_isBookmarked` ON `quran_ayahs` (`isBookmarked`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quran_ayahs_lastReadAt` ON `quran_ayahs` (`lastReadAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sadaqah_logs_category` ON `sadaqah_logs` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hadith_entries_sectionNumber` ON `hadith_entries` (`sectionNumber`)")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quran_translations` (`surahNumber` INTEGER NOT NULL, `ayahNumber` INTEGER NOT NULL, `translationId` INTEGER NOT NULL, `translation` TEXT NOT NULL, `downloadedAt` INTEGER NOT NULL, PRIMARY KEY(`surahNumber`, `ayahNumber`, `translationId`))"
                )
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_salah_records_isQadha` ON `salah_records` (`isQadha`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hifz_mistakes_lastReviewedTimestamp` ON `hifz_mistakes` (`lastReviewedTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sadaqah_logs_timestamp` ON `sadaqah_logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_adhkar_entries_categoryKey` ON `adhkar_entries` (`categoryKey`)")
            }
        }

        @Volatile
        private var INSTANCE: SakinahEncryptedDatabase? = null

        fun getInstance(context: Context, passphraseBytes: ByteArray): SakinahEncryptedDatabase {
            INSTANCE?.let { return it }
            return synchronized(this) {
                // Re-check inside the lock; two threads can race past the first read.
                INSTANCE ?: buildWithRecovery(context.applicationContext, passphraseBytes).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Describes a database that could not be opened with the current keystore passphrase.
         * The original file is kept (moved aside) so a support engineer — or a future build
         * that can recover the key — still has the user's prayer logs, Qadha and Hifz data.
         */
        @Volatile
        var lastRecovery: DatabaseRecovery? = null
            private set

        /**
         * A regenerated keystore passphrase, a restored backup, or a stale schema cannot be
         * opened with the current key. Deleting the file silently would destroy the user's
         * records, so it is renamed aside first and the fresh database starts empty.
         */
        private fun buildWithRecovery(
            context: Context,
            passphraseBytes: ByteArray
        ): SakinahEncryptedDatabase = try {
            build(context, passphraseBytes).also(::openNow)
        } catch (first: Exception) {
            val preserved = preserveUnreadableDatabase(context, first)
            lastRecovery = preserved
            runCatching { build(context, passphraseBytes).also(::openNow) }.getOrElse { rebuildError ->
                first.addSuppressed(rebuildError)
                throw first
            }
        }

        /**
         * Moves the unreadable database (and its WAL/journal sidecars) to a timestamped file
         * in the app's files directory. Returns null when nothing could be preserved.
         */
        internal fun preserveUnreadableDatabase(
            context: Context,
            cause: Throwable
        ): DatabaseRecovery? {
            val source = context.getDatabasePath(DB_NAME)
            if (!source.exists()) return null
            val stamp = System.currentTimeMillis()
            val target = java.io.File(
                context.filesDir,
                "recovery/sakinah_$stamp.db"
            )
            target.parentFile?.mkdirs()
            val moved = runCatching {
                source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.isSuccess
            if (!moved) return null
            // Only drop the originals once a readable copy exists.
            runCatching { context.deleteDatabase(DB_NAME) }
            listOf("$DB_NAME-wal", "$DB_NAME-shm", "$DB_NAME-journal").forEach { sidecar ->
                runCatching { context.getDatabasePath(sidecar).delete() }
            }
            return DatabaseRecovery(
                backupPath = target.absolutePath,
                occurredAtEpochMillis = stamp,
                reason = cause.message ?: cause::class.java.simpleName
            )
        }

        /**
         * Room.databaseBuilder(...).build() is lazy: without touching the helper, a corrupt or
         * unreadable SQLCipher file only throws on the first query, outside the recovery block.
         * Opening it here (on an IO dispatcher) keeps that failure inside buildWithRecovery.
         */
        private fun openNow(database: SakinahEncryptedDatabase) {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                database.openHelper.writableDatabase
            }
        }

        private fun build(context: Context, passphraseBytes: ByteArray): SakinahEncryptedDatabase {
            val factory = SupportOpenHelperFactory(passphraseBytes)
            return Room.databaseBuilder(
                context,
                SakinahEncryptedDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
                )
                // No destructive fallback for the released schema: an unreadable or older
                // database is preserved by buildWithRecovery() instead of being wiped, so a
                // failed migration can never silently erase prayer logs or Qadha records.
                .addCallback(DatabaseSeeder.createCallback(context) { INSTANCE })
                .build()
        }
    }
}

/**
 * Record of an encrypted database that could not be opened with the current keystore key.
 *
 * [backupPath] points at the preserved copy inside the app's files directory; the fresh
 * database starts empty, so this is the only handle on the user's previous records.
 */
data class DatabaseRecovery(
    val backupPath: String,
    val occurredAtEpochMillis: Long,
    val reason: String
)
