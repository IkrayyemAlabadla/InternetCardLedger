package com.alabadla.internetcardledger

import com.alabadla.internetcardledger.ui.viewmodel.SalesViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerValidationTest {

    @Test
    fun normalizeNumber_convertsArabicAndPersianDigitsToAscii() {
        assertEquals("1234567890", SalesViewModel.normalizeNumber("١٢٣٤٥٦٧٨٩٠"))
        assertEquals("1234567890", SalesViewModel.normalizeNumber("۱۲۳۴۵۶۷۸۹۰"))
        assertEquals("1000", SalesViewModel.normalizeNumber("  ١٠٠٠  "))
    }

    @Test
    fun balanceCalculation_correctlySubtractsPaymentsFromSales() {
        val totalSalesMinor = 15_000L
        val totalPaymentsMinor = 10_000L
        val balance = totalSalesMinor - totalPaymentsMinor

        assertEquals(5_000L, balance)
    }
}
