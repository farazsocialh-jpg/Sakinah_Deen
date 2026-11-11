package com.sakinah.deen.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sakinah.deen.data.database.converter.SakinahTypeConverters
import com.sakinah.deen.data.database.dao.HifzDao
import com.sakinah.deen.data.database.dao.QadhaDao
import com.sakinah.deen.data.database.dao.SadaqahDao
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.database.entity.QadhaLedgerEntity
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        SalahRecordEntity::class,
        QadhaLedgerEntity::class,
        HifzMistakeEntity::class,
        SadaqahLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(SakinahTypeConverters::class)
abstract class SakinahEncryptedDatabase : RoomDatabase() {
    abstract fun salahDao(): SalahDao
    abstract fun qadhaDao(): QadhaDao
    abstract fun hifzDao(): HifzDao
    abstract fun sadaqahDao(): SadaqahDao

    companion object {
        private const val DB_NAME = "sakinah_encrypted.db"

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
