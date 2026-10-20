package com.alabadla.internetcardledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alabadla.internetcardledger.data.local.dao.BackupDao
import com.alabadla.internetcardledger.data.local.dao.CustomerDao
import com.alabadla.internetcardledger.data.local.dao.PaymentDao
import com.alabadla.internetcardledger.data.local.dao.SaleDao
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceDao
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceEntity
import com.alabadla.internetcardledger.data.sync.DeviceIdentityEntity
import com.alabadla.internetcardledger.data.sync.SyncChangeLogEntity
import com.alabadla.internetcardledger.data.sync.SyncConflictEntity
import com.alabadla.internetcardledger.data.sync.SyncDao
import com.alabadla.internetcardledger.data.sync.SyncSessionEntity

@Database(
    entities = [
        CustomerEntity::class,
        SaleEntity::class,
        PaymentEntity::class,
        DeviceIdentityEntity::class,
        SyncChangeLogEntity::class,
        SyncConflictEntity::class,
        SyncSessionEntity::class,
        ApprovedDeviceEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun paymentDao(): PaymentDao
    abstract fun backupDao(): BackupDao
    abstract fun syncDao(): SyncDao
    abstract fun approvedDeviceDao(): ApprovedDeviceDao

    companion object {
        private const val DATABASE_NAME = "internet_card_ledger.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `device_identities` (
                        `device_id` TEXT NOT NULL,
                        `device_name` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `last_sequence_number` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`device_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_change_logs` (
                        `id` TEXT NOT NULL,
                        `record_id` TEXT NOT NULL,
                        `entity_type` TEXT NOT NULL,
                        `operation_type` TEXT NOT NULL,
                        `device_id` TEXT NOT NULL,
                        `sequence_number` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `sync_status` TEXT NOT NULL,
                        `payload_json` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_change_logs_record_id` ON `sync_change_logs` (`record_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_change_logs_device_id_sequence_number` ON `sync_change_logs` (`device_id`, `sequence_number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_change_logs_created_at` ON `sync_change_logs` (`created_at`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_conflicts` (
                        `id` TEXT NOT NULL,
                        `record_id` TEXT NOT NULL,
                        `entity_type` TEXT NOT NULL,
                        `main_version_json` TEXT NOT NULL,
                        `secondary_version_json` TEXT NOT NULL,
                        `main_device_id` TEXT NOT NULL,
                        `secondary_device_id` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_record_id` ON `sync_conflicts` (`record_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_status` ON `sync_conflicts` (`status`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_sessions` (
                        `id` TEXT NOT NULL,
                        `peer_device_id` TEXT NOT NULL,
                        `peer_device_name` TEXT NOT NULL,
                        `peer_device_role` TEXT NOT NULL,
                        `started_at` INTEGER NOT NULL,
                        `completed_at` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `changes_received_count` INTEGER NOT NULL,
                        `changes_sent_count` INTEGER NOT NULL,
                        `conflicts_count` INTEGER NOT NULL,
                        `error_message` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `approved_devices` (
                        `device_id` TEXT NOT NULL,
                        `device_name` TEXT NOT NULL,
                        `approved_at` INTEGER NOT NULL,
                        `last_synced_at` INTEGER,
                        PRIMARY KEY(`device_id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        @Volatile
        private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
