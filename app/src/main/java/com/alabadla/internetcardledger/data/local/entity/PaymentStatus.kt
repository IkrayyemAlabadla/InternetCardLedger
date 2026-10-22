package com.alabadla.internetcardledger.data.local.entity

import com.alabadla.internetcardledger.R

enum class PaymentStatus(val labelResource: Int) {
    PAID(R.string.payment_status_paid),
    UNPAID(R.string.payment_status_unpaid),
    PARTIALLY_PAID(R.string.payment_status_partially_paid);
}
