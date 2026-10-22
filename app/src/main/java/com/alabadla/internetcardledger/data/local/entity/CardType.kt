package com.alabadla.internetcardledger.data.local.entity

import com.alabadla.internetcardledger.R

enum class CardType(val labelResource: Int, val priceMinor: Long, val storageDescription: String) {
    SIX_HOURS(R.string.card_six_hours_title, 1L, "بطاقة 6 ساعات"),
    TWELVE_HOURS(R.string.card_twelve_hours_title, 2L, "بطاقة 12 ساعة");
}
