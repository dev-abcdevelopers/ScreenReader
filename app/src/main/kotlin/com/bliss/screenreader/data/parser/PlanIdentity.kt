@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

object PlanIdentity {

    private val CODE_PREFIX_REGEX = Regex("^\\s*(\\d{1,4})\\s*[-–—]\\s*")

    fun Split(RawLabel: String): Pair<String, String> {
        val TrimmedLabel = RawLabel.trim()
        if (TrimmedLabel.isEmpty()) return "" to ""

        val MatchResult = CODE_PREFIX_REGEX.find(TrimmedLabel)
            ?: return "" to TrimmedLabel

        val CodeValue = MatchResult.groupValues[1]
        val NameValue = TrimmedLabel.substring(MatchResult.range.last + 1).trim()

        if (NameValue.isEmpty()) return CodeValue to ""
        return CodeValue to NameValue
    }

    fun Code(RawLabel: String): String = Split(RawLabel = RawLabel).first

    fun Name(RawLabel: String): String = Split(RawLabel = RawLabel).second

    fun Combine(CodeValue: String, NameValue: String): String {
        return when {
            CodeValue.isEmpty() -> NameValue
            NameValue.isEmpty() -> CodeValue
            else -> "$CodeValue - $NameValue"
        }
    }
}
