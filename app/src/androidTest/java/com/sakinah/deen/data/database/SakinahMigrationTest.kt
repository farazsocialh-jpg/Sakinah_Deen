package com.sakinah.deen.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SakinahMigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SakinahEncryptedDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To11_preservesSchema() {
        helper.createDatabase(dbName, 4).close()
        helper.runMigrationsAndValidate(
            dbName,
            11,
            true,
            SakinahEncryptedDatabase.MIGRATION_4_5,
            SakinahEncryptedDatabase.MIGRATION_5_6,
            SakinahEncryptedDatabase.MIGRATION_6_7,
            SakinahEncryptedDatabase.MIGRATION_7_8,
            SakinahEncryptedDatabase.MIGRATION_8_9,
            SakinahEncryptedDatabase.MIGRATION_9_10,
            SakinahEncryptedDatabase.MIGRATION_10_11
        ).close()
    }

    @Test
    fun migrateStepwise_4_to_5() {
        helper.createDatabase(dbName + "45", 4).close()
        helper.runMigrationsAndValidate(dbName + "45", 5, true, SakinahEncryptedDatabase.MIGRATION_4_5).close()
    }
}
