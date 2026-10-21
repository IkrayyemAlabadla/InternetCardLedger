package com.alabadla.internetcardledger

import com.alabadla.internetcardledger.security.PinHasher
import java.security.SecureRandom
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun pinFormat_acceptsOnlyFourToEightDigits() {
        assertTrue(PinHasher.isValidFormat("1234"))
        assertTrue(PinHasher.isValidFormat("12345678"))
        assertFalse(PinHasher.isValidFormat("123"))
        assertFalse(PinHasher.isValidFormat("123456789"))
        assertFalse(PinHasher.isValidFormat("12a4"))
    }

    @Test
    fun hash_verifiesCorrectPinAndRejectsWrongPin() {
        val random = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(byteArrayOf(1, 2, 3, 4)) }
        val value = PinHasher.create("2468".toCharArray(), random)

        assertTrue(PinHasher.verify("2468".toCharArray(), value.salt, value.hash))
        assertFalse(PinHasher.verify("1357".toCharArray(), value.salt, value.hash))
        assertFalse(value.hash.contentEquals("2468".toByteArray()))
    }
}
