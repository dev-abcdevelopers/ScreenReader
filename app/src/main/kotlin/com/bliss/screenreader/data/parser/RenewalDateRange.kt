@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.parser

object RenewalDateRange {

    const val DEFAULT_SPAN_DAYS = 60

    val SUPPORTED_SPAN_DAYS = listOf(7, 15, 30, 60)

    private const val MAX_LABEL_LENGTH = 60

    private val RELATIVE_RANGE =
        Regex("(?i)\\bLast\\s+(\\d+)\\s+(Days?|Weeks?|Months?|Years?)\\b")

    private val NAMED_RANGE =
        Regex("(?i)\\b(Today|Yesterday|This Month|This Year|All Time|Custom)\\b")

    private val WHITESPACE = Regex("\\s+")

    fun Normalise(TextValue: String): String =
        TextValue.trim().replace(WHITESPACE, " ")

    fun FindRange(TextValue: String): String? {
        val NormalisedText = Normalise(TextValue = TextValue)
        if (NormalisedText.isEmpty() || NormalisedText.length > MAX_LABEL_LENGTH) return null
        RELATIVE_RANGE.find(NormalisedText)?.let { MatchRef -> return MatchRef.value }
        NAMED_RANGE.find(NormalisedText)?.let { MatchRef -> return MatchRef.value }
        return null
    }

    fun IsRangeLabel(TextValue: String): Boolean =
        FindRange(TextValue = TextValue) != null

    fun ChooseSpanDays(AvailableSpans: List<Int>, TargetDays: Int): Int? {
        if (AvailableSpans.isEmpty()) return null
        if (AvailableSpans.contains(TargetDays)) return TargetDays
        val BelowTarget = AvailableSpans.filter { SpanVal -> SpanVal < TargetDays }
        if (BelowTarget.isNotEmpty()) return BelowTarget.max()
        return AvailableSpans.min()
    }

    fun SpanDays(TextValue: String): Int? {
        val NormalisedText = Normalise(TextValue = TextValue)
        if (NormalisedText.isEmpty() || NormalisedText.length > MAX_LABEL_LENGTH) return null

        val RelativeMatch = RELATIVE_RANGE.find(NormalisedText)
        if (RelativeMatch != null) {
            val CountVal = RelativeMatch.groupValues[1].toIntOrNull() ?: return null
            val UnitDays = when (RelativeMatch.groupValues[2].lowercase().trimEnd('s')) {
                "day" -> 1
                "week" -> 7
                "month" -> 30
                "year" -> 365
                else -> return null
            }
            if (CountVal > Int.MAX_VALUE / UnitDays) return Int.MAX_VALUE
            return CountVal * UnitDays
        }

        val NamedMatch = NAMED_RANGE.find(NormalisedText) ?: return null
        return when (NamedMatch.value.lowercase()) {
            "today" -> 1
            "yesterday" -> 2
            "this month" -> 30
            "this year" -> 365
            "all time" -> Int.MAX_VALUE
            else -> null
        }
    }
}
