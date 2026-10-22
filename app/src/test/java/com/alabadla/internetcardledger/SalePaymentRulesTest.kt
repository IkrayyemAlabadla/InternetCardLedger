package com.alabadla.internetcardledger

import com.alabadla.internetcardledger.data.local.entity.CardType
import com.alabadla.internetcardledger.data.local.entity.PaymentStatus
import com.alabadla.internetcardledger.domain.SalePaymentRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SalePaymentRulesTest {
    @Test
    fun bothCardTypes_calculateIntegerIlsTotals() {
        assertEquals(3L, SalePaymentRules.total(3, CardType.SIX_HOURS))
        assertEquals(6L, SalePaymentRules.total(3, CardType.TWELVE_HOURS))
        assertEquals("ILS", SalePaymentRules.CURRENCY_CODE)
    }

    @Test
    fun quantity_mustBePositiveWholeNumber() {
        assertThrows(IllegalArgumentException::class.java) {
            SalePaymentRules.total(0, CardType.SIX_HOURS)
        }
    }

    @Test
    fun paymentStatuses_calculatePaidAndRemainingAmounts() {
        val total = 6L
        val paid = SalePaymentRules.paidAmount(total, PaymentStatus.PAID)
        val unpaid = SalePaymentRules.paidAmount(total, PaymentStatus.UNPAID)
        val partial = SalePaymentRules.paidAmount(total, PaymentStatus.PARTIALLY_PAID, 4L)

        assertEquals(6L, paid)
        assertEquals(0L, SalePaymentRules.remaining(total, paid))
        assertEquals(0L, unpaid)
        assertEquals(6L, SalePaymentRules.remaining(total, unpaid))
        assertEquals(4L, partial)
        assertEquals(2L, SalePaymentRules.remaining(total, partial))
    }

    @Test
    fun partialPayment_mustBeStrictlyBetweenZeroAndTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            SalePaymentRules.paidAmount(6L, PaymentStatus.PARTIALLY_PAID, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalePaymentRules.paidAmount(6L, PaymentStatus.PARTIALLY_PAID, 6L)
        }
        assertTrue(SalePaymentRules.isConsistent(6L, PaymentStatus.PARTIALLY_PAID, 1L))
        assertFalse(SalePaymentRules.isConsistent(6L, PaymentStatus.PARTIALLY_PAID, 6L))
    }
}
