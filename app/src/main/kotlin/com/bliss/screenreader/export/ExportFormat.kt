@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.export

import java.util.Locale

/**
 * Turns captured display text into machine-readable export values.
 *
 * The source app formats for humans: "25 Jan 2022", "₹5,641/Month", "20/15".
 * Every consumer of an export would otherwise have to re-implement the same
 * guesswork, so normalisation happens once, here.
 *
 * Two rules:
 * - dates are ISO-8601 `yyyy-MM-dd`, or blank
 * - amounts are plain numbers with no separators, symbols or suffixes
 *
 * A value that cannot be normalised yields null rather than a half-parsed
 * guess, so a column never mixes formats. [UnparsedValues] collects those so a
 * parser gap shows up instead of vanishing.
 */
object ExportFormat {

    private val MONTH_NAMES = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    // "25 Jan 2022", "08 Aug 2026", "25-Sep-2026"
    private val DAY_MONTH_YEAR_REGEX = Regex(
        "^(\\d{1,2})[\\s-]+([A-Za-z]{3,9})[\\s-]+(\\d{4})$"
    )

    // "25/09/2026" and "25-09-2026"
    private val NUMERIC_DMY_REGEX = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$")

    // Already normalised.
    private val ISO_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    /** "20/15" -> term 20, ppt 15. */
    private val TERM_PPT_REGEX = Regex("^\\s*(\\d{1,3})\\s*/\\s*(\\d{1,3})\\s*$")

    /** Values that reached an export without normalising, for diagnostics. */
    val UnparsedValues = linkedSetOf<String>()

    fun ResetDiagnostics() {
        UnparsedValues.clear()
    }

    /**
     * Returns `yyyy-MM-dd`, or an empty string when [RawText] is blank or is
     * not a date this app has ever seen. Never returns a partially converted
     * value: an importer can trust the column completely or not at all.
     */
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
            // Day-first, matching how the source app renders dates. A value
            // above 12 in the first position would be ambiguous otherwise.
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

    /**
     * Strips currency symbols, thousands separators and any trailing frequency
     * so "₹5,641/Month" becomes 5641.0. Returns null when there is no number,
     * which writes an empty cell rather than a zero - zero is a real amount and
     * must not stand in for "not captured".
     */
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

    /**
     * The frequency carried alongside an amount - the "Month" in
     * "₹5,641/Month". Empty when the value has no suffix.
     */
    fun AmountFrequency(RawText: String): String {
        val TrimmedText = RawText.trim()
        if (!TrimmedText.contains('/')) return ""
        return TrimmedText.substringAfter('/').trim()
    }

    /** The term half of a "20/15" term-and-PPT pair. */
    fun TermYears(RawText: String): Double? {
        return TERM_PPT_REGEX.find(RawText.trim())
            ?.groupValues?.get(1)
            ?.toDoubleOrNull()
    }

    /** The premium-paying-term half of a "20/15" pair. */
    fun PptYears(RawText: String): Double? {
        return TERM_PPT_REGEX.find(RawText.trim())
            ?.groupValues?.get(2)
            ?.toDoubleOrNull()
    }

    /**
     * Identifiers stay text. A policy or mobile number is not a quantity, and
     * writing one as a number invites lost leading zeros and scientific
     * notation the moment a spreadsheet opens the file.
     */
    fun Identifier(RawText: String): String = RawText.trim()
}
