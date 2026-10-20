package com.alabadla.internetcardledger.data

import android.content.Context
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import com.alabadla.internetcardledger.data.repository.BackupRepository
import com.alabadla.internetcardledger.data.repository.CustomerRepository
import com.alabadla.internetcardledger.data.repository.PaymentRepository
import com.alabadla.internetcardledger.data.repository.SaleRepository
import com.alabadla.internetcardledger.data.repository.SettingsRepository
import com.alabadla.internetcardledger.security.PinSecurityManager
import com.alabadla.internetcardledger.data.sync.SyncEngine
import com.alabadla.internetcardledger.data.sync.transport.NearbyConnectionsSyncTransport
import com.alabadla.internetcardledger.data.sync.transport.SyncTransport

class AppContainer(context: Context) {
    val database = LedgerDatabase.getInstance(context)
    val deviceIdProvider = DeviceIdProvider(context)
    val pinSecurityManager by lazy { PinSecurityManager(context) }

    val syncEngine by lazy {
        SyncEngine(database, deviceIdProvider)
    }

    val syncTransport: SyncTransport by lazy {
        NearbyConnectionsSyncTransport(context)
    }

    val customerRepository by lazy {
        CustomerRepository(database.customerDao(), deviceIdProvider, syncEngine = syncEngine)
    }
    val saleRepository by lazy {
        SaleRepository(database.saleDao(), deviceIdProvider, syncEngine = syncEngine)
    }
    val paymentRepository by lazy {
        PaymentRepository(database.paymentDao(), deviceIdProvider, syncEngine = syncEngine)
    }
    val backupRepository by lazy {
        BackupRepository(context, database.backupDao(), deviceIdProvider)
    }
    val settingsRepository by lazy {
        SettingsRepository(
            context = context,
            deviceIdProvider = deviceIdProvider,
            syncEngine = syncEngine,
            syncDao = database.syncDao(),
            approvedDeviceDao = database.approvedDeviceDao(),
        )
    }
}
