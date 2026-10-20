package com.alabadla.internetcardledger.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PinSecurityManager(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun hasPin(): Boolean = preferences.contains(KEY_HASH) && preferences.contains(KEY_SALT)

    fun setPin(pin: String) {
        require(PinHasher.isValidFormat(pin)) { "يجب أن يتكون الرقم السري من 4 إلى 8 أرقام." }
        val value = PinHasher.create(pin.toCharArray())
        preferences.edit()
            .putString(KEY_SALT, Base64.encodeToString(value.salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(value.hash, Base64.NO_WRAP))
            .apply()
    }

    fun verify(pin: String): Boolean {
        if (!PinHasher.isValidFormat(pin)) return false
        val salt = preferences.getString(KEY_SALT, null)?.decode() ?: return false
        val hash = preferences.getString(KEY_HASH, null)?.decode() ?: return false
        return PinHasher.verify(pin.toCharArray(), salt, hash)
    }

    fun changePin(currentPin: String, newPin: String): Boolean {
        if (!verify(currentPin) || !PinHasher.isValidFormat(newPin)) return false
        setPin(newPin)
        return true
    }

    /** Called only after the user has successfully exported a recovery backup. */
    fun resetAfterRecoveryBackup(newPin: String) = setPin(newPin)

    private fun String.decode(): ByteArray? = runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()

    private companion object {
        const val FILE_NAME = "secure_local_access"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
    }
}
