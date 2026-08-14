@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.export

import java.util.Locale

object ExportFormat {

    private val MONTH_NAMES = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val DAY_MONTH_YEAR_REGEX = Regex(
        "^(\\d{1,2})[\\s-]+([A-Za-z]{3,9})[\\s-]+(\\d{4})$"
    )

    private val NUMERIC_DMY_REGEX = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$")

    private val ISO_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    private val TERM_PPT_REGEX = Regex("^\\s*(\\d{1,3})\\s*/\\s*(\\d{1,3})\\s*$")

    val UnparsedValues = linkedSetOf<String>()

    fun ResetDiagnostics() {
        UnparsedValues.clear()
    }

    fun IsoDate(RawText: String): String {
        val TrimmedText = RawText.trim()
        if (TrimmedText.isEmpty()) return ""

        ISO_REGEX.find(TrimmedText)?.let { return TrimmedText }

        DAY_MONTH_YEAR_REGEX.find(TrimmedText)?.let { MatchResult ->
            val DayValue = MatchResult.groupValues[1].toIntOrNull()
            val MonthValue = MONTH_NAMES[
                MatchResult.groupValues[2].take(3).lowercase(Locale.ROOT)
            ]
            val YearValue = MatchResult.groupValues[3].toIntOrNull()
            if (DayValue != null && MonthValue != null && YearValue != null) {
                return FormatIso(YearValue = YearValue, MonthValue = MonthValue, DayValue = DayValue)
            }
        }

        NUMERIC_DMY_REGEX.find(TrimmedText)?.let { MatchResult ->
            val DayValue = MatchResult.groupValues[1].toIntOrNull()
            val MonthValue = MatchResult.groupValues[2].toIntOrNull()
            val YearValue = MatchResult.groupValues[3].toIntOrNull()
            if (DayValue != null && MonthValue != null && YearValue != null &&
                MonthValue in 1..12 && DayValue in 1..31
            ) {
                return FormatIso(YearValue = YearValue, MonthValue = MonthValue, DayValue = DayValue)
            }
        }

        UnparsedValues.add(TrimmedText)
        return ""
    }

    private fun FormatIso(YearValue: Int, MonthValue: Int, DayValue: Int): String {
        return String.format(Locale.US, "%04d-%02d-%02d", YearValue, MonthValue, DayValue)
    }

    fun PlainNumber(RawText: String): Double? {
        val TrimmedText = RawText.trim()
        if (TrimmedText.isEmpty()) return null

        val AmountPart = TrimmedText.substringBefore('/')
        val DigitsOnly = AmountPart
            .replace("₹", "")
            .replace(",", "")
            .replace(" ", "")
            .replace(" ", "")
            .trim()
        if (DigitsOnly.isEmpty()) return null

        val NumericValue = DigitsOnly.toDoubleOrNull()
        if (NumericValue == null) {
            UnparsedValues.add(TrimmedText)
            return null
        }
        return NumericValue
    }

    fun AmountFrequency(RawText: String): String {
        val TrimmedText = RawText.trim()
        if (!TrimmedText.contains('/')) return ""
        return TrimmedText.substringAfter('/').trim()
    }

    fun TermYears(RawText: String): Double? {
        return TERM_PPT_REGEX.find(RawText.trim())
            ?.groupValues?.get(1)
            ?.toDoubleOrNull()
    }

    fun PptYears(RawText: String): Double? {
        return TERM_PPT_REGEX.find(RawText.trim())
            ?.groupValues?.get(2)
            ?.toDoubleOrNull()
    }

    fun Identifier(RawText: String): String = RawText.trim()
}
