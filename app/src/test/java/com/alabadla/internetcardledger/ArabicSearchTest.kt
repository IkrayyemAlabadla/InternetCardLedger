package com.alabadla.internetcardledger

import com.alabadla.internetcardledger.domain.ArabicSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicSearchTest {
    @Test
    fun customerSearch_ignoresSpacingDiacriticsAndAlefVariants() {
        assertTrue(ArabicSearch.customerMatches("  أَحْمَد   إبراهيم ", null, "احمد ابراهيم"))
        assertTrue(ArabicSearch.customerMatches("فاطمة", "0599 123 456", "0599123"))
        assertFalse(ArabicSearch.customerMatches("سليم", null, "أحمد"))
    }
}
