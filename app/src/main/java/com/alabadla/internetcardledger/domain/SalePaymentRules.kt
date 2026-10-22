package com.alabadla.internetcardledger.domain

import com.alabadla.internetcardledger.data.local.entity.CardType
import com.alabadla.internetcardledger.data.local.entity.PaymentStatus

/** Integer-only sale calculations. Amounts use the app's existing whole-ILS Long representation. */
object SalePaymentRules {
    const val CURRENCY_CODE = "ILS"

    fun total(quantity: Int, cardType: CardType): Long {
        require(quantity > 0) { "Quantity must be positive" }
        return Math.multiplyExact(quantity.toLong(), cardType.priceMinor)
    }

    fun paidAmount(total: Long, status: PaymentStatus, partialAmount: Long? = null): Long {
        require(total > 0L) { "Total must be positive" }
        return when (status) {
            PaymentStatus.PAID -> total
            PaymentStatus.UNPAID -> 0L
            PaymentStatus.PARTIALLY_PAID -> requireNotNull(partialAmount).also {
                require(it in 1 until total) { "Partial payment must be greater than zero and less than total" }
            }
        }
    }

    fun remaining(total: Long, paidAmount: Long): Long {
        require(paidAmount in 0..total) { "Paid amount must be between zero and total" }
        return Math.subtractExact(total, paidAmount)
    }

    fun isConsistent(total: Long, status: PaymentStatus, paidAmount: Long): Boolean = when (status) {
        PaymentStatus.PAID -> paidAmount == total
        PaymentStatus.UNPAID -> paidAmount == 0L
        PaymentStatus.PARTIALLY_PAID -> paidAmount in 1 until total
    }
}
