package com.alabadla.internetcardledger

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LedgerDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To3_preservesLedgerDataAndCreatesSyncTables() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                """INSERT INTO customers
                    (id, name, phone, notes, created_at, updated_at, created_by_device_id, sync_status, is_deleted)
                    VALUES ('customer-1', 'زبون محفوظ', NULL, NULL, 10, 20, 'old-device', 'PENDING', 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            LedgerDatabase.MIGRATION_1_2,
            LedgerDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query("SELECT name FROM customers WHERE id = 'customer-1'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("زبون محفوظ", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM approved_devices").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_preservesLegacyFinancialTotalsWithoutDoubleCountingPayments() {
        helper.createDatabase(DATABASE_V4_TEST_NAME, 3).apply {
            execSQL(
                """INSERT INTO sales
                    (id, customer_id, description, quantity, unit_price_minor, sold_at, notes,
                     created_at, updated_at, created_by_device_id, sync_status, is_deleted)
                    VALUES ('sale-1', NULL, 'بيع قديم', 3, 2, 100, NULL, 100, 100, 'old-device', 'SYNCED', 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_V4_TEST_NAME,
            4,
            true,
            LedgerDatabase.MIGRATION_3_4,
        ).use { database ->
            database.query(
                "SELECT description, card_type, payment_status, paid_amount_minor FROM sales WHERE id = 'sale-1'",
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("بيع قديم", cursor.getString(0))
                assertEquals("SIX_HOURS", cursor.getString(1))
                assertEquals("UNPAID", cursor.getString(2))
                assertEquals(0L, cursor.getLong(3))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-audit.db"
        const val DATABASE_V4_TEST_NAME = "migration-sales-v4.db"
    }
}
