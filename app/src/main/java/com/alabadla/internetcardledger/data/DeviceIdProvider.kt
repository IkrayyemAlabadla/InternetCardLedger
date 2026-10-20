package com.alabadla.internetcardledger.data

import android.content.Context
import java.util.UUID

open class DeviceIdProvider(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedDeviceId: String? = null

    open fun getDeviceId(): String {
        cachedDeviceId?.let { return it }

        return synchronized(this) {
            cachedDeviceId ?: preferences.getString(DEVICE_ID_KEY, null)
                ?: UUID.randomUUID().toString().also { newDeviceId ->
                    preferences.edit().putString(DEVICE_ID_KEY, newDeviceId).apply()
                }
        }.also { cachedDeviceId = it }
    }

    private companion object {
        const val PREFERENCES_NAME = "device_identity"
        const val DEVICE_ID_KEY = "device_id"
    }
}
