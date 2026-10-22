package com.alabadla.internetcardledger.domain

import java.util.Locale

object ArabicSearch {
    private val diacritics = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val whitespace = Regex("\\s+")

    fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(diacritics, "")
        .replace("ـ", "")
        .replace(Regex("[أإآٱ]"), "ا")
        .replace("ى", "ي")
        .replace("ة", "ه")
        .replace(whitespace, " ")

    fun customerMatches(name: String, phone: String?, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return true
        return normalize(name).contains(normalizedQuery) ||
            phone?.filterNot(Char::isWhitespace)?.contains(normalizedQuery.filterNot(Char::isWhitespace)) == true
    }
}
