package com.alabadla.internetcardledger.data.backup

import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity

const val BACKUP_FORMAT_NAME = "internet-card-ledger-backup"
const val BACKUP_FORMAT_VERSION = 2

data class BackupDocument(
    val format: String = BACKUP_FORMAT_NAME,
    val version: Int = BACKUP_FORMAT_VERSION,
    val createdAt: Long,
    val deviceId: String,
    val customers: List<CustomerEntity>,
    val sales: List<SaleEntity>,
    val payments: List<PaymentEntity>,
) {
    val recordCount: Int get() = customers.size + sales.size + payments.size
}

data class LedgerSnapshot(
    val customers: List<CustomerEntity>,
    val sales: List<SaleEntity>,
    val payments: List<PaymentEntity>,
)

data class RestoreReport(
    val inserted: Int,
    val updated: Int,
    val unchanged: Int,
    val conflicts: Int,
    val safetyBackupName: String? = null,
)

class InvalidBackupException(message: String) : Exception(message)
