package com.alabadla.internetcardledger.data.sync

import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity

data class SyncBatch(
    val batchId: String,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val senderRole: DeviceRole,
    val createdAt: Long,
    val maxSequenceNumber: Long,
    val changes: List<SyncChangeLogEntity>,
    val customers: List<CustomerEntity>,
    val sales: List<SaleEntity>,
    val payments: List<PaymentEntity>,
)
