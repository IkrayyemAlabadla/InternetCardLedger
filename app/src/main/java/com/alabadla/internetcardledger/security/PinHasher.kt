package com.alabadla.internetcardledger.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinHash(val salt: ByteArray, val hash: ByteArray)

object PinHasher {
    const val ITERATIONS = 120_000
    const val KEY_LENGTH_BITS = 256

    fun isValidFormat(pin: String): Boolean = pin.length in 4..8 && pin.all(Char::isDigit)

    fun create(pin: CharArray, random: SecureRandom = SecureRandom()): PinHash {
        require(isValidFormat(String(pin)))
        val salt = ByteArray(16).also(random::nextBytes)
        return PinHash(salt, derive(pin, salt))
    }

    fun verify(pin: CharArray, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(derive(pin, salt), expected)

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val algorithm = runCatching { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") }
            .getOrElse { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1") }
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            algorithm.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            pin.fill('\u0000')
        }
    }
}
