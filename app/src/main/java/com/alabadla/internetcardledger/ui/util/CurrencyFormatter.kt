package com.alabadla.internetcardledger.ui.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val arabicLocale = Locale.forLanguageTag("ar")

    fun formatMoney(amountMinor: Long): String {
        val numberStr = NumberFormat.getNumberInstance(arabicLocale).format(amountMinor)
        return "$numberStr شيكل"
    }

    fun formatQuantity(quantity: Int): String {
        return NumberFormat.getNumberInstance(arabicLocale).format(quantity)
    }
}
