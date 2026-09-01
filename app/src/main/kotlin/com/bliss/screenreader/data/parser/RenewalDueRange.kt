@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

object RenewalDueRange {

    const val ALL_SPAN_DAYS = Int.MAX_VALUE

    const val DEFAULT_SPAN_DAYS = ALL_SPAN_DAYS

    val SUPPORTED_SPAN_DAYS = listOf(1, 7, 15, 30, ALL_SPAN_DAYS)

    private const val MAX_LABEL_LENGTH = 60

    private val FORWARD_RANGE =
        Regex("(?i)\\bNext\\s+(\\d+)\\s+(Days?|Weeks?|Months?|Years?)\\b")

    private val TODAY_ONLY = Regex("(?i)^Today$")

    private val ALL_ONLY = Regex("(?i)^All$")

    private val WHITESPACE = Regex("\\s+")

    private val DECORATION_WORDS = listOf(
        "arrow", "icon", "button", "dropdown", "chevron", "down", "up", "image", "view"
    )

    fun Normalise(TextValue: String): String =
        TextValue.trim().replace(WHITESPACE, " ")

    fun StripDecorations(TextValue: String): String {
        val Words = Normalise(TextValue = TextValue).split(" ")
        val KeptWords = Words.takeWhile { WordText ->
            val BareWord = WordText.trim('-', ',', ':').lowercase()
            val WithoutIndex = BareWord.substringBefore('-')
            DECORATION_WORDS.none { Decoration -> WithoutIndex == Decoration }
        }
        return KeptWords.joinToString(separator = " ").trim()
    }

    fun FindRange(TextValue: String): String? {
        val NormalisedText = Normalise(TextValue = TextValue)
        if (NormalisedText.isEmpty() || NormalisedText.length > MAX_LABEL_LENGTH) return null
        FORWARD_RANGE.find(NormalisedText)?.let { MatchRef -> return MatchRef.value }
        val BareText = StripDecorations(TextValue = NormalisedText)
        if (TODAY_ONLY.matches(BareText) || ALL_ONLY.matches(BareText)) return BareText
        return null
    }

    fun IsRangeLabel(TextValue: String): Boolean =
        FindRange(TextValue = TextValue) != null

    fun SpanDays(TextValue: String): Int? {
        val NormalisedText = Normalise(TextValue = TextValue)
        if (NormalisedText.isEmpty() || NormalisedText.length > MAX_LABEL_LENGTH) return null

        val ForwardMatch = FORWARD_RANGE.find(NormalisedText)
        if (ForwardMatch != null) {
            val CountVal = ForwardMatch.groupValues[1].toIntOrNull() ?: return null
            val UnitDays = when (ForwardMatch.groupValues[2].lowercase().trimEnd('s')) {
                "day" -> 1
                "week" -> 7
                "month" -> 30
                "year" -> 365
                else -> return null
            }
            if (CountVal > Int.MAX_VALUE / UnitDays) return ALL_SPAN_DAYS
            return CountVal * UnitDays
        }

        val BareText = StripDecorations(TextValue = NormalisedText)
        if (TODAY_ONLY.matches(BareText)) return 1
        if (ALL_ONLY.matches(BareText)) return ALL_SPAN_DAYS

        return null
    }

    fun ChooseSpanDays(AvailableSpans: List<Int>, TargetDays: Int): Int? {
        if (AvailableSpans.isEmpty()) return null
        if (AvailableSpans.contains(TargetDays)) return TargetDays
        val AtOrAboveTarget = AvailableSpans.filter { SpanVal -> SpanVal > TargetDays }
        if (AtOrAboveTarget.isNotEmpty()) return AtOrAboveTarget.min()
        return AvailableSpans.max()
    }

    fun LabelFor(SpanDays: Int): String = when (SpanDays) {
        ALL_SPAN_DAYS -> "All"
        1 -> "Today"
        else -> "Next $SpanDays Days"
    }
}
