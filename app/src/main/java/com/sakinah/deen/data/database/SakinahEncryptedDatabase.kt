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
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import com.sakinah.deen.data.database.entity.TafsirEntryEntity
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        SalahRecordEntity::class,
        QadhaLedgerEntity::class,
        HifzMistakeEntity::class,
        SadaqahLogEntity::class,
        QuranAyahEntity::class,
        SurahMetadataEntity::class,
        HadithEntryEntity::class,
        TafsirEntryEntity::class,
        AdhkarEntity::class,
        PrayerTimetableEntity::class
    ],
    version = 5,
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
         * v5 adds lookup indices for the hot queries (pending qadha, spaced-repetition
         * review, monthly sadaqah totals, adhkar categories). Index names follow Room's
         * `<prefix>_<table>_<column>` convention so the generated schema matches.
         */
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
            return INSTANCE ?: synchronized(this) {
                val factory = SupportFactory(passphraseBytes)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SakinahEncryptedDatabase::class.java,
                    DB_NAME
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_4_5)
                    .addCallback(DatabaseSeeder.createCallback(context.applicationContext) { INSTANCE })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
