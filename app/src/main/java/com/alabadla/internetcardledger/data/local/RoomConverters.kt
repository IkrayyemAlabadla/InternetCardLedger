package com.alabadla.internetcardledger.data.local

import androidx.room.TypeConverter

class RoomConverters {
    @TypeConverter
    fun syncStatusToString(status: SyncStatus): String = status.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
