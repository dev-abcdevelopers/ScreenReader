@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

/**
 * Splits the source app's combined plan label into its code and its name.
 *
 * The label reads `945 - LIC'S JEEVAN UMANG PLAN`. Only the *first* hyphen
 * separates the two: a plan name can contain further hyphens of its own, as in
 * `821 - NEW MONEY BACK PLAN - 25 YEARS`, and splitting on the last one - or on
 * every one - would truncate the name.
 */
object PlanIdentity {

    /** Leading code and the first separating hyphen, e.g. "945 - ". */
    private val CODE_PREFIX_REGEX = Regex("^\\s*(\\d{1,4})\\s*[-–—]\\s*")

    /**
     * Returns code to name. When the label carries no leading numeric code the
     * whole string is the name, so an unexpected format degrades to "name
     * only" rather than losing text.
     */
    fun Split(RawLabel: String): Pair<String, String> {
        val TrimmedLabel = RawLabel.trim()
        if (TrimmedLabel.isEmpty()) return "" to ""

        val MatchResult = CODE_PREFIX_REGEX.find(TrimmedLabel)
            ?: return "" to TrimmedLabel

        val CodeValue = MatchResult.groupValues[1]
        val NameValue = TrimmedLabel.substring(MatchResult.range.last + 1).trim()

        // "945 - " with nothing after it is a code, not a nameless plan.
        if (NameValue.isEmpty()) return CodeValue to ""
        return CodeValue to NameValue
    }

    fun Code(RawLabel: String): String = Split(RawLabel = RawLabel).first

    fun Name(RawLabel: String): String = Split(RawLabel = RawLabel).second

    /**
     * Rebuilds the combined label. Used when re-emitting captured records as
     * nodes, so a re-parse produces the same split.
     */
    fun Combine(CodeValue: String, NameValue: String): String {
        return when {
            CodeValue.isEmpty() -> NameValue
            NameValue.isEmpty() -> CodeValue
            else -> "$CodeValue - $NameValue"
        }
    }
}
