package com.alabadla.internetcardledger

import com.alabadla.internetcardledger.data.local.RoomConverters
import com.alabadla.internetcardledger.data.local.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomConvertersTest {
    private val converters = RoomConverters()

    @Test
    fun syncStatus_roundTripsEveryValue() {
        SyncStatus.entries.forEach { status ->
            assertEquals(status, converters.stringToSyncStatus(converters.syncStatusToString(status)))
        }
    }
}
